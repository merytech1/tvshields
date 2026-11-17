package io.github.merytech1.tvshields.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.browser.Switch
import io.github.merytech1.tvshields.lists.FilterList
import io.github.merytech1.tvshields.lists.OptionalLists
import io.github.merytech1.tvshields.ui.theme.Accent
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors
import io.github.merytech1.tvshields.ui.theme.UiMode
import io.github.merytech1.tvshields.ui.theme.tvShieldsColors

private enum class Section(@StringRes val title: Int) {
    APPEARANCE(R.string.settings_appearance),
    FILTER_LISTS(R.string.settings_filter_lists),
}

/** The optional lists as Settings groups them (docs/resources/filter-lists.md). */
private val listGroups = listOf(
    R.string.settings_lists_annoyances to OptionalLists.annoyances,
    R.string.settings_lists_embeds to OptionalLists.embeds,
    R.string.settings_lists_regional to OptionalLists.regional,
)

/**
 * Settings (design.md §2 and §14, roadmap 6.3), drawn over the browser so the page stays loaded.
 * Sections on the left: focusing one shows it on the right. Focus starts on the first section and
 * cannot leave the screen; Back closes it and [onClose] returns focus to where it was opened.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUiMode: (UiMode) -> Unit,
    onAccent: (Accent) -> Unit,
    onListToggled: (String) -> Unit,
    onUpdateLists: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    var section by rememberSaveable { mutableStateOf(Section.APPEARANCE) }
    val sectionFocus = remember { Section.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { sectionFocus.getValue(section).requestFocus() }

    Column(
        modifier = modifier
            .onPreviewKeyEvent {
                if (it.key != Key.Back) return@onPreviewKeyEvent false
                if (it.type == KeyEventType.KeyUp) onClose()
                true
            }
            // The browser is still behind: arrows must not reach it. Programmatic moves (back to
            // the top bar on close) pass.
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection in arrowDirections) cancelFocusChange()
                }
            }
            .focusGroup()
            .background(colors.background)
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        Row(Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Coming back from a section lands on its own entry, not on the nearest one.
                modifier = Modifier
                    .width(280.dp)
                    .focusRestorer(sectionFocus.getValue(section))
                    .focusGroup(),
            ) {
                Section.entries.forEach { entry ->
                    SectionItem(
                        title = entry.title,
                        selected = entry == section,
                        onFocused = { section = entry },
                        modifier = Modifier.focusRequester(sectionFocus.getValue(entry)),
                    )
                }
            }
            Spacer(Modifier.width(32.dp))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    Section.APPEARANCE -> AppearanceSection(state.settings, onUiMode, onAccent)
                    Section.FILTER_LISTS -> FilterListsSection(state, onListToggled, onUpdateLists)
                }
            }
        }
    }
}

private val arrowDirections = setOf(
    FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right,
    FocusDirection.Next, FocusDirection.Previous,
)

/** Focus ring with a gap, then the fill (design.md §6). */
private fun Modifier.focusFrame(focused: Boolean, ring: Color, fill: Color, radius: Dp = 8.dp) =
    border(3.dp, if (focused) ring else Color.Transparent, RoundedCornerShape(radius + 5.dp))
        .padding(5.dp)
        .background(fill, RoundedCornerShape(radius))

/** A section entry: selected = accent container and chevron (mockup). OK moves into the section. */
@Composable
private fun SectionItem(
    @StringRes title: Int,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val content = if (selected) colors.onAccentContainer else colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null) { focusManager.moveFocus(FocusDirection.Right) }
            .semantics { this.selected = selected }
            .focusFrame(focused, colors.accent, if (selected) colors.accentContainer else colors.surface)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = LocalTvShieldsColors.current.textPrimary,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupLabel(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = LocalTvShieldsColors.current.textSecondary,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalTvShieldsColors.current.textSecondary,
        modifier = modifier,
    )
}

/** Mode and accent (design.md §2): both apply at once. */
@Composable
private fun AppearanceSection(settings: Settings, onUiMode: (UiMode) -> Unit, onAccent: (Accent) -> Unit) {
    Column {
        SectionTitle(R.string.settings_appearance)
        GroupLabel(R.string.settings_mode)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            UiMode.entries.forEach { mode ->
                ChoiceCard(
                    label = stringResource(if (mode == UiMode.DARK) R.string.settings_mode_dark else R.string.settings_mode_light),
                    selected = settings.uiMode == mode,
                    onClick = { onUiMode(mode) },
                    minHeight = 100.dp,
                    modifier = Modifier.weight(1f),
                ) { ModePreview(mode, settings.accent) }
            }
        }
        GroupLabel(R.string.settings_accent, Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Accent.entries.forEach { accent ->
                ChoiceCard(
                    label = stringResource(if (accent == Accent.PURPLE) R.string.settings_accent_purple else R.string.settings_accent_emerald),
                    selected = settings.accent == accent,
                    onClick = { onAccent(accent) },
                    minHeight = 72.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(tvShieldsColors(settings.uiMode, accent).accent, CircleShape),
                    )
                }
            }
        }
        Note(stringResource(R.string.settings_appearance_note), Modifier.padding(top = 24.dp))
    }
}

/** Selectable card: selected = accent container and check, also when not focused (design.md §6). */
@Composable
private fun ChoiceCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    val fill = when {
        selected -> colors.accentContainer
        focused -> colors.surfaceRaised
        else -> colors.surface
    }
    val content = if (selected) colors.onAccentContainer else colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .semantics { this.selected = selected }
            .focusFrame(focused, colors.accent, fill, radius = 12.dp)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = 16.dp),
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** A small window in the colors of [mode]: bar and page, as in the mockup. */
@Composable
private fun ModePreview(mode: UiMode, accent: Accent) {
    val c = tvShieldsColors(mode, accent)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .size(width = 100.dp, height = 64.dp)
            .background(c.background, RoundedCornerShape(6.dp))
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(10.dp).background(c.surfaceRaised, RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxWidth().weight(1f).background(c.surface, RoundedCornerShape(3.dp)))
    }
}

/**
 * The default lists are always on; the optional ones are switched here. Each switch is saved at
 * once and applied by an update a moment later (several switches share one). The button and the
 * status stay in place while the lists scroll, so a switch far down still shows its update.
 */
@Composable
private fun FilterListsSection(state: SettingsUiState, onListToggled: (String) -> Unit, onUpdateLists: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SectionTitle(R.string.settings_filter_lists)
        Note(stringResource(R.string.settings_lists_intro))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
            PrimaryButton(stringResource(R.string.action_update_lists), onClick = onUpdateLists)
            Spacer(Modifier.width(16.dp))
            Note(stringResource(listsStatusText(state.lists)), Modifier.weight(1f))
        }
        OptionalListRows(state.settings.enabledLists, onListToggled, Modifier.weight(1f))
    }
}

@Composable
private fun OptionalListRows(enabled: Set<String>, onListToggled: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        listGroups.forEach { (title, lists) ->
            item { GroupLabel(title) }
            items(lists, key = FilterList::id) { list ->
                ListToggleRow(
                    title = list.title,
                    on = list.id in enabled,
                    onToggle = { onListToggled(list.id) },
                )
            }
        }
        item { Spacer(Modifier.height(27.dp)) }
    }
}

@StringRes
private fun listsStatusText(status: ListsStatus) = when (status) {
    ListsStatus.IDLE -> R.string.lists_status_idle
    ListsStatus.UPDATING -> R.string.lists_status_updating
    ListsStatus.UPDATED -> R.string.lists_status_updated
    ListsStatus.UP_TO_DATE -> R.string.lists_status_up_to_date
    ListsStatus.PARTLY_FAILED -> R.string.lists_status_partly_failed
    ListsStatus.OFFLINE -> R.string.lists_status_offline
    ListsStatus.FAILED -> R.string.lists_status_failed
}

/** Primary button (design.md §9): accent fill, one per screen. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .focusFrame(focused, colors.accent, colors.accent)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 24.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = colors.onAccent)
    }
}

/** List row with a toggle (design.md §9); the state is also said in words. OK switches it. */
@Composable
private fun ListToggleRow(title: String, on: Boolean, onToggle: () -> Unit) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val state = stringResource(if (on) R.string.shields_state_on else R.string.shields_state_off)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onToggle)
            .semantics { stateDescription = state }
            .focusFrame(focused, colors.accent, if (focused) colors.surfaceRaised else colors.surface)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(text = state, style = MaterialTheme.typography.labelLarge, color = if (on) colors.accent else colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Switch(on)
    }
}
