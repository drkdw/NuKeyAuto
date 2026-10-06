package com.ddw.nukeyauto.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Phone-side ViewModel; survives rotation so a running action keeps its scope.
 * The Android Auto screen talks to the same singleton repository directly.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: NukiRepository
) : ViewModel() {

    val uiState = repository.uiState

    /**
     * Refresh the lock state from the API.
     * Called from MainActivity.onResume; the car screen refreshes on its own.
     */
    fun refresh() {
        Log.d(TAG, "refresh()")
        viewModelScope.launch { repository.fetchState() }
    }

    /** Unlatch the door (action 3 — physically open). */
    fun openDoor() {
        viewModelScope.launch { repository.performLockAction(false) }
    }

    /** Abort the open-delay countdown. */
    fun cancelOpen() = repository.cancelOpen()

    /** Lock the door (action 2). */
    fun lockDoor() {
        viewModelScope.launch { repository.performLockAction(true) }
    }

    /** Whether the user wants a confirmation dialog before door actions. */
    fun isConfirmationEnabled(): Boolean = repository.isConfirmationEnabled()

    private companion object {
        const val TAG = "MainViewModel"
    }
}
