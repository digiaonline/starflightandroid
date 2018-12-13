package com.starcut.starflightclient.starflight.callbacks

class MessageOpenedResponse(val result: Result) : StarFlightResponse {

    enum class Result {
        /**
         * The message open was recorded successfully
         */
        OK,

        /**
         * The opening of the message had already been recorded
         */
        ALREADY_OPENED
    }
}
