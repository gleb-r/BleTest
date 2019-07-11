package com.example.bletest

import android.bluetooth.BluetoothAdapter.*

data class SensorData(val state: Int?=null, val value: ByteArray?=null) {
    val stateSting: String?
        get() {
            if (state == null) return null
            return when (state) {
                STATE_CONNECTED -> "CONNECTED"
                STATE_CONNECTING -> "CONNECTING"
                STATE_DISCONNECTING -> "DISCONNECTING"
                STATE_DISCONNECTED -> "DISCONNECTED"
                STATE_ON -> "ON"
                STATE_OFF -> "OFF"
                STATE_TURNING_ON -> "TURNING_ON"
                STATE_TURNING_OFF -> "TURNING_OFF"
                else -> "unknownState value: $state"
            }
        }

}