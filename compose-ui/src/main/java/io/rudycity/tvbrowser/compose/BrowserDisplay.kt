package io.rudycity.tvbrowser.compose

import java.net.URI

/** Display helper that never changes the actual navigation target. */
internal object BrowserDisplay {
    fun compact(url: String): String {
        return try {
            val parsed = URI(url)
            if (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) {
                parsed.host?.removePrefix("www.") ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
