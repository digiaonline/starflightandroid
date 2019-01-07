package com.starcut.starflightclient.starflight.logic

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import java.util.*

private const val KEY_VERSION = 1
private const val PROPERTY_CLIENT_UUID = "client_uuid_$KEY_VERSION"
private const val PROPERTY_LAST_SENT_TOKEN = "last_sent_token_$KEY_VERSION"
private const val PROPERTY_LAST_REGISTRATION_TIME = "last_registration_time_$KEY_VERSION"
private const val PROPERTY_REGISTERED_TAGS = "registered_tags_$KEY_VERSION"
private const val PROPERTY_OPENED_MESSAGES = "opened_messages_$KEY_VERSION"
private const val MAX_STORED_READ_MESSAGES_UUIDS = 100

private val LOG_TAG = StarflightPreferences::class.java.name

class StarflightPreferences {

    private fun getStarFlightPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(StarFlightClient::class.java.simpleName, Context.MODE_PRIVATE)
    }

    fun getRegisteredTags(context: Context): List<String> {
        val preferences = getStarFlightPreferences(context)
        val registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null)
        return registeredTags?.split(",") ?: emptyList()
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

    /**
     * Gets the currently active firebase token
     * @return the last token registered on starflight, or null if none exists
     */
    fun getFirebaseToken(context : Context): String? {
        return getStarFlightPreferences(context).getString(PROPERTY_LAST_SENT_TOKEN, null)
    }

    fun storeRegistration(context: Context, token: String, sortedTags: List<String>?, clientUuid: UUID) {
        val prefs = getStarFlightPreferences(context)
        Log.i(LOG_TAG, "Saving Firebase registration token $token")
        val editor = prefs.edit()
        editor.putString(PROPERTY_LAST_SENT_TOKEN, token)
        editor.putLong(PROPERTY_LAST_REGISTRATION_TIME, System.currentTimeMillis())
        editor.putString(PROPERTY_REGISTERED_TAGS, if(sortedTags != null) TextUtils.join(",", sortedTags) else null)
        editor.putString(PROPERTY_CLIENT_UUID, clientUuid.toString())
        editor.apply()
    }

    fun removeRegistrationFromStorage(context: Context) {
        val prefs = getStarFlightPreferences(context)
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }

    fun removeTagsFromStorage(context: Context, tags: List<String>?) {
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
     * Tells if the opening of the message with the supplied UUID has already been recorded
     */
    fun isMessageOpened(context: Context, messageUuid: UUID): Boolean {
        val prefs = getStarFlightPreferences(context)
        return prefs.getString(PROPERTY_OPENED_MESSAGES, "")!!.contains(messageUuid.toString())
    }

    /**
     * Stores that the opening of the message with the supplied UUID has been recorded
     */
    fun storeMessageOpened(context: Context, messageUuid: UUID) {
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

    fun getLastSentToken(context : Context) : String {
        val prefs = getStarFlightPreferences(context)
        return prefs.getString(PROPERTY_LAST_SENT_TOKEN, "")
    }

}