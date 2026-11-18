package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.ui.AuthScreen
import com.example.ui.MainScaffold
import com.example.ui.components.KeyEventDeduplicator
import com.example.ui.theme.FixMyAppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    private val keyEventDeduplicator = KeyEventDeduplicator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            FixMyAppTheme {
                Surface {
                    AppNavigation()
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyEventDeduplicator.shouldDrop(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainScaffold(
                currentUserId = user.uid,
                currentUserEmail = user.email,
                currentUserName = user.displayName
            )
        }
    }
}
