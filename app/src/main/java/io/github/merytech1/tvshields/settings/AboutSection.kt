package io.github.merytech1.tvshields.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.BuildConfig
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.lists.DefaultLists
import io.github.merytech1.tvshields.lists.ListCredits
import io.github.merytech1.tvshields.lists.OptionalLists
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * About (roadmap 6.4): the app's license, the engine and resources it uses (rules L1), every
 * filter list with its author and license (L2) and the servers list updates contact (C6).
 * Every row takes focus so the remote can scroll through all of it.
 */
@Composable
internal fun AboutSection(
    enabledLists: Set<String>,
    listState: LazyListState,
    licensesRow: FocusRequester,
    onOpenLicenses: () -> Unit,
) {
    val on = stringResource(R.string.shields_state_on)
    val off = stringResource(R.string.shields_state_off)
    val hosts = remember(enabledLists) { About.updateHosts(OptionalLists.withEnabled(enabledLists)) }
    Column(Modifier.fillMaxSize()) {
        SectionTitle(R.string.settings_about)
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            item {
                InfoRow(
                    title = stringResource(R.string.app_name),
                    lines = listOf(
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        stringResource(R.string.about_app_license),
                        About.SOURCE_URL,
                    ),
                )
            }
            item {
                InfoRow(
                    title = stringResource(R.string.about_licenses),
                    lines = listOf(stringResource(R.string.about_licenses_note)),
                    onClick = onOpenLicenses,
                    modifier = Modifier.focusRequester(licensesRow),
                )
            }

            item { GroupLabel(R.string.about_engine) }
            item { CreditRow(About.engine, stringResource(R.string.about_engine_note)) }

            item { Group(R.string.about_resources, R.string.about_resources_note) }
            items(About.resources) { CreditRow(it) }

            item { Group(R.string.about_lists, R.string.about_lists_note) }
            items(DefaultLists.all + OptionalLists.all, key = { "list:${it.id}" }) { list ->
                val enabled = list in DefaultLists.all || list.id in enabledLists
                InfoRow(
                    title = list.title,
                    lines = ListCredits.grouped(list).map { (credit, sources) ->
                        val names = sources.map { it.title }.filter { it != list.title }
                        val by = "${credit.author} · ${credit.license}"
                        if (names.isEmpty()) by else "${names.joinToString()}: $by"
                    },
                    trailing = if (enabled) on else off,
                    trailingAccent = enabled,
                )
            }

            item { Group(R.string.about_connections, R.string.about_connections_note) }
            items(hosts, key = { "host:$it" }) { InfoRow(title = it) }

            item { GroupLabel(R.string.about_included) }
            item {
                InfoRow(
                    title = stringResource(R.string.about_android_libraries),
                    lines = listOf(stringResource(R.string.about_android_libraries_note), "Apache-2.0"),
                )
            }
            item {
                InfoRow(
                    title = stringResource(R.string.about_rust_libraries),
                    lines = listOf(stringResource(R.string.about_rust_libraries_note), stringResource(R.string.about_rust_libraries_licenses)),
                )
            }
            item { Spacer(Modifier.height(27.dp)) }
        }
    }
}

@Composable
private fun Group(title: Int, note: Int) {
    Column {
        GroupLabel(title)
        Note(stringResource(note), Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun CreditRow(credit: Credit, note: String? = null) {
    InfoRow(title = credit.name, lines = listOfNotNull("${credit.author} · ${credit.license}", note, credit.url))
}

/** A row that only informs; with [onClick] it opens something and shows a chevron. */
@Composable
private fun InfoRow(
    title: String,
    modifier: Modifier = Modifier,
    lines: List<String> = emptyList(),
    trailing: String? = null,
    trailingAccent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
                else Modifier.focusable(),
            )
            .focusFrame(focused, colors.accent, if (focused) colors.surfaceRaised else colors.surface)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary) }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = if (trailingAccent) colors.accent else colors.textSecondary,
            )
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The bundled notices (rules L1). Plain text has nothing to focus, so the whole text takes focus
 * and Up / Down scroll it by most of a screen.
 */
@Composable
internal fun LicensesText(modifier: Modifier = Modifier) {
    val colors = LocalTvShieldsColors.current
    val context = LocalContext.current
    val paragraphs by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            context.assets.open(About.LICENSES_ASSET).bufferedReader().use { it.readText() }.split(Regex("\n\\s*\n"))
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .focusRequester(focus)
            .onPreviewKeyEvent {
                val direction = when (it.key) {
                    Key.DirectionDown -> 1f
                    Key.DirectionUp -> -1f
                    else -> return@onPreviewKeyEvent false
                }
                if (it.type == KeyEventType.KeyDown) {
                    val page = listState.layoutInfo.viewportSize.height * 0.8f
                    scope.launch { listState.animateScrollBy(direction * page) }
                }
                true
            }
            .focusable(),
    ) {
        items(paragraphs) { Text(text = it, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary) }
        item { Spacer(Modifier.height(27.dp)) }
    }
}
