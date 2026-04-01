package com.ddw.nukeyauto.data.api

import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.SmartLockResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Nuki Web API endpoints for smart lock state and actions. */
interface NukiApiService {

    @GET("smartlock/{smartlockId}")
    suspend fun getSmartLockState(
        @Path("smartlockId") smartlockId: Long
    ): SmartLockResponse

    /** Nuki Web API expects a JSON body, not a query parameter. */
    @POST("smartlock/{smartlockId}/action")
    suspend fun performAction(
        @Path("smartlockId") smartlockId: Long,
        @Body actionRequest: ActionRequest
    )
}
