package com.ddw.nukeyauto.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.repo.NukiRepository
import kotlinx.coroutines.launch

/**
 * Shared ViewModel logic for both mobile and Android Auto surfaces.
 * Subclasses supply their own [tag] for logging and the appropriate
 * DI mechanism (Hilt injection vs manual construction).
 */
abstract class BaseViewModel(
    protected val repository: NukiRepository,
    private val tag: String
) : ViewModel() {

    val uiState = repository.uiState

    init {
        refresh()
    }

    /** Refresh the lock state from the API. */
    fun refresh() {
        if (BuildConfig.DEBUG) Log.d(tag, "refresh()")
        viewModelScope.launch { repository.fetchState() }
    }

    /** Unlatch the door (action 3 — physically open). */
    fun openDoor() {
        viewModelScope.launch { repository.performLockAction(false) }
    }

    /** Lock the door (action 2). */
    fun lockDoor() {
        viewModelScope.launch { repository.performLockAction(true) }
    }

    /** Whether the user wants a confirmation dialog before door actions. */
    fun isConfirmationEnabled(): Boolean = repository.isConfirmationEnabled()
}
