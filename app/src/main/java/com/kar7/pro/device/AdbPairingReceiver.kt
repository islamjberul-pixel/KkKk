package com.kar7.pro.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface AdbNotificationEvent {
    data class PairingReplySubmitted(val rawCodeInput: String) : AdbNotificationEvent
    data object PairingCancelled : AdbNotificationEvent
}

object AdbPairingBus {
    private val _events = MutableSharedFlow<AdbNotificationEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AdbNotificationEvent> = _events.asSharedFlow()
    private val subscribers = AtomicInteger(0)

    fun registerSubscriber() {
        subscribers.incrementAndGet()
    }

    fun unregisterSubscriber() {
        subscribers.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
    }

    fun hasActiveSubscriber(): Boolean = subscribers.get() > 0

    fun emit(event: AdbNotificationEvent) {
        _events.tryEmit(event)
    }
}

class AdbPairingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            AdbPairingNotificationManager.ACTION_CANCEL_PAIRING -> {
                AdbPairingNotificationManager.cancelNotification(context)
                AdbPairingBus.emit(AdbNotificationEvent.PairingCancelled)
            }

            AdbPairingNotificationManager.ACTION_SUBMIT_PAIRING_CODE -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val submitted = results
                    ?.getCharSequence(AdbPairingNotificationManager.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                if (AdbPairingBus.hasActiveSubscriber()) {
                    AdbPairingBus.emit(AdbNotificationEvent.PairingReplySubmitted(submitted))
                    return
                }

                // Fallback if app UI process is not active
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val updated = AdbConnectManager(context).processPairingCode(submitted)
                        updateNotificationFromStatus(context, updated.pairingStatus)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        fun updateNotificationFromStatus(context: Context, status: AdbPairingStatus) {
            when (status) {
                is AdbPairingStatus.DeveloperOptionsDisabled -> {
                    AdbPairingNotificationManager.showPairingResultNotification(
                        context = context,
                        title = "KAR7 PRO — ADB PAIRING",
                        message = status.message,
                        keepReplyAction = false
                    )
                }
                is AdbPairingStatus.ValidationError -> {
                    AdbPairingNotificationManager.showPairingResultNotification(
                        context = context,
                        title = "KAR7 PRO — ADB PAIRING",
                        message = status.message,
                        keepReplyAction = true
                    )
                }
                is AdbPairingStatus.EndpointUnavailable -> {
                    AdbPairingNotificationManager.showPairingResultNotification(
                        context = context,
                        title = "KAR7 PRO — ADB ENDPOINT UNAVAILABLE",
                        message = status.message,
                        keepReplyAction = true
                    )
                }
                is AdbPairingStatus.HostPairingRequired -> {
                    AdbPairingNotificationManager.showPairingResultNotification(
                        context = context,
                        title = "KAR7 PRO — ADB HOST PAIRING REQUIRED",
                        message = status.message,
                        keepReplyAction = true
                    )
                }
                else -> Unit
            }
        }
    }
}
