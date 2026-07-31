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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.billiardtracker.data.remote.dto.VersionDto
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.ui.nav.BilliardNavHost
import com.example.billiardtracker.ui.screens.auth.AuthViewModel
import com.example.billiardtracker.ui.screens.auth.PhoneAuthScreen
import com.example.billiardtracker.ui.theme.BilliardTrackerTheme
import com.example.billiardtracker.util.ApkInstaller
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BilliardApp).container
        val vm: AuthViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return AuthViewModel(container.authRepository) as T
                }
            },
        )[AuthViewModel::class.java]

        setContent {
            BilliardTrackerTheme {
                val authed by container.authRepository.isAuthed
                    .collectAsStateWithLifecycle(initialValue = false)

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
                    if (authed) {
                        BilliardNavHost(container = container)
                    } else {
                        PhoneAuthScreen(
                            viewModel = vm,
                            onLoggedIn = { /* handled by isAuthed flow */ },
                        )
                    }
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
