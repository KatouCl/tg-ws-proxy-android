package com.amurcanov.tgwsproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Public automation endpoint: accepts only START/STOP, using the user's saved settings. */
class ProxyCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.action
        if (command != ProxyService.ACTION_START && command != ProxyService.ACTION_STOP) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                // Stay within the broadcast execution budget, including DataStore reads.
                withTimeout(8_000L) {
                    commandMutex.withLock {
                        when (command) {
                            ProxyService.ACTION_START -> {
                                if (!ProxyController.startFromSavedSettings(appContext)) {
                                    Log.w(TAG, "Proxy start rejected: invalid saved configuration")
                                }
                            }
                            ProxyService.ACTION_STOP -> ProxyController.stop(appContext)
                        }
                    }
                }
            } catch (e: Exception) {
                // Includes OS background-start restrictions. Do not open a UI as fallback.
                Log.w(TAG, "Proxy command failed: $command", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ProxyCommandReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val commandMutex = Mutex()
    }
}
