package com.shikkhasathi.core.common

import com.shikkhasathi.core.datastore.KeyValueStore
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication

class UserDefaultsKeyValueStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(
        key: String,
        value: String,
    ) = defaults.setObject(value, forKey = key)

    override fun remove(key: String) = defaults.removeObjectForKey(key)
}

/**
 * Opens Safari. For checkout an `ASWebAuthenticationSession` / `SFSafariViewController` presented from the
 * Swift host is nicer (stays in the app); the Universal Link back to `/checkout/result` works either way.
 */
class IosExternalBrowser : ExternalBrowser {
    override fun open(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        return true
    }
}
