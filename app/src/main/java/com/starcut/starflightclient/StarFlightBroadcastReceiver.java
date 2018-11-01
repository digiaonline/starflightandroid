package com.starcut.starflightclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public abstract class StarFlightBroadcastReceiver extends BroadcastReceiver
{
	private static final String UUID_KEY = "uuid";

	@Override
    public void onReceive(Context context, Intent data)
    {
    	Log.e("StarFlight Push Client", "Received broadcast: " + data);
    	
    	Bundle extras = data.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        String messageType = gcm.getMessageType(data);

        if (!extras.isEmpty() && extras.containsKey(UUID_KEY))
        {
        	if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType))
        	{
		        JSONObject options = new JSONObject();

		        for (String key : extras.keySet())
                {
			        if (!key.equals(StarFlightClient.TEXT_KEY) && !key.equals(StarFlightClient.URL_KEY) && !key.equals(UUID_KEY))
			        {
				        try
				        {
					        options.put(key, extras.get(key));
				        }
				        catch (JSONException e)
				        {
					        Log.w("StarFlight Push Client", "Failed to serialize extra as JSON: " + extras.get(key), e);
				        }
			        }
		        }
        		String url = (extras.containsKey(StarFlightClient.URL_KEY)) ? extras.getString(StarFlightClient.URL_KEY) : null;
        		UUID messageUuid = UUID.fromString(extras.getString(UUID_KEY));
        		onReceive(context, extras.getString(StarFlightClient.TEXT_KEY), url, messageUuid, options);
        	}
        }
    }

	/**
	 * Called when a StarFlight push notification was received with the supplied details
	 * @param context
	 * @param text the notification text
	 * @param url the notification URL, or null if the notification did not have an URL
	 * @param messageUuid the StarFlight message's UUID
	 * @param options the StarFlight message's extra options
	 */
	public abstract void onReceive(Context context, String text, String url, UUID messageUuid, JSONObject options);
}
