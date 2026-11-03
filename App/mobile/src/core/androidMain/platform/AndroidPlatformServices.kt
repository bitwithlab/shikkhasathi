package com.shikkhasathi.core.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.shikkhasathi.core.datastore.KeyValueStore

/** SharedPreferences-backed [KeyValueStore] (`ss_prefs`, private, not backed up). */
class PrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("ss_prefs", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/**
 * Opens URLs in a Chrome Custom Tab without the `androidx.browser` artifact: a VIEW intent carrying the
 * Custom Tabs session extra is what `CustomTabsIntent` builds, and browsers without Custom Tabs support
 * simply open the page. Swap in `androidx.browser:browser` (CustomTabsIntent + toolbar colour) when it is
 * added to the version catalog on an SDK machine.
 */
class CustomTabsBrowser(context: Context) : ExternalBrowser {
    private val app = context.applicationContext

    override fun open(url: String): Boolean {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtras(Bundle().apply { putBinder(EXTRA_SESSION, null) })
                putExtra(EXTRA_SHARE_STATE, SHARE_STATE_OFF)
            }
        return try {
            app.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
        const val EXTRA_SHARE_STATE = "androidx.browser.customtabs.extra.SHARE_STATE"
        const val SHARE_STATE_OFF = 2
    }
}
