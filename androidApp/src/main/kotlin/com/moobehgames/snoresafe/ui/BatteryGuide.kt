package com.moobehgames.snoresafe.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.device.PhoneMaker
import com.moobehgames.snoresafe.ui.components.BackTopBar

fun currentPhoneMaker(): PhoneMaker = PhoneMaker.from(Build.MANUFACTURER, Build.BRAND)

private fun PhoneMaker.nameRes() = when (this) {
    PhoneMaker.SAMSUNG -> R.string.maker_samsung
    PhoneMaker.XIAOMI -> R.string.maker_xiaomi
    PhoneMaker.HUAWEI_HONOR -> R.string.maker_huawei_honor
    PhoneMaker.ONEPLUS_OPPO_REALME -> R.string.maker_oneplus_oppo_realme
    PhoneMaker.VIVO -> R.string.maker_vivo
    PhoneMaker.OTHER -> R.string.maker_other
}

private fun PhoneMaker.stepsRes() = when (this) {
    PhoneMaker.SAMSUNG -> R.array.battery_steps_samsung
    PhoneMaker.XIAOMI -> R.array.battery_steps_xiaomi
    PhoneMaker.HUAWEI_HONOR -> R.array.battery_steps_huawei_honor
    PhoneMaker.ONEPLUS_OPPO_REALME -> R.array.battery_steps_oneplus_oppo_realme
    PhoneMaker.VIVO -> R.array.battery_steps_vivo
    PhoneMaker.OTHER -> R.array.battery_steps_other
}

/**
 * Best-effort shortcuts into each maker's own battery / autostart screen. These move between
 * versions, so every launch is tried in turn and anything that fails falls back to the app's
 * own settings page.
 */
private fun PhoneMaker.shortcuts(): List<ComponentName> = when (this) {
    PhoneMaker.SAMSUNG -> listOf(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
    PhoneMaker.XIAOMI -> listOf(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
    PhoneMaker.HUAWEI_HONOR -> listOf(
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
    )
    PhoneMaker.ONEPLUS_OPPO_REALME -> listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
    )
    PhoneMaker.VIVO -> listOf(
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
    )
    PhoneMaker.OTHER -> emptyList()
}

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    Log.i("BatteryGuide", "Not allowed to open ${intent.component}", e)
    false
}

fun openAppSettings(context: Context) {
    context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private fun openMakerSettings(context: Context, maker: PhoneMaker) {
    if (maker.shortcuts().none { context.tryStart(Intent().setComponent(it)) }) openAppSettings(context)
}

private fun openBatteryOptimisation(context: Context) {
    if (!context.tryStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) openAppSettings(context)
}

/** The steps for this phone's maker, plus buttons to jump to the right settings. */
@Composable
fun BatteryGuideSteps(modifier: Modifier = Modifier, maker: PhoneMaker = currentPhoneMaker()) {
    val context = LocalContext.current
    val makerName = stringResource(maker.nameRes())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.battery_guide_for, makerName), style = MaterialTheme.typography.titleSmall)
        stringArrayResource(maker.stepsRes()).forEachIndexed { i, step ->
            Row {
                Text("${i + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium)
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.battery_guide_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (maker != PhoneMaker.OTHER) {
            OutlinedButton(onClick = { openMakerSettings(context, maker) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.battery_guide_open_maker, makerName))
            }
        }
        OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.battery_guide_open_app))
        }
        OutlinedButton(onClick = { openBatteryOptimisation(context) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.battery_guide_open_battery))
        }
    }
}

@Composable
fun BatteryGuideScreen(onBack: () -> Unit, maker: PhoneMaker = currentPhoneMaker()) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.battery_guide_settings_row), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.battery_guide_intro), style = MaterialTheme.typography.bodyLarge)
            Card { BatteryGuideSteps(Modifier.padding(16.dp), maker) }
        }
    }
}
