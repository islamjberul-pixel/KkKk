package com.kar7.pro.device

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.R

object AdbPairingNotificationManager {

    const val CHANNEL_ID = "kar7_adb_pairing_channel"
    const val NOTIFICATION_ID = 7001

    const val ACTION_SUBMIT_PAIRING_CODE = "com.kar7.pro.action.SUBMIT_ADB_PAIRING_CODE"
    const val ACTION_CANCEL_PAIRING = "com.kar7.pro.action.CANCEL_ADB_PAIRING"
    const val KEY_PAIRING_CODE = "key_adb_pairing_code"

    fun hasNotificationPermission(context: Context): Boolean {
        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            systemEnabled && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            systemEnabled
        }
    }

    fun needsRuntimeNotificationRequest(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ADB Wireless Pairing",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Persistent KAR7 PRO notification for entering your Wireless Debugging pairing code."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showPairingPromptNotification(
        context: Context,
        statusOverride: String? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        ensureChannel(context)

        val replyIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING_CODE
        }
        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            replyIntent,
            replyFlags
        )

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Enter 6-digit pairing code")
            .build()

        val submitAction = NotificationCompat.Action.Builder(
            R.drawable.ic_kar7_notification,
            "Submit",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val cancelIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_CANCEL_PAIRING
        }
        val cancelFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            cancelIntent,
            cancelFlags
        )

        val bodyText = statusOverride
            ?: "Enter your 6-digit Wireless Debugging pairing code."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kar7_notification)
            .setContentTitle("KAR7 PRO — ADB PAIRING")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(submitAction)
            .addAction(R.drawable.ic_kar7_notification, "Cancel", cancelPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    fun showPairingResultNotification(
        context: Context,
        title: String,
        message: String,
        keepReplyAction: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (keepReplyAction) {
            showPairingPromptNotification(context, statusOverride = message)
            return
        }

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kar7_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
