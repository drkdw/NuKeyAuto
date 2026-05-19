package com.ddw.nukeyauto.ui.mobile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the mobile MainActivity.
 * Shares the same UiState flow as the Android Auto screen via the singleton repository.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: NukiRepository
) : ViewModel() {

    val uiState = repository.uiState

    init {
        refresh()
    }

    /** Refresh the lock state from the API. */
    fun refresh() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "refresh()")
        }
        viewModelScope.launch {
            repository.fetchState()
        }
    }

    /** Unlatch the door (action 3 — physically open). */
    fun openDoor() {
        viewModelScope.launch {
            repository.performLockAction(false)
        }
    }

    /** Lock the door (action 2). */
    fun lockDoor() {
        viewModelScope.launch {
            repository.performLockAction(true)
        }
    }

    /** Whether the user wants a confirmation dialog before door actions. */
    fun isConfirmationEnabled(): Boolean = repository.isConfirmationEnabled()

    companion object {
        private const val TAG = "MainViewModel"
    }
}
