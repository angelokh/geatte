package com.geatte.android.c2dm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import com.geatte.android.app.Config;
import com.geatte.android.app.DeviceRegistrar;
import com.geatte.android.app.GeatteComing;
import com.geatte.android.app.GeatteDBAdapter;
import com.geatte.android.app.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

/**
 * Broadcast receiver that handles Android Cloud to Data Messaging (AC2DM)
 * messages, initiated by the JumpNote App Engine server and routed/delivered by
 * Google AC2DM servers. The only currently defined message is 'sync'.
 */
public class C2DMReceiver extends C2DMBaseReceiver {
    static final String TAG = C2DMReceiver.class.getSimpleName();
    // Notification title
    public static String NOTIF_TITLE = "geatteid";
    // Notification id
    private static final int NOTIF_CONNECTED = 0;
    // Notification manager to displaying arrived push notifications
    // private NotificationManager	mNotifMan;


    public C2DMReceiver() {
	super(Config.C2DM_SENDER);
	//mNotifMan = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public void onRegistered(Context context, String registrationId) throws java.io.IOException {
	// The registrationId should be send to your applicatioin server.
	// We just log it to the LogCat view
	// We will copy it from there

	if (registrationId != null) {
	    Log.d(Config.LOGTAG_C2DM, "Registration ID arrived: Fantastic!!!");
	    Log.d(Config.LOGTAG_C2DM, registrationId);
	} else {
	    Log.e(Config.LOGTAG_C2DM, "Registration ID is null!!!");
	}

	// TODO if null, donot call REST at all
	DeviceRegistrar.registerWithServer(context, registrationId);

    };

    @Override
    public void onUnregistered(Context context) {
	Log.d(Config.LOGTAG_C2DM, "GeatteApp:onUnregistered() START");
	final SharedPreferences prefs = context.getSharedPreferences(Config.PREFERENCE_KEY, Context.MODE_PRIVATE);
	String registrationId = prefs.getString(Config.PREF_REGISTRATION_ID, null);
	DeviceRegistrar.unregisterWithServer(context, registrationId);
	Log.d(Config.LOGTAG_C2DM, "GeatteApp:onUnregistered() END");
    }

    @Override
    public void onError(Context context, String errorId) {
	Log.d(Config.LOGTAG_C2DM, "GeatteApp:onError() START");
	final SharedPreferences prefs = context.getSharedPreferences(Config.PREFERENCE_KEY, Context.MODE_PRIVATE);
	String registrationId = prefs.getString(Config.PREF_REGISTRATION_ID, null);
	DeviceRegistrar.unregisterWithServer(context, registrationId);
	Toast.makeText(context, "Messaging registration error: " + errorId, Toast.LENGTH_LONG).show();
	Log.d(Config.LOGTAG_C2DM, "GeatteApp:onError() END");
    }

    /*
    @Override
    protected void onMessage(Context context, Intent intent) {
	Log.d(Config.LOGTAG_C2DM, "onMessage: Fantastic!!!");
	Bundle extras = intent.getExtras();
	if (extras != null) {
	    String accountName = extras.getString(Config.C2DM_ACCOUNT_EXTRA);
	    if (accountName != null) {
		Log.d(Config.LOGTAG_C2DM, "Messaging request received for account " + accountName);
	    }
	    String message = extras.getString(Config.C2DM_MESSAGE_EXTRA);
	    if (message != null) {
		Log.d(Config.LOGTAG_C2DM, "Messaging request received for message " + message);
	    }
	    String payload = (String) extras.get(Config.C2DM_MESSAGE_PAYLOAD);
	    if (payload != null) {
		Log.d(Config.LOGTAG_C2DM, "Messaging request received for payload " + payload);
	    }
	    String geatteid = extras.getString(Config.C2DM_MESSAGE_GEATTE_ID);
	    if (geatteid != null) {
		showNotification("Got new geatte id : " + geatteid);
		Log.d(Config.LOGTAG_C2DM, "Messaging request received for geatteid " + geatteid);
	    }
	    // Now do something smart based on the information
	}

    }
     */

    @Override
    public void onMessage(Context context, Intent intent) {
	Bundle extras = intent.getExtras();
	if (extras != null) {

	    String geatteid = extras.getString(Config.C2DM_MESSAGE_GEATTE_ID);
	    if (geatteid != null) {
		//showNotification("Got new geatte message : " + geatteMessage);
		Log.d(Config.LOGTAG_C2DM, "Messaging request received for geatteid " + geatteid);

		//fetch coming message
		DefaultHttpClient client = new DefaultHttpClient();
		//		HttpGet get = new HttpGet(Config.BASE_URL + Config.GEATTE_INFO_GET_URL);

		//		final HttpParams getParams = new BasicHttpParams();
		//		getParams.setParameter(Config.GEATTE_ID_PARAM, geatteid);
		//		get.setParams(getParams);

		//		get.getParams().setParameter(Config.GEATTE_ID_PARAM, geatteid);

		List<NameValuePair> qparams = new ArrayList<NameValuePair>();
		qparams.add(new BasicNameValuePair(Config.GEATTE_ID_PARAM, geatteid));
		GeatteDBAdapter dbHelper = new GeatteDBAdapter(this);

		try {

		    URI uri = URIUtils.createURI("https", "geatte.appspot.com", -1, Config.GEATTE_INFO_GET_URL,
			    URLEncodedUtils.format(qparams, "UTF-8"), null);
		    Log.d(Config.LOGTAG_C2DM, "Sending request to geatte info to url = " + uri.toString());
		    HttpGet httpget = new HttpGet(uri);
		    HttpResponse response = client.execute(httpget);

		    JSONObject JResponse = null;
		    BufferedReader reader = new BufferedReader(
			    new InputStreamReader(
				    response.getEntity().getContent(), "UTF-8"));

		    char[] tmp = new char[2048];
		    StringBuffer body = new StringBuffer();
		    while (true) {
			int cnt = reader.read(tmp);
			if (cnt <= 0) {
			    break;
			}
			body.append(tmp, 0, cnt);
		    }
		    try {
			JResponse = new JSONObject(body.toString());
		    } catch (JSONException e) {
			Log.e(Config.LOGTAG, " " + TAG, e);
		    }

		    dbHelper.open();

		    if (JResponse != null) {
			String geatteId = JResponse.getString(Config.GEATTE_ID_PARAM);
			Log.d(Config.LOGTAG, " " + TAG + "GOT geatteId = " + geatteId);

			String fromNumber = JResponse.getString(Config.GEATTE_FROM_NUMBER_PARAM);
			Log.d(Config.LOGTAG, " " + TAG + "GOT fromNumber = " + fromNumber);

			String title = JResponse.getString(Config.GEATTE_TITLE_PARAM);
			Log.d(Config.LOGTAG, " " + TAG + "GOT title = " + title);

			String desc = JResponse.getString(Config.GEATTE_DESC_PARAM);
			Log.d(Config.LOGTAG, " " + TAG + "GOT desc = " + desc);

			String createdDate = JResponse.getString(Config.GEATTE_CREATED_DATE_PARAM);
			Log.d(Config.LOGTAG, " " + TAG + "GOT createdDate = " + createdDate);

			dbHelper.insertFriendInterest(geatteId, title, desc, fromNumber, createdDate);
			Log.d(Config.LOGTAG, " " + TAG + "Saved geatteId = " + geatteId + " to DB SUCCESSUL!");

			// fetch coming image
			//client = new DefaultHttpClient();
			//get = new HttpGet(Config.BASE_URL + Config.GEATTE_IMAGE_GET_URL);
			//response =  client.execute(get);

			if ((geatteId != null) && !geatteId.equals("")) {
			    try {
				URL url = new URL(Config.BASE_URL + Config.GEATTE_IMAGE_GET_URL + "?" + Config.GEATTE_ID_PARAM + "=" + geatteId);
				URLConnection conn = url.openConnection();
				conn.connect();
				BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
				Bitmap bm = BitmapFactory.decodeStream(bis);
				bis.close();

				String imagePath = saveToFile(bm);
				if (imagePath != null) {
				    Log.d(Config.LOGTAG, " " + TAG + "GOT image from server imagePath = " + imagePath);
				    dbHelper.insertFIImage(geatteId, imagePath);
				    Log.d(Config.LOGTAG, " " + TAG + "Saved geatteId = " + geatteId + ", image = " + imagePath + " to DB SUCCESSUL!");
				    //reviewImage.setImageResource(R.drawable.no_review_image);
				}
				else {
				    Log.w(Config.LOGTAG, " " + TAG + "geatteId = " + geatteId + " has no image.");
				}

				// send notification
				Intent intentNotify = new Intent(this, GeatteComing.class);
				intent.putExtra(Config.GEATTE_ID_PARAM, geatteId);
				intent.putExtra(Config.GEATTE_FROM_NUMBER_PARAM, fromNumber);
				intent.putExtra(Config.GEATTE_TITLE_PARAM, title);
				intent.putExtra(Config.GEATTE_DESC_PARAM, desc);
				intent.putExtra(Config.GEATTE_CREATED_DATE_PARAM, createdDate);
				intent.putExtra(Config.GEATTE_IMAGE_GET_URL, imagePath);
				// Simply open the parent activity
				C2DMReceiver.generateNotification(context, "You got a new Geatte", title, intentNotify);

			    } catch (IOException e) {
				Log.e(Config.LOGTAG, " " + TAG, e);
			    }
			} else {
			    Log.e(Config.LOGTAG, " " + TAG + " geatteId is null, drop this message without saving.");
			}
		    } else {
			Log.e(Config.LOGTAG, " " + TAG + " json response is null when try to retrieve geatte info for geatteid " + geatteid);
		    }

		} catch (ClientProtocolException e) {
		    Log.e(Config.LOGTAG, " " + TAG, e);
		} catch (IOException e) {
		    Log.e(Config.LOGTAG, " " + TAG, e);
		} catch (JSONException e) {
		    Log.e(Config.LOGTAG, " " + TAG, e);
		} catch (URISyntaxException e) {
		    Log.e(Config.LOGTAG, " " + TAG, e);
		} finally {
		    dbHelper.close();
		}

	    }

	    /*if (title != null && url != null && url.startsWith("http")) {
		SharedPreferences settings = Prefs.get(context);
		Intent launchIntent = LauncherUtils.getLaunchIntent(context, title, url, sel);

		// Notify and optionally start activity
		if (settings.getBoolean("launchBrowserOrMaps", true) && launchIntent != null) {
		    try {
			context.startActivity(launchIntent);
			LauncherUtils.playNotificationSound(context);
		    } catch (ActivityNotFoundException e) {
			return;
		    }
		} else {
		    if (sel != null && sel.length() > 0) {  // have selection
			LauncherUtils.generateNotification(context, sel,
				context.getString(R.string.copied_desktop_clipboard), launchIntent);
		    } else {
			LauncherUtils.generateNotification(context, url, title, launchIntent);
		    }
		}

		// Record history (for link/maps only)
		if (launchIntent != null && launchIntent.getAction().equals(Intent.ACTION_VIEW)) {
		    HistoryDatabase.get(context).insertHistory(title, url);
		}
	    }*/
	}
    }

    // Display the topbar notification
    private void showNotification(String text) {
	Notification n = new Notification();

	n.flags |= Notification.FLAG_SHOW_LIGHTS;
	n.flags |= Notification.FLAG_AUTO_CANCEL;

	n.defaults = Notification.DEFAULT_ALL;

	n.icon = R.drawable.icon;
	n.when = System.currentTimeMillis();

	Intent intent = new Intent(this, GeatteComing.class);
	intent.putExtra(Config.EXTRA_KEY_GEATTE_MESSAGE, text);
	// Simply open the parent activity
	PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);

	// Change the name of the notification here
	n.setLatestEventInfo(this, NOTIF_TITLE, text, pi);

	//mNotifMan.notify(NOTIF_CONNECTED, n);
    }

    public static void generateNotification(Context context, String msg, String title, Intent intent) {
	int icon = R.drawable.icon;
	long when = System.currentTimeMillis();

	Notification notification = new Notification(icon, title, when);
	notification.setLatestEventInfo(context, title, msg,
		PendingIntent.getActivity(context, 0, intent, 0));
	notification.flags |= Notification.FLAG_AUTO_CANCEL;

	SharedPreferences prefs = context.getSharedPreferences(
		Config.PREFERENCE_KEY,
		Context.MODE_PRIVATE);
	int notificatonID = prefs.getInt("notificationID", 0); // allow multiple notifications

	NotificationManager nm =
	    (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
	nm.notify(notificatonID, notification);
	playNotificationSound(context);

	SharedPreferences.Editor editor = prefs.edit();
	editor.putInt("notificationID", ++notificatonID % 32);
	editor.commit();
    }

    public static void playNotificationSound(Context context) {
	Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	if (uri != null) {
	    Ringtone rt = RingtoneManager.getRingtone(context, uri);
	    if (rt != null) {
		rt.setStreamType(AudioManager.STREAM_NOTIFICATION);
		rt.play();
	    }
	}
    }

    public String saveToFile(Bitmap bitmap) {
	if (bitmap == null) {
	    return null;
	}

	String filename;
	Date date = new Date();
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	filename = sdf.format(date);

	try {
	    String path = Environment.getExternalStorageDirectory().toString();
	    OutputStream fOut = null;
	    File dir = new File(path, "/geatte/images/");
	    if (!dir.isDirectory()) {
		dir.mkdirs();
	    }

	    File file = new File(dir, filename + ".jpg");

	    fOut = new FileOutputStream(file);

	    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
	    fOut.flush();
	    fOut.close();

	    MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), file
		    .getName());
	    return file.getAbsolutePath();
	} catch (Exception e) {
	    Log.w(Config.LOGTAG, " " + TAG + " Exception :" , e);
	}
	return null;
    }

    /**
     * Register or unregister based on phone sync settings. Called on each
     * performSync by the SyncAdapter.
     */
    /*
     * public static void refreshAppC2DMRegistrationState(Context context) { //
     * Determine if there are any auto-syncable accounts. If there are, make
     * sure we are // registered with the C2DM servers. If not, unregister the
     * application. boolean autoSyncDesired = false; if
     * (ContentResolver.getMasterSyncAutomatically()) { AccountManager am =
     * AccountManager.get(context); Account[] accounts =
     * am.getAccountsByType(Config.GOOGLE_ACCOUNT_TYPE); for (Account account :
     * accounts) { if (ContentResolver.getIsSyncable(account,
     * JumpNoteContract.AUTHORITY) > 0 &&
     * ContentResolver.getSyncAutomatically(account,
     * JumpNoteContract.AUTHORITY)) { autoSyncDesired = true; break; } } }
     * 
     * boolean autoSyncEnabled =
     * !C2DMessaging.getRegistrationId(context).equals("");
     * 
     * if (autoSyncEnabled != autoSyncDesired) { Log.i(TAG,
     * "System-wide desirability for JumpNote auto sync has changed; " +
     * (autoSyncDesired ? "registering" : "unregistering") +
     * " application with C2DM servers.");
     * 
     * if (autoSyncDesired == true) { C2DMessaging.register(context,
     * Config.C2DM_SENDER); } else { C2DMessaging.unregister(context); } } }
     */
}
