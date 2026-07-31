package com.example.billiardtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.billiardtracker.data.remote.dto.VersionDto
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.ui.nav.BilliardNavHost
import com.example.billiardtracker.ui.theme.BilliardTrackerTheme
import com.example.billiardtracker.util.ApkInstaller
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BilliardApp).container

        setContent {
            BilliardTrackerTheme {
                var pending by remember { mutableStateOf<VersionDto?>(null) }

                LaunchedEffect(Unit) {
                    if (container.updatePrefs.getAutoCheck()) {
                        container.updaterRepository.fetchLatest().onSuccess { v ->
                            val skip = container.updatePrefs.getSkipVersionCode()
                            if (v.versionCode > BuildConfig.VERSION_CODE && v.versionCode > skip) {
                                pending = v
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BilliardNavHost(container = container)
                }

                val p = pending
                if (p != null) {
                    UpdatePromptDialog(
                        latest = p,
                        onUpdate = {
                            ApkInstaller.downloadAndInstall(this@MainActivity, p.apkUrl, p.versionName)
                            pending = null
                        },
                        onDismiss = { pending = null },
                        onSkip = {
                            lifecycleScope.launch { container.updatePrefs.setSkipVersionCode(p.versionCode) }
                            pending = null
                        },
                    )
                }
            }
        }
    }
}
