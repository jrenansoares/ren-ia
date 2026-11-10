package br.com.projetoia.app.ui.pages.projeto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import br.com.projetoia.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.projetoia.app.Screen
import br.com.projetoia.app.ui.components.ui.CardGroup
import br.com.projetoia.app.ui.context.LocalNavController
import kotlin.uuid.Uuid

@Composable
fun ProjetoHomePage() {
    val nav = LocalNavController.current
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.projeto_brand), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.projeto_heading), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.projeto_intro), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                CardGroup(modifier = Modifier.fillMaxWidth()) {
                    item(onClick = { nav.navigate(Screen.Chat(Uuid.random().toString())) }, headlineContent = { Text(stringResource(R.string.projeto_new_chat)) }, supportingContent = { Text(stringResource(R.string.projeto_new_chat_desc)) })
                    item(onClick = { nav.navigate(Screen.Chat(Uuid.random().toString(), programmingSetup = true)) }, headlineContent = { Text(stringResource(R.string.programming_title)) }, supportingContent = { Text(stringResource(R.string.programming_intro)) })
                    item(onClick = { nav.navigate(Screen.History) }, headlineContent = { Text(stringResource(R.string.projeto_continue)) }, supportingContent = { Text(stringResource(R.string.projeto_continue_desc)) })
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.projeto_workspace)) }) {
                    item(onClick = { nav.navigate(Screen.SharedLinux) }, headlineContent = { Text(stringResource(R.string.linux_page_title)) }, supportingContent = { Text(stringResource(R.string.linux_page_intro)) })
                    item(onClick = { nav.navigate(Screen.Workspaces) }, headlineContent = { Text(stringResource(R.string.projeto_projects)) }, supportingContent = { Text(stringResource(R.string.projeto_projects_desc)) })
                    item(onClick = { nav.navigate(Screen.Repositories) }, headlineContent = { Text(stringResource(R.string.projeto_repositories_title)) }, supportingContent = { Text(stringResource(R.string.projeto_repositories_intro)) })
                    item(onClick = { nav.navigate(Screen.Connections) }, headlineContent = { Text(stringResource(R.string.projeto_connections)) }, supportingContent = { Text(stringResource(R.string.projeto_connections_desc)) })
                    item(onClick = { nav.navigate(Screen.FreeRouter) }, headlineContent = { Text(stringResource(R.string.projeto_router)) }, supportingContent = { Text(stringResource(R.string.projeto_router_desc)) })
                    item(onClick = { nav.navigate(Screen.SettingModels) }, headlineContent = { Text(stringResource(R.string.projeto_models)) }, supportingContent = { Text(stringResource(R.string.projeto_models_desc)) })
                    item(onClick = { nav.navigate(Screen.SettingSpeech) }, headlineContent = { Text(stringResource(R.string.projeto_voice)) }, supportingContent = { Text(stringResource(R.string.projeto_voice_desc)) })
                }
            }
            item {
                CardGroup {
                    item(onClick = { nav.navigate(Screen.Setting) }, headlineContent = { Text(stringResource(R.string.projeto_settings)) }, supportingContent = { Text(stringResource(R.string.projeto_settings_desc)) })
                    item(onClick = { nav.navigate(Screen.SettingAbout) }, headlineContent = { Text(stringResource(R.string.projeto_about)) }, supportingContent = { Text(stringResource(R.string.projeto_about_desc)) })
                }
            }
        }
    }
}
