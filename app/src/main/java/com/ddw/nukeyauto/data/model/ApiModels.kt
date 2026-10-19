package com.ddw.nukeyauto.data.model

import com.ddw.nukeyauto.R
import com.google.gson.annotations.SerializedName

/** Only the fields the app reads; Gson skips the rest of the response. */
data class SmartLockResponse(
    @SerializedName("state") val state: LockStateResponse
)

data class LockStateResponse(
    @SerializedName("state") val state: Int,
    // Missing on locks that only report batteryCritical (Smart Lock 1.0 / 2.0)
    @SerializedName("batteryCharge") val batteryCharge: Int? = null,
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

/** True while the lock is still moving or booting, so no action should be offered. */
val LockState.isTransition: Boolean
    get() = this == LockState.Locking || this == LockState.Unlocking ||
            this == LockState.Unlatching || this == LockState.BootRun

/** Faults drawn in the error (phone) or warning (car) color. */
val LockState.isFault: Boolean
    get() = this == LockState.MotorBlocked || this == LockState.Uncalibrated ||
            this == LockState.Undefined || this == LockState.Unknown

/** Icon per lock state, shared by the phone and car screens. */
val LockState.iconRes: Int
    get() = when (this) {
        LockState.Locked -> R.drawable.ic_lock
        LockState.Unlocked -> R.drawable.ic_lock_open
        LockState.Unlatched -> R.drawable.ic_door_open
        LockState.Locking, LockState.Unlocking, LockState.Unlatching,
        LockState.BootRun -> R.drawable.ic_hourglass
        LockState.Undefined -> R.drawable.ic_cloud_off
        LockState.MotorBlocked, LockState.Uncalibrated, LockState.Unknown -> R.drawable.ic_warning
    }

/** Status text per lock state, shared by the phone and car screens. */
val LockState.statusRes: Int
    get() = when (this) {
        LockState.Locked -> R.string.status_locked
        LockState.Locking -> R.string.status_locking
        LockState.Unlocked -> R.string.status_unlocked
        LockState.Unlocking -> R.string.status_unlocking
        LockState.Unlatched -> R.string.status_unlatched
        LockState.Unlatching -> R.string.status_unlatching
        LockState.BootRun -> R.string.status_boot_run
        LockState.MotorBlocked -> R.string.status_motor_blocked
        LockState.Uncalibrated -> R.string.status_uncalibrated
        LockState.Undefined -> R.string.status_undefined
        LockState.Unknown -> R.string.status_unknown
    }
