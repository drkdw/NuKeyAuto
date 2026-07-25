package com.ddw.nukeyauto.data.model

import com.google.gson.annotations.SerializedName

data class SmartLockResponse(
    @SerializedName("smartlockId") val smartlockId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("state") val state: LockStateResponse
)

data class LockStateResponse(
    @SerializedName("mode") val mode: Int,
    @SerializedName("state") val state: Int,
    @SerializedName("batteryCharge") val batteryCharge: Int = 0,
    @SerializedName("batteryCritical") val batteryCritical: Boolean = false
)

/** Request body for lock/unlock actions. */
data class ActionRequest(
    @SerializedName("action") val action: Int
)

/** Nuki lock states as defined by the Nuki Web API. */
enum class LockState {
    Uncalibrated, // State 0: not yet calibrated
    Locked,       // State 1: locked
    Unlocking,    // State 2: unlocking in progress
    Unlocked,     // State 3: unlocked, door closed (also state 6: lock 'n' go)
    Locking,      // State 4: locking in progress
    Unlatched,    // State 5: latch open, door physically open
    Unlatching,   // State 7: unlatching in progress
    BootRun,      // State 253: lock is booting
    MotorBlocked, // State 254: motor blocked
    Undefined,    // State 255: lock offline / unavailable
    Unknown
}
