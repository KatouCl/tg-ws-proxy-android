package com.amurcanov.tgwsproxy

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** A windowless entry point required by the App Shortcuts API. */
class ProxyShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val command = intent?.action
            if (command == ProxyService.ACTION_START || command == ProxyService.ACTION_STOP) {
                // The receiver owns the asynchronous work, so NoDisplay can finish
                // before onResume as required by Android. Never forward external extras.
                sendBroadcast(Intent(this, ProxyCommandReceiver::class.java).setAction(command))
            }
        } finally {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
