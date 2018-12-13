package com.starcut.starflightclient.starflight.callbacks

interface StarFlightCallback<T : StarFlightResponse> {
    /**
     * Called when the operation succeeds
     * @param result the result of the operation
     */
    fun onSuccess(result: T)

    /**
     * Called when the operation fails.
     * @param message error description
     * @param t an exception that occurred, or null if not applicable
     */
    fun onFailure(message: String, t: Throwable)
}
