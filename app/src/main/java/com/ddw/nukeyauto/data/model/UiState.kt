package com.ddw.nukeyauto.data.model

import com.ddw.nukeyauto.R

/**
 * UI state shared between the mobile and Android Auto surfaces.
 * Emitted by [com.ddw.nukeyauto.data.repo.NukiRepository] via a StateFlow.
 */
data class UiState(
    val lockState: LockState = LockState.Unknown,
    /** Charge in percent, or null when the lock only reports [batteryCritical]. */
    val battery: Int? = null,
    val batteryCritical: Boolean = false,
    val isLoading: Boolean = false,
    val error: NukiError? = null,
    val countdownSeconds: Int = 0,
    val doorState: DoorState = DoorState.None,
    val doorSensorBatteryCritical: Boolean = false
) {
    /**
     * Status text for the lock. After an unlatch the lock reports "door open"; with a door
     * sensor the door has its own line, so the lock itself just reads "Unlocked".
     */
    val lockStatusRes: Int
        get() = if (lockState == LockState.Unlatched && doorState.doorLabelRes != null) {
            R.string.status_unlocked
        } else {
            lockState.statusRes
        }

    /** The bolt is thrown but the door is ajar: the house is not actually locked. */
    val lockedButDoorOpen: Boolean
        get() = lockState == LockState.Locked && doorState == DoorState.Open
}
