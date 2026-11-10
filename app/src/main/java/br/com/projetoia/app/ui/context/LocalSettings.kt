package br.com.projetoia.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import br.com.projetoia.app.data.datastore.Settings

val LocalSettings = compositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
