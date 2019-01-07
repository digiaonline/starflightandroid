package com.starcut.starflightclient

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import com.google.firebase.messaging.FirebaseMessaging
import com.starcut.starflightclient.starflight.Starflight
import com.starcut.starflightclient.starflight.callbacks.MessageOpenedResponse
import com.starcut.starflightclient.starflight.callbacks.RegistrationResponse
import com.starcut.starflightclient.starflight.callbacks.StarFlightCallback
import com.starcut.starflightclient.starflight.callbacks.UnregistrationResponse
import com.starcut.starflightclient.starflight.network.StarFlightNetworkClient
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.awaitility.Awaitility.await
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.gms.Shadows
import org.robolectric.shadows.gms.common.ShadowGoogleApiAvailability
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, shadows = arrayOf(ShadowGoogleApiAvailability::class))
class StarflightTest {

    companion object {

        val CLIENT_UUID = UUID.randomUUID()
        val APP_ID = "appID"
        val CLIENT_SECRET = "secret"
        val TOKEN = "token"
        val SENDER_ID = "senderID"
        val REGISTRATION_ID = "registrationID"
        val MESSAGE_UUID : UUID = UUID.randomUUID()

        lateinit var shadowGoogleApiAvailability : ShadowGoogleApiAvailability
        lateinit var appContext : Context

        @JvmStatic @BeforeClass
        fun classSetup(){
            shadowGoogleApiAvailability  = Shadows.shadowOf(GoogleApiAvailability.getInstance())

            MockKAnnotations.init(this, relaxUnitFun = true)
            mockkConstructor(StarFlightNetworkClient::class)
            var registrationResponse = RegistrationResponse(CLIENT_UUID, RegistrationResponse.Result.REGISTERED)
            every {anyConstructed<StarFlightNetworkClient>().sendRegistrationTokenToBackend(APP_ID, CLIENT_SECRET, TOKEN, any())} returns registrationResponse
            var unregistrationResponse = UnregistrationResponse(UnregistrationResponse.Result.OK)
            every {anyConstructed<StarFlightNetworkClient>().sendUnregistrationToBackend(APP_ID, CLIENT_SECRET, TOKEN, any())} returns unregistrationResponse
            var markMessageAsReadResponse = MessageOpenedResponse(MessageOpenedResponse.Result.OK)
            every {anyConstructed<StarFlightNetworkClient>().markMessageOpened(APP_ID, CLIENT_SECRET, TOKEN, MESSAGE_UUID)} returns markMessageAsReadResponse

            var taskMock = TaskMock()
            taskMock.mockResult = object : InstanceIdResult {
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
            every {FirebaseInstanceId.getInstance().deleteInstanceId()} returns mockk()

            mockkStatic(FirebaseMessaging::class)
            every {FirebaseMessaging.getInstance().setAutoInitEnabled(any())} returns mockk()
        }
    }

    @MockK
    lateinit var firebaseMessaging : FirebaseMessaging

    var successCallbackCalled = false
    var failureCallbackCalled = false

    @Before
    fun methodSetup(){
        appContext = ApplicationProvider.getApplicationContext()
        shadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SUCCESS);
    }

    fun register(tags: List<String>? = null){
        successCallbackCalled = false
        failureCallbackCalled = false
        var callback = (object : StarFlightCallback<RegistrationResponse> {
            override fun onSuccess(result: RegistrationResponse){
                successCallbackCalled = true
            }

            override fun onFailure(message: String, t: Throwable){
                println(message)
                println(t.message)
                failureCallbackCalled = true
            }
        })

        if(tags == null){
            Starflight.register(appContext, callback)
        }
        else{
            Starflight.register(appContext, tags, callback)
        }
    }

    fun unregister(tags: List<String>? = null){
        successCallbackCalled = false
        failureCallbackCalled = false
        var callback = (object : StarFlightCallback<UnregistrationResponse> {
            override fun onSuccess(result: UnregistrationResponse) {
                successCallbackCalled = true
            }

            override fun onFailure(message: String, t: Throwable){
                println(message)
                println(t.message)
                failureCallbackCalled = true
            }
        })

        if(tags == null){
            Starflight.unregister(appContext, callback)
        }
        else{
            Starflight.unregister(appContext, tags, callback)
        }
    }

    @Test
    fun successfulRegistration(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        register()
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
    }

    @Test
    fun registerWithTags(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        val tags : List<String> = arrayListOf("tag1", "tag2", "tag3")
        register(tags)
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
        //Make sure tags are recorded locally
        assertThat<List<String>>(
            Starflight.getRegisteredTags(appContext),
            Matchers.containsInAnyOrder<String>("tag1", "tag2", "tag3")
        );
    }

    @Test
    fun modifyTags(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        val tags : List<String> = arrayListOf("tag1", "tag2", "tag3")
        register(tags)
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
        val unregisteredTags = arrayListOf("tag1", "tag2")
        try{
            unregister(unregisteredTags)
        }
        catch( e : Exception ){
            Log.e("dfsddsfs", "sdff")
        }
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
        //Make sure tags are recorded locally
        assertThat<List<String>>(
            Starflight.getRegisteredTags(appContext),
            Matchers.contains<String>("tag3")
        );
    }

    @Test
    fun unregisterTest(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        val tags : List<String> = arrayListOf("tag1", "tag2", "tag3")
        register(tags)
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
        unregister()
        await().atMost(5, TimeUnit.SECONDS).until{successCallbackCalled}
        assertThat<List<String>>(
            Starflight.getRegisteredTags(appContext), Matchers.hasSize(0)
        );
    }

    @Test
    fun markMessagesAsOpened(){
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        register()
        var callback = (object : StarFlightCallback<MessageOpenedResponse> {
            override fun onSuccess(result: MessageOpenedResponse) {
                successCallbackCalled = true
            }

            override fun onFailure(message: String, t: Throwable){
                println(message)
                println(t.message)
                failureCallbackCalled = true
            }
        })
        Starflight.markMessageAsOpened(appContext, MESSAGE_UUID, callback)
    }

    @Test
    fun missingGooglePlay() {
        Starflight.init(SENDER_ID, APP_ID, CLIENT_SECRET)
        shadowGoogleApiAvailability.setIsGooglePlayServicesAvailable(ConnectionResult.SERVICE_MISSING)
        try {
            register()
            fail("An exception should have been thrown")
        }
        catch (e: GooglePlayServicesNotAvailableException) {
            //success
        }
        catch(e : Exception){
            fail("wrong type of exception")
        }
    }

    @Test
    fun initNotCalled(){
        try{
            register()
            fail("An exception should have been thrown")
        }
        catch(e : Exception){

        }
    }

}