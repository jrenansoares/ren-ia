package br.com.projetoia.app.ui.pages.share.handler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.datastore.SettingsStore
import kotlin.uuid.Uuid

class ShareHandlerVM(
    text: String,
    private val settingsStore: SettingsStore
) : ViewModel() {
    val shareText = checkNotNull(text)
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings.dummy())

    suspend fun updateAssistant(assistantId: Uuid) {
        settingsStore.selectAssistant(assistantId)
    }
}
