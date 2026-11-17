package io.github.merytech1.tvshields.browser

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

/** What the badge and the panel show for the current page. */
enum class ShieldsStatus {
    ON,

    /** Turned off for this site (rules P3). */
    OFF_FOR_SITE,

    /** The engine has no lists yet: nothing is blocked anywhere. */
    NO_LISTS,
    ;

    companion object {
        fun of(offForSite: Boolean, engineReady: Boolean) = when {
            offForSite -> OFF_FOR_SITE
            !engineReady -> NO_LISTS
            else -> ON
        }
    }
}

/** Counters change in place; tabular figures keep them from jumping (design.md §4). */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/**
 * Shields badge in the top bar (design.md §9). On: accent fill with the page's blocked count, or
 * accent-container while the panel is open. Off for this site: warning outline, shield-off icon
 * and "Off". No lists: warning outline without a count.
 */
@Composable
fun ShieldsBadge(
    count: Int,
    status: ShieldsStatus,
    panelOpen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    val on = status == ShieldsStatus.ON
    val (fill, content) = when {
        !on -> Color.Transparent to colors.warning
        panelOpen -> colors.accentContainer to colors.onAccentContainer
        else -> colors.accent to colors.onAccent
    }
    val description = when (status) {
        ShieldsStatus.ON -> pluralStringResource(R.plurals.shields_blocked_on_page, count, count)
        ShieldsStatus.OFF_FOR_SITE -> stringResource(R.string.shields_off_title)
        ShieldsStatus.NO_LISTS -> stringResource(R.string.shields_no_lists_title)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            // Focus is shown by the ring below, not by the default indication.
            .clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .border(3.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(50))
            .padding(5.dp)
            .background(fill, RoundedCornerShape(50))
            .then(if (on) Modifier else Modifier.border(2.dp, colors.warning, RoundedCornerShape(50)))
            .height(48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(if (status == ShieldsStatus.OFF_FOR_SITE) R.drawable.ic_shield_off else R.drawable.ic_shield_check),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        val label = when (status) {
            ShieldsStatus.ON -> count.toString()
            ShieldsStatus.OFF_FOR_SITE -> stringResource(R.string.shields_state_off)
            ShieldsStatus.NO_LISTS -> null
        }
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge.tabular(), color = content)
        }
    }
}

/**
 * Shields panel (design.md §9, roadmaps 6.1 and 6.2): status, site, the per-site switch and what
 * was blocked on this page. The switch takes focus when the panel opens. Back or Left closes it
 * and [onClose] returns focus to the badge; Up leaves it for the top bar, and losing focus closes
 * it too ([onFocusLost]), so focus is never left behind it.
 */
@Composable
fun ShieldsPanel(
    site: String,
    shields: PageShields,
    status: ShieldsStatus,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    val toggleFocus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { toggleFocus.requestFocus() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .onFocusChanged {
                if (it.hasFocus) hadFocus = true
                else if (hadFocus) onFocusLost()
            }
            .onPreviewKeyEvent {
                when (it.key) {
                    Key.Back, Key.DirectionLeft -> {
                        if (it.type == KeyEventType.KeyUp) onClose()
                        true
                    }
                    else -> false
                }
            }
            // Down and Right would reach the page behind it: only Up leaves (to the top bar). Left
            // is handled above, and programmatic moves (back to the badge) must pass.
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Down || requestedFocusDirection == FocusDirection.Right) {
                        cancelFocusChange()
                    }
                }
            }
            .focusGroup()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (status == ShieldsStatus.OFF_FOR_SITE) R.drawable.ic_shield_off else R.drawable.ic_shield_check),
                contentDescription = null,
                tint = if (status == ShieldsStatus.ON) colors.accent else colors.warning,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(
                        when (status) {
                            ShieldsStatus.ON -> R.string.shields_on_title
                            ShieldsStatus.OFF_FOR_SITE -> R.string.shields_off_title
                            ShieldsStatus.NO_LISTS -> R.string.shields_no_lists_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(text = site, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        SiteToggleRow(
            on = status != ShieldsStatus.OFF_FOR_SITE,
            onToggle = onToggle,
            modifier = Modifier.focusRequester(toggleFocus),
        )
        when (status) {
            ShieldsStatus.ON -> {
                Text(
                    text = pluralStringResource(R.plurals.shields_blocked_on_page, shields.total, shields.total),
                    style = MaterialTheme.typography.bodyLarge.tabular(),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                CountRow(R.drawable.ic_trackers, R.string.shields_ads_and_trackers, shields.blocked)
                CountRow(R.drawable.ic_code, R.string.shields_scripts_replaced, shields.scriptsReplaced)
            }
            ShieldsStatus.OFF_FOR_SITE -> PanelNote(R.string.shields_off_message)
            ShieldsStatus.NO_LISTS -> PanelNote(R.string.shields_no_lists_message)
        }
    }
}

@Composable
private fun PanelNote(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = LocalTvShieldsColors.current.textSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** "Shields on this site" with the toggle (design.md §9): the state is also said in words. OK switches it. */
@Composable
private fun SiteToggleRow(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val state = stringResource(if (on) R.string.shields_state_on else R.string.shields_state_off)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onToggle)
            .semantics { stateDescription = state }
            .border(3.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(13.dp))
            .padding(5.dp)
            .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.shields_site_toggle),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(text = state, style = MaterialTheme.typography.labelLarge, color = if (on) colors.accent else colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Switch(on)
    }
}

/** Track and thumb; on = accent (design.md §9). Also used by Settings. */
@Composable
internal fun Switch(on: Boolean) {
    val colors = LocalTvShieldsColors.current
    Box(
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .background(if (on) colors.accent else Color.Transparent, RoundedCornerShape(50))
            .then(if (on) Modifier else Modifier.border(2.dp, colors.textTertiary, RoundedCornerShape(50)))
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(if (on) colors.onAccent else colors.textSecondary, CircleShape),
        )
    }
}

/** List row (design.md §9) with a right-aligned count. Focusable, so the remote can read each count. */
@Composable
private fun CountRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .border(3.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(13.dp))
            .padding(5.dp)
            .background(if (focused) colors.surfaceRaised else Color.Transparent, RoundedCornerShape(8.dp))
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge.tabular(), color = colors.textPrimary)
    }
}
