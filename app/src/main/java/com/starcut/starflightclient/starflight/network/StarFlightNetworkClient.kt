package com.starcut.starflightclient.starflight.network

import android.text.TextUtils
import android.util.Log
import com.starcut.starflightclient.starflight.callbacks.MessageOpenedResponse
import com.starcut.starflightclient.starflight.callbacks.RegistrationResponse
import com.starcut.starflightclient.starflight.callbacks.UnregistrationResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.util.*

private val LOG_TAG = StarFlightNetworkClient::class.java.name
private const val PUSH_SERVER_URL = "https://starflight.starcloud.us/push"

class StarFlightNetworkClient(private val okHttpClient: OkHttpClient) {

    @Throws(IOException::class)
    fun sendUnregistrationToBackend(
        appId: String,
        clientSecret: String,
        registrationId: String,
        tags: List<String>?
    ): UnregistrationResponse {
        val formBodyBuilder = FormBody.Builder()
            .add("action", "unregister")
            .add("appId", appId)
            .add("clientSecret", clientSecret)
            .add("type", "android")
            .add("token", registrationId)

        if (!tags.isNullOrEmpty()) {
            formBodyBuilder.add("tags", TextUtils.join(",", tags))
        }

        val request = Request.Builder()
            .url(PUSH_SERVER_URL)
            .post(formBodyBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()
        val code = response.code()

        if (code == HttpURLConnection.HTTP_OK) {
            Log.i(LOG_TAG, "Unregistration successful")
            return UnregistrationResponse(UnregistrationResponse.Result.OK)
        } else {
            throw IOException("Unexpected HTTP response code: $code")
        }

    }

    @Throws(IOException::class, JSONException::class)
    fun sendRegistrationTokenToBackend(
        appId: String,
        clientSecret: String,
        token: String,
        tags: List<String>?
    ): RegistrationResponse {

        val formBodyBuilder = FormBody.Builder()
            .add("action", "register")
            .add("appId", appId)
            .add("clientSecret", clientSecret)
            .add("type", "android")
            .add("token", token)

        if (!tags.isNullOrEmpty()) {
            formBodyBuilder.add("tags", TextUtils.join(",", tags))
        }

        val request = Request.Builder()
            .url(PUSH_SERVER_URL)
            .post(formBodyBuilder.build())
            .build()

        val response = okHttpClient.newCall(request).execute()
        val code = response.code()
        val result: RegistrationResponse.Result
        val responseText = response.body()!!.string()

        when (code) {
            HttpURLConnection.HTTP_CREATED -> {
                result = RegistrationResponse.Result.REGISTERED
                Log.i(LOG_TAG, "Registered push client")
            }
            HttpURLConnection.HTTP_OK -> {
                result = RegistrationResponse.Result.REFRESHED
                Log.i(LOG_TAG, "Push client registration refreshed")
            }
            else -> throw IOException("Unexpected HTTP response code: $code, response text: $responseText")
        }

        val json = JSONObject(responseText)
        val clientUuid = UUID.fromString(json.getString("clientUuid"))

        return RegistrationResponse(clientUuid, result)
    }

    @Throws(IOException::class)
    fun markMessageOpened(
        appId: String,
        registrationId: String,
        clientSecret: String,
        messageUuid: UUID
    ): MessageOpenedResponse {
        val formBody = FormBody.Builder()
            .add("action", "message_opened")
            .add("appId", appId)
            .add("clientSecret", clientSecret)
            .add("type", "android")
            .add("token", registrationId)
            .add("uuid", messageUuid.toString())
            .build()

        val request = Request.Builder()
            .post(formBody)
            .url(PUSH_SERVER_URL)
            .build()

        val response = okHttpClient.newCall(request).execute()

        val code = response.code()

        if (code != HttpURLConnection.HTTP_OK) {
            throw IOException("Unexpected HTTP response code: " + code + ", response text: " + response.message())
        }

        return MessageOpenedResponse(MessageOpenedResponse.Result.OK)
    }

}
