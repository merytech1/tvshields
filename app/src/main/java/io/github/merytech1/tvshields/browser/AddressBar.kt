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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

/**
 * Top bar with the address field (design.md, mockups "Browser" and "Address bar").
 * Shows the short URL; on focus it switches to the full URL, all selected, ready to type over.
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
    var value by remember { mutableStateOf(TextFieldValue(displayUrl(currentUrl))) }

    LaunchedEffect(currentUrl, focused) {
        if (!focused) value = TextFieldValue(displayUrl(currentUrl))
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
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(onGo = {
                if (onSubmit(value.text)) keyboard?.hide()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        value = TextFieldValue(currentUrl, TextRange(0, currentUrl.length))
                    }
                    focused = it.isFocused
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
