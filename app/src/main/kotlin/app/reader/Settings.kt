package app.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Page turn styles. */
enum class PageTurn(val label: String) { Curl("Curl"), Slide("Slide"), Scroll("Scroll") }

/** Light / Dark / follow the system / follow the room's light (light sensor, where there is one). */
enum class Appearance(val label: String) { Light("Light"), Dark("Dark"), System("System"), Ambient("Ambient") }

enum class ReadingOrientation(val label: String) { System("Follow phone"), Landscape("Landscape"), Locked("Lock current orientation") }

enum class Columns(val label: String) { Auto("Auto"), One("One"), Two("Two") }

enum class FontGroup(val label: String) { Book("From the book"), Serif("Serif"), Sans("Sans serif") }

/**
 * Reading fonts. Every font shares the same 12 size steps (em on a 16 px base), so changing the
 * font never jumps the text size. [lineHeight] is the font's own comfortable leading; 0 = publisher's.
 */
enum class ReaderFont(val label: String, val note: String, val group: FontGroup, val lineHeight: Float) {
    Publisher("Publisher’s font", "The typeface this book was set in", FontGroup.Book, 0f),
    Literata("Literata", "Made for long reading on screens", FontGroup.Serif, 1.6f),
    SourceSerif("Source Serif 4", "Even and classical", FontGroup.Serif, 1.6f),
    Lora("Lora", "Brushed, calligraphic curves", FontGroup.Serif, 1.6f),
    Newsreader("Newsreader", "Newsprint texture", FontGroup.Serif, 1.6f),
    AlegreyaSans("Alegreya Sans", "Humanist, with a calligraphic touch", FontGroup.Sans, 1.55f),
    Atkinson("Atkinson Hyperlegible", "Distinct letter shapes for low vision", FontGroup.Sans, 1.6f),
    Lexend("Lexend", "Wide spacing to ease reading", FontGroup.Sans, 1.6f),
    Roboto("Roboto", "The system typeface", FontGroup.Sans, 1.55f);

    val familyOrDefault: FontFamily get() = when (this) {
        Publisher -> FontFamily.Serif
        Literata -> BundledFonts.literata ?: FontFamily.Serif
        SourceSerif -> BundledFonts.sourceSerif ?: FontFamily.Serif
        Lora -> BundledFonts.lora ?: FontFamily.Serif
        Newsreader -> BundledFonts.newsreader ?: FontFamily.Serif
        AlegreyaSans -> BundledFonts.alegreyaSans ?: FontFamily.SansSerif
        Atkinson -> BundledFonts.atkinson ?: FontFamily.SansSerif
        Lexend -> BundledFonts.lexend ?: FontFamily.SansSerif
        Roboto -> FontFamily.SansSerif
    }

    companion object {
        /** 12 size steps in em (16 px base): 12 … 32 px; step 6 = 18 px, the design's body size. */
        val SIZES = listOf(0.75f, 0.8125f, 0.875f, 0.9375f, 1f, 1.0625f, 1.125f, 1.25f, 1.375f, 1.5f, 1.75f, 2f)
    }
}

/** Fonts shipped with the app, loaded once from the app's resources (see [rememberBundledFonts]). */
object BundledFonts {
    var literata: FontFamily? = null
    var sourceSerif: FontFamily? = null
    var lora: FontFamily? = null
    var newsreader: FontFamily? = null
    var alegreyaSans: FontFamily? = null
    var atkinson: FontFamily? = null
    var lexend: FontFamily? = null
}

data class ThemeColors(val page: Color, val text: Color, val secondary: Color, val link: Color)

/**
 * The six reading themes from the JustRead design. Light themes have a dark counterpart that is used
 * when the appearance is dark; Dusk and Night are dark already.
 */
enum class ReaderTheme(
    val label: String,
    val light: ThemeColors,
    val dark: ThemeColors,
    val font: ReaderFont,
    val isDarkTheme: Boolean = false,
) {
    Parchment("Parchment",
        ThemeColors(Color(0xFFFBF6EA), Color(0xFF2B2118), Color(0xFF51463A), Color(0xFF6A4A2B)),
        ThemeColors(Color(0xFF1D1913), Color(0xFFDACFBF), Color(0xFFD2C4B2), Color(0xFFE7C399)),
        ReaderFont.Literata),
    White("White",
        ThemeColors(Color(0xFFFFFFFF), Color(0xFF1D1B19), Color(0xFF5C5650), Color(0xFF6A4A2B)),
        ThemeColors(Color(0xFF121212), Color(0xFFE6E1DA), Color(0xFFA8A29A), Color(0xFFE7C399)),
        ReaderFont.Literata),
    Sepia("Sepia",
        ThemeColors(Color(0xFFEBDDC3), Color(0xFF4A3524), Color(0xFF6B5541), Color(0xFF7A4E25)),
        ThemeColors(Color(0xFF2B2219), Color(0xFFE3D3BA), Color(0xFFB4A48C), Color(0xFFE7C399)),
        ReaderFont.Literata),
    Stone("Stone",
        ThemeColors(Color(0xFFE4E1DA), Color(0xFF26272A), Color(0xFF55565B), Color(0xFF3D4C6B)),
        ThemeColors(Color(0xFF1E1F21), Color(0xFFD6D4CF), Color(0xFFA3A19C), Color(0xFFAFC1E4)),
        ReaderFont.Roboto),
    Dusk("Dusk",
        ThemeColors(Color(0xFF2A2520), Color(0xFFDACFBF), Color(0xFFB3A898), Color(0xFFE7C399)),
        ThemeColors(Color(0xFF2A2520), Color(0xFFDACFBF), Color(0xFFB3A898), Color(0xFFE7C399)),
        ReaderFont.Literata, isDarkTheme = true),
    Night("Night",
        ThemeColors(Color(0xFF000000), Color(0xFFB5ABA0), Color(0xFF8E857B), Color(0xFFD9B98F)),
        ThemeColors(Color(0xFF000000), Color(0xFFB5ABA0), Color(0xFF8E857B), Color(0xFFD9B98F)),
        ReaderFont.Roboto, isDarkTheme = true);

    fun colors(dark: Boolean) = if (dark) this.dark else light
}

/** Everything the reader lets you change. */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Parchment,
    val appearance: Appearance = Appearance.Light,
    val font: ReaderFont = ReaderFont.Literata,
    val sizeStep: Int = DEFAULT_SIZE_STEP,
    /** The person changed the theme's font or layout; picking another theme then keeps them. */
    val customized: Boolean = false,
    val bold: Boolean = false,
    val justify: Boolean = true,
    /** "Custom spacing": use the values below instead of the book's own. */
    val customSpacing: Boolean = true,
    /** Line height multiplier; 0 = the font's own value. */
    val lineHeight: Float = 1.6f,
    /** Extra space between letters, as a fraction of the font size (0.05 = +5%). */
    val letterSpacing: Float = 0f,
    /** Extra space between words, as a fraction of the font size. */
    val wordSpacing: Float = 0f,
    /** Margin adjustment in percent of the page width. */
    val marginAdjust: Int = 0,
    val columns: Columns = Columns.Auto,
    val pageTurn: PageTurn = PageTurn.Curl,
    /** Screen brightness 0…1 while reading, or −1 to follow the system. */
    val brightness: Float = -1f,
    val lineGuide: Boolean = false,
    val orientation: ReadingOrientation = ReadingOrientation.System,
) {
    /** Whether pages and the app chrome are dark, given the system's and the room's state. */
    fun isDark(systemDark: Boolean, ambientDark: Boolean? = null) = theme.isDarkTheme || appearanceDark(systemDark, ambientDark)

    /** Whether the app's own chrome (library, sheets) is dark. */
    fun appearanceDark(systemDark: Boolean, ambientDark: Boolean? = null) = when (appearance) {
        Appearance.Light -> false
        Appearance.Dark -> true
        Appearance.System -> systemDark
        Appearance.Ambient -> ambientDark ?: systemDark
    }

    val fontSizeEm: Float get() = ReaderFont.SIZES[sizeStep.coerceIn(0, ReaderFont.SIZES.lastIndex)]
    val fontSizePx: Float get() = BASE_FONT_PX * fontSizeEm
    val effectiveLineHeight: Float get() = when {
        customSpacing && lineHeight > 0f -> lineHeight
        font.lineHeight > 0f -> font.lineHeight
        else -> PUBLISHER_LINE_HEIGHT
    }
    val effectiveLetterSpacing: Float get() = if (customSpacing) letterSpacing else 0f
    val effectiveWordSpacing: Float get() = if (customSpacing) wordSpacing else 0f
    val effectiveMargin: Int get() = if (customSpacing) marginAdjust else 0
    val effectiveBold: Boolean get() = bold

    /** Only the values that change where text falls on the page. */
    fun layoutKey() = listOf(font, sizeStep, bold, justify, effectiveLineHeight, effectiveLetterSpacing, effectiveWordSpacing, effectiveMargin, columns, pageTurn == PageTurn.Scroll)

    /** Picking a theme brings its font unless the person customised it. */
    fun withTheme(t: ReaderTheme) = if (customized) copy(theme = t) else copy(theme = t, font = t.font)

    fun resetTheme() = copy(customized = false, bold = false, justify = true, customSpacing = true, lineHeight = 1.6f,
        letterSpacing = 0f, wordSpacing = 0f, marginAdjust = 0, columns = Columns.Auto).withTheme(theme)

    fun encode(): String = listOf(
        "v=2", "theme=${theme.name}", "appearance=${appearance.name}", "font=${font.name}", "size=$sizeStep",
        "custom=$customized", "bold=$bold", "justify=$justify", "cs=$customSpacing", "lh=$lineHeight", "ls=$letterSpacing",
        "ws=$wordSpacing", "madj=$marginAdjust", "columns=${columns.name}", "turn=${pageTurn.name}",
        "bright=$brightness", "guide=$lineGuide", "orientation=${orientation.name}",
    ).joinToString(";")

    companion object {
        const val BASE_FONT_PX = 16f
        const val DEFAULT_SIZE_STEP = 6
        const val PUBLISHER_LINE_HEIGHT = 1.45f
        /** Side margin before the adjustment: 28 dp total with the 5 dp html margin, as in the design. */
        const val BASE_MARGIN_DP = 23f
        const val LINE_HEIGHT_MIN = 1.0f
        const val LINE_HEIGHT_MAX = 2.4f
        const val LETTER_SPACING_LIMIT = 0.10f
        const val WORD_SPACING_LIMIT = 0.50f
        const val MARGIN_ADJUST_LIMIT = 10

        private inline fun <reified T : Enum<T>> enumOr(v: String?, d: T): T =
            v?.let { s -> enumValues<T>().firstOrNull { it.name == s } } ?: d

        fun decode(s: String?): ReaderSettings {
            if (s.isNullOrBlank()) return ReaderSettings()
            val m = s.split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
            val d = ReaderSettings()
            // settings saved before the redesign used other themes and fonts: keep only what still applies
            if (m["v"] != "2") return d.copy(
                pageTurn = enumOr(m["turn"], d.pageTurn),
                sizeStep = d.sizeStep,
            )
            return runCatching {
                ReaderSettings(
                    theme = enumOr(m["theme"], d.theme),
                    appearance = enumOr(m["appearance"], d.appearance),
                    font = enumOr(m["font"], d.font),
                    sizeStep = m["size"]?.toIntOrNull()?.coerceIn(0, 11) ?: d.sizeStep,
                    customized = m["custom"]?.toBoolean() ?: false,
                    bold = m["bold"]?.toBoolean() ?: false,
                    justify = m["justify"]?.toBoolean() ?: true,
                    customSpacing = m["cs"]?.toBoolean() ?: true,
                    lineHeight = m["lh"]?.toFloatOrNull()?.coerceIn(LINE_HEIGHT_MIN, LINE_HEIGHT_MAX) ?: d.lineHeight,
                    letterSpacing = m["ls"]?.toFloatOrNull()?.coerceIn(-LETTER_SPACING_LIMIT, LETTER_SPACING_LIMIT) ?: 0f,
                    wordSpacing = m["ws"]?.toFloatOrNull()?.coerceIn(-WORD_SPACING_LIMIT, WORD_SPACING_LIMIT) ?: 0f,
                    marginAdjust = m["madj"]?.toIntOrNull()?.coerceIn(-MARGIN_ADJUST_LIMIT, MARGIN_ADJUST_LIMIT) ?: 0,
                    columns = enumOr(m["columns"], Columns.Auto),
                    pageTurn = enumOr(m["turn"], d.pageTurn),
                    brightness = m["bright"]?.toFloatOrNull()?.coerceIn(-1f, 1f) ?: -1f,
                    lineGuide = m["guide"]?.toBoolean() ?: false,
                    orientation = enumOr(m["orientation"], if (m["lock"] == "true") ReadingOrientation.Locked else ReadingOrientation.System),
                )
            }.getOrDefault(d)
        }
    }
}
