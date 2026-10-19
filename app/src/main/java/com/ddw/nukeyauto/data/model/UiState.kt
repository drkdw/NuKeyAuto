package com.ddw.nukeyauto.data.model

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
    val countdownSeconds: Int = 0
)
