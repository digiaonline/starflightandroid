package com.starcut.starflightclient.starflight

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONException
import org.json.JSONObject
import java.util.*

const val TEXT_KEY = "text"
const val URL_KEY = "url"
const val SUBJECT_KEY = "subject"

abstract class StarflightMessagingService : FirebaseMessagingService() {

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String?) {
        Log.d(TAG, "Refreshed token: " + token!!)
        try {
            Starflight.refreshRegistration(this)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to refresh token")
        }

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        //starflightClient.refreshRegistration();
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        super.onMessageReceived(remoteMessage)

        if (remoteMessage!!.data != null) {
            val options = JSONObject()
            for (key in remoteMessage.data.keys) {
                if (key != TEXT_KEY && key != URL_KEY && key != UUID_KEY) {
                    try {
                        options.put(key, remoteMessage.data[key])
                    } catch (e: JSONException) {
                        Log.w(
                            "StarFlight Push Client",
                            "Failed to serialize extra as JSON: " + remoteMessage.data[key],
                            e
                        )
                    }

                }
            }
            val url =
                if (remoteMessage.notification!!.link != null) remoteMessage.notification!!.link!!.toString() else null
            val messageUuid = UUID.fromString(remoteMessage.data[UUID_KEY])
            onReceive(remoteMessage.data[TEXT_KEY], url, messageUuid, options)
        }
    }

    /**
     * Called when a StarFlight push notification was received with the supplied details
     * @param text the notification text
     * @param url the notification URL, or null if the notification did not have an URL
     * @param messageUuid the StarFlight message's UUID
     * @param options the starFlight message's extra options
     */
    abstract fun onReceive(text: String?, url: String?, messageUuid: UUID, options: JSONObject)

    companion object {


        private const val TAG = "MessagingService"
        private const val UUID_KEY = "uuid"
    }
}
