package io.github.merytech1.tvshields.browser

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

/** Top bar (design.md §5 and §9): back, reload, home and the address bar. */
@Composable
fun TopBar(
    state: BrowserUiState,
    addressFocus: FocusRequester,
    onSubmit: (String) -> Boolean,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    val onPage = state.currentUrl.isNotEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        BarIconButton(R.drawable.ic_arrow_back, R.string.action_back, enabled = state.canGoBack, onClick = onBack)
        BarIconButton(R.drawable.ic_refresh, R.string.action_reload, enabled = onPage, onClick = onReload)
        BarIconButton(R.drawable.ic_home, R.string.action_home, enabled = onPage, onClick = onHome)
        Spacer(Modifier.width(8.dp))
        AddressBar(
            currentUrl = state.currentUrl,
            progress = state.progress,
            focusRequester = addressFocus,
            onSubmit = onSubmit,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Icon-only bar button: 48 dp target, focus ring and scale from design.md §6. Disabled is not focusable. */
@Composable
private fun BarIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    val shape = RoundedCornerShape(13.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            // Focus is shown by the ring below, not by the default indication.
            .clickable(interactionSource = null, indication = null, enabled = enabled, onClick = onClick)
            .border(3.dp, if (focused) colors.accent else Color.Transparent, shape)
            .padding(5.dp)
            .background(if (focused) colors.surfaceRaised else Color.Transparent, RoundedCornerShape(8.dp))
            .size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
            tint = if (enabled) colors.textPrimary else colors.textTertiary,
            modifier = Modifier.size(24.dp),
        )
    }
}
