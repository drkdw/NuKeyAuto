package com.ddw.nukeyauto.data.demo

import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockStateResponse
import com.ddw.nukeyauto.data.model.SmartLockResponse
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simulated Nuki lock with a door sensor for demo mode: lets people try every screen (and
 * Play reviewers test the app) without a Nuki account. Never talks to the network or a
 * real door. Moves through the same states as a real lock, one state per poll; after an
 * open the door stands open for a few refreshes, then closes, like someone walking in.
 */
@Singleton
class DemoLock @Inject constructor() : NukiApiService {
    private var lock = LOCKED
    private var door = DOOR_CLOSED
    /** (lock, door) pairs reported on the next fetches, oldest first. */
    private val upcoming = ArrayDeque<Pair<Int, Int>>()

    override suspend fun getSmartLockState(smartlockId: Long): SmartLockResponse {
        delay(NETWORK_DELAY_MS)
        upcoming.removeFirstOrNull()?.let { (l, d) -> lock = l; door = d }
        return SmartLockResponse(LockStateResponse(lock, batteryCharge = BATTERY, doorState = door))
    }

    override suspend fun performAction(smartlockId: Long, actionRequest: ActionRequest) {
        delay(NETWORK_DELAY_MS)
        upcoming.clear()
        when (actionRequest.action) {
            2 -> upcoming += listOf(LOCKING to door, LOCKED to door)
            3 -> upcoming += listOf(
                UNLATCHING to DOOR_CLOSED,
                UNLATCHED to DOOR_OPEN,
                UNLOCKED to DOOR_OPEN,
                UNLOCKED to DOOR_OPEN,
                UNLOCKED to DOOR_CLOSED
            )
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
        const val DOOR_CLOSED = 2
        const val DOOR_OPEN = 3
    }
}
