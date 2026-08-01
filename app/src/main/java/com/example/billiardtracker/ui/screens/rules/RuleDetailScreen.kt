package com.example.billiardtracker.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.components.BilliardTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(
    viewModel: RuleDetailViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BilliardTopBar(
                title = { Text(ui.displayName) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                ui.loading && ui.markdown.isBlank() -> CircularProgressIndicator()
                ui.error != null && ui.markdown.isBlank() ->
                    Text(
                        "Ошибка загрузки: ${ui.error}",
                        color = MaterialTheme.colorScheme.error,
                    )
                else -> SimpleMarkdown(ui.markdown)
            }
        }
    }
}

/**
 * Minimal markdown renderer for MVP — handles headings (`#`, `##`, `###`),
 * blockquotes (`>`), bullet lists (`-`, `*`), and blank-line spacing.
 * Inline formatting (bold/italic) is intentionally skipped.
 */
@Composable
private fun SimpleMarkdown(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        md.split("\n").forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("### ") -> Text(
                    line.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall,
                )
                line.startsWith("> ") -> Text(
                    line.removePrefix("> "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    "• ${line.drop(2)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
