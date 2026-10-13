package com.immensive.screencontroller.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TestControllerScreen(
    state: TestControllerUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onMonitorIdChanged: (String) -> Unit,
    onSendAction: (TestAction) -> Unit,
    onCancelCurrent: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Test protocole écran",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP ou host") },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.port,
            onValueChange = onPortChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") },
            singleLine = true,
        )

        OutlinedTextField(
            value = state.monitorId,
            onValueChange = onMonitorIdChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monitor ID (0..255)") },
            singleLine = true,
        )

        state.inputError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TestAction.entries.forEach { action ->
                Button(
                    enabled = !state.isSending,
                    onClick = { onSendAction(action) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(action.label)
                }
            }
        }

        if (state.isSending) {
            Button(onClick = onCancelCurrent) {
                Text("Annuler l'action en cours")
            }
            CircularProgressIndicator()
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Dernière action: ${state.lastAction ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Résultat: ${state.lastResult}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
