package com.ddw.nukeyauto.ui.auto

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.repo.NukiRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the Android Auto DoorControlScreen.
 * Not using @HiltViewModel because it is instantiated manually with a repository reference.
 */
class MainViewModel(private val repository: NukiRepository) : ViewModel() {

    val uiState = repository.uiState

    init {
        refresh()
    }

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
        private const val TAG = "AutoMainViewModel"
    }
}
