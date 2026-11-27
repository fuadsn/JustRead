package app.reader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** One piece of a page: some lines of a paragraph, a picture or a divider. Coordinates are within its column. */
sealed class Frag {
    class Lines(val layout: TextLayoutResult, val first: Int, val last: Int, val x: Float, val y: Float, val height: Float, val secondary: Boolean,
                /** The block (paragraph) of the chapter these lines belong to; character offsets are within it. */
                val block: Int) : Frag()
    class Picture(val path: String, val x: Float, val y: Float, val w: Float, val h: Float, val alt: String) : Frag()
    class Rule(val x: Float, val y: Float, val w: Float) : Frag()
}

class Page(
    val chapter: Int,
    val columns: List<List<Frag>>,
    val startBlock: Int,
    val startChar: Int,
    val pageInChapter: Int,
) {
    var chapterPageCount: Int = 1
}

/** Where a page sits: alone on the screen, or the left or right page of a two-page spread. */
enum class PageSide { Single, Left, Right }

/** Size and margins of a page, in pixels. */
class PageSpec(
    val width: Float, val height: Float, val density: Float, val settings: ReaderSettings, val rtl: Boolean = false,
    /** System bars drawn over the page (the reader is full screen); text stays clear of them. */
    val insetTop: Float = 0f, val insetBottom: Float = 0f,
    /** Two pages side by side on the screen; [width] is one of them. */
    val spread: Boolean = false,
    /** The whole screen, which sets the margins: phone, phone on its side, or tablet. */
    val screenWidth: Float = width, val screenHeight: Float = height,
) {
    private val dpW = screenWidth / density
    private val dpH = screenHeight / density
    /** A tablet: the short side is at least 600 dp. */
    val tablet = minOf(dpW, dpH) >= 600f
    /** A phone on its side. */
    val compactHeight = dpH < 480f
    // header, top margin, bottom margin, footer (dp), as in the design for each kind of screen
    private val v = when {
        tablet && dpW > dpH -> floatArrayOf(60f, 112f, 120f, 52f)
        tablet -> floatArrayOf(76f, 136f, 136f, 76f)
        compactHeight -> floatArrayOf(32f, 62f, 64f, 32f)
        else -> floatArrayOf(22f, 64f, 56f, 26f)
    }
    /** Centre lines of the running header and the page number, from the top and the bottom. */
    val headerY = v[0] * density + insetTop
    val footerY = v[3] * density + insetBottom

    private val phoneLandscape = !tablet && dpW > dpH
    val marginH = ((ReaderSettings.BASE_MARGIN_DP + HTML_SIDE_MARGIN_DP) * density + width * settings.effectiveMargin / 100f)
        .let { if (tablet) maxOf(it, width * (if (spread) 0.125f else 0.14f)) else it } // the boards: 80 of 640 dp, 112 of 800 dp
        .coerceAtLeast(if (phoneLandscape) maxOf(8f * density, (width - 580f * density) / 2) else 8f * density)
    val marginTop = v[1] * density + insetTop
    val marginBottom = v[2] * density + insetBottom
    /** On a narrow spread the margins at the spine are half the outer ones, as in a printed book. */
    private val narrowSpread = spread && width / density < 500f
    /** How far a page's text moves sideways from the centred position, by side of the spread. */
    fun shift(side: PageSide) = when (side) {
        PageSide.Single -> 0f
        PageSide.Left -> if (narrowSpread) marginH / 3f else 0f
        PageSide.Right -> if (narrowSpread) -marginH / 3f else 0f
    }
    val columns = when {
        phoneLandscape || spread || settings.pageTurn == PageTurn.Scroll -> 1
        settings.columns == Columns.One -> 1
        settings.columns == Columns.Two -> if (width / density >= 480f) 2 else 1
        else -> if (width > height * 1.05f && width / density >= 640f) 2 else 1
    }
    val gap = maxOf(marginH * 2f, 40f * density)
    val contentWidth = width - 2 * marginH
    val columnWidth = (contentWidth - gap * (columns - 1)) / columns
    val columnHeight = height - marginTop - marginBottom
    fun columnX(c: Int) = marginH + c * (columnWidth + gap)

    fun sameLayout(o: PageSpec) = width == o.width && height == o.height && density == o.density &&
        insetTop == o.insetTop && insetBottom == o.insetBottom && spread == o.spread &&
        screenWidth == o.screenWidth && screenHeight == o.screenHeight &&
        settings.layoutKey() == o.settings.layoutKey()

    companion object {
        /** A small side margin on the page body, like `html { margin: 0px 5px }`. */
        const val HTML_SIDE_MARGIN_DP = 5f
    }
}

class Pagination(val spec: PageSpec, val pages: List<Page>, val chapterFirstPage: List<Int>) {
    fun pageFor(chapter: Int, block: Int, char: Int): Int {
        val first = chapterFirstPage.getOrNull(chapter) ?: return 0
        var best = first
        var i = first
        while (i < pages.size && pages[i].chapter == chapter) {
            val p = pages[i]
            if (p.startBlock < block || (p.startBlock == block && p.startChar <= char)) best = i else break
            i++
        }
        return best
    }
}

/** Lays the book out into pages with print-style rules (widows/orphans 2, pictures ≤ 95% of the page). */
class Paginator(
    private val book: EpubBook,
    private val spec: PageSpec,
    private val measurer: TextMeasurer,
    /** A picture's size in px (only its header is read), or null if it can't be read. */
    private val pictureSize: (String) -> androidx.compose.ui.unit.IntSize?,
) {
    private val s = spec.settings
    private val density = Density(spec.density, 1f)
    private val em = s.fontSizePx * spec.density
    private val locale = runCatching { LocaleList(book.language.ifBlank { "en" }) }.getOrDefault(LocaleList("en"))

    private class Column(val startBlock: Int, val startChar: Int) { val frags = mutableListOf<Frag>() }

    private class Style(val text: TextStyle, val before: Float, val after: Float, val indent: Float, val endIndent: Float, val secondary: Boolean)

    fun run(onChapter: (Int) -> Unit = {}): Pagination {
        val pages = mutableListOf<Page>()
        val firsts = mutableListOf<Int>()
        book.chapters.forEachIndexed { ci, ch ->
            firsts += pages.size
            val cols = layoutChapter(ch)
            val chapterPages = cols.chunked(spec.columns)
            chapterPages.forEachIndexed { pi, group ->
                pages += Page(ci, group.map { it.frags }, group.first().startBlock, group.first().startChar, pi)
            }
            val count = chapterPages.size
            for (k in pages.size - count until pages.size) pages[k].chapterPageCount = count
            onChapter(ci)
        }
        return Pagination(spec, pages, firsts)
    }

    private fun styleFor(b: Block.Text): Style {
        val base = baseStyleFor(b)
        if (b.marginTop == null && b.marginBottom == null) return base
        return Style(base.text, b.marginTop?.let { it * em } ?: base.before, b.marginBottom?.let { it * em } ?: base.after,
            base.indent, base.endIndent, base.secondary)
    }

    private fun baseStyleFor(b: Block.Text): Style {
        val lh = s.effectiveLineHeight
        val baseAlign = if (s.justify) TextAlign.Justify else TextAlign.Start
        val weight = if (s.effectiveBold) FontWeight.Bold else FontWeight.Normal
        val family = s.font.familyOrDefault
        fun ts(sizeEm: Float, lineH: Float, align: TextAlign, w: FontWeight = weight, italic: Boolean = false, fam: FontFamily = family) = TextStyle(
            fontSize = (ReaderSettings.BASE_FONT_PX * s.fontSizeEm * sizeEm).sp,
            lineHeight = (ReaderSettings.BASE_FONT_PX * s.fontSizeEm * sizeEm * lineH).sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            fontFamily = fam, fontWeight = w, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = b.align ?: align,
            letterSpacing = if (s.effectiveLetterSpacing != 0f) s.effectiveLetterSpacing.em else TextUnit.Unspecified,
            hyphens = if (s.justify) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
            textIndent = b.textIndent?.let { TextIndent(firstLine = it.em) },
            localeList = locale,
        )
        return when (b.kind) {
            Block.Kind.Heading -> if (isChapterLabel(b)) {
                // "Chapter 1" over a chapter title: a small tracked label, as in the design
                Style(ts(0.68f, 1.35f, TextAlign.Start, FontWeight.Medium, fam = FontFamily.SansSerif)
                    .copy(letterSpacing = 0.12.em), 0.6f * em, 0.15f * em, 0f, 0f, true)
            } else {
                // chapter titles as in the design: larger, medium weight, set to the left
                val scale = listOf(1.45f, 1.3f, 1.15f, 1.05f, 1.0f, 0.95f)[(b.level - 1).coerceIn(0, 5)]
                Style(ts(scale, 1.3f, TextAlign.Start, if (s.effectiveBold) FontWeight.Bold else FontWeight.Medium), 1.4f * em, 1.1f * em, 0f, 0f, false)
            }
            Block.Kind.Quote -> Style(ts(0.95f, lh, baseAlign, italic = true), 0.3f * em, 0.8f * em, 1.5f * em, 1.5f * em, false)
            Block.Kind.Pre -> Style(ts(0.82f, 1.3f, TextAlign.Start, fam = FontFamily.Monospace), 0.3f * em, 0.8f * em, 0.5f * em, 0f, false)
            Block.Kind.ListItem -> Style(ts(1f, lh, TextAlign.Start), 0f, 0.3f * em, (0.6f + 1.2f * maxOf(0, b.level - 1)) * em, 0f, false)
            Block.Kind.Caption -> Style(ts(0.85f, 1.3f, TextAlign.Center), 0.2f * em, 0.9f * em, 0f, 0f, true)
            Block.Kind.Body -> Style(ts(1f, lh, baseAlign), 0f, 0.55f * em, 0f, 0f, false)
        }
    }

    private val chapterLabel = Regex("^(chapter|part|book)\\s+[0-9ivxlcdm]+\\.?$", RegexOption.IGNORE_CASE)
    private fun isChapterLabel(b: Block.Text) = b.kind == Block.Kind.Heading && b.text.text.trim().matches(chapterLabel)

    /** Word spacing has no direct equivalent here, so it is added as letter spacing on each space. */
    private fun withWordSpacing(t: AnnotatedString): AnnotatedString {
        if (s.effectiveWordSpacing == 0f) return t
        val b = AnnotatedString.Builder(t)
        val sp = SpanStyle(letterSpacing = (s.effectiveWordSpacing + s.effectiveLetterSpacing).em)
        t.text.forEachIndexed { i, c -> if (c == ' ') b.addStyle(sp, i, i + 1) }
        return b.toAnnotatedString()
    }

    private fun layoutChapter(ch: Chapter): List<Column> {
        val colW = spec.columnWidth
        val colH = spec.columnHeight
        val columns = mutableListOf<Column>()
        var cur = Column(0, 0)
        var y = 0f
        fun newColumn(block: Int, char: Int) { columns += cur; cur = Column(block, char); y = 0f }

        var afterLabel = false
        ch.blocks.forEachIndexed { bi, b ->
            if (b !is Block.Text) afterLabel = false
            when (b) {
                is Block.Text -> {
                    val st = styleFor(b)
                    val width = (colW - st.indent - st.endIndent).coerceAtLeast(40f)
                    val layout = measurer.measure(
                        if (isChapterLabel(b)) AnnotatedString(b.text.text.trim().uppercase()) else withWordSpacing(b.text), st.text,
                        constraints = Constraints(maxWidth = width.toInt()),
                        density = density, layoutDirection = if (spec.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    )
                    val n = layout.lineCount
                    var yy = if (cur.frags.isEmpty()) 0f else y + (if (afterLabel) 0.1f * em else st.before)
                    var line = 0
                    while (line < n) {
                        val avail = colH - yy
                        var k = 0
                        while (line + k < n && layout.getLineBottom(line + k) - layout.getLineTop(line) <= avail + 0.5f) k++
                        if (k < n - line) {
                            // orphans: 2 — never leave a single first line at the bottom of a column
                            if (line == 0 && k < ORPHANS && n >= ORPHANS && cur.frags.isNotEmpty()) k = 0
                            // widows: 2 — never carry a single last line to the next column
                            if (k > 0 && n - line - k < WIDOWS) {
                                k = maxOf(0, n - line - WIDOWS)
                                if (line == 0 && k < ORPHANS && cur.frags.isNotEmpty()) k = 0
                            }
                            if (k == 0 && cur.frags.isEmpty()) k = maxOf(1, minOf(n - line, (avail / (layout.getLineBottom(line) - layout.getLineTop(line))).toInt()))
                        }
                        if (k > 0) {
                            val h = layout.getLineBottom(line + k - 1) - layout.getLineTop(line)
                            cur.frags += Frag.Lines(layout, line, line + k, st.indent, yy, h, st.secondary, bi)
                            yy += h
                            line += k
                        }
                        if (line < n) { newColumn(bi, layout.getLineStart(line)); yy = 0f }
                    }
                    y = yy + st.after
                    afterLabel = isChapterLabel(b)
                }
                is Block.Picture -> {
                    val px = pictureSize(b.path)
                    val iw = (px?.width ?: 300) * spec.density
                    val ih = (px?.height ?: 200) * spec.density
                    val scale = minOf(1f, colW / iw, colH * IMAGE_MAX_HEIGHT / ih)
                    val w = iw * scale; val h = ih * scale
                    if (cur.frags.isNotEmpty() && y + h > colH) newColumn(bi, 0)
                    val top = if (cur.frags.isEmpty()) 0f else y + 0.3f * em
                    cur.frags += Frag.Picture(b.path, (colW - w) / 2f, top, w, h, b.alt)
                    y = top + h + 0.6f * em
                }
                Block.Rule -> {
                    if (cur.frags.isNotEmpty() && y + em > colH) newColumn(bi, 0)
                    val top = if (cur.frags.isEmpty()) 0f else y + 0.6f * em
                    cur.frags += Frag.Rule(colW * 0.35f, top, colW * 0.3f)
                    y = top + 1.2f * em
                }
            }
        }
        if (cur.frags.isNotEmpty() || columns.isEmpty()) columns += cur
        return columns
    }

    companion object {
        /** Never strand fewer than two lines of a paragraph: `p { widows: 2; orphans: 2 }`. */
        const val WIDOWS = 2
        const val ORPHANS = 2
        /** Pictures take at most 95% of the page height: `img { max-height: 95% }`. */
        const val IMAGE_MAX_HEIGHT = 0.95f
    }
}
