package app.reader

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------- icons

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0z"
private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

private fun icon(name: String, stroke: List<String>, fill: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in stroke) addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        for (d in fill) addPath(addPathNodes(d), fill = SolidColor(Color.Black))
    }.build()

/** Stroke icons from the design (24 px grid, 2 px round strokes). */
object JrIcons {
    val More by lazy { icon("more", emptyList(), listOf(circle(12f, 5f, 1.7f), circle(12f, 12f, 1.7f), circle(12f, 19f, 1.7f))) }
    val Check by lazy { icon("check", listOf("M5 12.5l4.5 4.5L19 7.5")) }
    val Search by lazy { icon("search", listOf(circle(10.5f, 10.5f, 6.5f), "M15.5 15.5 20 20")) }
    val Contents by lazy { icon("contents", listOf("M9 6h11", "M9 12h11", "M9 18h11"), listOf(circle(4.5f, 6f, 1.3f), circle(4.5f, 12f, 1.3f), circle(4.5f, 18f, 1.3f))) }
    val Close by lazy { icon("close", listOf("M6 6l12 12", "M18 6 6 18")) }
    val Sort by lazy { icon("sort", listOf("M8 5v14", "M4.5 8.5 8 5l3.5 3.5", "M16 19V5", "M12.5 15.5 16 19l3.5-3.5")) }
    val Bookmark by lazy { icon("bookmark", listOf("M7 4h10a1 1 0 0 1 1 1v15l-6-3.5L6 20V5a1 1 0 0 1 1-1z")) }
    val BookmarkFilled by lazy { icon("bookmark-filled", listOf("M7 4h10a1 1 0 0 1 1 1v15l-6-3.5L6 20V5a1 1 0 0 1 1-1z"), listOf("M7 4h10a1 1 0 0 1 1 1v15l-6-3.5L6 20V5a1 1 0 0 1 1-1z")) }
    val ChevronDown by lazy { icon("chevron-down", listOf("M7 10l5 5 5-5")) }
    val ChevronRight by lazy { icon("chevron-right", listOf("M9 6l6 6-6 6")) }
    val Back by lazy { icon("back", listOf("M20 12H5", "M11 6l-6 6 6 6")) }
    val Sun by lazy { icon("sun", listOf(circle(12f, 12f, 3.5f), "M12 3v2", "M12 19v2", "M3 12h2", "M19 12h2", "M5.6 5.6 7 7", "M17 17l1.4 1.4", "M5.6 18.4 7 17", "M17 7l1.4-1.4")) }
    val Cloud by lazy { icon("cloud", listOf("M7.5 18.5a4.5 4.5 0 0 1-.7-8.95 5.5 5.5 0 0 1 10.6 1.2A3.9 3.9 0 0 1 17 18.5z")) }
    val Grid by lazy { icon("grid", listOf(rrect(4f, 4f, 6.5f, 6.5f, 1f), rrect(13.5f, 4f, 6.5f, 6.5f, 1f), rrect(4f, 13.5f, 6.5f, 6.5f, 1f), rrect(13.5f, 13.5f, 6.5f, 6.5f, 1f))) }
    val LineGuide by lazy { icon("line-guide", listOf("M4 5.5h16", "M4 18.5h16", rrect(3f, 9f, 18f, 6f, 1.5f))) }
    val LockRotation by lazy { icon("lock-rotation", listOf("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5V8H16", rrect(9.25f, 11f, 5.5f, 4.5f, 1f), "M10.5 11V9.9a1.5 1.5 0 0 1 3 0V11")) }
    val Share by lazy { icon("share", listOf(circle(17.5f, 5.5f, 2.5f), circle(6.5f, 12f, 2.5f), circle(17.5f, 18.5f, 2.5f), "M8.7 10.8l6.6-4", "M8.7 13.2l6.6 4")) }
    val Plus by lazy { icon("plus", listOf("M12 5v14", "M5 12h14")) }
    val Trash by lazy { icon("trash", listOf("M5 7h14", "M10 4h4", "M7 7l1 13h8l1-13")) }
    val Download by lazy { icon("download", listOf("M12 4v11", "M7 10.5l5 5 5-5", "M5 20h14")) }
    val Alert by lazy { icon("alert", listOf(circle(12f, 12f, 8.5f), "M12 7.5v5.5"), listOf(circle(12f, 16.3f, 1.3f))) }
    val Note by lazy { icon("note", listOf(rrect(5f, 4f, 14f, 16f, 2f), "M9 9h6", "M9 13h6", "M9 17h3")) }
    val NoteRemove by lazy { icon("note-remove", listOf("M14.5 4.5l4 4L10 17H6v-4z", "M4 20h16")) }
    val Highlight by lazy { icon("highlight", listOf("M14.5 4.5l4 4L10 17H6v-4z", "M4 20h16")) }
    val Target by lazy { icon("target", listOf(circle(12f, 12f, 8f), circle(12f, 12f, 3.5f))) }
}

@Composable
fun IconBtn(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 48.dp, onClick: () -> Unit) {
    Box(modifier.size(size).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

// ---------------------------------------------------------------- back handling

/** Lets screens close their topmost overlay on the system back gesture (Android). */
class BackStack {
    val actions = mutableStateListOf<() -> Unit>()
    val canGoBack get() = actions.isNotEmpty()
    fun back(): Boolean { val a = actions.lastOrNull() ?: return false; a(); return true }
}

val LocalBackStack = compositionLocalOf { BackStack() }

/** While composed and [enabled], the system back gesture runs [onBack] (topmost wins). */
@Composable
fun BackAction(enabled: Boolean = true, onBack: () -> Unit) {
    val stack = LocalBackStack.current
    val current by rememberUpdatedState(onBack)
    DisposableEffect(stack, enabled) {
        val action: () -> Unit = { current() }
        if (enabled) stack.actions.add(action)
        onDispose { stack.actions.remove(action) }
    }
}

// ---------------------------------------------------------------- slider

/**
 * The design's slider: a 16 dp track split by a tall 4 dp handle, with stop dots when stepped.
 * [value] is 0…1; [steps] is the number of positions (0 = continuous).
 */
@Composable
fun JrSlider(
    value: Float, label: String, modifier: Modifier = Modifier, steps: Int = 0,
    onDone: () -> Unit = {}, onChange: (Float) -> Unit,
) {
    val jr = LocalJr.current
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    fun snap(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return if (steps > 1) (c * (steps - 1)).roundToInt() / (steps - 1).toFloat() else c
    }
    Canvas(
        modifier.fillMaxWidth().height(44.dp)
            .semantics { contentDescription = label }
            .pointerInput(steps) { detectTapGestures { change(snap(it.x / size.width)); done() } }
            .pointerInput(steps) {
                detectHorizontalDragGestures(
                    onDragStart = { change(snap(it.x / size.width)) },
                    onDragEnd = { done() },
                    onDragCancel = { done() },
                ) { c, _ -> c.consume(); change(snap(c.position.x / size.width)) }
            }
    ) {
        val w = size.width
        val dp = density
        val track = 16f * dp
        val top = (size.height - track) / 2f
        val hx = (value.coerceIn(0f, 1f) * w).coerceIn(2f * dp, w - 2f * dp)
        val gap = 8f * dp
        val big = CornerRadius(8f * dp); val small = CornerRadius(2f * dp)
        if (hx - gap > 0f) {
            drawPath(Path().apply {
                addRoundRect(RoundRect(0f, top, hx - gap, top + track, big, small, small, big))
            }, jr.primary)
        }
        if (hx + gap < w) {
            drawPath(Path().apply {
                addRoundRect(RoundRect(hx + gap, top, w, top + track, small, big, big, small))
            }, jr.secondaryContainer)
        }
        val dotR = 2f * dp
        val cy = size.height / 2f
        if (steps > 1) {
            for (i in 0 until steps) {
                val x = 8f * dp + (w - 16f * dp) * i / (steps - 1)
                if (kotlin.math.abs(x - hx) < gap + dotR) continue
                drawCircle(if (x < hx) jr.onPrimary else jr.onSecondaryContainer, dotR, Offset(x, cy))
            }
        } else if (w - 8f * dp > hx + gap + dotR) {
            drawCircle(jr.primary, dotR, Offset(w - 8f * dp, cy))
        }
        drawRoundRect(jr.primary, Offset(hx - 2f * dp, 0f), Size(4f * dp, size.height), CornerRadius(2f * dp))
    }
}

// ---------------------------------------------------------------- segmented button

@Composable
fun Segmented(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val jr = LocalJr.current
    Row(modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, jr.outline, RoundedCornerShape(20.dp))) {
        labels.forEachIndexed { i, l ->
            if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(jr.outline))
            val on = i == selected
            Row(Modifier.weight(1f).height(40.dp).background(if (on) jr.secondaryContainer else Color.Transparent)
                .clickable { onSelect(i) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (on) {
                    Icon(JrIcons.Check, null, tint = jr.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(l, style = JrType.label, color = if (on) jr.onSecondaryContainer else jr.onBackground, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- switch row

@Composable
fun JrSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val jr = LocalJr.current
    Switch(
        checked = checked, onCheckedChange = onChange,
        thumbContent = if (checked) { { Icon(JrIcons.Check, null, Modifier.size(16.dp)) } } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = jr.onPrimary, checkedTrackColor = jr.primary, checkedIconColor = jr.primary,
            checkedBorderColor = jr.primary,
            uncheckedThumbColor = jr.outline, uncheckedTrackColor = jr.container, uncheckedBorderColor = jr.outline,
        ),
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val jr = LocalJr.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = JrType.bodyLarge, color = jr.onBackground)
            if (subtitle != null) Text(subtitle, style = JrType.caption, color = jr.onVariant)
        }
        Spacer(Modifier.width(12.dp))
        JrSwitch(checked, onChange)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalJr.current.outlineVariant))
}

// ---------------------------------------------------------------- progress bar with stop

/** A thin progress bar: filled part, a gap, the rest, and a stop dot at the end. */
@Composable
fun StopProgress(fraction: Float, modifier: Modifier = Modifier, track: Color = LocalJr.current.track) {
    val jr = LocalJr.current
    Canvas(modifier.height(4.dp)) {
        val dp = density
        val h = size.height
        val activeW = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(4f * dp).coerceAtMost(size.width)
        drawRoundRect(jr.primary, Offset.Zero, Size(activeW, h), CornerRadius(h / 2))
        val restX = activeW + 4f * dp
        if (restX < size.width) {
            drawRoundRect(track, Offset(restX, 0f), Size(size.width - restX, h), CornerRadius(h / 2))
            drawRoundRect(jr.primary, Offset(size.width - h, 0f), Size(h, h), CornerRadius(h / 2))
        }
    }
}

/** The daily goal ring. */
@Composable
fun GoalRing(fraction: Float, modifier: Modifier = Modifier) {
    val jr = LocalJr.current
    Canvas(modifier.size(36.dp)) {
        val sw = 5f * density
        val inset = sw / 2f
        val sz = Size(size.width - sw, size.height - sw)
        drawArc(jr.track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
        if (fraction > 0f) drawArc(jr.tertiary, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), sz,
            style = Stroke(sw, cap = StrokeCap.Butt))
    }
}

// ---------------------------------------------------------------- bottom sheet

/**
 * A modal bottom sheet: scrim, 28 dp top corners, drag handle. Slides up when shown; dragging the
 * handle down or tapping the scrim closes it.
 */
@Composable
fun JrSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, fullHeightTop: Dp? = null, maxWidth: Dp = 640.dp, scrim: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val jr = LocalJr.current
    val density = LocalDensity.current
    val offset = remember { Animatable(1f) } // fraction of the sheet's height pushed down
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(280, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))) }
    BackAction(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        // the scrim's fade is read while drawing, so the sheet isn't recomposed on every frame of it
        Box(Modifier.fillMaxSize().drawBehind { if (scrim) drawRect(jr.scrim.copy(alpha = jr.scrim.alpha * (1f - offset.value))) }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        BoxWithConstraints(Modifier.align(Alignment.BottomCenter).widthIn(max = maxWidth).fillMaxWidth()) {
            val maxH = constraints.maxHeight
            Column(
                (if (fullHeightTop != null) Modifier.padding(top = fullHeightTop).fillMaxSize() else Modifier)
                    .then(modifier)
                    .fillMaxWidth()
                    .offset { IntOffset(0, (offset.value * maxH + drag.value).roundToInt()) }
                    .shadow(if (scrim) 0.dp else 12.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = jr.shadow, spotColor = jr.shadow)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(jr.sheet)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    // stays above the keyboard when a field in the sheet has it open
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                Box(Modifier.fillMaxWidth().height(36.dp).pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (drag.value > with(density) { 96.dp.toPx() }) onDismiss() else drag.animateTo(0f)
                            }
                        },
                    ) { c, dy -> c.consume(); scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) } }
                }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(jr.outline))
                }
                content()
            }
        }
    }
}

/**
 * A sheet along the left or right edge of a wide screen, full height, its inner corners rounded. With
 * [scrim] the page behind dims; either way a tap outside closes it.
 */
@Composable
fun JrSideSheet(onDismiss: () -> Unit, fromLeft: Boolean, width: Dp = 400.dp, scrim: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val jr = LocalJr.current
    val offset = remember { Animatable(1f) } // fraction of the sheet's width pushed off screen
    LaunchedEffect(Unit) { offset.animateTo(0f, tween(260, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))) }
    BackAction(onBack = onDismiss)
    val shape = if (fromLeft) RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp) else RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().drawBehind { if (scrim) drawRect(jr.scrim.copy(alpha = jr.scrim.alpha * (1f - offset.value))) }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        // the sheet runs to the screen edge; a cutout or bar on that side only pushes its content in
        val edge = bars.only(if (fromLeft) WindowInsetsSides.Start else WindowInsetsSides.End)
        val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
        val extra = with(LocalDensity.current) {
            (if (fromLeft) edge.getLeft(this, dir) else edge.getRight(this, dir)).toDp()
        }
        Column(Modifier.align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .width(width + extra)
            .offset { IntOffset(((if (fromLeft) -1 else 1) * offset.value * (width + extra).toPx()).roundToInt(), 0) }
            .shadow(if (scrim) 0.dp else 12.dp, shape, ambientColor = jr.shadow, spotColor = jr.shadow)
            .clip(shape).background(jr.sheet)
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(edge)
            .windowInsetsPadding(bars.only(WindowInsetsSides.Vertical)),
            content = content)
    }
}

@Composable
fun SheetTitle(title: String, onClose: (() -> Unit)?) {
    val jr = LocalJr.current
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = JrType.headline, color = jr.onBackground, modifier = Modifier.weight(1f))
        if (onClose != null) IconBtn(JrIcons.Close, "Close", jr.onBackground, onClick = onClose) else Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = JrType.label, color = LocalJr.current.onVariant, modifier = modifier)
}

// ---------------------------------------------------------------- covers

private class CoverArt(val bg: Color, val ink: Color, val motif: Int)

private val coverArts = listOf(
    CoverArt(Color(0xFF1E3A4C), Color(0xFFEFE6D2), 0),
    CoverArt(Color(0xFFDAD0BC), Color(0xFF2C2620), 1),
    CoverArt(Color(0xFF566B4D), Color(0xFFF1EBD8), 2),
    CoverArt(Color(0xFFD7A596), Color(0xFF4B2925), 3),
    CoverArt(Color(0xFF5B1F24), Color(0xFFF1E4D6), 4),
    CoverArt(Color(0xFFB5532F), Color(0xFFF7E8D3), 0),
    CoverArt(Color(0xFF344A5E), Color(0xFFE7DCC6), 2),
)

/** A book cover: the book's own picture, or a quiet generated one in the design's style. */
@Composable
fun BookCover(b: ShelfBook, modifier: Modifier, showText: Boolean = true, corner: Dp = 4.dp) {
    val jr = LocalJr.current
    val shape = RoundedCornerShape(corner)
    Box(modifier.shadow(3.dp, shape, ambientColor = jr.shadow, spotColor = jr.shadow).clip(shape)) {
        val img: ImageBitmap? = b.cover
        if (img != null) {
            Image(img, contentDescription = "Cover of ${b.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val art = if (b.storeId == null && b.title == SampleBook.TITLE) coverArts.first() else coverArts[(b.key.hashCode() and 0x7fffffff) % coverArts.size]
            BoxWithConstraints(Modifier.fillMaxSize().background(art.bg)) {
                val w = maxWidth.value
                Canvas(Modifier.fillMaxSize()) { drawMotif(art, b.storeId == null && b.title == SampleBook.TITLE) }
                if (showText && w > 50f) {
                    Column(Modifier.padding((w * 0.088f).dp), verticalArrangement = Arrangement.spacedBy((w * 0.037f).dp)) {
                        Text(b.title, color = art.ink, fontFamily = JrType.serifFamily, fontWeight = FontWeight.SemiBold,
                            fontSize = (w * 0.125f).sp, lineHeight = (w * 0.143f).sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (b.author.isNotBlank()) Text(b.author.uppercase(), color = art.ink, fontWeight = FontWeight.Medium,
                            fontSize = (w * 0.053f).sp, lineHeight = (w * 0.075f).sp, letterSpacing = 0.08.em, maxLines = 2)
                    }
                }
            }
        }
        // spine shadow
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.031f).background(Color.Black.copy(alpha = 0.14f)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMotif(art: CoverArt, lighthouse: Boolean) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.76f
    val sunX = w * (0.24f + art.motif * 0.12f)
    drawCircle(art.ink, w * 0.085f, Offset(sunX, h * 0.60f))
    // Parallel engraved ridges form a different horizon for each cloth colour.
    repeat(12) { line ->
        val y = horizon + line * h * 0.026f
        val ridge = Path().apply {
            moveTo(-w * 0.05f, y)
            cubicTo(w * 0.28f, y - h * (0.12f + art.motif * 0.02f),
                w * 0.58f, y + h * 0.10f, w * 1.05f, y - h * 0.055f)
        }
        drawPath(ridge, art.ink.copy(alpha = 0.78f), style = Stroke(w * 0.009f))
    }
    if (lighthouse) {
        val tower = Path().apply {
            moveTo(w * 0.69f, h * 0.736f)
            lineTo(w * 0.708f, h * 0.57f)
            lineTo(w * 0.75f, h * 0.57f)
            lineTo(w * 0.774f, h * 0.736f)
            close()
        }
        drawPath(tower, art.ink)
        drawRect(art.ink, Offset(w * 0.696f, h * 0.542f), Size(w * 0.064f, h * 0.016f))
    }
    drawLine(art.ink.copy(alpha = 0.28f), Offset(w * 0.048f, 0f), Offset(w * 0.048f, h), w * 0.005f)
}
