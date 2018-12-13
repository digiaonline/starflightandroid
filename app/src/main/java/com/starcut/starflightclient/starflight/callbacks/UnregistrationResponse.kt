package com.starcut.starflightclient.starflight.callbacks

class UnregistrationResponse(private val result: Result) : StarFlightResponse {

    enum class Result {
        /**
         * Unregistration was successful
         */
        OK,
        /**
         * The device was not registered in the first place.
         */
        NOT_REGISTERED
    }
}
