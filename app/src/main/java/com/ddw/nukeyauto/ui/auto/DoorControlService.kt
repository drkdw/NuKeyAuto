package com.ddw.nukeyauto.ui.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Car App Service entry point for Android Auto. */
@AndroidEntryPoint
class DoorControlService : CarAppService() {

    @Inject
    lateinit var repository: NukiRepository

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return DoorControlScreen(carContext, repository)
            }
        }
    }
}
