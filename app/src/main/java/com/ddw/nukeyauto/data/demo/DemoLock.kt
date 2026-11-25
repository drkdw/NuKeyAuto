package com.ddw.nukeyauto.data.demo

import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockStateResponse
import com.ddw.nukeyauto.data.model.SmartLockResponse
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simulated Nuki lock for demo mode: lets people try every screen (and Play reviewers
 * test the app) without a Nuki account. Never talks to the network or a real door.
 * Moves through the same states as a real lock, one state per poll.
 */
@Singleton
class DemoLock @Inject constructor() : NukiApiService {
    private var state = LOCKED
    /** States reported on the next fetches, oldest first. */
    private val upcoming = ArrayDeque<Int>()

    override suspend fun getSmartLockState(smartlockId: Long): SmartLockResponse {
        delay(NETWORK_DELAY_MS)
        upcoming.removeFirstOrNull()?.let { state = it }
        return SmartLockResponse(LockStateResponse(state, batteryCharge = BATTERY))
    }

    override suspend fun performAction(smartlockId: Long, actionRequest: ActionRequest) {
        delay(NETWORK_DELAY_MS)
        upcoming.clear()
        when (actionRequest.action) {
            2 -> upcoming += listOf(LOCKING, LOCKED)
            3 -> upcoming += listOf(UNLATCHING, UNLATCHED, UNLOCKED)
        }
    }

    private companion object {
        const val NETWORK_DELAY_MS = 600L
        const val BATTERY = 82
        const val LOCKED = 1
        const val UNLOCKED = 3
        const val LOCKING = 4
        const val UNLATCHED = 5
        const val UNLATCHING = 7
    }
}
