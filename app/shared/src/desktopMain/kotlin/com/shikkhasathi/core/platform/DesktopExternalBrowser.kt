package com.shikkhasathi.core.common

import java.awt.Desktop
import java.net.URI

/** Dev harness: the system browser when there is a desktop session, otherwise prints the URL. */
class DesktopExternalBrowser : ExternalBrowser {
    @Suppress("TooGenericExceptionCaught") // headless CI, missing browser…: just print it
    override fun open(url: String): Boolean =
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                println("ShikkhaSathi: open $url")
                false
            }
        } catch (e: Exception) {
            println("ShikkhaSathi: cannot open $url (${e.message})")
            false
        }
}
