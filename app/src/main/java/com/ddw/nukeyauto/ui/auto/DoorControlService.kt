package com.ddw.nukeyauto.ui.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.repo.NukiRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Car App Service entry point for Android Auto. */
@AndroidEntryPoint
class DoorControlService : CarAppService() {

    @Inject lateinit var repository: NukiRepository

    override fun createHostValidator(): HostValidator {
        // Debug builds allow any host (for Desktop Head Unit testing).
        // Production builds use the Car App Library's known-good allowlist of
        // official Android Auto host apps (Google Maps, Assistant, etc.).
        return if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return DoorControlScreen(carContext, repository)
            }
        }
    }
}
