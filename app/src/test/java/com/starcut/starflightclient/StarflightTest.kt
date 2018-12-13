package com.starcut.starflightclient

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import com.starcut.starflightclient.starflight.Starflight
import com.starcut.starflightclient.starflight.callbacks.RegistrationResponse
import com.starcut.starflightclient.starflight.callbacks.StarFlightCallback
import com.starcut.starflightclient.starflight.callbacks.UnregistrationResponse
import com.starcut.starflightclient.starflight.network.StarFlightNetworkClient

import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.awaitility.Awaitility.await
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.gms.Shadows
import org.robolectric.shadows.gms.common.ShadowGoogleApiAvailability
import java.util.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class StarflightTest {

    companion object {

        val CLIENT_UUID = UUID.randomUUID()
        val APP_ID = "appID"
        val CLIENT_SECRET = "secret"
        val TOKEN = "token"
        val SENDER_ID = "senderID"
        val REGISTRATION_ID = "registrationID"
    }

    @MockK
    lateinit var firebaseMessaging : FirebaseMessaging

    @Before
    fun setup(){
        var context : Context = ApplicationProvider.getApplicationContext()
        var shadowGoogleApiAvailability : ShadowGoogleApiAvailability = Shadows.shadowOf(GoogleApiAvailability.getInstance());
        shadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
        FirebaseApp.initializeApp(context)
        MockKAnnotations.init(this, relaxUnitFun = true)

        mockkConstructor(StarFlightNetworkClient::class)
        var registrationResponse = RegistrationResponse(CLIENT_UUID, RegistrationResponse.Result.REGISTERED)
        every {anyConstructed<StarFlightNetworkClient>().sendRegistrationTokenToBackend(APP_ID, CLIENT_SECRET, TOKEN, any())} returns registrationResponse
        var unregistrationResponse = UnregistrationResponse(UnregistrationResponse.Result.OK)
        every {anyConstructed<StarFlightNetworkClient>().sendUnregistrationToBackend(APP_ID, CLIENT_SECRET, REGISTRATION_ID, any())} returns unregistrationResponse
        assert(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationProvider.getApplicationContext()) == ConnectionResult.SUCCESS)

        var taskMock = TaskMock()
        taskMock.rezult = object : InstanceIdResult{
            override fun getId(): String {
                Log.i("setup", "Registration ID")
                return REGISTRATION_ID
            }

            override fun getToken(): String {
                Log.i("setup", "Token ID")
                return TOKEN
            }
        }

        mockkStatic(FirebaseInstanceId::class)
        every {FirebaseInstanceId.getInstance().instanceId} returns taskMock

        mockkStatic(FirebaseMessaging::class)
        every {FirebaseMessaging.getInstance().setAutoInitEnabled(true)} returns mockk()
    }

    var callbackCalled = false

    @Test
    fun testRegister(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        val appContext : Context = ApplicationProvider.getApplicationContext()

        var callback = (object : StarFlightCallback<RegistrationResponse> {
            override fun onSuccess(result: RegistrationResponse){
                callbackCalled = true
            }

            override fun onFailure(message: String, t: Throwable){
                println(message)
                println(t.message)
                Assert.fail()
            }
        })
        Starflight.register(appContext, callback)

        await().atMost(5, TimeUnit.SECONDS).until{callbackCalled}
    }
}