package com.example.billiardtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billiardtracker.ui.nav.BilliardNavHost
import com.example.billiardtracker.ui.screens.auth.AuthViewModel
import com.example.billiardtracker.ui.screens.auth.PhoneAuthScreen
import com.example.billiardtracker.ui.theme.BilliardTrackerTheme

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
            }
        }
    }
}
