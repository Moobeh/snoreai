package com.moobehgames.snoresafe

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moobehgames.snoresafe.recording.RecordingService
import com.moobehgames.snoresafe.recording.RecordingState
import com.moobehgames.snoresafe.recording.RecordingStateHolder
import com.moobehgames.snoresafe.ui.HomeScreen
import com.moobehgames.snoresafe.ui.RecordingScreen
import com.moobehgames.snoresafe.ui.ResultsScreen
import com.moobehgames.snoresafe.ui.SessionsViewModel
import com.moobehgames.snoresafe.ui.theme.SnoreSafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    @Composable
    private fun App() {
        val recording by RecordingStateHolder.state.collectAsStateWithLifecycle()
        val vm: SessionsViewModel = viewModel()
        val sessions by vm.sessions.collectAsStateWithLifecycle()
        var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }
        var permissionDenied by rememberSaveable { mutableStateOf(false) }

        val isRecording = recording is RecordingState.Starting || recording is RecordingState.Active
        LaunchedEffect(isRecording) {
            // Keep the screen available for the night clock while recording, otherwise normal.
            if (isRecording) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        LaunchedEffect(recording) {
            val r = recording
            if (r is RecordingState.Finished) {
                openSessionId = r.sessionId
                RecordingStateHolder.acknowledge()
            }
        }

        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) {
                permissionDenied = false
                RecordingService.start(this)
            } else {
                permissionDenied = true
            }
        }

        SnoreSafeTheme(forceNight = isRecording) {
            val open = openSessionId?.let { id -> sessions.firstOrNull { it.id == id } }
            when {
                isRecording -> RecordingScreen(
                    state = recording,
                    onPause = { RecordingService.send(this, RecordingService.ACTION_PAUSE) },
                    onResume = { RecordingService.send(this, RecordingService.ACTION_RESUME) },
                    onStop = { RecordingService.send(this, RecordingService.ACTION_STOP) },
                )
                open != null -> {
                    BackHandler { openSessionId = null }
                    ResultsScreen(
                        session = open,
                        onBack = { openSessionId = null },
                        onDelete = {
                            vm.delete(open.id)
                            openSessionId = null
                        },
                    )
                }
                else -> HomeScreen(
                    sessions = sessions,
                    permissionDenied = permissionDenied,
                    onStart = { launcher.launch(permissions) },
                    onOpenSession = { openSessionId = it },
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
}
