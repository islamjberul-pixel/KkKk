package com.kar7.pro.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kar7.pro.models.WhatsappInfo
import java.net.URLEncoder

sealed interface WhatsappLaunchResult {
    data object Launched : WhatsappLaunchResult
    data class Unavailable(val reason: String) : WhatsappLaunchResult
}

object WhatsappHelper {

    fun buildChatUri(chatLink: String?, message: String?): Uri? {
        val rawLink = chatLink?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { Uri.parse(rawLink) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "https" && scheme != "http" && scheme != "whatsapp") return null

        val msg = message?.trim().orEmpty()
        if (msg.isEmpty()) return parsed

        // Append or preserve text query parameter cleanly without inventing phone numbers
        return if (parsed.getQueryParameter("text") != null) {
            parsed
        } else {
            val separator = if (rawLink.contains("?")) "&" else "?"
            val encoded = URLEncoder.encode(msg, Charsets.UTF_8.name()).replace("+", "%20")
            Uri.parse("$rawLink${separator}text=$encoded")
        }
    }

    fun openBuyKey(context: Context, whatsapp: WhatsappInfo?): WhatsappLaunchResult {
        return openChat(context, whatsapp?.chatLink, whatsapp?.buyKeyMessage)
    }

    fun openSupport(context: Context, whatsapp: WhatsappInfo?): WhatsappLaunchResult {
        return openChat(context, whatsapp?.chatLink, whatsapp?.supportMessage)
    }

    fun openChat(context: Context, chatLink: String?, message: String?): WhatsappLaunchResult {
        val uri = buildChatUri(chatLink, message)
            ?: return WhatsappLaunchResult.Unavailable("WhatsApp link has not been configured by the backend yet.")

        // Try standard WhatsApp first, then WhatsApp Business, then system browser/handler
        val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in whatsappPackages) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(intent)
                    WhatsappLaunchResult.Launched
                } catch (_: ActivityNotFoundException) {
                    continue
                }
            }
        }

        // Fallback to web link (e.g., https://wa.me/... opened in browser)
        val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(webIntent)
            WhatsappLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            WhatsappLaunchResult.Unavailable("No WhatsApp app or web browser is available to open the link.")
        }
    }
}
