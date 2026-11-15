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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

/** Counters change in place; tabular figures keep them from jumping (design.md §4). */
private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/**
 * Shields badge in the top bar (design.md §9): shield icon and the page's blocked count. Accent
 * fill normally, accent-container while the panel is open, warning outline when Shields have no
 * lists.
 */
@Composable
fun ShieldsBadge(
    count: Int,
    active: Boolean,
    panelOpen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    val (fill, content) = when {
        !active -> Color.Transparent to colors.warning
        panelOpen -> colors.accentContainer to colors.onAccentContainer
        else -> colors.accent to colors.onAccent
    }
    val description = if (active) {
        pluralStringResource(R.plurals.shields_blocked_on_page, count, count)
    } else {
        stringResource(R.string.shields_no_lists_title)
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
            .then(if (active) Modifier else Modifier.border(2.dp, colors.warning, RoundedCornerShape(50)))
            .height(48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield_check),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
        if (active) {
            Spacer(Modifier.width(8.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.labelLarge.tabular(), color = content)
        }
    }
}

/**
 * Shields panel (design.md §9, roadmap 6.1): status, site and what was blocked on this page. It
 * takes focus when it opens. Back or Left closes it and [onClose] returns focus to the badge; Up
 * leaves it for the top bar, and losing focus closes it too ([onFocusLost]), so focus is never
 * left behind it.
 */
@Composable
fun ShieldsPanel(
    site: String,
    shields: PageShields,
    active: Boolean,
    onClose: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    val firstRow = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { firstRow.requestFocus() }

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
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_shield_check),
                contentDescription = null,
                tint = if (active) colors.accent else colors.warning,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(if (active) R.string.shields_on_title else R.string.shields_no_lists_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(text = site, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (active) {
            Text(
                text = pluralStringResource(R.plurals.shields_blocked_on_page, shields.total, shields.total),
                style = MaterialTheme.typography.bodyLarge.tabular(),
                color = colors.textPrimary,
            )
            CountRow(R.drawable.ic_trackers, R.string.shields_ads_and_trackers, shields.blocked, Modifier.focusRequester(firstRow))
            CountRow(R.drawable.ic_code, R.string.shields_scripts_replaced, shields.scriptsReplaced)
        } else {
            // Read-only text still needs a focus target, so the panel can take focus.
            Text(
                text = stringResource(R.string.shields_no_lists_message),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.focusRequester(firstRow).focusable(),
            )
        }
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
