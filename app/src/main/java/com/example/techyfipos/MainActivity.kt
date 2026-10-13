package com.example.techyfipos

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.theme.TechyfiPOSTheme

import android.view.KeyEvent
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.techyfipos.data.BackupImportMode
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.db.AppDatabase
import com.example.techyfipos.utils.restartApp
import com.example.techyfipos.utils.HardwareBarcodeScanner

class MainActivity : ComponentActivity() {
  companion object {
    var currentActivity: java.lang.ref.WeakReference<android.app.Activity>? = null
      private set
    val pendingNavigationDestination = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    currentActivity = java.lang.ref.WeakReference(this)
    handleDeepLink(intent)

    try {
      System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
      System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
      System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")
    } catch (e: Throwable) {
      e.printStackTrace()
    }

    if (savedInstanceState == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    enableEdgeToEdge()
    setContent {
      val settingsDataStore = remember { SettingsDataStore(applicationContext) }
      val themeMode by settingsDataStore.themeModeFlow.collectAsState(initial = "SYSTEM")
      val appLanguage by settingsDataStore.languageFlow.collectAsState(initial = "en")
      val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
      }
      androidx.compose.runtime.CompositionLocalProvider(com.example.techyfipos.utils.LocalAppLanguage provides appLanguage) {
        TechyfiPOSTheme(darkTheme = isDark) {
          androidx.compose.runtime.DisposableEffect(isDark) {
            enableEdgeToEdge(
              statusBarStyle = if (isDark) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
              } else {
                androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
              },
              navigationBarStyle = if (isDark) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
              } else {
                androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
              }
            )
            onDispose {}
          }
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainNavigation()
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    currentActivity = java.lang.ref.WeakReference(this)
  }

  override fun onUserInteraction() {
    super.onUserInteraction()
    com.example.techyfipos.ui.viewmodel.AuthViewModel.recordUserActivity()
  }

  @SuppressLint("RestrictedApi")
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val focused = currentFocus
    val isEditor = focused?.onCheckIsTextEditor() == true
    if (HardwareBarcodeScanner.dispatchKeyEventWithEditorCheck(event, isEditor, focused)) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onStop() {
    super.onStop()
    // Persist a WorkManager request before the UI process can be removed.
    // The worker checks the configured daily/weekly/monthly due time.
    com.example.techyfipos.utils.AutoBackupScheduler.performImmediateBackupIfDue(this)
  }

  override fun onDestroy() {
    if (currentActivity?.get() == this) {
      currentActivity = null
    }
    super.onDestroy()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  private fun handleDeepLink(intent: Intent?) {
    val destination = intent?.getStringExtra("navigate_to") ?: return
    intent.removeExtra("navigate_to")
    pendingNavigationDestination.value = destination
  }
}
