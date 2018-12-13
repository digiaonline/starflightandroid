package com.starcut.starflightclient.starflight.callbacks

import java.util.*

class RegistrationResponse(val clientUuid: UUID, val result: Result) : StarFlightResponse {

    enum class Result {
        /**
         * Registration was successful
         */
        REGISTERED,
        /**
         * The device is already registered and a refresh was performed
         */
        REFRESHED,
        /**
         * The device is already registered and the registration does not need refreshing at the moment.
         */
        ALREADY_REGISTERED
    }
}
