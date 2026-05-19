package com.ddw.nukeyauto.ui.mobile

import com.ddw.nukeyauto.data.repo.NukiRepository
import com.ddw.nukeyauto.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for the mobile MainActivity.
 * Shares the same UiState flow as the Android Auto screen via the singleton repository.
 */
@HiltViewModel
class MainViewModel @Inject constructor(repository: NukiRepository) : BaseViewModel(repository, TAG) {

    companion object {
        private const val TAG = "MainViewModel"
    }
}
