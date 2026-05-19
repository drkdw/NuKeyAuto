package com.ddw.nukeyauto.ui.auto

import com.ddw.nukeyauto.data.repo.NukiRepository
import com.ddw.nukeyauto.ui.BaseViewModel

/**
 * ViewModel for the Android Auto DoorControlScreen.
 * Not using @HiltViewModel because it is instantiated manually with a repository reference.
 */
class MainViewModel(repository: NukiRepository) : BaseViewModel(repository, TAG) {

    companion object {
        private const val TAG = "AutoMainViewModel"
    }
}
