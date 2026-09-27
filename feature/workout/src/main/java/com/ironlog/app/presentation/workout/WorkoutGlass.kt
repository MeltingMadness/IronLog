package com.ironlog.app.presentation.workout

import com.ironlog.feature.workout.R as UxR
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.model.AppearanceStyle
import com.ironlog.app.presentation.theme.AthleticLabel
import com.ironlog.app.presentation.theme.GlassLevel
import com.ironlog.app.presentation.theme.LocalAppearanceStyle
import com.ironlog.app.presentation.theme.liquidGlass
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val GlassInk = Color(0xFF0B0D12)
private val GlassTeal = Color(0xFF2EC4B6)

@Composable
internal fun isLiquidGlass(): Boolean = LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS

@Composable
private fun isDarkGlass(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Status of an exercise in the Liquid Glass exercise rail. */
internal enum class RailStatus { DONE, CURRENT, UPCOMING }

internal data class RailItem(val key: String, val name: String, val status: RailStatus)

/**
 * Workout head in Liquid Glass: plan and exercise position, the elapsed time as a large number,
 * set progress and volume, and "Beenden" as a glass pill. Replaces top bar and timer ring.
 */
@Composable
internal fun GlassWorkoutHeader(
    label: String,
    startTime: LocalDateTime,
    progressText: String,
    finishLabel: String,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(Locale.GERMAN),
                style = AthleticLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ElapsedTimeText(startTime)
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = onFinish)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(finishLabel, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ElapsedTimeText(startTime: LocalDateTime) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startTime) {
        val startMillis = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        while (isActive) {
            elapsed = ((Instant.now().toEpochMilli() - startMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val hours = elapsed / 3600
    val minutes = (elapsed % 3600) / 60
    val seconds = elapsed % 60
    val text = if (hours > 0) {
        String.format(Locale.GERMAN, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.GERMAN, "%02d:%02d", minutes, seconds)
    }
    Text(
        text = text,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        style = TextStyle(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Horizontal exercise rail: done (check), current (bright pill) and upcoming (outlined). */
@Composable
internal fun GlassExerciseRail(
    items: List<RailItem>,
    doneLabel: String,
    currentLabel: String,
    onSelect: (Int) -> Unit
) {
    val dark = isDarkGlass()
    val ink = if (dark) Color.White else GlassInk
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            val shape = RoundedCornerShape(18.dp)
            val stateLabel = when (item.status) {
                RailStatus.DONE -> doneLabel
                RailStatus.CURRENT -> currentLabel
                RailStatus.UPCOMING -> stringResource(UxR.string.workout_glass_rail_upcoming)
            }
            val base = Modifier
                .widthIn(min = 48.dp)
                .heightIn(min = 48.dp)
                .clip(shape)
                .semantics {
                    contentDescription = listOfNotNull(item.name, stateLabel).joinToString(", ")
                    selected = item.status == RailStatus.CURRENT
                }
                .clickable(role = Role.Tab) { onSelect(index) }
            when (item.status) {
                RailStatus.DONE -> Row(
                    modifier = base
                        .background(ink.copy(alpha = 0.10f))
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                        .alpha(0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier.size(22.dp).background(GlassTeal.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    RailText(item.name, ink)
                }
                RailStatus.CURRENT -> Box(
                    modifier = base
                        .background(if (dark) Color.White else GlassInk)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RailText(item.name, if (dark) GlassInk else Color.White, FontWeight.ExtraBold)
                }
                RailStatus.UPCOMING -> Box(
                    modifier = base
                        .border(1.dp, ink.copy(alpha = 0.18f), shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RailText(item.name, ink.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun RailText(text: String, color: Color, weight: FontWeight = FontWeight.Bold) {
    Text(text = text, color = color, fontSize = 13.sp, fontWeight = weight, maxLines = 1)
}

/**
 * Large number with round −/+ buttons for the set cockpit. The number stays a text field, so
 * typing keeps working exactly as before.
 */
@Composable
internal fun GlassStepper(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String?,
    unit: String?,
    keyboardOptions: KeyboardOptions,
    enabled: Boolean,
    decreaseLabel: String,
    increaseLabel: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isDarkGlass()
    val ink = if (dark) Color.White else GlassInk
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(ink.copy(alpha = 0.07f))
            .border(1.dp, ink.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
            .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label.uppercase(Locale.GERMAN),
            style = AthleticLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.Bottom) {
            val numberStyle = TextStyle(
                fontSize = 46.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum",
                color = ink
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = numberStyle,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // Shrink to the number so the unit sits right next to it.
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 28.dp)
                    .semantics { contentDescription = label },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder ?: "–",
                                style = numberStyle.copy(color = ink.copy(alpha = 0.35f))
                            )
                        }
                        inner()
                    }
                }
            )
            if (unit != null) {
                Text(
                    text = stringResource(UxR.string.workout_number_unit_suffix, unit),
                    fontSize = 18.sp,
                    color = ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassRoundButton("−", decreaseLabel, enabled, onDecrease)
            GlassRoundButton("+", increaseLabel, enabled, onIncrease)
        }
    }
}

@Composable
private fun GlassRoundButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (isDarkGlass()) Color.White else GlassInk
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.10f))
            .border(1.dp, ink.copy(alpha = 0.22f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** Segmented intensity bar (RPE or RIR): the selected value is a bright pill. */
@Composable
internal fun GlassIntensityBar(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val dark = isDarkGlass()
    val ink = if (dark) Color.White else GlassInk
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Keep the evenly spread segments when they fit; scroll instead of shrinking targets.
        val optionWidth = maxOf(
            48.dp,
            (maxWidth - 8.dp - 4.dp * (options.size - 1).coerceAtLeast(0)) / options.size.coerceAtLeast(1)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(23.dp))
                .background(Color.Black.copy(alpha = if (dark) 0.18f else 0.05f))
                .border(1.dp, ink.copy(alpha = 0.08f), RoundedCornerShape(23.dp))
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .widthIn(min = optionWidth)
                        .heightIn(min = 48.dp)
                        .then(if (isSelected) Modifier.shadow(6.dp, RoundedCornerShape(20.dp)) else Modifier)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) (if (dark) Color.White else GlassInk) else Color.Transparent)
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) (if (dark) GlassInk else Color.White) else ink
                    )
                }
            }
        }
    }
}

/** Bright primary pill used for "Satz loggen" in Liquid Glass. */
@Composable
internal fun GlassPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isDarkGlass()
    val container = if (dark) Color.White else GlassInk
    val content = if (dark) GlassInk else Color.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (enabled) Modifier.shadow(14.dp, RoundedCornerShape(30.dp), clip = false) else Modifier)
            .clip(RoundedCornerShape(30.dp))
            .background(if (enabled) container else container.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
