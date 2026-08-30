package com.example.billiardtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.billiardtracker.data.remote.dto.VersionDto
import com.example.billiardtracker.di.AppContainer
import com.example.billiardtracker.ui.components.UpdatePromptDialog
import com.example.billiardtracker.ui.components.UpdateStage
import com.example.billiardtracker.ui.nav.BilliardNavHost
import com.example.billiardtracker.ui.theme.AppColorScheme
import com.example.billiardtracker.ui.theme.BilliardTrackerTheme
import androidx.compose.runtime.collectAsState
import com.example.billiardtracker.util.ApkInstaller
import com.example.billiardtracker.util.InstallResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BilliardApp).container

        container.devLogger.log(kind = "lifecycle", action = "onCreate",
            payload = mapOf("versionName" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE))
        handleDeepLink(intent, container)

        setContent {
            // v1.24.0: тема из UserPrefs (5 палитр × dark/light). Меняется на лету.
            val schemeKey by container.userPrefs.colorSchemeFlow.collectAsState(initial = "ORIGINAL")
            val darkPref by container.userPrefs.darkThemeFlow.collectAsState(initial = true)
            BilliardTrackerTheme(
                scheme = AppColorScheme.fromKey(schemeKey),
                darkTheme = darkPref,
            ) {
                var pending by remember { mutableStateOf<VersionDto?>(null) }
                var stage by remember { mutableStateOf<UpdateStage>(UpdateStage.Idle) }
                val scope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_PAUSE) {
                            container.devLogger.log(kind = "lifecycle", action = event.name)
                        }
                        if (event == Lifecycle.Event.ON_RESUME) {
                            scope.launch {
                                if (!container.updatePrefs.getAutoCheck()) return@launch
                                container.updaterRepository.fetchLatest().onSuccess { v ->
                                    val skip = container.updatePrefs.getSkipVersionCode()
                                    if (v.versionCode > BuildConfig.VERSION_CODE && v.versionCode > skip) {
                                        pending = v
                                    }
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                        stage = stage,
                        onUpdate = {
                            scope.launch {
                                stage = UpdateStage.Downloading(0f)
                                val result = ApkInstaller.downloadAndInstall(
                                    context = this@MainActivity,
                                    apkUrl = p.apkUrl,
                                    versionName = p.versionName,
                                    onProgress = { stage = UpdateStage.Downloading(it) },
                                )
                                when (result) {
                                    is InstallResult.Success -> {
                                        stage = UpdateStage.Idle
                                        pending = null
                                    }
                                    is InstallResult.NeedInstallPermission -> {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Разреши установку из этого приложения и повтори",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        ApkInstaller.openInstallPermissionSettings(this@MainActivity)
                                        stage = UpdateStage.Idle
                                    }
                                    is InstallResult.Error -> {
                                        stage = UpdateStage.Error(result.message)
                                    }
                                }
                            }
                        },
                        onDismiss = { pending = null; stage = UpdateStage.Idle },
                        onSkip = {
                            lifecycleScope.launch { container.updatePrefs.setSkipVersionCode(p.versionCode) }
                            pending = null
                            stage = UpdateStage.Idle
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val container = (application as BilliardApp).container
        handleDeepLink(intent, container)
    }

    /**
     * Deep link handler for `https://billiardtracker.alekseylosev.ru/live/<token>`.
     *
     * If the user has a JWT, subscribes immediately and makes the token active.
     * If not, stashes the token in prefs so onboarding can consume it after
     * the user registers — otherwise the invite would silently drop.
     */
    private fun handleDeepLink(intent: Intent?, container: AppContainer) {
        val uri = intent?.data ?: return
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "live") return
        val token = segments[1].takeIf { it.isNotBlank() } ?: return
        // Clear the intent so re-composition doesn't retrigger the subscribe.
        intent.data = null

        lifecycleScope.launch {
            val hasJwt = !container.userPrefs.getToken().isNullOrEmpty()
            if (!hasJwt) {
                container.userPrefs.setPendingSharedToken(token)
                Toast.makeText(
                    this@MainActivity,
                    "Пройди регистрацию — путь мастера подключится сразу после",
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            container.tokenRepository.subscribe(token).fold(
                onSuccess = { t ->
                    container.userPrefs.setActiveTokenId(t.id)
                    Toast.makeText(
                        this@MainActivity,
                        "Подключён путь: ${t.name ?: "Без названия"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось подключить путь: ${e.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }
}
