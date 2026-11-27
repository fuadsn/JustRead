package app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class Panel { Themes, Customise, Fonts, Contents, Search, Note, HlNote, Orientation }

/** A page turn in progress. */
private class Turn {
    var curl by mutableStateOf<Curl?>(null)
    var top: ImageBitmap? = null
    var under: ImageBitmap? = null
    var forward = true
    var start = Offset.Zero
    var grab = Offset.Zero
    var finger = Offset.Zero
    val velocity = VelocityTracker()
    var busy = false
    var slide by mutableFloatStateOf(0f)
    // two-page spreads: the leaf's back and the page uncovered on the left ([under] is the right one)
    var spread = false
    var back: ImageBitmap? = null
    var underL: ImageBitmap? = null
}

/** A row of the table of contents with the page it starts on. */
class TocRow(val title: String, val level: Int, val page: Int)

@Composable
fun ReaderScreen(controller: AppController, shelf: ShelfBook, book: EpubBook, uiDark: Boolean, onClose: () -> Unit) {
    val settings = controller.settings
    val colors = settings.theme.colors(uiDark)
    val pageDark = settings.isDark(uiDark)
    val density = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    var locator by remember(shelf) { mutableStateOf(controller.position(shelf) ?: Locator(0, 0, 0)) }
    var chrome by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var guideY by remember { mutableStateOf(-1f) }
    val pictureCache = remember(book) { PictureCache(book.files) }
    DisposableEffect(pictureCache) { onDispose { pictureCache.clear() } }
    val focus = remember { FocusRequester() }
    val turn = remember { Turn() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // highlights and notes
    val marks = remember(shelf, controller.highlightsVersion) { controller.highlights(shelf) }
    var selection by remember { mutableStateOf<TextSpan?>(null) } // words picked on this page, not yet a highlight
    var active by remember { mutableStateOf<Long?>(null) }        // the highlight whose toolbar is open
    var noteFor by remember { mutableStateOf<Long?>(null) }       // the highlight whose note is being written
    var searchFor by remember { mutableStateOf("") }
    var lastColor by remember { mutableStateOf(HlColor.entries.firstOrNull { it.name == SettingsStore.get("hlColor") } ?: HlColor.Yellow) }

    // reading time for the daily goal (frames stop while the app is in the background)
    LaunchedEffect(shelf) {
        while (true) {
            delay(15_000)
            withFrameNanos { }
            controller.addReadingSeconds(15)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.page)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        // bars and cutouts only: the keyboard (a note being typed) must not change the page layout
        val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val insetTop = safe.getTop(density).toFloat()
        val insetBottom = safe.getBottom(density).toFloat()
        // Phone landscape uses the full width for one reading surface.
        val dpW = w / density.density; val dpH = h / density.density
        val phoneLandscape = dpW > dpH && minOf(dpW, dpH) < 600f
        var manualSpeed by remember(book, phoneLandscape) { mutableStateOf(false) }
        val speedReading = if (phoneLandscape) settings.landscapeSpeedReading else manualSpeed
        fun setSpeed(enabled: Boolean) {
            panel = null
            chrome = !enabled
            if (phoneLandscape) controller.updateSettings(settings.copy(landscapeSpeedReading = enabled))
            else manualSpeed = enabled
        }
        val spread = !phoneLandscape && settings.pageTurn != PageTurn.Scroll && when (settings.columns) {
            Columns.One -> false
            Columns.Two -> dpW >= 480f
            Columns.Auto -> dpW > dpH && dpW >= 600f
        }
        val pw = if (spread) (w / 2f).toInt().toFloat() else w // one page
        val spec = remember(w, h, density.density, settings, insetTop, insetBottom, spread) {
            PageSpec(pw, h, density.density, settings, book.rightToLeft, insetTop, insetBottom, spread, w, h)
        }
        val layoutKey = listOf(w, h, density.density, insetTop, insetBottom, spread, settings.layoutKey())

        val sizes = remember(book) { HashMap<String, androidx.compose.ui.unit.IntSize?>() }
        fun pictureSize(p: String) = synchronized(sizes) { sizes.getOrPut(p) { book.files[p]?.let { pictureSize(it) } } }
        val picture: (String) -> ImageBitmap? = { pictureCache[it] }
        fun paginate(): Pagination {
            val m = TextMeasurer(fontResolver, Density(density.density, 1f), LayoutDirection.Ltr, 0)
            return Paginator(book, spec, m) { pictureSize(it) }.run()
        }

        var pagination by remember(book) { mutableStateOf<Pagination?>(null) }
        LaunchedEffect(book, layoutKey) {
            if (pagination?.spec?.sameLayout(spec) == true) return@LaunchedEffect
            pagination = withContext(Dispatchers.Default) { paginate() }
        }
        val pg = pagination
        if (pg == null || pg.pages.isEmpty()) {
            OpeningScreen(shelf, controller.lastPage(shelf))
            return@BoxWithConstraints
        }

        val pages = pg.pages
        val index = pg.pageFor(locator.chapter, locator.block, locator.char).coerceIn(0, pages.lastIndex)
        val step = if (spread) 2 else 1          // pages per screen
        val left = index - index % step           // the page on the left of the screen
        fun side(i: Int) = if (!spread) PageSide.Single else if (i % 2 == 0) PageSide.Left else PageSide.Right
        val bitmaps = remember(pg, colors, marks) { HashMap<Int, ImageBitmap>() }
        val toc = remember(book, pg) { tocRows(book, pg) }

        fun chapterName(i: Int): String = toc.lastOrNull { it.page <= i }?.title
            ?: book.chapters.getOrNull(pages[i].chapter)?.title.orEmpty()
        // Repagination changes page numbers, but keep the precise character reached in sentence view.
        LaunchedEffect(pg) { controller.savePosition(shelf, locator, index, pages.size, chapterName(index)) }
        if (speedReading && panel == null) {
            SentenceReader(book, locator, settings, colors, onMove = { position ->
                val page = pg.pageFor(position.chapter, position.block, position.char)
                locator = position
                controller.savePosition(shelf, position, page, pages.size, chapterName(page))
            }, onNormal = { setSpeed(false) }, onLibrary = onClose)
            return@BoxWithConstraints
        }
        /** A page's running header: the book's title, or the chapter's on the right-hand page of a spread. */
        fun header(i: Int) = if (side(i) == PageSide.Right) chapterName(i) else book.title
        fun DrawScope.paintPage(i: Int) {
            val page = pages.getOrNull(i)
            drawPage(page, pg.spec, colors, measurer, if (page != null) header(i) else "", if (page != null) "${i + 1}" else "",
                picture, marks, pageDark, side(i))
        }
        /** One page as a GPU picture; a blank sheet past either end of the book. */
        fun bitmap(i: Int): ImageBitmap = bitmaps.getOrPut(i) {
            if (bitmaps.size > 4 * step + 2) bitmaps.keys.filter { abs(it - i) > 3 * step }.forEach { bitmaps.remove(it) }
            renderToBitmap(pw.toInt(), h.toInt(), density) { paintPage(i) }
        }
        /** The page(s) on screen with their left edge at [x]: one page, or a spread from [first]. */
        fun DrawScope.drawScreen(first: Int, x: Float) {
            for (i in first until first + step) {
                val px = x + (i - first) * pw
                val ready = bitmaps[i]
                if (ready != null) drawImage(ready, topLeft = Offset(px, 0f))
                else withTransform({ translate(left = px); clipRect(0f, 0f, pw, h) }) { paintPage(i) }
            }
        }
        fun goTo(i: Int) {
            val k = i.coerceIn(0, pages.lastIndex)
            val p = pages[k]
            locator = Locator(p.chapter, p.startBlock, p.startChar)
            controller.savePosition(shelf, locator, k, pages.size, chapterName(k))
        }
        fun goToLocator(l: Locator) = goTo(pg.pageFor(l.chapter, l.block, l.char))

        // where the text is on each page on screen, for selecting and tapping
        val pageTexts = remember(pg, left) {
            (left until minOf(left + step, pages.size)).associateWith { i ->
                PageText(pages[i], pg.spec, pg.spec.marginTop, (i - left) * pw + pg.spec.shift(side(i)))
            }
        }
        fun pageAt(pos: Offset) = if (spread && pos.x >= pw && left + 1 < pages.size) left + 1 else left
        var selPage by remember { mutableStateOf(left) } // the page the selection is on
        val selText = pageTexts[selPage] ?: pageTexts.getValue(left)
        val chapter = pages[if (selPage in pageTexts) selPage else left].chapter
        LaunchedEffect(left, pg) { selection = null; active = null }
        fun place(h: Highlight): String {
            val p = pg.pageFor(h.span.chapter, h.span.start.block, h.span.start.char)
            return "${chapterName(p)} · Page ${p + 1}"
        }
        fun newHighlight(span: TextSpan): Highlight {
            val h = Highlight(maxOf(nowMillis(), (marks.maxOfOrNull { it.id } ?: 0L) + 1), span, lastColor)
            controller.putHighlight(shelf, h)
            return h
        }

        // render this page and its neighbours ahead of time, one per frame, while nothing moves
        LaunchedEffect(pg, colors, marks, index, w, h) {
            val near = if (spread) listOf(left, left + 1, left + 2, left + 3, left - 2, left - 1) else listOf(index, index + 1, index - 1)
            for (i in near) {
                if (i !in pages.indices || bitmaps.containsKey(i)) continue
                withFrameNanos { }
                if (!turn.busy) bitmap(i)
            }
        }

        /** A curl for the finger at [finger] while the page grabbed at [grab] is turned. */
        fun curlAt(grab: Offset, finger: Offset) =
            Curl.forDrag(grab, finger, Curl.radiusFor(w, (grab - finger).getDistance()), h)

        /** Lets a curl go: on to [turned] (the page is over) or back flat. */
        suspend fun settle(turnedAway: Boolean) {
            val from = turn.curl ?: return
            val r = from.radius
            val target = when {
                // a spread's leaf ends folded flat on the other page; position and radius shrink together
                turn.spread -> if (turnedAway) Curl(0f, 0.5f, 0.5f) else Curl(0f, pw + r, r)
                turnedAway -> Curl(0f, -r, r)
                else -> Curl(0f, w + r, r)
            }
            val a = Animatable(0f)
            a.animateTo(1f, spring(dampingRatio = 1f, stiffness = 900f)) { turn.curl = Curl.lerp(from, target, value) }
        }

        /** Sets up a two-page turn: the leaf's two sides and the pages it uncovers. */
        fun prepareSpread(forward: Boolean) {
            turn.spread = true; turn.forward = forward
            if (forward) { turn.top = bitmap(left + 1); turn.back = bitmap(left + 2); turn.underL = bitmap(left); turn.under = bitmap(left + 3) }
            else { turn.top = bitmap(left); turn.back = bitmap(left - 1); turn.underL = bitmap(left - 2); turn.under = bitmap(left + 1) }
        }

        suspend fun autoTurn(forward: Boolean) {
            if (turn.busy) return
            val target = if (forward) left + step else left - step
            if (target !in pages.indices) return
            turn.busy = true
            when (settings.pageTurn) {
                PageTurn.Curl -> if (spread) {
                    prepareSpread(forward)
                    val grab = Offset(pw, h * 0.86f)
                    fun at(t: Float) = Curl.forSpreadDrag(grab, Offset(pw - t.coerceAtLeast(0.002f) * 2f * pw, h * (0.86f - 0.05f * t)), pw, h)
                    val a = Animatable(0f)
                    turn.curl = at(0f)
                    a.animateTo(1f, tween(520, easing = Curl.Easing)) { turn.curl = at(value) }
                    goTo(target)
                    turn.curl = null; turn.spread = false
                } else {
                    turn.top = if (forward) bitmap(index) else bitmap(index - 1)
                    turn.under = if (forward) bitmap(index + 1) else bitmap(index)
                    val grab = Offset(w, h * 0.86f)
                    fun at(t: Float) = curlAt(grab, Offset(w - t.coerceAtLeast(0.002f) * 2f * w, h * (0.86f - 0.05f * t)))
                    val a = Animatable(if (forward) 0f else 1f)
                    turn.curl = at(a.value)
                    a.animateTo(if (forward) 1f else 0f, tween(450, easing = Curl.Easing)) { turn.curl = at(value) }
                    goTo(target)
                    turn.curl = null
                }
                PageTurn.Slide -> {
                    val a = Animatable(0f)
                    a.animateTo(if (forward) -w else w, tween(250, easing = FastOutSlowInEasing)) { turn.slide = value }
                    goTo(target); turn.slide = 0f
                }
                PageTurn.Scroll -> goTo(target)
            }
            turn.busy = false
        }

        fun hitLink(pos: Offset): Boolean {
            val pi = pageAt(pos)
            val page = pages[pi]
            page.columns.forEachIndexed { c, frags ->
                val x0 = pg.spec.columnX(c) + (pi - left) * pw + pg.spec.shift(side(pi))
                for (f in frags) if (f is Frag.Lines) {
                    val left = x0 + f.x; val top = pg.spec.marginTop + f.y
                    if (pos.x < left || pos.x > left + f.layout.size.width || pos.y < top || pos.y > top + f.height) continue
                    val off = f.layout.getOffsetForPosition(Offset(pos.x - left, pos.y - top + f.layout.getLineTop(f.first)))
                    val ann = f.layout.layoutInput.text.getStringAnnotations(off, off).firstOrNull() ?: continue
                    if (ann.tag == "noteref") { note = book.footnotes[ann.item] ?: "(This note is empty.)"; panel = Panel.Note; return true }
                    if (ann.tag == "link") {
                        val path = ann.item.substringBefore('#'); val frag = ann.item.substringAfter('#', "")
                        val ci = book.chapterIndexFor(path)
                        if (ci >= 0) {
                            val bi = book.chapters[ci].blocks.indexOfFirst { b ->
                                frag.isNotEmpty() && ((b is Block.Text && frag in b.anchors) || (b is Block.Picture && frag in b.anchors))
                            }.coerceAtLeast(0)
                            goToLocator(Locator(ci, bi, 0))
                            return true
                        }
                    }
                }
            }
            return false
        }

        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        val readerModifier = Modifier.fillMaxSize()
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.Spacebar, Key.PageDown, Key.VolumeDown -> { scope.launch { autoTurn(true) }; true }
                    Key.DirectionLeft, Key.PageUp, Key.VolumeUp -> { scope.launch { autoTurn(false) }; true }
                    Key.Escape -> { if (panel != null) panel = null else chrome = !chrome; true }
                    else -> false
                }
            }

        val lineH = settings.fontSizePx * density.density * settings.effectiveLineHeight
        val guide = if (settings.lineGuide) (if (guideY < 0f) h * 0.42f else guideY) else -1f

        if (settings.pageTurn == PageTurn.Scroll) {
            androidx.compose.runtime.key(pg) {
                ScrollReader(pages, pg, colors, index, picture, marks, pageDark, readerModifier, onTap = { chrome = !chrome }) { i ->
                    if (i != index) goTo(i)
                }
            }
        } else {
            Canvas(readerModifier
                .clipToBounds()
                .reserveEdgesForPageTurns()
                .pointerInput(pg, settings.pageTurn, left, settings.lineGuide, marks) {
                    detectTapGestures(
                        onLongPress = { pos ->
                            if (settings.lineGuide) guideY = pos.y
                            if (turn.busy) return@detectTapGestures
                            val pi = pageAt(pos)
                            val word = pageTexts[pi]?.wordAt(pos, pages[pi].chapter) ?: return@detectTapGestures
                            chrome = false; active = null; selPage = pi; selection = word
                        },
                    ) { pos ->
                        if (selection != null || active != null) { selection = null; active = null; return@detectTapGestures }
                        if (chrome) { chrome = false; return@detectTapGestures }
                        val pi = pageAt(pos)
                        val at = pageTexts[pi]?.charAt(pos)
                        val hit = at?.let { p -> marks.lastOrNull { it.span.contains(pages[pi].chapter, p) } }
                        if (hit != null) { active = hit.id; return@detectTapGestures }
                        if (hitLink(pos)) return@detectTapGestures
                        val edge = max(64f * density.density, w * 0.25f)
                        when {
                            pos.x < edge -> scope.launch { autoTurn(false) }
                            pos.x > w - edge -> scope.launch { autoTurn(true) }
                            else -> chrome = true
                        }
                    }
                }
                .pointerInput(pg, settings.pageTurn, left) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (turn.busy || selection != null || active != null) return@detectDragGestures
                            chrome = false
                            val forward = pos.x > w / 2f
                            if ((forward && left + step > pages.lastIndex) || (!forward && left <= 0)) return@detectDragGestures
                            turn.forward = forward; turn.start = pos; turn.busy = true; turn.velocity.resetTracking()
                            if (settings.pageTurn == PageTurn.Curl && spread) {
                                // the leaf is grabbed at its outer edge; everything is worked out in its own space
                                prepareSpread(forward)
                                turn.grab = Offset(pw, pos.y.coerceIn(h * 0.04f, h * 0.96f))
                                turn.finger = turn.grab
                                turn.curl = Curl.forSpreadDrag(turn.grab, turn.finger, pw, h)
                            } else if (settings.pageTurn == PageTurn.Curl) {
                                turn.top = if (forward) bitmap(index) else bitmap(index - 1)
                                turn.under = if (forward) bitmap(index + 1) else bitmap(index)
                                turn.grab = Offset(w, pos.y.coerceIn(h * 0.04f, h * 0.96f))
                                turn.finger = if (forward) turn.grab else Offset(-w, turn.grab.y)
                                turn.curl = curlAt(turn.grab, turn.finger)
                            }
                        },
                        onDrag = { change, _ ->
                            if (!turn.busy) return@detectDragGestures
                            change.consume()
                            turn.velocity.addPosition(change.uptimeMillis, change.position)
                            val p = change.position
                            val dx = p.x - turn.start.x; val dy = p.y - turn.start.y
                            if (settings.pageTurn == PageTurn.Curl && turn.spread) {
                                val lx = if (turn.forward) dx else -dx // the finger's travel, in leaf space
                                turn.finger = Offset((turn.grab.x + lx).coerceIn(-pw, pw - 0.5f), (turn.grab.y + dy).coerceIn(-h * 0.2f, h * 1.2f))
                                turn.curl = Curl.forSpreadDrag(turn.grab, turn.finger, pw, h)
                            } else if (settings.pageTurn == PageTurn.Curl) {
                                // the grabbed edge follows the finger; going back, the page comes in from the left at twice the pace
                                val fx = if (turn.forward) turn.grab.x + dx else -w + 2f * dx
                                turn.finger = Offset(fx.coerceIn(-w, w - 0.5f), (turn.grab.y + dy).coerceIn(-h * 0.2f, h * 1.2f))
                                turn.curl = curlAt(turn.grab, turn.finger)
                            } else {
                                turn.slide = if (turn.forward) dx.coerceIn(-w, 0f) else dx.coerceIn(0f, w)
                            }
                        },
                        onDragEnd = {
                            if (!turn.busy) return@detectDragGestures
                            scope.launch {
                                val forward = turn.forward
                                val vx = turn.velocity.calculateVelocity().x / density.density // dp per second
                                val target = if (forward) left + step else left - step
                                if (settings.pageTurn == PageTurn.Curl && turn.spread) {
                                    // past the spine, or a flick towards the turn, completes it
                                    val leafV = if (forward) vx else -vx
                                    val complete = turn.finger.x < 0f || leafV < -300f
                                    settle(turnedAway = complete)
                                    if (complete) goTo(target)
                                    turn.curl = null; turn.spread = false
                                } else if (settings.pageTurn == PageTurn.Curl) {
                                    // past half the page, or a flick towards the turn, completes it
                                    val complete = if (forward) turn.finger.x < w * Curl.COMPLETE_FRACTION || vx < -300f
                                    else turn.finger.x > -w * Curl.COMPLETE_FRACTION || vx > 300f
                                    settle(turnedAway = forward == complete)
                                    if (complete) goTo(target)
                                    turn.curl = null
                                } else {
                                    val fling = if (forward) vx < -300f else vx > 300f
                                    val complete = abs(turn.slide) > w * 0.25f || fling
                                    val endX = if (!complete) 0f else if (forward) -w else w
                                    val a = Animatable(turn.slide)
                                    a.animateTo(endX, tween(220, easing = LinearOutSlowInEasing)) { turn.slide = value }
                                    if (complete) goTo(target)
                                    turn.slide = 0f
                                }
                                turn.busy = false
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                settle(turnedAway = if (turn.spread) false else !turn.forward)
                                turn.curl = null; turn.spread = false; turn.slide = 0f; turn.busy = false
                            }
                        },
                    )
                }
            ) {
                val c = turn.curl; val top = turn.top; val under = turn.under
                val back = turn.back; val underL = turn.underL
                if (settings.pageTurn == PageTurn.Curl && c != null && turn.spread && top != null && under != null && back != null && underL != null) {
                    drawSpreadCurl(top, back, underL, under, c, turn.forward, colors.page, pageDark)
                } else if (settings.pageTurn == PageTurn.Curl && c != null && top != null && under != null) {
                    drawCurl(top, under, c, colors.page, pageDark)
                } else if (turn.slide != 0f) {
                    val other = if (turn.slide < 0) left + step else left - step
                    drawScreen(left, turn.slide)
                    if (other in pages.indices) drawScreen(other, turn.slide + (if (turn.slide < 0) w else -w))
                    val edge = if (turn.slide < 0) w + turn.slide else turn.slide
                    drawLine(colors.secondary.copy(alpha = 0.25f), Offset(edge, 0f), Offset(edge, size.height), 1f)
                } else {
                    // the pre-rendered pages are GPU textures; fall back to drawing the text
                    drawScreen(left, 0f)
                    selection?.let { drawSelection(selText.boxes(it, density.density), if (pageDark) Color(0xFFE7C399) else Color(0xFF6A4A2B)) }
                    if (guide >= 0f) {
                        // line guide: everything outside a three-line band fades back
                        val band = lineH * 1.6f
                        val veil = colors.page.copy(alpha = 0.62f)
                        drawRect(veil, Offset.Zero, Size(size.width, (guide - band / 2f).coerceAtLeast(0f)))
                        drawRect(veil, Offset(0f, guide + band / 2f), Size(size.width, (size.height - guide - band / 2f).coerceAtLeast(0f)))
                    }
                }
            }
        }

        fun onScreen(l: Locator) = pg.pageFor(l.chapter, l.block, l.char) - left in 0 until step
        val bookmarked = controller.bookmarks(shelf).any { onScreen(it.locator) }
        fun toggleBookmark() {
            if (bookmarked) controller.removeBookmarks(shelf) { onScreen(it.locator) }
            else controller.addBookmark(shelf, Locator(pages[left].chapter, pages[left].startBlock, pages[left].startChar))
        }

        if (chrome && panel == null) BackAction { chrome = false }
        if ((selection != null || active != null) && panel == null) BackAction { selection = null; active = null }

        // the selection's handles and toolbar, or a highlight's toolbar
        if (panel == null && settings.pageTurn != PageTurn.Scroll) {
            val sel = selection
            val hl = active?.let { id -> marks.firstOrNull { it.id == id } }
            val boxes = when {
                sel != null -> selText.boxes(sel, density.density)
                hl != null -> pageTexts.values.map { it.boxes(hl.span, density.density) }.firstOrNull { it.isNotEmpty() } ?: emptyList()
                else -> emptyList()
            }
            if (boxes.isNotEmpty()) {
                val dp = density.density
                if (sel != null) {
                    val lineH = boxes.first().height
                    SelectionHandle(Offset(boxes.first().left - 10f * dp, boxes.first().bottom + 10f * dp)) { p ->
                        val at = selText.charAt(Offset(p.x + 10f * dp, p.y - 10f * dp - lineH / 2f), nearest = true)?.let { selText.snapToWord(it, false) } ?: return@SelectionHandle
                        selection = TextSpan(chapter, at, sel.end).normalized().takeIf { !it.isEmpty } ?: sel
                    }
                    SelectionHandle(Offset(boxes.last().right + 10f * dp, boxes.last().bottom + 10f * dp)) { p ->
                        val at = selText.charAt(Offset(p.x - 10f * dp, p.y - 10f * dp - lineH / 2f), nearest = true)?.let { selText.snapToWord(it, true) } ?: return@SelectionHandle
                        selection = TextSpan(chapter, sel.start, at).normalized().takeIf { !it.isEmpty } ?: sel
                    }
                }
                val barH = 52f * dp
                val below = boxes.last().bottom + (if (sel != null) 28f else 12f) * dp
                val y = if (below + barH < h - insetBottom - 16f * dp) below
                    else (boxes.first().top - 12f * dp - barH).coerceAtLeast(insetTop + 8f * dp)
                val bar = Modifier.offset { IntOffset(0, y.roundToInt()) }.padding(horizontal = 16.dp).fillMaxWidth()
                if (sel != null) {
                    val text = book.textOf(sel)
                    SelectionToolbar(bar,
                        onHighlight = { val n = newHighlight(sel); selection = null; active = n.id },
                        onNote = { val n = newHighlight(sel); selection = null; active = n.id; noteFor = n.id; panel = Panel.HlNote },
                        onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); selection = null },
                        onShare = {
                            Hooks.share("“$text”\n\n— ${book.title}${if (book.author.isNotBlank()) ", ${book.author}" else ""}")
                            selection = null
                        },
                        onTranslate = { translate(context, text); selection = null },
                        onSearch = { searchFor = text.take(80); selection = null; panel = Panel.Search },
                        onSelectAll = { selText.all(chapter)?.let { selection = it } },
                    )
                } else if (hl != null) {
                    HighlightToolbar(bar, hl,
                        onColor = { c ->
                            lastColor = c; SettingsStore.put("hlColor", c.name)
                            controller.putHighlight(shelf, hl.copy(color = c))
                        },
                        onNote = { noteFor = hl.id; panel = Panel.HlNote },
                        onRemoveHighlight = { controller.removeHighlight(shelf, hl.id); active = null },
                        onRemoveNote = { controller.putHighlight(shelf, hl.copy(note = "", edited = nowMillis())) },
                    )
                }
            }
        }
        ReaderChrome(
            visible = chrome && panel == null,
            book = book, chapter = chapterName(index), pages = pages, index = left, bookmarked = bookmarked, settings = settings,
            wide = dpW >= 600f, landscape = dpW > dpH, step = step,
            onClose = onClose, onBookmark = { toggleBookmark() },
            onScrub = { goTo(it) },
            onContents = { panel = Panel.Contents }, onSearch = { panel = Panel.Search }, onThemes = { panel = Panel.Themes },
            onOrientation = { panel = Panel.Orientation }, onPractice = { setSpeed(true) },
            onSettings = { controller.updateSettings(it) },
            onShare = { Hooks.share("${book.title}${if (book.author.isNotBlank()) " by ${book.author}" else ""} — reading it in JustRead") },
        )

        when (panel) {
            Panel.Themes -> ThemesSheet(settings, uiDark, onChange = { controller.updateSettings(it) },
                onCustomise = { panel = Panel.Customise }, onDismiss = { panel = null })
            Panel.Customise -> CustomiseScreen(settings, colors, onChange = { controller.updateSettings(it) },
                onFonts = { panel = Panel.Fonts }, onDone = { panel = Panel.Themes })
            Panel.Fonts -> FontScreen(settings, onChange = { controller.updateSettings(it) }, onBack = { panel = Panel.Customise })
            Panel.Contents -> ContentsSheet(shelf, book, controller, pg, toc, left,
                onPick = { goTo(it); panel = null; chrome = false },
                onPickLocator = { goToLocator(it); panel = null; chrome = false },
                onDismiss = { panel = null },
                marks = marks, place = { place(it) },
                onNote = { h -> noteFor = h.id; panel = Panel.HlNote },
                onCopy = { h -> clipboard.setText(androidx.compose.ui.text.AnnotatedString(book.textOf(h.span))) },
                onRemove = { h -> controller.removeHighlight(shelf, h.id) }, step = step)
            Panel.Search -> SearchSheet(book, pg, toc,
                onPick = { goTo(it); panel = null; chrome = false; searchFor = "" }, onDismiss = { panel = null; searchFor = "" }, initial = searchFor)
            Panel.HlNote -> {
                val h = marks.firstOrNull { it.id == noteFor }
                if (h == null) LaunchedEffect(Unit) { panel = null }
                else NoteSheet(h, book.textOf(h.span), place(h),
                    onSave = { t ->
                        controller.putHighlight(shelf, h.copy(note = t, edited = nowMillis()))
                        panel = null; noteFor = null; active = null
                    },
                    onDelete = { controller.putHighlight(shelf, h.copy(note = "", edited = nowMillis())); panel = null; noteFor = null },
                    onDismiss = { panel = null; noteFor = null })
            }
            Panel.Orientation -> androidx.compose.material3.AlertDialog(
                onDismissRequest = { panel = null },
                title = { Text("Reading orientation") },
                text = {
                    Column {
                        for (option in ReadingOrientation.entries) {
                            MenuRow(option.label, if (option == settings.orientation) JrIcons.Check else null) {
                                controller.updateSettings(settings.copy(orientation = option))
                                panel = null
                            }
                        }
                    }
                },
                confirmButton = {},
            )
            Panel.Note -> JrSheet(onDismiss = { panel = null }) {
                SheetTitle("Note", onClose = { panel = null })
                Text(note.orEmpty(), style = JrType.bodyLarge, color = LocalJr.current.onBackground,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
            }
            null -> {}
        }
    }
}

/** The table of contents with the page each entry starts on (the spine's chapters if the book has no TOC). */
fun tocRows(book: EpubBook, pg: Pagination): List<TocRow> {
    val fromToc = book.toc.mapNotNull { e ->
        val ci = book.chapterIndexFor(e.path)
        if (ci < 0) null else {
            val bi = e.fragment?.let { f -> book.chapters[ci].blocks.indexOfFirst { b -> b is Block.Text && f in b.anchors } }?.takeIf { it >= 0 } ?: 0
            TocRow(e.title.ifBlank { book.chapters[ci].title }, e.level, pg.pageFor(ci, bi, 0))
        }
    }
    return fromToc.ifEmpty { book.chapters.mapIndexed { i, c -> TocRow(c.title.ifBlank { "Section ${i + 1}" }, 0, pg.chapterFirstPage[i]) } }
}

@Composable
fun OpeningScreen(shelf: ShelfBook, page: Int?) {
    val jr = LocalJr.current
    val t = rememberInfiniteTransition()
    val progress = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart))
    val x = { progress.value }
    Box(Modifier.fillMaxSize().background(jr.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BookCover(shelf, Modifier.size(200.dp, 300.dp), corner = 6.dp)
            Spacer(Modifier.height(36.dp))
            Text(shelf.title, style = JrType.serif(24, 32), color = jr.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp))
            if (shelf.author.isNotBlank()) Text(shelf.author, style = JrType.bodyLarge, color = jr.onVariant)
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(128.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(jr.secondaryContainer)) {
                // read in the layout phase, so the running bar doesn't recompose every frame
                Box(Modifier.offset { IntOffset(((176f * x() - 48f) * density).roundToInt(), 0) }.size(48.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(jr.primary))
            }
            Spacer(Modifier.height(12.dp))
            Text(if (page != null) "Opening at page $page" else "Opening", style = JrType.caption, color = jr.onVariant)
        }
    }
}

@Composable
private fun ScrollReader(
    pages: List<Page>, pg: Pagination, colors: ThemeColors, index: Int, pictures: (String) -> ImageBitmap?,
    marks: List<Highlight>, pageDark: Boolean, modifier: Modifier, onTap: () -> Unit, onVisible: (Int) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val density = LocalDensity.current
    val reportVisible by androidx.compose.runtime.rememberUpdatedState(onVisible)
    LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex }.collect { reportVisible(it) } }
    val pad = pg.spec.marginTop / 3f
    val itemH = with(density) { (pg.spec.columnHeight + pad).toDp() }
    LazyColumn(state = state, modifier = modifier.windowInsetsPadding(WindowInsets.statusBars).pointerInput(Unit) { detectTapGestures { onTap() } }) {
        itemsIndexed(pages) { i, page ->
            if (page.pageInChapter == 0 && i > 0) {
                Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(60.dp).height(1.dp).background(colors.secondary.copy(alpha = 0.4f)))
                }
            }
            Canvas(Modifier.fillMaxWidth().height(itemH)) { drawColumns(page, pg.spec, colors, pad / 2f, pictures, marks, pageDark) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

/** Top bar (back, title and chapter, bookmark) and bottom bar (scrubber, page count, actions). */
@Composable
private fun ReaderChrome(
    visible: Boolean, book: EpubBook, chapter: String, pages: List<Page>, index: Int, bookmarked: Boolean, settings: ReaderSettings,
    wide: Boolean, landscape: Boolean, step: Int,
    onClose: () -> Unit, onBookmark: () -> Unit, onScrub: (Int) -> Unit,
    onContents: () -> Unit, onSearch: () -> Unit, onThemes: () -> Unit,
    onOrientation: () -> Unit, onPractice: () -> Unit, onSettings: (ReaderSettings) -> Unit, onShare: () -> Unit,
) {
    val jr = LocalJr.current
    if (wide) {
        WideChrome(visible, book, chapter, pages, index, bookmarked, settings, landscape, step,
            onClose, onBookmark, onScrub, onContents, onSearch, onThemes, onOrientation, onPractice, onSettings, onShare)
        return
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Row(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }.background(jr.container).windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconBtn(JrIcons.Back, "Back to library", jr.onBackground, onClick = onClose)
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = JrType.title, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (chapter.isNotBlank()) Text(chapter, style = JrType.caption, color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconBtn(if (bookmarked) JrIcons.BookmarkFilled else JrIcons.Bookmark,
                    if (bookmarked) "Remove bookmark" else "Bookmark this page", if (bookmarked) jr.primary else jr.onBackground, onClick = onBookmark)
            }
        }
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            var scrub by remember(index) { mutableStateOf(index) }
            val last = max(1, pages.lastIndex)
            Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }.background(jr.container).windowInsetsPadding(WindowInsets.navigationBars).padding(top = 6.dp, bottom = 12.dp)) {
                JrSlider(scrub / last.toFloat(), "Position in book", Modifier.padding(horizontal = 24.dp),
                    onDone = { if (scrub != index) onScrub(scrub) }) { scrub = (it * last).roundToInt() }
                val p = pages[scrub.coerceIn(0, pages.lastIndex)]
                val left = p.chapterPageCount - p.pageInChapter - 1
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 4.dp)) {
                    Text("Page ${scrub + 1} of ${pages.size}", style = JrType.caption, color = jr.onVariant, modifier = Modifier.weight(1f))
                    Text(when { left <= 0 -> "Last page in chapter"; left == 1 -> "1 page left in chapter"; else -> "$left pages left in chapter" },
                        style = JrType.caption, color = jr.onVariant)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    ChromeAction("Contents", Modifier.weight(1f), onContents) { Icon(JrIcons.Contents, null, tint = it, modifier = Modifier.size(24.dp)) }
                    ChromeAction("Search", Modifier.weight(1f), onSearch) { Icon(JrIcons.Search, null, tint = it, modifier = Modifier.size(24.dp)) }
                    ChromeAction("Themes", Modifier.weight(1f), onThemes) {
                        Text("Aa", style = JrType.sans(18, 24, androidx.compose.ui.text.font.FontWeight.Medium), color = it, modifier = Modifier.height(24.dp))
                    }
                    var more by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        ChromeAction("More", Modifier.fillMaxWidth(), { more = true }) { Icon(JrIcons.More, null, tint = it, modifier = Modifier.size(24.dp)) }
                        DropdownMenu(more, onDismissRequest = { more = false }, containerColor = jr.menu) {
                            MenuRow(if (bookmarked) "Remove bookmark" else "Add bookmark", if (bookmarked) JrIcons.BookmarkFilled else JrIcons.Bookmark) { more = false; onBookmark() }
                            MenuRow("Line guide", JrIcons.LineGuide, if (settings.lineGuide) "On" else "Off") { onSettings(settings.copy(lineGuide = !settings.lineGuide)) }
                            MenuRow("Speed reading", JrIcons.LineGuide) { more = false; onPractice() }
                            MenuRow("Orientation", JrIcons.LockRotation, settings.orientation.label) { more = false; onOrientation() }
                            MenuRow("Share", JrIcons.Share) { more = false; onShare() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The reader's bars on a wide screen, as in the design: one top bar with the actions spelt out, and the
 * position in the book along the bottom (in one row when the screen is held sideways).
 */
@Composable
private fun WideChrome(
    visible: Boolean, book: EpubBook, chapter: String, pages: List<Page>, index: Int, bookmarked: Boolean, settings: ReaderSettings,
    landscape: Boolean, step: Int,
    onClose: () -> Unit, onBookmark: () -> Unit, onScrub: (Int) -> Unit,
    onContents: () -> Unit, onSearch: () -> Unit, onThemes: () -> Unit,
    onOrientation: () -> Unit, onPractice: () -> Unit, onSettings: (ReaderSettings) -> Unit, onShare: () -> Unit,
) {
    val jr = LocalJr.current
    val sides = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    val compact = LocalConfiguration.current.screenHeightDp < 480
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, Modifier.align(Alignment.TopCenter),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Row(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }.background(jr.container)
                .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(sides)
                .height(if (compact) 56.dp else 64.dp).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconBtn(JrIcons.Back, "Back to library", jr.onBackground, onClick = onClose)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(book.title, style = JrType.serif(if (compact) 15 else 16, if (compact) 20 else 24), color = jr.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (chapter.isNotBlank()) Text(chapter, style = JrType.caption, color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PillAction("Contents", onContents) { Icon(JrIcons.Contents, null, tint = it, modifier = Modifier.size(24.dp)) }
                PillAction("Search", onSearch) { Icon(JrIcons.Search, null, tint = it, modifier = Modifier.size(24.dp)) }
                PillAction("Themes", onThemes) {
                    Text("Aa", style = JrType.sans(17, 24, androidx.compose.ui.text.font.FontWeight.Medium), color = it,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.width(24.dp))
                }
                IconBtn(if (bookmarked) JrIcons.BookmarkFilled else JrIcons.Bookmark,
                    if (bookmarked) "Remove bookmark" else if (step > 1) "Bookmark these pages" else "Bookmark this page",
                    if (bookmarked) jr.primary else jr.onBackground, onClick = onBookmark)
                var more by remember { mutableStateOf(false) }
                Box {
                    IconBtn(JrIcons.More, "More reading options", jr.onBackground) { more = true }
                    DropdownMenu(more, onDismissRequest = { more = false }, containerColor = jr.menu) {
                        MenuRow("Line guide", JrIcons.LineGuide, if (settings.lineGuide) "On" else "Off") { onSettings(settings.copy(lineGuide = !settings.lineGuide)) }
                        MenuRow("Speed reading", JrIcons.LineGuide) { more = false; onPractice() }
                        MenuRow("Orientation", JrIcons.LockRotation, settings.orientation.label) { more = false; onOrientation() }
                        MenuRow("Share", JrIcons.Share) { more = false; onShare() }
                    }
                }
            }
        }
        AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            var scrub by remember(index) { mutableStateOf(index) }
            val last = max(1, pages.lastIndex)
            val p = pages[scrub.coerceIn(0, pages.lastIndex)]
            val leftIn = p.chapterPageCount - p.pageInChapter - 1
            val where = if (step > 1 && scrub + 1 < pages.size) "Pages ${scrub + 1}–${scrub + 2} of ${pages.size}" else "Page ${scrub + 1} of ${pages.size}"
            val rest = when { leftIn <= 0 -> "Last page in chapter"; leftIn == 1 -> "1 page left in chapter"; else -> "$leftIn pages left in chapter" }
            val text = if (compact) JrType.caption else JrType.body
            val bar = Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }.background(jr.container)
                .windowInsetsPadding(WindowInsets.navigationBars).windowInsetsPadding(sides)
            val slider: @Composable (Modifier) -> Unit = { m ->
                JrSlider(scrub / last.toFloat(), "Position in book", m, onDone = { if (scrub != index) onScrub(scrub) }) {
                    scrub = ((it * last).roundToInt()).let { v -> v - v % step }
                }
            }
            if (landscape) {
                Row(bar.height(if (compact) 56.dp else 72.dp).padding(horizontal = if (compact) 24.dp else 32.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 24.dp)) {
                    Text(where, style = text, color = jr.onVariant)
                    slider(Modifier.weight(1f))
                    Text(rest, style = text, color = jr.onVariant)
                }
            } else {
                Column(bar.padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 16.dp)) {
                    slider(Modifier)
                    Row(Modifier.fillMaxWidth()) {
                        Text(where, style = text, color = jr.onVariant, modifier = Modifier.weight(1f))
                        Text(rest, style = text, color = jr.onVariant)
                    }
                }
            }
        }
    }
}

/** A labelled action in the wide top bar: icon and word in a 40 dp pill. */
@Composable
private fun PillAction(label: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val jr = LocalJr.current
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon(jr.onVariant)
        Text(label, style = JrType.label, color = jr.onVariant)
    }
}

@Composable
private fun ChromeAction(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val jr = LocalJr.current
    Column(modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        icon(jr.onVariant)
        Text(label, style = JrType.labelSmall, color = jr.onVariant)
    }
}

/** A selection handle: a 44 dp touch target that reports where the finger has moved it to, in page coordinates. */
@Composable
private fun SelectionHandle(center: Offset, onMove: (Offset) -> Unit) {
    val c by androidx.compose.runtime.rememberUpdatedState(center)
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    val half = with(LocalDensity.current) { 22.dp.toPx() }
    Box(Modifier.offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }.size(44.dp)
        .pointerInput(Unit) {
            var at = Offset.Zero
            detectDragGestures(onDragStart = { at = c - Offset(half, half) + it }) { change, d ->
                change.consume(); at += d; move(at)
            }
        })
}

/** Opens a translation app for [text]: the system's Translate action, else any app that handles selected text. */
private fun translate(context: android.content.Context, text: String) {
    val translate = android.content.Intent("android.intent.action.TRANSLATE").putExtra(android.content.Intent.EXTRA_TEXT, text)
    val process = android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, text).putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    runCatching { context.startActivity(translate) }
        .recoverCatching { context.startActivity(android.content.Intent.createChooser(process, "Translate")) }
}
