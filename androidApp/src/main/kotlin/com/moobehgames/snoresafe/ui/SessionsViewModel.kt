package com.moobehgames.snoresafe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moobehgames.snoresafe.appGraph
import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = app.appGraph.sessions

    val sessions: StateFlow<List<SessionSummary>> = store.changes
        .map { store.all() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.delete(id) }
    }
}
