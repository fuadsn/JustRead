package app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val SAMPLE = "Call me Ishmael. Some years ago—never mind how long precisely—having little or no money in my purse, " +
    "and nothing particular to interest me on shore, I thought I would sail about a little and see the watery part of the world."

private fun fmt2(x: Float): String {
    val i = (x * 100).roundToInt()
    return "${i / 100}.${(i % 100).toString().padStart(2, '0')}"
}

private fun pct(x: Float) = "${(x * 100).roundToInt()}%"

// ---------------------------------------------------------------- themes and settings

@Composable
fun ThemesSheet(settings: ReaderSettings, uiDark: Boolean, onChange: (ReaderSettings) -> Unit, onCustomise: () -> Unit, onDismiss: () -> Unit) {
    val jr = LocalJr.current
    val conf = LocalConfiguration.current
    val short = conf.screenHeightDp < 480
    val sideways = conf.screenWidthDp > conf.screenHeightDp && conf.screenWidthDp >= 600
    val size: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", style = JrType.label, color = jr.onVariant, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            JrSlider(settings.sizeStep / 11f, "Text size", Modifier.weight(1f), steps = 12) {
                val step = (it * 11).roundToInt()
                if (step != settings.sizeStep) onChange(settings.copy(sizeStep = step))
            }
            Text("A", style = JrType.sans(22, 24, FontWeight.Medium), color = jr.onVariant, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        }
    }
    val brightness: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icon(JrIcons.Sun, null, tint = jr.onVariant, modifier = Modifier.size(18.dp)) }
            JrSlider(if (settings.brightness < 0f) 0.5f else settings.brightness, "Brightness", Modifier.weight(1f)) {
                onChange(settings.copy(brightness = it.coerceIn(0.02f, 1f)))
            }
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icon(JrIcons.Sun, null, tint = jr.onVariant, modifier = Modifier.size(24.dp)) }
        }
    }
    val appearance: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Appearance")
            Segmented(Appearance.entries.map { it.label }, settings.appearance.ordinal) { onChange(settings.copy(appearance = Appearance.entries[it])) }
        }
    }
    val pageTurn: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Page turn")
            Segmented(PageTurn.entries.map { it.label }, settings.pageTurn.ordinal) { onChange(settings.copy(pageTurn = PageTurn.entries[it])) }
        }
    }
    val themes: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Theme")
            for (row in ReaderTheme.entries.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in row) ThemeTile(t, t == settings.theme, Modifier.weight(1f), short) { onChange(settings.withTheme(t)) }
                }
            }
        }
    }
    val customise: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(if (short) 44.dp else 48.dp).clip(RoundedCornerShape(24.dp)).background(jr.secondaryContainer)
            .clickable(onClick = onCustomise).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Customise theme", style = JrType.label, color = jr.onSecondaryContainer, modifier = Modifier.weight(1f))
            Icon(JrIcons.ChevronRight, null, tint = jr.onSecondaryContainer, modifier = Modifier.size(24.dp))
        }
    }
    val oneColumn: @Composable () -> Unit = {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            size(); brightness(); appearance(); pageTurn(); themes(); customise()
        }
    }
    when {
        // a phone on its side: a wide sheet in two columns
        short -> JrSheet(onDismiss, maxWidth = 760.dp, scrim = false) {
            SheetTitle("Themes and settings", onDismiss)
            Row(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    size(); brightness(); appearance(); Spacer(Modifier.height(4.dp)); customise()
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { pageTurn(); themes() }
            }
        }
        // a tablet held sideways: beside the pages, which stay in view as they change
        sideways -> JrSideSheet(onDismiss, fromLeft = false, scrim = false) {
            Spacer(Modifier.height(28.dp))
            SheetTitle("Themes and settings", onDismiss)
            oneColumn()
        }
        else -> JrSheet(onDismiss, scrim = conf.smallestScreenWidthDp < 600) { // no scrim on a tablet, as designed
            SheetTitle("Themes and settings", onDismiss)
            oneColumn()
        }
    }
    uiDark.hashCode()
}

@Composable
private fun ThemeTile(t: ReaderTheme, selected: Boolean, modifier: Modifier, short: Boolean = false, onClick: () -> Unit) {
    val jr = LocalJr.current
    val c = t.light
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.height(if (short) 56.dp else 64.dp).clip(shape).background(c.page)
        .border(if (selected) 2.dp else 1.dp, if (selected) jr.primary else jr.outlineVariant, shape)
        .clickable(onClick = onClick)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Aa", color = c.text, fontFamily = t.font.familyOrDefault, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp)
            Text(t.label, color = c.text, style = JrType.labelSmall)
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).clip(CircleShape).background(jr.primary),
            contentAlignment = Alignment.Center) {
            Icon(JrIcons.Check, null, tint = jr.onPrimary, modifier = Modifier.size(13.dp))
        }
    }
}

// ---------------------------------------------------------------- customise theme

@Composable
fun CustomiseScreen(settings: ReaderSettings, colors: ThemeColors, onChange: (ReaderSettings) -> Unit, onFonts: () -> Unit, onDone: () -> Unit) {
    val jr = LocalJr.current
    var tab by remember { mutableStateOf(0) }
    fun set(s: ReaderSettings) = onChange(s.copy(customized = true))
    val conf = LocalConfiguration.current
    val short = conf.screenHeightDp < 480
    val tablet = minOf(conf.screenWidthDp, conf.screenHeightDp) >= 600
    val sideways = conf.screenWidthDp > conf.screenHeightDp
    BackAction(onBack = onDone)

    val textSettings: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onFonts), verticalAlignment = Alignment.CenterVertically) {
            Text("Font", style = JrType.bodyLarge, color = jr.onBackground, modifier = Modifier.weight(1f))
            Text(settings.font.label, fontFamily = settings.font.familyOrDefault, fontSize = 16.sp, color = jr.onVariant)
            Spacer(Modifier.width(8.dp))
            Icon(JrIcons.ChevronRight, null, tint = jr.onVariant, modifier = Modifier.size(24.dp))
        }
        Divider()
        ValueRow("Text size", "${settings.fontSizePx.roundToInt()} pt")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A", style = JrType.label, color = jr.onVariant)
            JrSlider(settings.sizeStep / 11f, "Text size", Modifier.weight(1f), steps = 12) {
                val step = (it * 11).roundToInt()
                if (step != settings.sizeStep) onChange(settings.copy(sizeStep = step))
            }
            Text("A", style = JrType.sans(22, 24, FontWeight.Medium), color = jr.onVariant)
        }
        Divider(Modifier.padding(top = 8.dp))
        SwitchRow("Bold text", null, settings.bold) { set(settings.copy(bold = it)) }
        Divider()
        SwitchRow("Justify text", "Align both edges of each line", settings.justify) { set(settings.copy(justify = it)) }
    }
    val layoutSettings: @Composable () -> Unit = {
        SwitchRow("Custom spacing", "Use these values instead of the book’s own", settings.customSpacing) { set(settings.copy(customSpacing = it)) }
        Divider()
        Column(Modifier.alpha(if (settings.customSpacing) 1f else 0.38f)) {
            val on = settings.customSpacing
            val lh = settings.effectiveLineHeight
            ValueRow("Line spacing", fmt2(lh))
            JrSlider((lh - ReaderSettings.LINE_HEIGHT_MIN) / (ReaderSettings.LINE_HEIGHT_MAX - ReaderSettings.LINE_HEIGHT_MIN), "Line spacing") {
                if (on) set(settings.copy(lineHeight = ((ReaderSettings.LINE_HEIGHT_MIN + it * (ReaderSettings.LINE_HEIGHT_MAX - ReaderSettings.LINE_HEIGHT_MIN)) * 20).roundToInt() / 20f))
            }
            val ls = ReaderSettings.LETTER_SPACING_LIMIT
            ValueRow("Character spacing", pct(settings.letterSpacing))
            JrSlider((settings.letterSpacing + ls) / (2 * ls), "Character spacing") {
                if (on) set(settings.copy(letterSpacing = ((it * 2 - 1) * ls * 100).roundToInt() / 100f))
            }
            val ws = ReaderSettings.WORD_SPACING_LIMIT
            ValueRow("Word spacing", pct(settings.wordSpacing))
            JrSlider((settings.wordSpacing + ws) / (2 * ws), "Word spacing") {
                if (on) set(settings.copy(wordSpacing = ((it * 2 - 1) * ws * 20).roundToInt() / 20f))
            }
            val ml = ReaderSettings.MARGIN_ADJUST_LIMIT
            ValueRow("Margins", "${settings.marginAdjust}%")
            JrSlider((settings.marginAdjust + ml) / (2f * ml), "Margins", steps = 2 * ml + 1) {
                if (on) set(settings.copy(marginAdjust = ((it * 2 - 1) * ml).roundToInt()))
            }
        }
        if (tablet || !sideways) {
            SectionLabel("Columns", Modifier.padding(top = 16.dp, bottom = 8.dp))
            Segmented(Columns.entries.map { it.label }, settings.columns.ordinal) { set(settings.copy(columns = Columns.entries[it])) }
        }
        if (!tablet) SwitchRow("Speed reading in landscape", "Show one passage at a time when the phone is sideways", settings.landscapeSpeedReading) {
            onChange(settings.copy(landscapeSpeedReading = it))
        }
    }
    /** The theme as it reads, in [lines] lines of the sample. */
    val preview: @Composable (Modifier, Int) -> Unit = { m, lines ->
        Box(m.clip(RoundedCornerShape(12.dp)).background(colors.page)
            .border(1.dp, jr.outlineVariant, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 16.dp)) {
            val size = (settings.fontSizePx * 0.9f)
            Text(SAMPLE, maxLines = lines, overflow = TextOverflow.Clip, color = colors.text, style = TextStyle(
                fontFamily = settings.font.familyOrDefault, fontSize = size.sp, lineHeight = (size * settings.effectiveLineHeight).sp,
                fontWeight = if (settings.effectiveBold) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = settings.effectiveLetterSpacing.em,
                textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            ))
        }
    }
    val tabs: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth()) {
            listOf("Text", "Layout").forEachIndexed { i, l ->
                Box(Modifier.weight(1f).height(48.dp).clickable { tab = i }, contentAlignment = Alignment.Center) {
                    Text(l, style = JrType.label, color = if (tab == i) jr.primary else jr.onVariant)
                    if (tab == i) Box(Modifier.align(Alignment.BottomCenter).size(40.dp, 3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(jr.primary))
                }
            }
        }
        Divider()
    }
    /** Close, title, Reset theme and Done, as the wide layouts have them. */
    val header: @Composable (Modifier) -> Unit = { m ->
        Row(m.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(JrIcons.Close, "Close without saving", jr.onBackground, onClick = onDone)
            Text("Customise theme", style = JrType.headline, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable { onChange(settings.resetTheme()) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center) { Text("Reset theme", style = JrType.label, color = jr.error) }
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(jr.primary).clickable(onClick = onDone).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center) { Text("Done", style = JrType.label, color = jr.onPrimary) }
        }
    }
    val swallow = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {}

    when {
        // a phone on its side: the sample on the left, the settings beside it
        short -> Column(Modifier.fillMaxSize().background(jr.background).then(swallow)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            header(Modifier.height(56.dp).padding(start = 4.dp, end = 24.dp))
            Row(Modifier.weight(1f).padding(start = 24.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preview(Modifier.width(240.dp).fillMaxHeight().padding(top = 8.dp), 12)
                Column(Modifier.weight(1f)) {
                    tabs()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                        if (tab == 0) textSettings() else layoutSettings()
                    }
                }
            }
        }
        // a tablet: a dialog over the pages; side by side when held sideways
        tablet -> Box(Modifier.fillMaxSize().background(jr.scrim).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDone)
            .windowInsetsPadding(WindowInsets.systemBars), contentAlignment = Alignment.Center) {
            Column(Modifier.then(if (sideways) Modifier.padding(24.dp).widthIn(max = 1000.dp).heightIn(max = 728.dp).fillMaxSize() else Modifier.padding(24.dp).width(640.dp).heightIn(max = 1056.dp).fillMaxHeight())
                .clip(RoundedCornerShape(28.dp)).background(jr.sheet).then(swallow)) {
                header(Modifier.height(72.dp).padding(start = 12.dp, end = 24.dp))
                preview(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), 3)
                if (sideways) {
                    Row(Modifier.weight(1f).padding(top = 8.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                            SectionLabel("Text", Modifier.padding(top = 12.dp)); textSettings()
                        }
                        Box(Modifier.padding(top = 16.dp, bottom = 24.dp).width(1.dp).fillMaxHeight().background(jr.outlineVariant))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                            SectionLabel("Layout", Modifier.padding(top = 12.dp)); layoutSettings()
                        }
                    }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
                        SectionLabel("Text", Modifier.padding(top = 12.dp)); textSettings()
                        SectionLabel("Layout", Modifier.padding(top = 24.dp)); layoutSettings()
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
        else -> Column(Modifier.fillMaxSize().background(jr.background).then(swallow).windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(JrIcons.Close, "Close", jr.onBackground, onClick = onDone)
                Text("Customise theme", style = JrType.headline, color = jr.onBackground, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(jr.primary).clickable(onClick = onDone).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center) { Text("Done", style = JrType.label, color = jr.onPrimary) }
            }
            // live preview in the reading theme
            preview(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), 3)
            Box(Modifier.padding(top = 8.dp)) { Column { tabs() } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)) {
                if (tab == 0) textSettings() else layoutSettings()
                Text("Reset theme", style = JrType.label, color = jr.error,
                    modifier = Modifier.padding(top = 20.dp, bottom = 24.dp).clip(RoundedCornerShape(8.dp))
                        .clickable { onChange(settings.resetTheme()) }.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    val jr = LocalJr.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, style = JrType.body, color = jr.onBackground, modifier = Modifier.weight(1f))
        Text(value, style = JrType.body, color = jr.onVariant)
    }
}

// ---------------------------------------------------------------- font picker

@Composable
fun FontScreen(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, onBack: () -> Unit) {
    val jr = LocalJr.current
    BackAction(onBack = onBack)
    Column(Modifier.fillMaxSize().background(jr.background).clickable(remember { MutableInteractionSource() }, indication = null) {}
        .windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(JrIcons.Back, "Back", jr.onBackground, onClick = onBack)
            Text("Font", style = JrType.headline, color = jr.onBackground, modifier = Modifier.padding(start = 4.dp))
        }
        LazyColumn(Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars)) {
            for (g in FontGroup.entries) {
                item { SectionLabel(g.label, Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)) }
                items(ReaderFont.entries.filter { it.group == g }) { f ->
                    val pick = { onChange(settings.copy(font = f, customized = true)) }
                    Row(Modifier.fillMaxWidth().clickable(onClick = pick).padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.label, fontFamily = f.familyOrDefault, fontSize = 18.sp, lineHeight = 24.sp, color = jr.onBackground)
                            Text(f.note, style = JrType.caption, color = jr.onVariant)
                        }
                        RadioButton(selected = settings.font == f, onClick = pick,
                            colors = RadioButtonDefaults.colors(selectedColor = jr.primary, unselectedColor = jr.onVariant))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------- contents and bookmarks

@Composable
fun ContentsSheet(
    shelf: ShelfBook, book: EpubBook, controller: AppController, pg: Pagination, toc: List<TocRow>, current: Int,
    onPick: (Int) -> Unit, onPickLocator: (Locator) -> Unit, onDismiss: () -> Unit,
    marks: List<Highlight> = emptyList(), place: (Highlight) -> String = { "" },
    onNote: (Highlight) -> Unit = {}, onCopy: (Highlight) -> Unit = {}, onRemove: (Highlight) -> Unit = {},
    step: Int = 1,
) {
    val jr = LocalJr.current
    var tab by remember { mutableStateOf(0) }
    var goTo by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val version = controller.metaVersion
    val bookmarks = controller.bookmarks(shelf)
    val pages = pg.pages.size
    val conf = LocalConfiguration.current
    val wide = conf.screenWidthDp >= 600
    val short = conf.screenHeightDp < 480
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (short) {
            // a phone on its side: one compact row
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BookCover(shelf, Modifier.size(28.dp, 42.dp), showText = false, corner = 2.dp)
                Text(book.title, style = JrType.title, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!goTo) Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, jr.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { goTo = true }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text("Go to page", style = JrType.label, color = jr.onBackground)
                }
                IconBtn(JrIcons.Close, "Close contents", jr.onBackground, onClick = onDismiss)
            }
        } else {
            if (wide) Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(shelf, Modifier.size(40.dp, 60.dp), showText = false, corner = 3.dp)
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = JrType.headline, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, style = JrType.body, color = jr.onVariant, maxLines = 1)
                }
                IconBtn(JrIcons.Close, "Close contents", jr.onBackground, onClick = onDismiss)
            }
        }
        if (!short || goTo) Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (goTo) {
                var text by remember { mutableStateOf("") }
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                val go = { text.toIntOrNull()?.let { onPick((it - 1).coerceIn(0, pages - 1)) }; Unit }
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, jr.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Page 1–$pages", style = JrType.bodyLarge, color = jr.onVariant)
                    BasicTextField(text, { t -> text = t.filter { it.isDigit() }.take(6) }, singleLine = true,
                        textStyle = JrType.bodyLarge.copy(color = jr.onBackground), cursorBrush = SolidColor(jr.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(jr.primary).clickable { go() }.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center) { Text("Go", style = JrType.label, color = jr.onPrimary) }
            } else {
                val where = if (step > 1 && current + 1 < pages) "Pages ${current + 1}–${current + 2}" else "Page ${current + 1}"
                Text("$where of $pages · ${((current + 1) * 100f / pages).roundToInt()}%", style = JrType.body, color = jr.onVariant,
                    modifier = Modifier.weight(1f))
                Box(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, jr.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { goTo = true }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text("Go to page", style = JrType.label, color = jr.onBackground)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Chapters" to null, "Bookmarks" to bookmarks.size, "Highlights" to marks.size).forEachIndexed { i, (label, count) ->
                Box(Modifier.weight(1f).height(48.dp).clickable { tab = i }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, style = JrType.label, color = if (tab == i) jr.primary else jr.onVariant)
                        if (count != null && count > 0) Text("$count", style = JrType.labelSmall, color = jr.onVariant)
                    }
                    if (tab == i) Box(Modifier.align(Alignment.BottomCenter).size(64.dp, 3.dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(jr.primary))
                }
            }
        }
        Divider()
        if (tab == 0) {
            val currentRow = toc.indexOfLast { it.page <= current }
            LazyColumn(Modifier.weight(1f), state = rememberLazyListState(maxOf(0, currentRow - 3)), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp)) {
                itemsIndexed(toc) { i, r ->
                    val on = i == currentRow
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                        .background(if (on) jr.secondaryContainer else jr.sheet.copy(alpha = 0f))
                        .clickable { onPick(r.page) }.padding(start = (16 + 16 * r.level).dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(r.title, style = JrType.bodyLarge, color = if (on) jr.onSecondaryContainer else jr.onBackground,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${r.page + 1}", style = JrType.body, color = if (on) jr.onSecondaryContainer else jr.onVariant)
                    }
                }
            }
        } else if (tab == 2) {
            HighlightsList(marks, book, pg, place,
                onPick = { h -> onPickLocator(Locator(h.span.chapter, h.span.start.block, h.span.start.char)) },
                onNote = onNote, onCopy = onCopy, onRemove = onRemove)
        } else if (bookmarks.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(JrIcons.Bookmark, null, tint = jr.onVariant, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(12.dp))
                Text("No bookmarks yet", style = JrType.title, color = jr.onBackground)
                Text("Tap the bookmark at the top of a page to keep your place.", style = JrType.body, color = jr.onVariant, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(bookmarks) { b ->
                    val l = b.locator
                    val page = pg.pageFor(l.chapter, l.block, l.char)
                    val snippet = (book.chapters.getOrNull(l.chapter)?.blocks?.getOrNull(l.block) as? Block.Text)?.text?.text
                        ?.drop(l.char)?.trim()?.take(90).orEmpty()
                    Row(Modifier.fillMaxWidth().clickable { onPickLocator(l) }.padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(JrIcons.BookmarkFilled, null, tint = jr.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${toc.lastOrNull { it.page <= page }?.title ?: ""} · page ${page + 1}", style = JrType.labelSmall, color = jr.onVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (snippet.isNotEmpty()) Text(snippet, style = JrType.body, color = jr.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconBtn(JrIcons.Close, "Remove bookmark", jr.onVariant, size = 44.dp) { controller.removeBookmarks(shelf) { it == b } }
                    }
                }
            }
        }
    }
    if (wide) JrSideSheet(onDismiss, fromLeft = true, width = if (short) 420.dp else 400.dp, content = body)
    else JrSheet(onDismiss, fullHeightTop = 60.dp, content = body)
}

// ---------------------------------------------------------------- search in book

private class Hit(val chapter: Int, val block: Int, val char: Int, val page: Int, val snippet: AnnotatedString)

@Composable
fun SearchSheet(book: EpubBook, pg: Pagination, toc: List<TocRow>, onPick: (Int) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    val jr = LocalJr.current
    var query by remember { mutableStateOf(initial) }
    var hits by remember { mutableStateOf<List<Hit>?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { hits = null; return@LaunchedEffect }
        delay(250)
        val mark = SpanStyle(fontWeight = FontWeight.Bold, background = jr.secondaryContainer, color = jr.onSecondaryContainer)
        hits = withContext(Dispatchers.Default) {
            val out = ArrayList<Hit>()
            outer@ for ((ci, ch) in book.chapters.withIndex()) {
                for ((bi, b) in ch.blocks.withIndex()) {
                    if (b !is Block.Text) continue
                    val t = b.text.text
                    var from = 0
                    while (true) {
                        val at = t.indexOf(q, from, ignoreCase = true)
                        if (at < 0) break
                        val s = (at - 40).coerceAtLeast(0); val e = (at + q.length + 60).coerceAtMost(t.length)
                        val snippet = buildAnnotatedString {
                            if (s > 0) append("…")
                            append(t.substring(s, at))
                            withStyle(mark) { append(t.substring(at, at + q.length)) }
                            append(t.substring(at + q.length, e))
                            if (e < t.length) append("…")
                        }
                        out += Hit(ci, bi, at, pg.pageFor(ci, bi, at), snippet)
                        if (out.size >= 300) break@outer
                        from = at + q.length
                    }
                }
            }
            out
        }
    }
    JrSheet(onDismiss, fullHeightTop = 60.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 8.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(jr.container).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JrIcons.Search, null, tint = jr.onVariant, modifier = Modifier.size(24.dp))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search in ${book.title}", style = JrType.bodyLarge, color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = JrType.bodyLarge.copy(color = jr.onBackground),
                    cursorBrush = SolidColor(jr.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            if (query.isNotEmpty()) IconBtn(JrIcons.Close, "Clear", jr.onVariant) { query = "" }
            else IconBtn(JrIcons.Close, "Close search", jr.onVariant, onClick = onDismiss)
        }
        val list = hits
        if (list != null) Text(if (list.isEmpty()) "No results" else if (list.size >= 300) "300+ results" else "${list.size} result${if (list.size == 1) "" else "s"}",
            style = JrType.labelSmall, color = jr.onVariant, modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            items(list.orEmpty()) { hit ->
                Column(Modifier.fillMaxWidth().clickable { onPick(hit.page) }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Row {
                        Text(toc.lastOrNull { it.page <= hit.page }?.title ?: book.chapters[hit.chapter].title, style = JrType.labelSmall,
                            color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${hit.page + 1}", style = JrType.labelSmall, color = jr.onVariant)
                    }
                    Text(hit.snippet, style = JrType.body, color = jr.onBackground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
