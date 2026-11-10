package com.deepinnovations.call_history

import android.telecom.Call
import android.telecom.CallScreeningService

// Only reads the number. Never blocks or rejects any call.
class CallScreeningServiceImpl : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        CallStore.setNumber(this, number)
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
