package com.moobehgames.snoresafe

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moobehgames.snoresafe.backup.BackupManager
import com.moobehgames.snoresafe.billing.TipJar
import com.moobehgames.snoresafe.core.audio.SessionAudio
import com.moobehgames.snoresafe.core.export.CsvExport
import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.support.SupportMoment
import com.moobehgames.snoresafe.export.PdfReport
import com.moobehgames.snoresafe.export.Sharing
import com.moobehgames.snoresafe.data.freeBytes
import com.moobehgames.snoresafe.recording.RecordingService
import com.moobehgames.snoresafe.recording.RecordingState
import com.moobehgames.snoresafe.recording.RecordingStateHolder
import com.moobehgames.snoresafe.ui.AppViewModel
import com.moobehgames.snoresafe.ui.FaqScreen
import com.moobehgames.snoresafe.ui.HomeScreen
import com.moobehgames.snoresafe.ui.LicencesScreen
import com.moobehgames.snoresafe.ui.OnboardingScreen
import com.moobehgames.snoresafe.ui.PrivacyScreen
import com.moobehgames.snoresafe.ui.RecordingScreen
import com.moobehgames.snoresafe.ui.ResultsScreen
import com.moobehgames.snoresafe.ui.SettingsActions
import com.moobehgames.snoresafe.ui.SettingsScreen
import com.moobehgames.snoresafe.ui.StorageScreen
import com.moobehgames.snoresafe.ui.SupportScreen
import com.moobehgames.snoresafe.ui.TrendsScreen
import com.moobehgames.snoresafe.ui.theme.SnoreSafeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val tipJar by lazy { TipJar(this) { appGraph.settings.update { it.copy(support = it.support.copy(supported = true)) } } }
    private var tipJarStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onDestroy() {
        if (tipJarStarted) tipJar.close()
        super.onDestroy()
    }

    private val tabs = listOf("home", "trends", "settings")

    @Composable
    private fun App() {
        val vm: AppViewModel = viewModel()
        val recording by RecordingStateHolder.state.collectAsStateWithLifecycle()
        val sessions by vm.sessions.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val supportCard by vm.supportCard.collectAsStateWithLifecycle()
        val logs by vm.logs.collectAsStateWithLifecycle()
        // Which tag set the picker is editing: "tonight", or a night's session id.
        var pickTagsFor by remember { mutableStateOf<String?>(null) }
        val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>({ it.toList() }, { it.toMutableStateList() })) {
            mutableStateListOf("home")
        }
        val route = stack.last()
        fun go(r: String) = stack.add(r)
        fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        fun tab(r: String) { stack.clear(); stack.add(r) }

        var permissionDenied by rememberSaveable { mutableStateOf(false) }
        var micGranted by remember { mutableStateOf(hasMic()) }
        val isRecording = recording is RecordingState.Starting || recording is RecordingState.Active || recording is RecordingState.Waiting

        LaunchedEffect(isRecording) {
            if (isRecording) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        LaunchedEffect(recording) {
            val r = recording
            if (r is RecordingState.Finished) {
                tab("home")
                go("night:${r.sessionId}")
                RecordingStateHolder.acknowledge()
                vm.offerSupport(r.supportMoment)
            }
        }

        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        var startAfterGrant by remember { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            micGranted = result[Manifest.permission.RECORD_AUDIO] == true || hasMic()
            permissionDenied = !micGranted
            if (micGranted && startAfterGrant) RecordingService.start(this)
            startAfterGrant = false
        }

        val data = rememberDataActions(vm)

        SnoreSafeTheme(forceNight = isRecording) {
            when {
                !settings.onboardingDone || route == "onboarding" -> OnboardingScreen(
                    permissionsGranted = micGranted,
                    onRequestPermissions = { permissionLauncher.launch(permissions) },
                    onDone = {
                        vm.updateSettings { it.copy(onboardingDone = true) }
                        if (route == "onboarding") back()
                    },
                )
                isRecording -> RecordingScreen(
                    state = recording,
                    onPause = { RecordingService.send(this, RecordingService.ACTION_PAUSE) },
                    onResume = { RecordingService.send(this, RecordingService.ACTION_RESUME) },
                    onStop = { RecordingService.send(this, RecordingService.ACTION_STOP) },
                )
                route in tabs -> Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(route == "home", { tab("home") }, { Icon(Icons.Filled.Bedtime, null) }, label = { Text(stringResource(R.string.nav_tonight)) })
                            NavigationBarItem(route == "trends", { tab("trends") }, { Icon(Icons.Filled.Insights, null) }, label = { Text(stringResource(R.string.nav_trends)) })
                            NavigationBarItem(route == "settings", { tab("settings") }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.nav_settings)) })
                        }
                    },
                ) { padding ->
                    val m = Modifier.padding(padding)
                    if (route != "home") BackHandler { tab("home") }
                    when (route) {
                        "home" -> HomeScreen(
                            sessions = sessions,
                            permissionDenied = permissionDenied,
                            startDelayMinutes = settings.startDelayMinutes,
                            showSupportCard = supportCard,
                            tonightTags = settings.tonightTags,
                            customTags = settings.customTags,
                            onEditTonight = { pickTagsFor = "tonight" },
                            batteryGuideSeen = settings.batteryGuideSeen,
                            activeTrial = settings.activeTrial,
                            onStopTrial = vm::stopTrial,
                            onBatteryGuide = { go("battery") },
                            onStart = {
                                if (hasMic()) RecordingService.start(this) else {
                                    startAfterGrant = true
                                    permissionLauncher.launch(permissions)
                                }
                            },
                            onOpenSession = { go("night:$it") },
                            onSupport = { vm.dismissSupportCard(); go("support") },
                            onDismissSupport = vm::dismissSupportCard,
                            modifier = m,
                        )
                        "trends" -> TrendsScreen(
                            sessions, logs, settings.customTags,
                            activeTrial = settings.activeTrial,
                            finishedTrials = settings.finishedTrials,
                            onStartTrial = vm::startTrial,
                            onStopTrial = vm::stopTrial,
                            onOpenNight = { go("night:$it") }, onShareReport = data.shareTrendReport, modifier = m)
                        else -> SettingsScreen(
                            settings = settings,
                            update = vm::updateSettings,
                            labelCounts = vm.labelCounts.collectAsStateWithLifecycle().value,
                            onResetPersonal = vm::resetPersonalModel,
                            actions = SettingsActions(
                                onStorage = { go("storage") },
                                onBackup = data.backup,
                                onRestore = data.restore,
                                onExportCsv = data.exportCsv,
                                onExportJson = data.exportJson,
                                onSupport = { go("support") },
                                onFaq = { go("faq") },
                                onPrivacy = { go("privacy") },
                                onLicences = { go("licences") },
                                onShowIntro = { go("onboarding") },
                                onBatteryGuide = { go("battery") },
                            ),
                            modifier = m,
                        )
                    }
                }
                else -> {
                    BackHandler { back() }
                    when {
                        route.startsWith("night:") -> {
                            val id = route.removePrefix("night:")
                            val session = sessions.firstOrNull { it.id == id }
                            val audio by remember(id) { vm.audioFor(id) }.collectAsState(initial = SessionAudio(id))
                            if (session == null) {
                                LaunchedEffect(Unit) { if (sessions.isNotEmpty()) back() }
                            } else {
                                ResultsScreen(
                                    session = session,
                                    audio = audio,
                                    log = logs[id] ?: com.moobehgames.snoresafe.core.tags.NightLog(id),
                                    customTags = settings.customTags,
                                    weightUnit = settings.weightUnit,
                                    onSaveLog = vm::saveLog,
                                    onEditTags = { pickTagsFor = id },
                                    player = vm.player,
                                    showTutorial = !settings.resultsTutorialSeen,
                                    clipFile = { vm.clipFile(id, it.fileName) },
                                    onTutorialSeen = { vm.updateSettings { it.copy(resultsTutorialSeen = true) } },
                                    onPin = { clip, pin -> vm.setPinned(id, clip.id, pin) },
                                    onLabel = { clip, label -> vm.setLabel(id, clip, label) },
                                    onDeleteClip = { clip -> vm.player.stop(); vm.deleteClip(id, clip.id) },
                                    onProtect = { vm.setProtected(id, it) },
                                    onShare = { withClips -> data.shareNight(session, if (withClips) audio else null) },
                                    onBack = { vm.player.stop(); back() },
                                    onDelete = { vm.deleteNight(id); back() },
                                    onBatteryGuide = { go("battery") },
                                    onShareReel = { data.shareReel(session, audio) },
                                )
                            }
                        }
                        route == "storage" -> {
                            val used by vm.audioBytes.collectAsStateWithLifecycle()
                            val allAudio by vm.allAudio.collectAsStateWithLifecycle()
                            StorageScreen(
                                settings, used, freeBytes(this), sessions, allAudio, vm::updateSettings,
                                onCleanUp = vm::cleanUpNow, onDeleteAudio = vm::deleteNightAudio, onBack = ::back,
                            )
                        }
                        route == "support" -> {
                            LaunchedEffect(Unit) { tipJarStarted = true; tipJar.connect() }
                            val tipState by tipJar.state.collectAsStateWithLifecycle()
                            SupportScreen(
                                tipState, settings.support.supported,
                                onTip = { tipJar.tip(this, it) },
                                onAlreadySupported = { vm.markSupported(); back() },
                                onBack = ::back,
                            )
                        }
                        route == "battery" -> {
                            LaunchedEffect(Unit) { if (!settings.batteryGuideSeen) vm.updateSettings { it.copy(batteryGuideSeen = true) } }
                            com.moobehgames.snoresafe.ui.BatteryGuideScreen(::back)
                        }
                        route == "faq" -> FaqScreen(::back)
                        route == "privacy" -> PrivacyScreen(::back)
                        route == "licences" -> LicencesScreen(::back)
                        else -> LaunchedEffect(route) { tab("home") }
                    }
                }
            }

            data.Dialogs()

            pickTagsFor?.let { target ->
                val current = if (target == "tonight") settings.tonightTags else logs[target]?.tags.orEmpty()
                com.moobehgames.snoresafe.ui.TagPickerDialog(
                    selected = current,
                    custom = settings.customTags,
                    onAddCustom = vm::addCustomTag,
                    onDeleteCustom = vm::deleteCustomTag,
                    onDismiss = { pickTagsFor = null },
                    onDone = { picked ->
                        if (target == "tonight") vm.setTonightTags(picked)
                        else vm.saveLog((logs[target] ?: com.moobehgames.snoresafe.core.tags.NightLog(target)).copy(tags = picked))
                        pickTagsFor = null
                    },
                )
            }

            (recording as? RecordingState.Failed)?.let { failed ->
                AlertDialog(
                    onDismissRequest = { RecordingStateHolder.acknowledge() },
                    text = {
                        Text(
                            when (failed.reason) {
                                RecordingState.Reason.MICROPHONE, RecordingState.Reason.PERMISSION -> stringResource(R.string.error_mic)
                                RecordingState.Reason.MODEL -> stringResource(R.string.error_model)
                                RecordingState.Reason.OTHER -> stringResource(R.string.error_generic, failed.detail ?: "")
                            },
                        )
                    },
                    confirmButton = { TextButton(onClick = { RecordingStateHolder.acknowledge() }) { Text(stringResource(R.string.dismiss)) } },
                )
            }
        }
    }

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun sharedDir() = File(cacheDir, "shared").apply { deleteRecursively(); mkdirs() }

    /** Backup, restore, export and share flows, with their file pickers and dialogs. */
    private inner class DataActions(
        val vm: AppViewModel,
        val backup: () -> Unit,
        val restore: () -> Unit,
        val exportCsv: () -> Unit,
        val exportJson: () -> Unit,
        val shareTrendReport: () -> Unit,
        val shareReel: (com.moobehgames.snoresafe.core.session.SessionSummary, SessionAudio) -> Unit,
        val shareNight: (com.moobehgames.snoresafe.core.session.SessionSummary, SessionAudio?) -> Unit,
        val Dialogs: @Composable () -> Unit,
    )

    @Composable
    private fun rememberDataActions(vm: AppViewModel): DataActions {
        val scope = rememberCoroutineScope()
        var askNewPassword by remember { mutableStateOf(false) }
        var askRestorePasswordFor by remember { mutableStateOf<Uri?>(null) }
        var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
        var busy by remember { mutableStateOf(false) }
        val manager = remember { BackupManager(this, appGraph) }

        fun background(work: suspend () -> String) {
            busy = true
            scope.launch {
                val message = runCatching { withContext(Dispatchers.IO) { work() } }.getOrElse { it.message ?: it.javaClass.simpleName }
                busy = false
                toast(message)
            }
        }

        fun exporting(write: () -> Unit): String = try {
            write()
            lifecycleScope.launch { vm.offerSupport(SupportMoment.EXPORT_DONE) }
            getString(R.string.export_done)
        } catch (e: Exception) {
            getString(R.string.export_failed, e.message ?: e.javaClass.simpleName)
        }

        val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            val password = pendingPassword
            pendingPassword = null
            if (uri == null || password == null) return@rememberLauncherForActivityResult
            background {
                try {
                    manager.create(uri, password)
                    lifecycleScope.launch { vm.offerSupport(SupportMoment.EXPORT_DONE) }
                    getString(R.string.backup_done)
                } catch (e: Exception) {
                    getString(R.string.backup_failed, e.message ?: e.javaClass.simpleName)
                } finally {
                    password.fill('\u0000')
                }
            }
        }
        val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) askRestorePasswordFor = uri
        }
        val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            background {
                exporting {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    val allLogs = appGraph.logs.all()
                    val custom = appGraph.settings.current.customTags
                    val csv = CsvExport.sessions(appGraph.sessions.all(), { fmt.format(Date(it)) }) { s ->
                        val log = allLogs[s.id] ?: return@sessions CsvExport.Extras()
                        CsvExport.Extras(
                            tags = log.tags.mapNotNull { com.moobehgames.snoresafe.ui.tagName(this@MainActivity, it, custom) }.joinToString("; "),
                            restRating = log.restRating,
                            weightKg = log.weightKg,
                            note = log.note,
                        )
                    }
                    contentResolver.openOutputStream(uri, "w")!!.use { it.write(csv.toByteArray()) }
                }
            }
        }
        val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            background {
                exporting {
                    val json = SessionCodec.encodeSummaries(appGraph.sessions.all())
                    contentResolver.openOutputStream(uri, "w")!!.use { it.write(json.toByteArray()) }
                }
            }
        }

        return DataActions(
            vm = vm,
            backup = { askNewPassword = true },
            restore = { openBackup.launch(arrayOf("*/*")) },
            exportCsv = { createCsv.launch("SnoreSafe-nights-${stamp()}.csv") },
            exportJson = { createJson.launch("SnoreSafe-nights-${stamp()}.json") },
            shareTrendReport = {
                scope.launch {
                    val file = withContext(Dispatchers.IO) { PdfReport.trend(this@MainActivity, appGraph.sessions.all(), File(sharedDir(), "SnoreSafe-trends-${stamp()}.pdf")) }
                    Sharing.share(this@MainActivity, listOf(file), "application/pdf", getString(R.string.share_trend_report))
                }
            },
            shareReel = { session, audio ->
                scope.launch {
                    val parts = com.moobehgames.snoresafe.core.insights.HighlightReel.plan(audio.clips)
                    if (parts.isEmpty()) {
                        toast(getString(R.string.reel_none))
                        return@launch
                    }
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            com.moobehgames.snoresafe.audio.ReelBuilder.build(
                                parts,
                                { p -> appGraph.audio.fileFor(session.id, p.clip) },
                                File(sharedDir(), "SnoreSafe-highlights-${stamp()}.m4a"),
                            )
                        }.getOrNull()
                    }
                    if (file == null) toast(getString(R.string.reel_failed))
                    else Sharing.share(this@MainActivity, listOf(file), "audio/mp4", getString(R.string.share_reel))
                }
            },
            shareNight = { session, audio ->
                scope.launch {
                    val files = withContext(Dispatchers.IO) {
                        val dir = sharedDir()
                        val log = appGraph.logs.get(session.id)
                        val custom = appGraph.settings.current.customTags
                        val tagNames = log.tags.mapNotNull { com.moobehgames.snoresafe.ui.tagName(this@MainActivity, it, custom) }
                        val pdf = PdfReport.night(this@MainActivity, session, File(dir, "SnoreSafe-night-${stamp()}.pdf"), tagNames, log.note)
                        // Copies only the pinned clips into the share folder; the rest of the audio stays private.
                        val clips = audio?.clips?.filter { it.pinned }.orEmpty().mapNotNull { clip ->
                            val src = appGraph.audio.fileFor(session.id, clip)
                            if (src.isFile) File(dir, clip.fileName).also { it.writeBytes(com.moobehgames.snoresafe.data.Secure.decrypt(src.readBytes())) } else null
                        }
                        listOf(pdf) + clips
                    }
                    Sharing.share(this@MainActivity, files, if (files.size == 1) "application/pdf" else "*/*", getString(R.string.share_report))
                }
            },
            Dialogs = {
                if (askNewPassword) {
                    PasswordDialog(
                        title = stringResource(R.string.backup_password_title),
                        body = stringResource(R.string.backup_password_body),
                        confirmTwice = true,
                        onDismiss = { askNewPassword = false },
                        onConfirm = { pw ->
                            askNewPassword = false
                            pendingPassword = pw
                            createBackup.launch("SnoreSafe-backup-${stamp()}.snoresafe")
                        },
                    )
                }
                askRestorePasswordFor?.let { uri ->
                    PasswordDialog(
                        title = stringResource(R.string.restore_password_title),
                        body = null,
                        confirmTwice = false,
                        onDismiss = { askRestorePasswordFor = null },
                        onConfirm = { pw ->
                            askRestorePasswordFor = null
                            background {
                                try {
                                    manager.restore(uri, pw).let { n -> resources.getQuantityString(R.plurals.restore_done, n, n) }
                                } catch (e: Exception) {
                                    getString(R.string.restore_failed)
                                }
                            }
                        },
                    )
                }
                if (busy) {
                    AlertDialog(onDismissRequest = {}, text = { Text(stringResource(R.string.working)) }, confirmButton = {})
                }
            },
        )
    }

    @Composable
    private fun PasswordDialog(title: String, body: String?, confirmTwice: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
        var first by remember { mutableStateOf("") }
        var second by remember { mutableStateOf("") }
        val tooShort = confirmTwice && first.length < 8
        val mismatch = confirmTwice && first != second
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    if (body != null) Text(body)
                    OutlinedTextField(
                        first, { first = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (confirmTwice) {
                        OutlinedTextField(
                            second, { second = it }, label = { Text(stringResource(R.string.password_confirm)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        if (first.isNotEmpty() && tooShort) Text(stringResource(R.string.password_too_short))
                        else if (second.isNotEmpty() && mismatch) Text(stringResource(R.string.password_mismatch))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(first.toCharArray()) }, enabled = first.isNotEmpty() && !tooShort && !mismatch) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
