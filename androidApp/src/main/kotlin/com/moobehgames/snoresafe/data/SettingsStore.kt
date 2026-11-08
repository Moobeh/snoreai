package com.moobehgames.snoresafe.data

import com.moobehgames.snoresafe.core.session.SessionCodec
import com.moobehgames.snoresafe.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** App settings in one small JSON file; read once, then served from memory. */
class SettingsStore(private val file: File) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        file.writeTextAtomically(SessionCodec.encodeSettings(next))
        _settings.value = next
    }

    @Synchronized
    fun replace(settings: AppSettings) = update { settings }

    private fun load(): AppSettings = if (file.isFile) SessionCodec.decodeSettings(file.readText()) else AppSettings()
}
