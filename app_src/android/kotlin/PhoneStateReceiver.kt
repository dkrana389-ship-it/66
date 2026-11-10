package com.deepinnovations.call_history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class PhoneStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val st = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val extraNumber: String? = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val last = CallStore.getState(context)

        when (st) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (last != "RINGING") CallStore.setState(context, "RINGING")
                if (!extraNumber.isNullOrBlank()) CallStore.setNumber(context, extraNumber)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (last != "OFFHOOK") {
                    CallStore.setIncoming(context, last == "RINGING")
                    CallStore.setStart(context, System.currentTimeMillis())
                    CallStore.setState(context, "OFFHOOK")
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (last == "IDLE") return
                val number: String? =
                    CallStore.takeNumber(context)?.takeIf { it.isNotBlank() } ?: extraNumber
                val name: String? = ContactLookup.name(context, number)
                val type: String
                var dur = 0L
                if (last == "OFFHOOK") {
                    dur = (System.currentTimeMillis() - CallStore.getStart(context)) / 1000
                    type = if (CallStore.isIncoming(context)) "Incoming" else "Outgoing"
                    CallStore.addCall(context, dur, false)
                } else {
                    type = "Missed"
                    CallStore.addCall(context, 0, true)
                }
                CallStore.setState(context, "IDLE")
                PopupOverlay.show(context, name, number, type, dur, CallStore.today(context))
            }
        }
    }
}
