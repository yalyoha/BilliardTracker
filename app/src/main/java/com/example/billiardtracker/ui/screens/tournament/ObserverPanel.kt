package com.example.billiardtracker.ui.screens.tournament

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ObserverPanel(refereeName: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Судит: $refereeName", style = MaterialTheme.typography.titleSmall)
        Text(
            "Ты в режиме наблюдателя — счёт обновляется автоматически.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
