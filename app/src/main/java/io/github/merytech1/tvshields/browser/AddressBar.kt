package io.github.merytech1.tvshields.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

private const val SETTLE_MS = 250L

/**
 * Top bar with the address field (design.md, mockups "Browser" and "Address bar").
 * Shows the short URL; on focus it switches to the full URL, all selected. OK opens the keyboard
 * to type over it.
 */
@Composable
fun AddressBar(
    currentUrl: String,
    progress: Int?,
    focusRequester: FocusRequester,
    onSubmit: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTvShieldsColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // Focus alone only shows the URL; OK starts editing. While read-only there is no text input
    // session, so the system cannot bring the keyboard back over the page on its own.
    var editing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(displayUrl(currentUrl))) }

    LaunchedEffect(currentUrl, focused) {
        if (!focused) value = TextFieldValue(displayUrl(currentUrl))
    }
    LaunchedEffect(editing) {
        if (!editing) return@LaunchedEffect
        keyboard?.show()
        // When the input session starts, the keyboard (Gboard on the reference TV) moves the
        // cursor to the end and resumes composing the last word. Select all again once it has
        // settled, so typing replaces the URL instead of appending to it.
        delay(SETTLE_MS)
        value = value.copy(selection = TextRange(0, value.text.length), composition = null)
    }

    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            readOnly = !editing,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
                // On TV, focus alone must not cover the page; OK opens the keyboard.
                showKeyboardOnFocus = false,
            ),
            keyboardActions = KeyboardActions(onGo = {
                if (onSubmit(value.text)) {
                    keyboard?.hide()
                    editing = false
                }
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        value = TextFieldValue(currentUrl, TextRange(0, currentUrl.length))
                    }
                    if (!it.isFocused) editing = false
                    focused = it.isFocused
                }
                .onPreviewKeyEvent {
                    val ok = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
                    if (!ok || editing) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyUp) editing = true
                    true
                }
                // Focus ring: 3 dp accent, 2 dp gap (design.md §6).
                .border(3.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(13.dp))
                .padding(5.dp),
            decorationBox = { field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceRaised, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.address_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    field()
                }
            },
        )
        // Page load progress: a thin accent line under the field.
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(5, 100) / 100f)
                        .height(3.dp)
                        .background(colors.accent),
                )
            }
        }
    }
}
