package com.starcut.starflightclient.starflight.logic

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
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
private const val KEY_VERSION = 1
private const val PROPERTY_CLIENT_UUID = "client_uuid_$KEY_VERSION"
private const val PROPERTY_LAST_SENT_TOKEN = "last_sent_token_$KEY_VERSION"
private const val PROPERTY_LAST_REGISTRATION_TIME = "last_registration_time_$KEY_VERSION"
private const val PROPERTY_REGISTERED_TAGS = "registered_tags_$KEY_VERSION"
private const val PROPERTY_OPENED_MESSAGES = "opened_messages_$KEY_VERSION"
private const val MAX_STORED_READ_MESSAGES_UUIDS = 100

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
    fun getRegisteredTags(context: Context): List<String> {
        val preferences = getStarFlightPreferences(context)
        val registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null)
        return registeredTags?.split(",") ?: emptyList()
    }

    /**
     * Refreshes the current StarFlight registration if needed. It is advisable to call this method every time your application starts.
     */
    fun refreshRegistration(context: Context) {
        if (!isRegistered(context)) {
            throw IllegalStateException("Not registered")
        }

        val preferences = getStarFlightPreferences(context)
        val registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null)
        val tags = registeredTags?.split(",")
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
        val registrationId = getFirebaseToken(context)

        if (registrationId == null) {
            val response = UnregistrationResponse(UnregistrationResponse.Result.NOT_REGISTERED)
            callOnSuccess(callback, response)
            return
        }

        var response: UnregistrationResponse? = null
        try {
            runBlocking(Dispatchers.IO) {
                if (!tags.isNullOrEmpty()) {
                    // only unregister the specified tags
                    // Unregister tags from server
                    response = networkClient.sendUnregistrationToBackend(appId, clientSecret, registrationId, tags)
                    removeTagsFromStorage(context, tags)
                }
                else {
                    response = networkClient.sendUnregistrationToBackend(appId, clientSecret, registrationId, null)
                    FirebaseMessaging.getInstance().isAutoInitEnabled = false
                    FirebaseInstanceId.getInstance().deleteInstanceId()
                    removeRegistrationFromStorage(context)
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

        val preferences = getStarFlightPreferences(context)
        val lastSentToken = preferences.getString(PROPERTY_LAST_SENT_TOKEN, "")
        val registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null)
        val shouldSend: Boolean

        shouldSend = token != lastSentToken ||
            (registeredTags != if(sortedTags == null) null else TextUtils.join(",", sortedTags ))

        if (shouldSend) {
            var response: RegistrationResponse? = null
            try{
                runBlocking(Dispatchers.IO) {
                    response = networkClient.sendRegistrationTokenToBackend(appId, clientSecret, token, sortedTags)
                    storeRegistration(context, token, sortedTags, response!!.clientUuid)
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
        else {
            val response =
                RegistrationResponse(getClientUuid(context)!!, RegistrationResponse.Result.ALREADY_REGISTERED)
            callOnSuccess(callback, response)
            Log.i(LOG_TAG, "already registered and refreshing was not necessary")
        }
    }

    /**
     * Gets the currently active firebase token
     * @return the last token registered on starflight, or null if none exists
     */
    private fun getFirebaseToken(context : Context): String? {
        return getStarFlightPreferences(context).getString(PROPERTY_LAST_SENT_TOKEN, null)
    }

    /**
     * Gets the client UUID of the current registration
     * @param context
     * @return the client UUID, or null if the app is not registered for notifications
     */
    fun getClientUuid(context: Context): UUID? {
        val prefs = getStarFlightPreferences(context)
        val uuid = prefs.getString(PROPERTY_CLIENT_UUID, null)
        return if (uuid == null) null else UUID.fromString(uuid)
    }

    private fun getStarFlightPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(StarFlightClient::class.java.simpleName, Context.MODE_PRIVATE)
    }

    private fun storeRegistration(context: Context, token: String, sortedTags: List<String>?, clientUuid: UUID) {
        val prefs = getStarFlightPreferences(context)
        Log.i(LOG_TAG, "Saving Firebase registration token $token")
        val editor = prefs.edit()
        editor.putString(PROPERTY_LAST_SENT_TOKEN, token)
        editor.putLong(PROPERTY_LAST_REGISTRATION_TIME, System.currentTimeMillis())
        editor.putString(PROPERTY_REGISTERED_TAGS, if(sortedTags != null) TextUtils.join(",", sortedTags) else null)
        editor.putString(PROPERTY_CLIENT_UUID, clientUuid.toString())
        editor.apply()
    }

    private fun removeRegistrationFromStorage(context: Context) {
        val prefs = getStarFlightPreferences(context)
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }

    private fun removeTagsFromStorage(context: Context, tags: List<String>?) {
        val prefs = getStarFlightPreferences(context)

        if (!tags.isNullOrEmpty()) {
            val previousTags = prefs.getString(PROPERTY_REGISTERED_TAGS, "").split(",").toMutableList()
            for (tag in tags) {
                previousTags.remove(tag)
            }

            val editor = prefs.edit()
            editor.putString(PROPERTY_REGISTERED_TAGS, TextUtils.join(",", previousTags))
            editor.apply()
        } else {
            // no tags specified, we remove them all
            val editor = prefs.edit()
            editor.remove(PROPERTY_REGISTERED_TAGS)
            editor.apply()
        }
    }

    /**
     * Tells if this app is currently registered for notifications
     */
    fun isRegistered(context : Context): Boolean {
        return getFirebaseToken(context) != null
    }

    /**
     * Tells if the opening of the message with the supplied UUID has already been recorded
     */
    private fun isMessageOpened(context: Context, messageUuid: UUID): Boolean {
        val prefs = getStarFlightPreferences(context)
        return prefs.getString(PROPERTY_OPENED_MESSAGES, "")!!.contains(messageUuid.toString())
    }

    /**
     * Stores that the opening of the message with the supplied UUID has been recorded
     */
    private fun storeMessageOpened(context: Context, messageUuid: UUID) {
        val prefs = getStarFlightPreferences(context)
        val editor = prefs.edit()
        var openedMessageUuids = Arrays.asList(
            *prefs.getString(
                PROPERTY_OPENED_MESSAGES,
                ""
            )!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        )

        if (!openedMessageUuids.contains(messageUuid.toString())) {
            if (openedMessageUuids.size > MAX_STORED_READ_MESSAGES_UUIDS) {
                openedMessageUuids = openedMessageUuids.subList(1, openedMessageUuids.size)
            }

            val newValue =
                TextUtils.join(",", openedMessageUuids) + (if (openedMessageUuids.size == 0) "" else ",") + messageUuid
            editor.putString(PROPERTY_OPENED_MESSAGES, newValue)
        }

        editor.apply()
    }

    fun markMessageAsRead(context: Context, messageUuid: UUID, callback: StarFlightCallback<MessageOpenedResponse>) {
        if (isMessageOpened(context, messageUuid)) {
            callOnSuccess(callback, MessageOpenedResponse(MessageOpenedResponse.Result.ALREADY_OPENED))
            return
        }

        try {
            runBlocking(Dispatchers.IO){
                val registrationId = getFirebaseToken(context)
                networkClient.markMessageOpened(appId, clientSecret, registrationId!!, messageUuid)
                storeMessageOpened(context, messageUuid)
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
