package com.starcut.starflightclient.starflight

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.starcut.starflightclient.starflight.callbacks.MessageOpenedResponse
import com.starcut.starflightclient.starflight.callbacks.RegistrationResponse
import com.starcut.starflightclient.starflight.callbacks.StarFlightCallback
import com.starcut.starflightclient.starflight.callbacks.UnregistrationResponse
import com.starcut.starflightclient.starflight.logic.StarFlightClient
import com.starcut.starflightclient.starflight.network.StarFlightNetworkClient
import okhttp3.OkHttpClient
import java.util.*

object Starflight {

    private const val PLAY_SERVICES_RESOLUTION_REQUEST = 9000

    private var starflightClient: StarFlightClient? = null

    /**
     * Call init function before using this class
     *
     * @param client: OkHttpClient if used in other parts of the app
     * @param senderId: firebase sender id
     * @param appId: starflight appId
     * @param clientSecret: starflight client secret
     */
    fun init(client: OkHttpClient, senderId: String, appId: String, clientSecret: String) {
        val starflightNetworkClient = StarFlightNetworkClient(client)
        starflightClient = StarFlightClient(starflightNetworkClient, senderId, appId, clientSecret)
    }

    /**
     * Call init function before using this class
     * This will init a OkHttpClient for communicating with Starflight. Provide your own if you have already one
     *
     * @param senderId: firebase sender id
     * @param appId: starflight appId
     * @param clientSecret: starflight client secret
     */
    fun init(senderId: String, appId: String, clientSecret: String) {
        init(OkHttpClient.Builder().build(), senderId, appId, clientSecret)
    }

    /**
     * Refreshes the current StarFlight registration if needed.
     */
    fun refreshRegistration(context: Context) {
        starflightClient!!.refreshRegistration(context)
    }

    /**
     * Registers for push notifications
     * @param callback callback that will be notified of success or failure
     */
    fun register(activity: Activity, callback: StarFlightCallback<RegistrationResponse>) {
        try {
            register(activity as Context, callback)
        } catch (e: Exception) {
            handlePlayServicesError(activity)
        }
    }

    /**
     * Registers for push notifications
     * @param callback callback that will be notified of success or failure
     */
    @Throws(Exception::class)
    fun register(context: Context, callback: StarFlightCallback<RegistrationResponse>) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            starflightClient!!.register(context, null, callback)
        } else {
            throw Exception("Google play missing")
        }
    }

    fun register(activity: Activity, tags: List<String>, callback: StarFlightCallback<RegistrationResponse>) {
        try {
            register(activity as Context, tags, callback)
        } catch (e: Exception) {
            handlePlayServicesError(activity)
        }

    }

    @Throws(Exception::class)
    fun register(context: Context, tags: List<String>, callback: StarFlightCallback<RegistrationResponse>) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            starflightClient!!.register(context, null, callback)
        }
    }

    fun unregister(activity: Activity, callback: StarFlightCallback<UnregistrationResponse>) {
        try {
            starflightClient!!.unregister(activity as Context, callback)
        } catch (e: Exception) {
            handlePlayServicesError(activity)
        }

    }

    @Throws(Exception::class)
    fun unregister(context: Context, callback: StarFlightCallback<UnregistrationResponse>) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            starflightClient!!.unregister(context, callback)
        } else {
            throw Exception("Google play missing")
        }
    }

    fun unregister(activity: Activity, tags: List<String>, callback: StarFlightCallback<UnregistrationResponse>) {
        try {
            starflightClient!!.unregister(activity as Context, tags, callback)
        } catch (e: Exception) {
            handlePlayServicesError(activity)
        }
    }

    @Throws(Exception::class)
    fun unregister(context: Context, tags: List<String>, callback: StarFlightCallback<UnregistrationResponse>) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            starflightClient!!.unregister(context, tags, callback)
        } else {
            throw Exception("Google play missing")
        }
    }

    fun getRegisteredTags(context: Context): List<String> {
        return starflightClient!!.getRegisteredTags(context)
    }

    fun markMessageAsOpened(context: Context, messageUuid: UUID, callback: StarFlightCallback<MessageOpenedResponse>) {
        starflightClient!!.markMessageAsRead(context, messageUuid, callback)
    }

    private fun handlePlayServicesError(activity: Activity) {
        val resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        if (GoogleApiAvailability.getInstance().isUserResolvableError(resultCode)) {
            GoogleApiAvailability.getInstance().getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                .show()
        } else {
            Log.e(Starflight::class.java.name, "This device is not supported.")
        }
    }

}
