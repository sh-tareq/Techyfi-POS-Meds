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

    enableEdgeToEdge()
    setContent {
      val settingsDataStore = remember { SettingsDataStore(applicationContext) }
      val themeMode by settingsDataStore.themeModeFlow.collectAsState(initial = "SYSTEM")
      val isDark = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemInDarkTheme()
      }
      TechyfiPOSTheme(darkTheme = isDark) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    currentActivity = java.lang.ref.WeakReference(this)
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
