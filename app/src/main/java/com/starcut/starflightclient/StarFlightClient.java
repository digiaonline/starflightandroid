package com.starcut.starflightclient;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class StarFlightClient
{
	public static final String TEXT_KEY = "text";
	public static final String URL_KEY = "url";
	public static final String SUBJECT_KEY = "subject";

	private static final String PUSH_SERVER_URL = "https://starflight.starcloud.us/push";

	private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	/**
	 * How frequently registrations should be refreshed in milliseconds
	 */
	private static final long REGISTRATION_REFRESH_INTERVAL = 1000 * 60 * 60 * 24 * 10; // 10 days

	private static final String LOG_TAG = "StarFlight Push Client";
	private static final int KEY_VERSION = 1;
	private static final String PROPERTY_REGISTRATION_ID = "registration_id_" + KEY_VERSION;
	private static final String PROPERTY_CLIENT_UUID = "client_uuid_" + KEY_VERSION;
	private static final String PROPERTY_LAST_SENT_REG_ID = "last_sent_registration_id_" + KEY_VERSION;
	private static final String PROPERTY_LAST_REGISTRATION_TIME = "last_registration_time_" + KEY_VERSION;
	private static final String PROPERTY_REGISTERED_TAGS = "registered_tags_" + KEY_VERSION;
	private static final String PROPERTY_OPENED_MESSAGES = "opened_messages_" + KEY_VERSION;

	private static final Handler CALLBACK_HANDLER = new Handler(Looper.getMainLooper());

	private final String senderId;
	private final String appId;
	private final String clientSecret;

	/**
	 * Constructs a new StarFlight Client with the supplied GCM sender id, StarFlight app id and StarFlight client secret
	 * @param senderId the GCM sender id
	 * @param appId the StarFlight app id
	 * @param clientSecret the StarFlight client secret
	 */
	public StarFlightClient(String senderId, String appId, String clientSecret)
	{
		this.senderId = senderId;
		this.appId = appId;
		this.clientSecret = clientSecret;
	}

	/**
	 * Registers for push notifications
	 * @param callback callback that will be notified of success or failure
	 */
	public void register(Activity activity, StarFlightCallback<RegistrationResponse> callback)
	{
		register(activity, null, callback);
	}

	/**
	 * Refreshes the current StarFlight registration if needed. It is advisable to call this method every time your application starts.
	 */
	public void refreshRegistration(Activity activity)
	{
		if (!isRegistered(activity))
		{
			throw new IllegalStateException("Not registered");
		}

		final SharedPreferences preferences = getStarFlightPreferences(activity);
		final String registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, null);
		List<String> tags = registeredTags == null ?
                Collections.<String>emptyList() :
                Arrays.asList(registeredTags.split(","));
		Log.d("NOTIF", "Registered flags to refresh " + tags);
		register(activity, tags, null);
	}

	/**
	 * <p>Registers for push notifications with the supplied list of tags.</p>
	 *
	 * <p>If a registration already exists, its tags will be replaced with the supplied values.</p>
	 * @param tags the tags
	 * @param callback callback that will be notified of success or failure
	 */
	public void register(Activity activity, List<String> tags, StarFlightCallback<RegistrationResponse> callback)
	{
		if (checkPlayServices(activity))
		{
            Context context = activity.getApplicationContext();
			String registrationId = getRegistrationId(context);
			if (registrationId == null)
			{
				getRegistrationIdInBackground(context, tags, callback);
			}
			else
			{
				sendRegistrationIdIfNeeded(context, tags, callback);
			}
		}
	}

	/**
	 * Removes an existing registration
	 * @param activity
	 * @param callback callback that will be notified of success or failure
	 */
    public void unregister(Activity activity, StarFlightCallback<UnregistrationResponse> callback)
    {
        unregister(activity, null, callback);
    }

	/**
	 * Removes the supplied list of tags from an existing registration
	 * @param activity
	 * @param tags
	 * @param callback callback that will be notified of success or failure
	 */
	public void unregister(Activity activity, List<String> tags, StarFlightCallback<UnregistrationResponse> callback)
	{
		if (checkPlayServices(activity))
		{
            Context context = activity.getApplicationContext();
			String registrationId = getRegistrationId(context);

			if (registrationId == null)
			{
                UnregistrationResponse response = new UnregistrationResponse(UnregistrationResponse.Result.NOT_REGISTERED);
				callOnSuccess(callback, response);
				return;
			}

			sendUnregistrationInBackground(context, tags, callback);
		}
	}

	@SuppressWarnings("unchecked")
	private void sendUnregistrationInBackground(final Context context, List<String> tags, final StarFlightCallback<UnregistrationResponse> callback)
	{
		final String registrationId = getRegistrationId(context);

        new AsyncTask<List<String>, Void, Void>()
		{
			@Override
			protected Void doInBackground(List<String> ... params)
			{
				List<String> tags = params[0];
                UnregistrationResponse response = null;

				if (tags != null && tags.size() > 0)
				{
					// only unregister the specified tags
					// Unregister tags from server

                    try
                    {
                        response = sendUnregistrationToBackend(registrationId, tags);
                        removeTagsFromStorage(context, tags);
                    }
                    catch (IOException e)
                    {
                        callOnFailure(callback, "Unregistration failed: " + e.getMessage(), e);
                    }
				}
				else
				{
					// Unregister completely
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);

					try
					{
                        response = sendUnregistrationToBackend(registrationId, null);
                        removeRegistrationFromStorage(context);
                        gcm.unregister();
					}
					catch (IOException ex)
					{
						callOnFailure(callback, "Unregistration failed: " + ex.getMessage(), ex);
					}
				}

                if (response != null)
                {
					callOnSuccess(callback, response);
                }

                return null;
			}
		}.execute(tags);
	}

	@SuppressWarnings("unchecked")
	private void sendRegistrationIdIfNeeded(final Context context, List<String> tags, final StarFlightCallback<RegistrationResponse> callback)
	{
		if(tags == null){
			tags = Collections.<String>emptyList();
		}
		Collections.sort(tags);

		final SharedPreferences preferences = getStarFlightPreferences(context);
		final String lastSentId = preferences.getString(PROPERTY_LAST_SENT_REG_ID, "");
		final long lastRegistrationTime = preferences.getLong(PROPERTY_LAST_REGISTRATION_TIME, -1);
		String registeredTags = preferences.getString(PROPERTY_REGISTERED_TAGS, "");
		final String registrationId = getRegistrationId(context);
		final boolean shouldSend;

		if (lastRegistrationTime == -1 || System.currentTimeMillis() - lastRegistrationTime > REGISTRATION_REFRESH_INTERVAL)
		{
			shouldSend = true;
		}
		else if (!lastSentId.equals(registrationId))
		{
			shouldSend = true;
		}
		else if (!registeredTags.equals(join(tags, ",")))
		{
			shouldSend = true;
		}
		else
		{
			shouldSend = false;
		}

		if (shouldSend)
		{
			new AsyncTask<List<String>, Void, Void>()
			{
				@Override
				protected Void doInBackground(List<String> ... params)
				{
					List<String> tags = null;
					if (params.length > 0)
					{
						tags = params[0];
					}

                    RegistrationResponse response = null;

                    try
                    {
                        response = sendRegistrationIdToBackend(registrationId, tags);
						storeRegistration(context, registrationId, tags, response.getClientUuid());
                    }
					catch (IOException e)
                    {
						callOnFailure(callback, "Failed to send registration id to StarFlight: " + e.getMessage(), e);
                    }
                    catch (JSONException e)
                    {
						callOnFailure(callback, "Failed to parse server response: " + e.getMessage(), e);
                    }

                    if (response != null)
                    {
						callOnSuccess(callback, response);
                    }

                    return null;
				}
			}.execute(tags, null, null);
		}
		else
		{
			RegistrationResponse response = new RegistrationResponse(getClientUuid(context), RegistrationResponse.Result.ALREADY_REGISTERED);
			callOnSuccess(callback, response);
			Log.i(LOG_TAG, "already registered and refreshing was not necessary");
		}
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private static boolean checkPlayServices(Activity activity)
	{
		int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);
		if (resultCode != ConnectionResult.SUCCESS)
		{
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
			{
				GooglePlayServicesUtil.getErrorDialog(resultCode, activity, PLAY_SERVICES_RESOLUTION_REQUEST).show();
			}
			else
			{
				Log.e(LOG_TAG, "This device is not supported.");
			}

			return false;
		}
		return true;
	}

	/**
	 * Gets the currently active GCM registration id
	 * @return the GCM registration id, or null if none exists
	 */
	private String getRegistrationId(Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		return prefs.getString(PROPERTY_REGISTRATION_ID, null);
	}

	/**
	 * Gets the client UUID of the current registration
	 * @param context
	 * @return the client UUID, or null if the app is not registered for notifications
	 */
	public UUID getClientUuid(Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		String uuid = prefs.getString(PROPERTY_CLIENT_UUID, null);
		return (uuid == null ? null : UUID.fromString(uuid));
	}

	private SharedPreferences getStarFlightPreferences(Context context)
	{
		return context.getSharedPreferences(StarFlightClient.class.getSimpleName(), Context.MODE_PRIVATE);
	}

	@SuppressWarnings("unchecked")
	private void getRegistrationIdInBackground(final Context context, List<String> tags, final StarFlightCallback<RegistrationResponse> callback)
	{
		new AsyncTask<List<String>, Void, Void>()
		{
			@Override
			protected Void doInBackground(List<String> ... params)
			{
				try
				{
					GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
					String registrationId = gcm.register(senderId);

					List<String> tags = null;
					if (params.length > 0)
					{
						tags = params[0];
					}

                    RegistrationResponse response = sendRegistrationIdToBackend(registrationId, tags);
					storeRegistration(context, registrationId, tags, response.getClientUuid());
					callOnSuccess(callback, response);
				}
				catch (IOException ex)
				{
					callOnFailure(callback, "Registration failed: " + ex.getMessage(), ex);
				}
                catch (JSONException ex)
                {
					callOnFailure(callback, "Failed to parse registration response: " + ex.getMessage(), ex);
                }

                return null;
			}
		}.execute(tags, null, null);
	}

	private UnregistrationResponse sendUnregistrationToBackend(String registrationId, List<String> tags) throws IOException
	{
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(PUSH_SERVER_URL);

        List<NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new BasicNameValuePair("action", "unregister"));
        nameValuePairs.add(new BasicNameValuePair("appId", appId));
        nameValuePairs.add(new BasicNameValuePair("clientSecret", clientSecret));
        nameValuePairs.add(new BasicNameValuePair("type", "android"));
        nameValuePairs.add(new BasicNameValuePair("token", registrationId));

        if (tags != null && tags.size() > 0)
        {
            nameValuePairs.add(new BasicNameValuePair("tags", join(tags, ",")));
        }

        post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        HttpResponse response = client.execute(post);
        int code = response.getStatusLine().getStatusCode();

        if (code == HttpStatus.SC_OK)
        {
            Log.i(LOG_TAG, "Unregistration successful");
        }
        else
        {
            throw new IOException("Unexpected HTTP response code: " + code);
        }

		return new UnregistrationResponse(UnregistrationResponse.Result.OK);
	}

	@VisibleForTesting
	public RegistrationResponse sendRegistrationIdToBackend(String registrationId, List<String> tags) throws IOException, JSONException
	{
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(PUSH_SERVER_URL);

        List<NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new BasicNameValuePair("action", "register"));
        nameValuePairs.add(new BasicNameValuePair("appId", appId));
        nameValuePairs.add(new BasicNameValuePair("clientSecret", clientSecret));
        nameValuePairs.add(new BasicNameValuePair("type", "android"));
        nameValuePairs.add(new BasicNameValuePair("token", registrationId));

        if (tags != null && tags.size() > 0)
        {
            nameValuePairs.add(new BasicNameValuePair("tags", join(tags, ",")));
        }

        post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

        HttpResponse response = client.execute(post);
        int code = response.getStatusLine().getStatusCode();
        final RegistrationResponse.Result result;
		String responseText = EntityUtils.toString(response.getEntity());

        if (code == HttpStatus.SC_CREATED)
        {
            result = RegistrationResponse.Result.REGISTERED;
            Log.i(LOG_TAG, "Registered push client");
        }
        else if (code == HttpStatus.SC_OK)
        {
            result = RegistrationResponse.Result.REFRESHED;
            Log.i(LOG_TAG, "Push client registration refreshed");
        }
        else
        {
            throw new IOException("Unexpected HTTP response code: " + code + ", response text: " + responseText);
        }

        JSONObject json = new JSONObject(responseText);
		UUID clientUuid = UUID.fromString(json.getString("clientUuid"));

        return new RegistrationResponse(clientUuid, result);
	}

	private void storeRegistration(Context context, String registrationId, List<String> tags, UUID clientUuid)
	{
		if(tags == null){
			tags = Collections.<String>emptyList();
		}
		final SharedPreferences prefs = getStarFlightPreferences(context);
		Log.i(LOG_TAG, "Saving GCM registration id " + registrationId);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(PROPERTY_REGISTRATION_ID, registrationId);
        editor.putString(PROPERTY_LAST_SENT_REG_ID, registrationId);
		editor.putLong(PROPERTY_LAST_REGISTRATION_TIME, System.currentTimeMillis());
		editor.putString(PROPERTY_REGISTERED_TAGS, join(tags, ","));
		editor.putString(PROPERTY_CLIENT_UUID, clientUuid.toString());
		editor.apply();
	}

	private void removeRegistrationFromStorage(Context context)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.apply();
	}

	private void removeTagsFromStorage(Context context, List<String> tags)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);

		if (tags != null && tags.size() > 0)
		{
			List<String> previousTags = new ArrayList<>(Arrays.asList(prefs.getString(PROPERTY_REGISTERED_TAGS, "").split(",")));

			for (String tag : tags)
			{
				previousTags.remove(tag);
			}

			SharedPreferences.Editor editor = prefs.edit();
			editor.putString(PROPERTY_REGISTERED_TAGS, join(previousTags, ","));
			editor.apply();
		}
		else
		{
			// no tags specified, we remove them all
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(PROPERTY_REGISTERED_TAGS);
			editor.apply();
		}
	}

	private static String join(List<String> list, String separator)
	{
		if (list != null && list.size() > 0)
		{
			StringBuilder joined = new StringBuilder();

			for (int i = 0; i < list.size(); i++)
			{
				joined.append(list.get(i));

				if (i < list.size() - 1)
				{
					joined.append(separator);
				}
			}

			return joined.toString();
		}

		return null;
	}

	/**
	 * Tells if this app is currently registered for notifications
	 */
	public boolean isRegistered(Context context)
	{
		return getRegistrationId(context) != null;
	}

	/**
	 * Records that the message with the supplied UUID was opened by the user
	 */
	public void messageOpened(final Context context, final UUID messageUuid, final StarFlightCallback<MessageOpenedResponse> callback)
	{
		if (isMessageOpened(context, messageUuid))
		{
			callOnSuccess(callback, new MessageOpenedResponse(MessageOpenedResponse.Result.ALREADY_OPENED));
			return;
		}

		final String registrationId = getRegistrationId(context);

		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void ... params)
			{
				try
				{
					HttpClient client = new DefaultHttpClient();
					HttpPost post = new HttpPost(PUSH_SERVER_URL);

					List<NameValuePair> nameValuePairs = new ArrayList<>();
					nameValuePairs.add(new BasicNameValuePair("action", "message_opened"));
					nameValuePairs.add(new BasicNameValuePair("appId", appId));
					nameValuePairs.add(new BasicNameValuePair("clientSecret", clientSecret));
					nameValuePairs.add(new BasicNameValuePair("type", "android"));
					nameValuePairs.add(new BasicNameValuePair("token", registrationId));
					nameValuePairs.add(new BasicNameValuePair("uuid", messageUuid.toString()));
					post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

					HttpResponse response = client.execute(post);
					int code = response.getStatusLine().getStatusCode();

					if (code != HttpStatus.SC_OK)
					{
						throw new IOException("Unexpected HTTP response code: " + code + ", response text: " + EntityUtils.toString(response.getEntity()));
					}

					storeMessageOpened(context, messageUuid);
					callOnSuccess(callback, new MessageOpenedResponse(MessageOpenedResponse.Result.OK));
				}
				catch (IOException ex)
				{
					callOnFailure(callback, "Recording message open failed: " + ex.getMessage(), ex);
				}

				return null;
			}
		}.execute(null, null, null);
	}

	/**
	 * Tells if the opening of the message with the supplied UUID has already been recorded
	 */
	private boolean isMessageOpened(Context context, UUID messageUuid)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		return Arrays.asList(prefs.getString(PROPERTY_OPENED_MESSAGES, "").split(",")).contains(messageUuid.toString());
	}

	/**
	 * Stores that the opening of the message with the supplied UUID has been recorded
	 */
	private void storeMessageOpened(Context context, UUID messageUuid)
	{
		final SharedPreferences prefs = getStarFlightPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		List<String> openedMessageUuids = Arrays.asList(prefs.getString(PROPERTY_OPENED_MESSAGES, "").split(","));

		if (!openedMessageUuids.contains(messageUuid.toString()))
		{
			String newValue = join(openedMessageUuids, ",") + (openedMessageUuids.size() == 0 ? "" : ",") + messageUuid;
			editor.putString(PROPERTY_OPENED_MESSAGES, newValue);
		}

		editor.apply();
	}

	/**
	 * Calls the onSuccess method of the supplied callback if the callback is not null
	 */
	private static <T extends StarFlightResponse> void callOnSuccess(final StarFlightCallback<T> callback, final T response)
	{
		if (callback == null)
		{
			return;
		}

		CALLBACK_HANDLER.post(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onSuccess(response);
			}
		});
	}

	/**
	 * Calls the onFailure method of the supplied callback if the callback is not null
	 */
	private static void callOnFailure(final StarFlightCallback<? extends StarFlightResponse> callback, final String message, final Throwable t)
	{
		if (callback == null)
		{
			return;
		}

		CALLBACK_HANDLER.post(new Runnable()
		{
			@Override
			public void run()
			{
				callback.onFailure(message, t);
			}
		});
	}
}
