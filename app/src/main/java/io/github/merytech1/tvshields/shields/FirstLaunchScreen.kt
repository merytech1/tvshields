package io.github.merytech1.tvshields.shields

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.engine.EngineStatus
import io.github.merytech1.tvshields.ui.components.TextButton
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

/** First launch (rules C1): downloading the lists, or no connection. Mockups in design.md §14. */
@Composable
fun FirstLaunchScreen(status: EngineStatus, onRetry: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalTvShieldsColors.current
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            is EngineStatus.Downloading -> Downloading(status)
            else -> NoConnection(onRetry, onContinue)
        }
    }
}

@Composable
private fun Downloading(status: EngineStatus.Downloading) {
    val colors = LocalTvShieldsColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(64.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.first_launch_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = stringResource(R.string.first_launch_message),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        val fraction = if (status.total > 0) status.done.toFloat() / status.total else 0f
        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .width(360.dp)
                .height(8.dp)
                .background(colors.surfaceRaised, RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(colors.accent, RoundedCornerShape(4.dp)),
            )
        }
        // Before the first catalog entry finishes, the total is not known yet.
        Text(
            text = if (status.total > 0) stringResource(R.string.first_launch_progress, status.done, status.total) else "",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun NoConnection(onRetry: () -> Unit, onContinue: () -> Unit) {
    val colors = LocalTvShieldsColors.current
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocus.requestFocus() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(R.drawable.ic_wifi_off),
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.no_connection_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(R.string.no_connection_message),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).width(560.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 32.dp),
        ) {
            TextButton(
                text = stringResource(R.string.action_try_again),
                onClick = onRetry,
                primary = true,
                modifier = Modifier.focusRequester(retryFocus),
            )
            TextButton(text = stringResource(R.string.action_continue_unprotected), onClick = onContinue)
        }
    }
}
