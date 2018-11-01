package com.starcut.starflightclient;

import java.util.UUID;

public class RegistrationResponse implements StarFlightResponse
{
    private final UUID clientUuid;
    private final Result result;

    RegistrationResponse(UUID clientUuid, Result result)
    {
        this.clientUuid = clientUuid;
        this.result = result;
    }

    public UUID getClientUuid()
    {
        return clientUuid;
    }

    public enum Result
    {
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
        ALREADY_REGISTERED;
    }
}
