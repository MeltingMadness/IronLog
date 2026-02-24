package com.ironlog.app.presentation.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dims = ironLogDimens

    var incidentSummary by remember { mutableStateOf("") }
    var incidentDetails by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updateReminderConfig(state.preferences.reminderConfig.copy(enabled = true))
        } else {
            viewModel.onNotificationPermissionDenied()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> {
                        val message = if (event.args.isEmpty()) {
                            context.getString(event.textRes)
                        } else {
                            context.getString(event.textRes, *event.args.toTypedArray())
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                is SettingsEvent.ShareIncident -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, event.report.uri)
                        putExtra(Intent.EXTRA_SUBJECT, "IronLog Incident ${event.report.id}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.settings_incident_share)
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.nav_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (state.isBusy) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(dims.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dims.spacingSm)
        ) {
            item {
                PreferenceCard(title = stringResource(id = R.string.settings_section_appearance)) {
                    Text(
                        text = stringResource(id = R.string.settings_theme_mode_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        FilterChip(
                            selected = state.preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { viewModel.updateThemeMode(ThemeMode.SYSTEM) },
                            label = { Text(stringResource(id = R.string.settings_theme_system)) }
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { viewModel.updateThemeMode(ThemeMode.LIGHT) },
                            label = { Text(stringResource(id = R.string.settings_theme_light)) }
                        )
                        FilterChip(
                            selected = state.preferences.themeMode == ThemeMode.DARK,
                            onClick = { viewModel.updateThemeMode(ThemeMode.DARK) },
                            label = { Text(stringResource(id = R.string.settings_theme_dark)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(dims.spacingXs))

                    Text(
                        text = stringResource(id = R.string.settings_theme_scheme_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        FilterChip(
                            selected = state.preferences.themeScheme == ThemeScheme.AMBER,
                            onClick = { viewModel.updateThemeScheme(ThemeScheme.AMBER) },
                            label = { Text(stringResource(id = R.string.settings_scheme_amber)) }
                        )
                        FilterChip(
                            selected = state.preferences.themeScheme == ThemeScheme.DEEP_CYAN,
                            onClick = { viewModel.updateThemeScheme(ThemeScheme.DEEP_CYAN) },
                            label = { Text(stringResource(id = R.string.settings_scheme_deep_cyan)) }
                        )
                        FilterChip(
                            selected = state.preferences.themeScheme == ThemeScheme.NEON_RED,
                            onClick = { viewModel.updateThemeScheme(ThemeScheme.NEON_RED) },
                            label = { Text(stringResource(id = R.string.settings_scheme_neon_red)) }
                        )
                    }

                    ToggleRow(
                        title = stringResource(id = R.string.settings_dynamic_color_title),
                        subtitle = stringResource(id = R.string.settings_dynamic_color_subtitle),
                        checked = state.preferences.useDynamicColor,
                        onCheckedChange = viewModel::updateUseDynamicColor
                    )
                    ToggleRow(
                        title = stringResource(id = R.string.settings_reduced_motion_title),
                        subtitle = stringResource(id = R.string.settings_reduced_motion_subtitle),
                        checked = state.preferences.reducedMotion,
                        onCheckedChange = viewModel::updateReducedMotion
                    )
                }
            }

            item {
                PreferenceCard(title = stringResource(id = R.string.settings_section_preferences)) {
                    ToggleRow(
                        title = stringResource(id = R.string.settings_unit_system_title),
                        subtitle = stringResource(id = R.string.settings_unit_system_subtitle),
                        checked = state.preferences.unitSystem == UnitSystem.IMPERIAL,
                        onCheckedChange = { enabled ->
                            viewModel.updateUnitSystem(
                                if (enabled) UnitSystem.IMPERIAL else UnitSystem.METRIC
                            )
                        },
                        checkedLabel = stringResource(id = R.string.settings_unit_imperial),
                        uncheckedLabel = stringResource(id = R.string.settings_unit_metric)
                    )

                    Spacer(modifier = Modifier.height(dims.spacingXs))

                    Text(
                        text = stringResource(id = R.string.settings_week_start_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        FilterChip(
                            selected = state.preferences.weekStart == WeekStart.MONDAY,
                            onClick = { viewModel.updateWeekStart(WeekStart.MONDAY) },
                            label = { Text(stringResource(id = R.string.settings_week_start_monday)) }
                        )
                        FilterChip(
                            selected = state.preferences.weekStart == WeekStart.SUNDAY,
                            onClick = { viewModel.updateWeekStart(WeekStart.SUNDAY) },
                            label = { Text(stringResource(id = R.string.settings_week_start_sunday)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(dims.spacingXs))

                    ToggleRow(
                        title = stringResource(id = R.string.settings_default_warmup_title),
                        subtitle = stringResource(id = R.string.settings_default_warmup_subtitle),
                        checked = state.preferences.defaultWarmupFlag,
                        onCheckedChange = viewModel::updateDefaultWarmupFlag
                    )
                    ToggleRow(
                        title = stringResource(id = R.string.settings_keep_screen_on_title),
                        subtitle = stringResource(id = R.string.settings_keep_screen_on_subtitle),
                        checked = state.preferences.timerKeepScreenOn,
                        onCheckedChange = viewModel::updateTimerKeepScreenOn
                    )
                    ToggleRow(
                        title = stringResource(id = R.string.settings_beta_diagnostics_title),
                        subtitle = stringResource(id = R.string.settings_beta_diagnostics_subtitle),
                        checked = state.preferences.betaDiagnosticsOptIn,
                        onCheckedChange = viewModel::updateBetaDiagnosticsOptIn
                    )

                    Spacer(modifier = Modifier.height(dims.spacingXs))

                    Text(
                        text = stringResource(id = R.string.settings_intensity_system_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        FilterChip(
                            selected = state.preferences.intensitySystem == IntensitySystem.OFF,
                            onClick = { viewModel.updateIntensitySystem(IntensitySystem.OFF) },
                            label = { Text(stringResource(id = R.string.settings_intensity_off)) }
                        )
                        FilterChip(
                            selected = state.preferences.intensitySystem == IntensitySystem.RPE,
                            onClick = { viewModel.updateIntensitySystem(IntensitySystem.RPE) },
                            label = { Text("RPE") }
                        )
                        FilterChip(
                            selected = state.preferences.intensitySystem == IntensitySystem.RIR,
                            onClick = { viewModel.updateIntensitySystem(IntensitySystem.RIR) },
                            label = { Text("RIR") }
                        )
                    }
                }
            }

            item {
                PreferenceCard(title = stringResource(id = R.string.settings_section_reminder)) {
                    val reminder = state.preferences.reminderConfig

                    ToggleRow(
                        title = stringResource(id = R.string.settings_reminder_enabled_title),
                        subtitle = stringResource(id = R.string.settings_reminder_enabled_subtitle),
                        checked = reminder.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val needsPermission =
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED

                                if (needsPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.updateReminderConfig(reminder.copy(enabled = true))
                                }
                            } else {
                                viewModel.updateReminderConfig(reminder.copy(enabled = false))
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_reminder_time_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        viewModel.updateReminderConfig(
                                            reminder.copy(hour = hour, minute = minute)
                                        )
                                    },
                                    reminder.hour,
                                    reminder.minute,
                                    true
                                ).show()
                            }
                        ) {
                            Text(DateFormatting.formatClock(reminder.hour, reminder.minute))
                        }
                    }

                    Text(
                        text = stringResource(id = R.string.settings_reminder_days_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in reminder.daysOfWeek,
                                onClick = {
                                    val updated = reminder.daysOfWeek.toMutableSet()
                                    if (!updated.add(day)) {
                                        updated.remove(day)
                                    }
                                    viewModel.updateReminderConfig(reminder.copy(daysOfWeek = updated))
                                },
                                label = { Text(DateFormatting.dayShort(day)) }
                            )
                        }
                    }
                }
            }

            item {
                PreferenceCard(title = stringResource(id = R.string.settings_section_data)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.spacingXs)) {
                        Button(
                            onClick = {
                                val fileName = "ironlog-backup-${LocalDate.now()}.json"
                                exportLauncher.launch(fileName)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.settings_backup_export))
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.settings_backup_import))
                        }
                    }
                    TextButton(onClick = viewModel::showResetDialog) {
                        Text(stringResource(id = R.string.settings_data_reset))
                    }
                    Text(
                        text = stringResource(
                            id = R.string.settings_build_info,
                            state.buildInfo.versionName,
                            state.buildInfo.versionCode
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                PreferenceCard(title = stringResource(id = R.string.settings_section_incident)) {
                    OutlinedTextField(
                        value = incidentSummary,
                        onValueChange = { incidentSummary = it },
                        label = { Text(stringResource(id = R.string.settings_incident_summary_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(dims.spacingXs))
                    OutlinedTextField(
                        value = incidentDetails,
                        onValueChange = { incidentDetails = it },
                        label = { Text(stringResource(id = R.string.settings_incident_details_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(dims.spacingXs))
                    Button(
                        onClick = {
                            viewModel.createIncidentReport(
                                summary = incidentSummary,
                                details = incidentDetails,
                                currentScreen = "settings"
                            )
                            incidentSummary = ""
                            incidentDetails = ""
                        },
                        enabled = incidentSummary.isNotBlank() || incidentDetails.isNotBlank()
                    ) {
                        Text(stringResource(id = R.string.settings_incident_create))
                    }
                }
            }
        }

        if (state.showResetDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissResetDialog,
                title = { Text(stringResource(id = R.string.settings_reset_dialog_title)) },
                text = { Text(stringResource(id = R.string.settings_reset_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = viewModel::resetUserData) {
                        Text(stringResource(id = R.string.settings_reset_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissResetDialog) {
                        Text(stringResource(id = R.string.settings_reset_dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun PreferenceCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val dims = ironLogDimens
    val surfaces = ironLogSurfaceRoles

    Card(
        colors = CardDefaults.cardColors(
            containerColor = surfaces.muted
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedLabel: String? = null,
    uncheckedLabel: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (checkedLabel != null && uncheckedLabel != null) {
                Text(
                    text = if (checked) checkedLabel else uncheckedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
