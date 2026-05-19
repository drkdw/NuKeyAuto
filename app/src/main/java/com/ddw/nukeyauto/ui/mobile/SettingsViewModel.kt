package com.ddw.nukeyauto.ui.mobile

import androidx.lifecycle.ViewModel
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for SettingsActivity.
 * Keeps the UI layer decoupled from the repository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: NukiRepository
) : ViewModel() {

    /** Persist new Nuki API credentials. */
    fun saveCredentials(token: String, id: String) = repository.saveCredentials(token, id)
}
