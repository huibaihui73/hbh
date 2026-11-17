package com.quntodo.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.quntodo.app.MainActivity
import com.quntodo.app.QunTodoApp
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as QunTodoApp
        val incoming = intent

        lifecycleScope.launch {
            runCatching {
                if (incoming?.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(
                        incoming,
                        Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
                    val path = uri?.let { app.container.imageStore.copyFrom(it) }
                    if (path != null) {
                        app.container.repository.ingestScreenshot(path)
                    }
                } else {
                    val text = when (incoming?.action) {
                        Intent.ACTION_PROCESS_TEXT ->
                            incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                        else ->
                            incoming?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    }
                    if (!text.isNullOrBlank()) {
                        app.container.repository.ingestManualText(text)
                    }
                }
            }

            startActivity(
                Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_INBOX, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            finish()
        }
    }
}
