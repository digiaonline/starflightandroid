package com.starcut.starflightclient.starflight.logic

import android.content.Context
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.starcut.starflightclient.starflight.callbacks.*
import com.starcut.starflightclient.starflight.network.StarFlightNetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.io.IOException
import java.util.*

private val LOG_TAG = StarFlightClient::class.java.name

class StarFlightClient

/**
 * Constructs a new StarFlight Client with the supplied Firebase sender id, StarFlight app id and StarFlight client secret
 * @param senderId the Firebase sender id
 * @param appId the StarFlight app id
 * @param clientSecret the StarFlight client secret
 */
    (
    private val networkClient: StarFlightNetworkClient,
    private val senderId: String,
    private val appId: String,
    private val clientSecret: String
   ) {

    private val starflightPreferences : StarflightPreferences = StarflightPreferences()

    /**
     * Refreshes the current StarFlight registration if needed. It is advisable to call this method every time your application starts.
     */
    fun refreshRegistration(context: Context) {
        if (!isRegistered(context)) {
            throw IllegalStateException("Not registered")
        }

        val tags = starflightPreferences.getRegisteredTags(context)
        Log.d("NOTIF", "Registered flags to refresh $tags")
        register(context, tags, null)
    }

    /**
     *
     * Registers for push notifications with the supplied list of tags.
     *
     * If a registration already exists, its tags will be replaced with the supplied values.
     * @param tags the tags
     * @param callback callback that will be notified of success or failure
     */
    fun register(context: Context, tags: List<String>?, callback: StarFlightCallback<RegistrationResponse>?) {
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { instanceIdResult ->
            sendTokenIfNeeded(
                instanceIdResult.token,
                context.applicationContext,
                tags,
                callback
            )
        }
    }

    /**
     * Removes an existing registration
     * @param context
     * @param callback callback that will be notified of success or failure
     */
    fun unregister(context: Context, callback: StarFlightCallback<UnregistrationResponse>) {
        unregister(context, null, callback)
    }

    /**
     * Removes the supplied list of tags from an existing registration
     * @param context
     * @param tags
     * @param callback callback that will be notified of success or failure
     */
    fun unregister(context: Context, tags: List<String>?, callback: StarFlightCallback<UnregistrationResponse>) {
        val registrationId = starflightPreferences.getFirebaseToken(context)

        if (registrationId == null) {
            val response = UnregistrationResponse(UnregistrationResponse.Result.NOT_REGISTERED)
            callOnSuccess(callback, response)
            return
        }

        var response: UnregistrationResponse? = null
        try {
            runBlocking(Dispatchers.Default) {
                if (!tags.isNullOrEmpty()) {
                    // only unregister the specified tags
                    // Unregister tags from server
                    response = networkClient.sendUnregistrationToBackend(appId, clientSecret, registrationId, tags)
                    starflightPreferences.removeTagsFromStorage(context, tags)
                }
                else {
                    response = networkClient.sendUnregistrationToBackend(appId, clientSecret, registrationId, null)
                    FirebaseMessaging.getInstance().isAutoInitEnabled = false
                    FirebaseInstanceId.getInstance().deleteInstanceId()
                    starflightPreferences.removeRegistrationFromStorage(context)
                }
            }
        }
        catch (e: Exception) {
            callOnFailure(callback, "Unregistration failed: " + e.message, e)
        }
        if (response != null) {
            callOnSuccess(callback, response)
        }
    }

    private fun sendTokenIfNeeded(
        token: String,
        context: Context,
        tags: List<String>?,
        callback: StarFlightCallback<RegistrationResponse>?
    ) {
        val sortedTags = tags?.sorted()

        val lastSentToken = starflightPreferences.getLastSentToken(context)
        val registeredTags = starflightPreferences.getRegisteredTags(context)
        val sortedRegisteredTags = registeredTags.sorted()

        val skipSend: Boolean

        skipSend = (token == lastSentToken && sortedRegisteredTags == sortedTags)

        if (skipSend) {
            val response =
                RegistrationResponse(starflightPreferences.getClientUuid(context)!!, RegistrationResponse.Result.ALREADY_REGISTERED)
            callOnSuccess(callback, response)
            Log.i(LOG_TAG, "already registered and refreshing was not necessary")
        }
        else {
            var response: RegistrationResponse? = null
            try{
                runBlocking(Dispatchers.Default) {
                    response = networkClient.sendRegistrationTokenToBackend(appId, clientSecret, token, sortedTags)
                    starflightPreferences.storeRegistration(context, token, sortedTags, response!!.clientUuid)
                }
            }
            catch (e: IOException) {
                callOnFailure(callback, "Failed to send registration id to StarFlight: " + e.message, e)
            } catch (e: JSONException) {
                callOnFailure(callback, "Failed to parse server response: " + e.message, e)
            } catch (e: Exception) {
                callOnFailure(callback, "Unexpected exception" + e.message, e)
            }

            if (response != null) {
                callOnSuccess(callback, response)
            }
        }
    }

    /**
     * Tells if this app is currently registered for notifications
     */
    fun isRegistered(context : Context): Boolean {
        return starflightPreferences.getFirebaseToken(context) != null
    }

    fun markMessageAsRead(context: Context, messageUuid: UUID, callback: StarFlightCallback<MessageOpenedResponse>) {
        if (starflightPreferences.isMessageOpened(context, messageUuid)) {
            callOnSuccess(callback, MessageOpenedResponse(MessageOpenedResponse.Result.ALREADY_OPENED))
            return
        }

        try {
            runBlocking(Dispatchers.Default){
                val registrationId = starflightPreferences.getFirebaseToken(context)
                networkClient.markMessageOpened(appId, clientSecret, registrationId!!, messageUuid)
                starflightPreferences.storeMessageOpened(context, messageUuid)
            }
            callOnSuccess(callback, MessageOpenedResponse(MessageOpenedResponse.Result.OK))
        } catch (ex: IOException) {
            callOnFailure(callback, "Recording message open failed: " + ex.message, ex)
        }
    }

    /**
     * Calls the onSuccess method of the supplied callback if the callback is not null
     */
    private fun <T : StarFlightResponse> callOnSuccess(callback: StarFlightCallback<T>?, response: T?) {
        if (callback == null) {
            return
        }
        callback.onSuccess(response!!)
    }

    /**
     * Calls the onFailure method of the supplied callback if the callback is not null
     */
    private fun callOnFailure(
        callback: StarFlightCallback<out StarFlightResponse>?,
        message: String,
        t: Throwable
    ) {
        if (callback == null) {
            return
        }

        callback.onFailure(message, t)
    }
}
