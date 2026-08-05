package com.ironlog.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.Radius

@Composable
fun CompactTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suffix: String,
    keyboardOptions: KeyboardOptions,
    placeholderText: String = "-",
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = modifier
            .height(ButtonSize.iconButton)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                shape = shape
            )
            .border(1.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholderText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }

                if (suffix.isNotEmpty()) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardOptions: KeyboardOptions,
    placeholderText: String = "-",
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = modifier
            .height(ButtonSize.iconButton)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                shape = shape
            )
            .border(1.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholderText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }

                if (suffix.isNotEmpty()) {
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    )
}
