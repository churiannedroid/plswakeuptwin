package com.raisetowake.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RaiseToWakeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RaiseToWakeScreen()
                }
            }
        }
    }
}

@Composable
private fun RaiseToWakeTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF38BDF8), secondary = Color(0xFF818CF8))
    } else {
        lightColorScheme(primary = Color(0xFF0284C7), secondary = Color(0xFF4F46E5))
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaiseToWakeScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE) }
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val hasAccelerometer = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    var serviceEnabled by remember { mutableStateOf(isServiceActuallyRunning(context)) }
    var sensitivity by remember { mutableStateOf(prefs.getFloat(Prefs.SENSITIVITY, Prefs.DEFAULT_SENSITIVITY)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryOptimizationIgnored by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // Re-sync every time the screen resumes (e.g. coming back from Settings,
    // or after the OS killed and restarted the service in the background).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isServiceActuallyRunning(context)
                notificationsGranted = hasNotificationPermission(context)
                batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Raise to Wake") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ServiceStatusCard(
                enabled = serviceEnabled,
                hasAccelerometer = hasAccelerometer,
                onToggle = { checked ->
                    serviceEnabled = checked
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        ContextCompat.startForegroundService(context, WakeService.startIntent(context))
                    } else {
                        context.startService(WakeService.stopIntent(context))
                    }
                }
            )

            SensitivityCard(
                sensitivity = sensitivity,
                onSensitivityChange = { value ->
                    sensitivity = value
                    prefs.edit().putFloat(Prefs.SENSITIVITY, value).apply()
                }
            )

            PermissionsCard(
                notificationsGranted = notificationsGranted,
                onRequestNotifications = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                onRequestBatteryOptimization = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { batteryOptimizationLauncher.launch(intent) }
                        .onFailure {
                            batteryOptimizationLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                },
                onOpenAppSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            InfoFooter()
        }
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, hasAccelerometer: Boolean, onToggle: (Boolean) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Raise to Wake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        !hasAccelerometer -> "No accelerometer detected on this device"
                        enabled -> "Active — lift your phone to wake the screen"
                        else -> "Off — the sensor listener is stopped"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = hasAccelerometer)
        }
    }
}

@Composable
private fun SensitivityCard(sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sensitivity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Lower sensitivity needs a bigger, faster lift and helps avoid accidental wakes in your pocket or bag.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0f..1f)
            Text(text = sensitivityLabel(sensitivity), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun sensitivityLabel(value: Float): String = when {
    value < 0.33f -> "Low sensitivity — requires a deliberate lift"
    value < 0.66f -> "Medium sensitivity — balanced (recommended)"
    else -> "High sensitivity — wakes on small movements"
}

@Composable
private fun PermissionsCard(
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    batteryOptimizationIgnored: Boolean,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    label = "Notifications",
                    description = "Shows the persistent status notification",
                    granted = notificationsGranted,
                    actionLabel = "Grant",
                    onAction = onRequestNotifications
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            PermissionRow(
                label = "Battery optimization",
                description = "Prevents Android from killing the background listener",
                granted = batteryOptimizationIgnored,
                actionLabel = "Disable",
                onAction = onRequestBatteryOptimization
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                "Some phone makers (Xiaomi, Oppo, Vivo, Huawei, Samsung, etc.) layer their own battery or auto-start manager on top of Android's. If the service keeps getting stopped, open your app settings below and look for \"Auto-start\", \"Protected apps\", or a battery setting, and allow this app to run in the background.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenAppSettings) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) Color(0xFF16A34A) else Color(0xFFD97706)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!granted) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun InfoFooter() {
    Text(
        "Raise to Wake keeps the accelerometer listening while the screen is off. This uses more battery than a phone's built-in raise-to-wake feature, since it runs from an app rather than a dedicated low-power sensor chip.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
    )
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Suppress("DEPRECATION")
private fun isServiceActuallyRunning(context: Context): Boolean {
    // getRunningServices() is deprecated for inspecting *other* apps'
    // services (for privacy reasons), but still reliably reports the
    // calling app's own services, which is all we use it for here.
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == WakeService::class.java.name
    }
}
