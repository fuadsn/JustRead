package app.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import app.reader.android.R

/** The JustRead palette: warm paper, brown ink, one leather-brown accent and a moss green for goals. */
@Immutable
data class JrColors(
    val dark: Boolean,
    val background: Color,
    val onBackground: Color,
    val onVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Cards, the reader's top and bottom bars. */
    val container: Color,
    /** Bottom sheets. */
    val sheet: Color,
    /** Selected segments, active slider tracks, selected list rows. */
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    /** Inactive progress tracks. */
    val track: Color,
    val menu: Color,
    val tertiary: Color,
    val scrim: Color,
    val error: Color,
    val shadow: Color,
)

val LightJr = JrColors(
    dark = false,
    background = Color(0xFFF9F2E5), onBackground = Color(0xFF211A12), onVariant = Color(0xFF51463A),
    primary = Color(0xFF6A4A2B), onPrimary = Color(0xFFFFFFFF),
    container = Color(0xFFEEE5D3), sheet = Color(0xFFF4ECDC),
    secondaryContainer = Color(0xFFEBDCC2), onSecondaryContainer = Color(0xFF251A0A),
    outline = Color(0xFF83766A), outlineVariant = Color(0xFFD4C7B4), track = Color(0xFFE2D7C2),
    menu = Color(0xFFE8DECB), tertiary = Color(0xFF55633A), scrim = Color(0x70211A12), error = Color(0xFFA8322A),
    shadow = Color(0xFF3C280A),
)

val DarkJr = JrColors(
    dark = true,
    background = Color(0xFF18140F), onBackground = Color(0xFFECE1D2), onVariant = Color(0xFFD2C4B2),
    primary = Color(0xFFE7C399), onPrimary = Color(0xFF412C10),
    container = Color(0xFF25201A), sheet = Color(0xFF201C16),
    secondaryContainer = Color(0xFF534531), onSecondaryContainer = Color(0xFFF5DFC0),
    outline = Color(0xFF9B8E7F), outlineVariant = Color(0xFF4F4539), track = Color(0xFF3A352E),
    menu = Color(0xFF2E2820), tertiary = Color(0xFFBDCD9B), scrim = Color(0x8F000000), error = Color(0xFFFFB4A9),
    shadow = Color(0xFF000000),
)

private val SageJr = LightJr.copy(
    background = Color(0xFFF1F4EB), onBackground = Color(0xFF19281D), onVariant = Color(0xFF425748),
    primary = Color(0xFF345C42), container = Color(0xFFE4EADB), sheet = Color(0xFFF1F4EB),
    secondaryContainer = Color(0xFFCFE1CC), onSecondaryContainer = Color(0xFF17331F),
    outline = Color(0xFF718273), outlineVariant = Color(0xFFC6D1C2), track = Color(0xFFD4DECF),
    menu = Color(0xFFE5ECDF), tertiary = Color(0xFF436052),
)

private val MidnightJr = DarkJr.copy(
    background = Color(0xFF121723), onBackground = Color(0xFFEDF0FC), onVariant = Color(0xFFB8C3D9),
    primary = Color(0xFFBBCBF7), onPrimary = Color(0xFF182644), container = Color(0xFF1F2739), sheet = Color(0xFF1A2130),
    secondaryContainer = Color(0xFF2D3951), onSecondaryContainer = Color(0xFFE4EBFF),
    outline = Color(0xFF8895AF), outlineVariant = Color(0xFF3B465C), track = Color(0xFF333E52),
    menu = Color(0xFF242D41), tertiary = Color(0xFFABCFCC),
)

enum class HomeTheme(val label: String) {
    MatchReader("Match reader appearance"), Parchment("Parchment"), Sage("Sage"), Midnight("Midnight");

    fun palette(readerDark: Boolean): JrColors = when (this) {
        MatchReader -> if (readerDark) DarkJr else LightJr
        Parchment -> LightJr
        Sage -> SageJr
        Midnight -> MidnightJr
    }
}

val LocalJr = staticCompositionLocalOf { LightJr }

/** UI type: Literata for titles and book names, the system sans (Roboto on Android) for everything else. */
object JrType {
    val serifFamily: FontFamily get() = BundledFonts.literata ?: FontFamily.Serif
    val sansFamily: FontFamily = FontFamily.SansSerif

    fun serif(size: Int, line: Int, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = serifFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)
    fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = sansFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking)

    val wordmark get() = serif(28, 36, FontWeight.SemiBold).copy(letterSpacing = (-0.01).em)
    val headline get() = serif(22, 28)
    val titleLarge get() = serif(18, 24)
    val title get() = serif(16, 24)
    val bookTitle get() = serif(15, 20)
    val body = sans(14, 20)
    val bodyLarge = sans(16, 24)
    val label = sans(14, 20, FontWeight.Medium)
    val labelSmall = sans(12, 16, FontWeight.Medium)
    val caption = sans(12, 16)
}

/** Loads the bundled reading fonts once. */
@Composable
fun rememberBundledFonts() {
    if (BundledFonts.literata != null) return // built once; new families would only defeat the text caches
    val literata = FontFamily(
        Font(R.font.literata_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.literata_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.literata_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.literata_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.literata_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val sourceSerif = FontFamily(
        Font(R.font.source_serif_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.source_serif_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.source_serif_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.source_serif_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val lora = FontFamily(
        Font(R.font.lora_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.lora_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.lora_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val newsreader = FontFamily(
        Font(R.font.newsreader_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.newsreader_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.newsreader_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val alegreya = FontFamily(
        Font(R.font.alegreya_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.alegreya_sans_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.alegreya_sans_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.alegreya_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val atkinson = FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.atkinson_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.atkinson_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )
    val lexend = FontFamily(
        Font(R.font.lexend_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.lexend_bold, FontWeight.Bold, FontStyle.Normal),
    )
    BundledFonts.literata = literata
    BundledFonts.sourceSerif = sourceSerif
    BundledFonts.lora = lora
    BundledFonts.newsreader = newsreader
    BundledFonts.alegreyaSans = alegreya
    BundledFonts.atkinson = atkinson
    BundledFonts.lexend = lexend
}

@Composable
fun JustReadTheme(jr: JrColors, content: @Composable () -> Unit) {
    val scheme = remember(jr) {
        if (jr.dark) darkColorScheme(
            primary = jr.primary, onPrimary = jr.onPrimary, background = jr.background, onBackground = jr.onBackground,
            surface = jr.background, onSurface = jr.onBackground, onSurfaceVariant = jr.onVariant,
            secondaryContainer = jr.secondaryContainer, onSecondaryContainer = jr.onSecondaryContainer,
            outline = jr.outline, outlineVariant = jr.outlineVariant, surfaceContainer = jr.container, error = jr.error,
        ) else lightColorScheme(
            primary = jr.primary, onPrimary = jr.onPrimary, background = jr.background, onBackground = jr.onBackground,
            surface = jr.background, onSurface = jr.onBackground, onSurfaceVariant = jr.onVariant,
            secondaryContainer = jr.secondaryContainer, onSecondaryContainer = jr.onSecondaryContainer,
            outline = jr.outline, outlineVariant = jr.outlineVariant, surfaceContainer = jr.container, error = jr.error,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalJr provides jr, content = content)
    }
}
