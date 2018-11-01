package com.starcut.starflightclient;

public interface StarFlightCallback<T extends StarFlightResponse>
{
    /**
     * Called when the operation succeeds
     * @param result the result of the operation
     */
    public void onSuccess(T result);

    /**
     * Called when the operation fails.
     * @param message error description
     * @param t an exception that occurred, or null if not applicable
     */
    public void onFailure(String message, Throwable t);
}
