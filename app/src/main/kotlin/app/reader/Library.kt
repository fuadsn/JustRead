package app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun Library(controller: AppController) {
    val jr = LocalJr.current
    @Suppress("UNUSED_VARIABLE") val version = controller.metaVersion // recompose when saved data changes
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sortSheet by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }

    val books = controller.sortedShelf().let { all ->
        if (query.isBlank()) all else all.filter { it.title.contains(query, true) || it.author.contains(query, true) }
    }
    val current = controller.shelf.filter { controller.position(it) != null && !controller.finished(it) }
        .maxByOrNull { controller.lastOpened(it) }
    val featured = current ?: books.firstOrNull { !controller.finished(it) }
    val grid = controller.view == ShelfView.Grid
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // screen size, as the design has it: phone, phone on its side (short), tablet
    val conf = LocalConfiguration.current
    val short = conf.screenHeightDp < 480
    val tablet = minOf(conf.screenWidthDp, conf.screenHeightDp) >= 600
    val sideways = conf.screenWidthDp > conf.screenHeightDp && conf.screenWidthDp >= 600
    val edge = if (tablet) 32.dp else 24.dp
    val columns = when {
        !grid -> GridCells.Fixed(1)
        short || (tablet && conf.screenWidthDp >= 1000) -> GridCells.Fixed(6)
        conf.screenWidthDp >= 600 -> GridCells.Fixed(4)
        else -> GridCells.Adaptive(150.dp)
    }

    Box(Modifier.fillMaxSize().background(jr.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal))) {
            TopBar(controller, searching, query, onQuery = { query = it }, onSearch = { searching = it; if (!it) query = "" },
                onGoal = { goalDialog = true }, start = edge, height = if (short) 48.dp else 64.dp)
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = if (grid) edge else 0.dp, end = if (grid) edge else 0.dp, bottom = 24.dp + navBottom),
                horizontalArrangement = Arrangement.spacedBy(if (tablet) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) (if (tablet) 28.dp else 20.dp) else 0.dp),
            ) {
                val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
                if (!searching) {
                    val inset = Modifier.padding(horizontal = if (grid) 0.dp else 16.dp)
                    if (sideways) item(span = full) {
                        // side by side when the screen is wide and held sideways
                        Row(inset.padding(top = if (short) 4.dp else 8.dp, bottom = if (grid) 0.dp else 12.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(if (tablet) 24.dp else 16.dp)) {
                            if (featured != null) {
                                if (short) FeaturedBook(controller, featured, Modifier.weight(3f), compact = true)
                                else FeaturedBook(controller, featured, Modifier.weight(3f), compact = false)
                            }
                            GoalCard(controller, Modifier.weight(2f).fillMaxHeight(), tall = featured != null) { goalDialog = true }
                        }
                    } else {
                        if (featured != null) item(span = full) {
                            FeaturedBook(controller, featured, inset.padding(top = 8.dp, bottom = if (grid) 0.dp else 12.dp), compact = false)
                        }
                        item(span = full) {
                            GoalCard(controller, inset.padding(top = if (featured != null) 0.dp else 8.dp, bottom = if (grid) 0.dp else 12.dp)) { goalDialog = true }
                        }
                    }
                }
                item(span = full) {
                    ShelfHeader(controller, books.size, Modifier.padding(start = if (grid) 0.dp else 24.dp, end = if (grid) 0.dp else 4.dp)) { sortSheet = true }
                }
                if (books.isEmpty()) item(span = full) {
                    Text(if (query.isNotBlank()) "No books match “$query”" else "Your library is empty",
                        style = JrType.body, color = jr.onVariant, modifier = Modifier.padding(24.dp))
                }
                items(books, key = { it.key }) { b ->
                    if (grid) GridBook(controller, b) else ListBook(controller, b)
                }
            }
        }
        if (sortSheet) SortSheet(controller) { sortSheet = false }
        if (goalDialog) GoalDialog(controller) { goalDialog = false }
    }
}

@Composable
private fun TopBar(
    controller: AppController, searching: Boolean, query: String, onQuery: (String) -> Unit, onSearch: (Boolean) -> Unit, onGoal: () -> Unit,
    start: androidx.compose.ui.unit.Dp = 24.dp, height: androidx.compose.ui.unit.Dp = 64.dp,
) {
    val jr = LocalJr.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(height).padding(start = if (searching) 4.dp else start, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (searching) {
            BackAction { onSearch(false) }
            IconBtn(JrIcons.Back, "Close search", jr.onBackground) { onSearch(false) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search your library", style = JrType.bodyLarge, color = jr.onVariant)
                BasicTextField(query, onQuery, singleLine = true, textStyle = JrType.bodyLarge.copy(color = jr.onBackground),
                    cursorBrush = SolidColor(jr.primary), modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            if (query.isNotEmpty()) IconBtn(JrIcons.Close, "Clear search", jr.onVariant) { onQuery("") }
        } else {
            Text(buildAnnotatedString {
                append("Just")
                withStyle(SpanStyle(color = jr.primary)) { append("Read") }
            }, style = JrType.wordmark, color = jr.onBackground, modifier = Modifier.weight(1f).semantics { contentDescription = "JustRead" })
            controller.requestOpenFile?.let { IconBtn(JrIcons.Plus, "Add an EPUB", jr.primary, onClick = it) }
            IconBtn(JrIcons.Search, "Search library", jr.onBackground) { onSearch(true) }
            Box {
                IconBtn(JrIcons.More, "More options", jr.onBackground) { menu = true }
                DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = jr.menu,
                    // keep clear of the screen edge, a little below the button
                    offset = androidx.compose.ui.unit.DpOffset((-12).dp, 4.dp), modifier = Modifier.width(232.dp)) {
                    MenuRow("Daily reading goal", JrIcons.Target) { menu = false; onGoal() }
                }
            }
        }
    }
}

@Composable
fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, trailing: String? = null, onClick: () -> Unit) {
    val jr = LocalJr.current
    DropdownMenuItem(
        text = { Text(label, style = JrType.bodyLarge, color = jr.onBackground) },
        leadingIcon = if (icon != null) { { Icon(icon, null, tint = jr.onVariant, modifier = Modifier.size(24.dp)) } } else null,
        trailingIcon = if (trailing != null) { { Text(trailing, style = JrType.labelSmall, color = jr.onVariant) } } else null,
        onClick = onClick,
        modifier = Modifier.width(232.dp),
    )
}

@Composable
private fun FeaturedBook(controller: AppController, book: ShelfBook, modifier: Modifier, compact: Boolean) {
    val jr = LocalJr.current
    val progress = controller.progress(book)
    val pct = ((progress ?: 0f) * 100).roundToInt()
    val action = if (progress == null) "Start reading" else "Continue reading"
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(jr.secondaryContainer)
        .clickable(onClickLabel = "$action ${book.title}") { controller.reading = book }
        .padding(if (compact) 12.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BookCover(book, Modifier.width(if (compact) 48.dp else 76.dp).aspectRatio(0.67f), showText = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(action, style = JrType.labelSmall, color = jr.onSecondaryContainer)
            Text(book.title, style = if (compact) JrType.title else JrType.headline, color = jr.onSecondaryContainer,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val detail = if (progress == null) book.author else listOfNotNull(controller.chapterTitle(book), "$pct% read").joinToString(" · ")
            if (detail.isNotBlank()) Text(detail, style = JrType.caption, color = jr.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GoalCard(controller: AppController, modifier: Modifier, tall: Boolean = false, onClick: () -> Unit) {
    val jr = LocalJr.current
    val goal = controller.goalMinutes
    val minutes = controller.secondsToday() / 60
    val streak = controller.streak()
    Row(modifier.fillMaxWidth().let { if (tall) it else it.height(64.dp) }.clip(RoundedCornerShape(12.dp)).border(1.dp, jr.outlineVariant, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GoalRing(minutes / goal.toFloat())
        Column(Modifier.weight(1f)) {
            Text(if (minutes >= goal) "Goal reached · $minutes minutes today" else "$minutes of $goal minutes today",
                style = JrType.label, color = jr.onBackground)
            Text(when (streak) { 0 -> "Daily goal"; 1 -> "Daily goal · 1 day in a row"; else -> "Daily goal · $streak days in a row" },
                style = JrType.caption, color = jr.onVariant)
        }
    }
}

@Composable
private fun ShelfHeader(controller: AppController, count: Int, modifier: Modifier, onSort: () -> Unit) {
    val jr = LocalJr.current
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("All books", style = JrType.title, color = jr.onBackground)
            Text("$count", style = JrType.body, color = jr.onVariant, modifier = Modifier.padding(bottom = 2.dp))
        }
        Row(Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, jr.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onSort).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(JrIcons.Sort, null, tint = jr.primary, modifier = Modifier.size(18.dp))
            Text(controller.sortBy.label, style = JrType.label, color = jr.onBackground)
            Icon(JrIcons.ChevronDown, null, tint = jr.onBackground, modifier = Modifier.size(18.dp))
        }
        val grid = controller.view == ShelfView.Grid
        IconBtn(if (grid) JrIcons.Contents else JrIcons.Grid, if (grid) "Show as list" else "Show as grid", jr.onVariant) {
            controller.updateView(if (grid) ShelfView.List else ShelfView.Grid)
        }
    }
}

/** The small status under a book: a progress bar, "New", or "Finished". */
@Composable
private fun BookStatus(controller: AppController, b: ShelfBook, compact: Boolean) {
    val jr = LocalJr.current
    val p = controller.progress(b)
    when {
        p == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(20.dp).clip(RoundedCornerShape(10.dp)).background(jr.primary).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text("New", style = JrType.labelSmall.copy(fontSize = JrType.labelSmall.fontSize * 0.92f), color = jr.onPrimary)
            }
        }
        p >= 0.999f -> Text("Finished", style = JrType.labelSmall, color = jr.onVariant)
        compact -> Text("${(p * 100).roundToInt()}%", style = JrType.labelSmall, color = jr.onVariant)
        else -> Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StopProgress(p, Modifier.weight(1f))
            Text("${(p * 100).roundToInt()}%", style = JrType.labelSmall, color = jr.onVariant)
        }
    }
}

@Composable
private fun BookMenu(controller: AppController, b: ShelfBook) {
    val jr = LocalJr.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn(JrIcons.More, "Options for ${b.title}", jr.onVariant, size = 44.dp) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = jr.menu) {
            MenuRow("Open", JrIcons.ChevronRight) { open = false; controller.reading = b }
            if (controller.finished(b)) MenuRow("Mark as unread", JrIcons.Check) { open = false; controller.markFinished(b, false) }
            else MenuRow("Mark as finished", JrIcons.Check) { open = false; controller.markFinished(b, true) }
            if (b.storeId != null) MenuRow("Remove from library", JrIcons.Trash) { open = false; controller.remove(b) }
        }
    }
}

@Composable
private fun GridBook(controller: AppController, b: ShelfBook) {
    val jr = LocalJr.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BookCover(b, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clickable { controller.reading = b })
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(b.title, style = JrType.bookTitle, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, style = JrType.caption, color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 2.dp)) { BookStatus(controller, b, compact = false) }
            }
            Box(Modifier.padding(end = 0.dp).padding(top = 0.dp)) { BookMenu(controller, b) }
        }
    }
}

@Composable
private fun ListBook(controller: AppController, b: ShelfBook) {
    val jr = LocalJr.current
    Column {
        Row(Modifier.fillMaxWidth().clickable { controller.reading = b }.padding(start = 24.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(b, Modifier.width(40.dp).height(60.dp), showText = false, corner = 3.dp)
            Column(Modifier.weight(1f)) {
                Text(b.title, style = JrType.bookTitle, color = jr.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, style = JrType.body, color = jr.onVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BookStatus(controller, b, compact = true)
            BookMenu(controller, b)
        }
        Divider(Modifier.padding(start = 80.dp, end = 16.dp))
    }
}

@Composable
private fun SortSheet(controller: AppController, onDismiss: () -> Unit) {
    val jr = LocalJr.current
    JrSheet(onDismiss) {
        SheetTitle("Sort and view", null)
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            SectionLabel("Sort by", Modifier.padding(top = 4.dp, bottom = 4.dp))
            for (s in SortBy.entries) {
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { controller.updateSort(s) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label, style = JrType.bodyLarge, color = jr.onBackground, modifier = Modifier.weight(1f))
                    RadioButton(selected = controller.sortBy == s, onClick = { controller.updateSort(s) },
                        colors = RadioButtonDefaults.colors(selectedColor = jr.primary, unselectedColor = jr.onVariant))
                }
            }
            SectionLabel("Show as", Modifier.padding(top = 12.dp, bottom = 8.dp))
            Segmented(listOf("Grid", "List"), controller.view.ordinal) { controller.updateView(ShelfView.entries[it]) }
        }
    }
}

@Composable
private fun GoalDialog(controller: AppController, onDismiss: () -> Unit) {
    val jr = LocalJr.current
    JrSheet(onDismiss) {
        SheetTitle("Daily reading goal", null)
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Minutes of reading a day. The ring on your library fills as you read.", style = JrType.body, color = jr.onVariant,
                modifier = Modifier.padding(bottom = 8.dp))
            for (m in listOf(5, 10, 15, 20, 30, 45, 60)) {
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { controller.updateGoal(m); onDismiss() }, verticalAlignment = Alignment.CenterVertically) {
                    Text("$m minutes", style = JrType.bodyLarge, color = jr.onBackground, modifier = Modifier.weight(1f))
                    RadioButton(selected = controller.goalMinutes == m, onClick = { controller.updateGoal(m); onDismiss() },
                        colors = RadioButtonDefaults.colors(selectedColor = jr.primary, unselectedColor = jr.onVariant))
                }
            }
        }
    }
}
