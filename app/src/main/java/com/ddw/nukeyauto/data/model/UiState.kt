package com.ddw.nukeyauto.data.model

/**
 * UI state shared between the mobile and Android Auto surfaces.
 * Emitted by [com.ddw.nukeyauto.data.repo.NukiRepository] via a StateFlow.
 */
data class UiState(
    val lockState: LockState = LockState.Unknown,
    val battery: Int = 0,
    val isLoading: Boolean = false,
    val error: NukiError? = null,
    val countdownSeconds: Int = 0
)
