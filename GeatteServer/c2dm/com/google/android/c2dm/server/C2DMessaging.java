package com.google.android.c2dm.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.PersistenceManagerFactory;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import com.geatte.app.server.Config;
import com.geatte.app.server.DBHelper;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;

public class C2DMessaging {
    private static final String UPDATE_CLIENT_AUTH = "Update-Client-Auth";

    private static final Logger log = Logger.getLogger(C2DMessaging.class.getName());

    public static final String PARAM_REGISTRATION_ID = "registration_id";

    public static final String PARAM_DELAY_WHILE_IDLE = "delay_while_idle";

    public static final String PARAM_COLLAPSE_KEY = "collapse_key";

    /**
     * Jitter - random interval to wait before retry.
     */
    public static final int DATAMESSAGING_MAX_JITTER_MSEC = 3000;

    static C2DMessaging singleton;

    final C2DMConfigLoader serverConfig;

    // Testing
    protected C2DMessaging() {
	serverConfig = null;
    }

    private C2DMessaging(C2DMConfigLoader serverConfig) {
	this.serverConfig = serverConfig;
    }

    public synchronized static C2DMessaging get(ServletContext servletContext) {
	if (singleton == null) {
	    C2DMConfigLoader serverConfig = new C2DMConfigLoader(DBHelper.getPMF(servletContext));
	    singleton = new C2DMessaging(serverConfig);
	}
	return singleton;
    }

    public synchronized static C2DMessaging get(PersistenceManagerFactory pmf) {
	if (singleton == null) {
	    C2DMConfigLoader serverConfig = new C2DMConfigLoader(pmf);
	    singleton = new C2DMessaging(serverConfig);
	}
	return singleton;
    }

    C2DMConfigLoader getServerConfig() {
	return serverConfig;
    }

    public boolean sendNoRetry(String registrationId, String collapse, Map<String, String[]> params,
	    boolean delayWhileIdle) throws IOException {

	// Send a sync message to this Android device.
	StringBuilder postDataBuilder = new StringBuilder();
	postDataBuilder.append(PARAM_REGISTRATION_ID).append("=").append(registrationId);

	if (delayWhileIdle) {
	    postDataBuilder.append("&").append(PARAM_DELAY_WHILE_IDLE).append("=1");
	}
	postDataBuilder.append("&").append(PARAM_COLLAPSE_KEY).append("=").append(collapse);

	for (Object keyObj : params.keySet()) {
	    String key = (String) keyObj;
	    if (key.startsWith("data.")) {
		String[] values = params.get(key);
		postDataBuilder.append("&").append(key).append("=").append(URLEncoder.encode(values[0], Config.ENCODE_UTF8));
	    }
	}

	log.info("C2DMessaging.sendNoRetry() : the data to post : " + postDataBuilder.toString());

	byte[] postData = postDataBuilder.toString().getBytes(Config.ENCODE_UTF8);

	// Hit the dm URL.
	URL url = new URL(serverConfig.getC2DMUrl());

	log.info("C2DMessaging.sendNoRetry() : build connection to C2DM url : " + url.toString());

	HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	conn.setDoOutput(true);
	conn.setUseCaches(false);
	conn.setRequestMethod("POST");
	conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
	conn.setRequestProperty("Content-Length", Integer.toString(postData.length));
	String authToken = serverConfig.getToken();
	conn.setRequestProperty("Authorization", "GoogleLogin auth=" + authToken);

	if (authToken == null || authToken.trim().equals("")) {
	    log.info("C2DMessaging.sendNoRetry() : authToken is null : " + authToken);
	} else {
	    log.info("C2DMessaging.sendNoRetry() : send authToken to C2DM : " + authToken);
	}

	OutputStream out = conn.getOutputStream();
	out.write(postData);
	out.close();

	int responseCode = conn.getResponseCode();

	if (responseCode == HttpServletResponse.SC_UNAUTHORIZED || responseCode == HttpServletResponse.SC_FORBIDDEN) {
	    // The token is too old - return false to retry later, will fetch the token
	    // from DB. This happens if the password is changed or token expires. Either admin
	    // is updating the token, or Update-Client-Auth was received by another server,
	    // and next retry will get the good one from database.
	    log.warning("C2DMessaging.sendNoRetry() : Unauthorized - need token" + ", responseCode : " + responseCode);
	    serverConfig.invalidateCachedToken();
	    return false;
	}

	// Check for updated token header
	String updatedAuthToken = conn.getHeaderField(UPDATE_CLIENT_AUTH);
	if (updatedAuthToken != null && !authToken.equals(updatedAuthToken)) {
	    log.info("C2DMessaging.sendNoRetry() : Got updated auth token from datamessaging servers: " + updatedAuthToken);
	    serverConfig.updateToken(updatedAuthToken);
	}

	String responseLine = new BufferedReader(new InputStreamReader(conn.getInputStream())).readLine();

	// NOTE: You *MUST* use exponential backoff if you receive a 503 response code.
	// Since App Engine's task queue mechanism automatically does this for
	// tasks that return non-success error codes, this is not explicitly implemented
	// here.
	// If we weren't using App Engine, we'd need to manually implement this.
	if (responseLine == null || responseLine.equals("")) {
	    log.info("C2DMessaging.sendNoRetry() : Got " + responseCode + " response from Google AC2DM endpoint.");
	    throw new IOException("Got empty response from Google AC2DM endpoint.");
	}

	String[] responseParts = responseLine.split("=", 2);
	if (responseParts.length != 2) {
	    log.warning("C2DMessaging.sendNoRetry() : Invalid message from google: " + responseCode + " " + responseLine);
	    throw new IOException("Invalid response from Google " + responseCode + " " + responseLine);
	}

	if (responseParts[0].equals("id")) {
	    log.info("C2DMessaging.sendNoRetry() : Successfully sent data message to device: " + responseLine);
	    return true;
	}

	if (responseParts[0].equals("Error")) {
	    String err = responseParts[1];
	    log.warning("Got error response from Google datamessaging endpoint: " + err);
	    throw new IOException(err);
	} else {
	    // 500 or unparseable response - server error, needs to retry
	    log.warning("C2DMessaging.sendNoRetry() : Invalid response from google " + responseLine + " " + responseCode);
	    return false;
	}
    }

    /**
     * Helper method to send a message, with 2 parameters.
     * 
     * Permanent errors will result in IOException. Retriable errors will cause
     * the message to be scheduled for retry.
     */
    public void sendWithRetry(String token, String collapseKey, String name1, String value1, String name2,
	    String value2, String name3, String value3) throws IOException {

	Map<String, String[]> params = new HashMap<String, String[]>();
	if (value1 != null)
	    params.put("data." + name1, new String[] { value1 });
	if (value2 != null)
	    params.put("data." + name2, new String[] { value2 });
	if (value3 != null)
	    params.put("data." + name3, new String[] { value3 });

	boolean sentOk = sendNoRetry(token, collapseKey, params, true);
	if (!sentOk) {
	    retry(token, collapseKey, params, true);
	}
    }

    public boolean sendNoRetry(String token, String collapseKey, String name1, String value1, String name2,
	    String value2, String name3, String value3) throws IOException {

	Map<String, String[]> params = new HashMap<String, String[]>();
	if (value1 != null)
	    params.put("data." + name1, new String[] { value1 });
	if (value2 != null)
	    params.put("data." + name2, new String[] { value2 });
	if (value3 != null)
	    params.put("data." + name3, new String[] { value3 });

	try {
	    return sendNoRetry(token, collapseKey, params, true);
	} catch (IOException ex) {
	    return false;
	}
    }

    public boolean sendNoRetry(String token, String collapseKey, String... nameValues) throws IOException {

	Map<String, String[]> params = new HashMap<String, String[]>();
	int len = nameValues.length;
	if (len % 2 == 1) {
	    len--; // ignore last
	}
	for (int i = 0; i < len; i += 2) {
	    String name = nameValues[i];
	    String value = nameValues[i + 1];
	    if (name != null && value != null) {
		params.put("data." + name, new String[] { value });
	    }
	}

	try {
	    return sendNoRetry(token, collapseKey, params, true);
	} catch (IOException ex) {
	    return false;
	}
    }

    private void retry(String token, String collapseKey, Map<String, String[]> params, boolean delayWhileIdle) {
	Queue dmQueue = QueueFactory.getQueue("c2dm");
	try {
	    TaskOptions url = TaskOptions.Builder.withUrl(C2DMRetryServlet.URI).param(C2DMessaging.PARAM_REGISTRATION_ID,
		    token).param(C2DMessaging.PARAM_COLLAPSE_KEY, collapseKey);
	    if (delayWhileIdle) {
		url.param(PARAM_DELAY_WHILE_IDLE, "1");
	    }
	    for (String key : params.keySet()) {
		String[] values = params.get(key);
		url.param(key, URLEncoder.encode(values[0], Config.ENCODE_UTF8));
	    }

	    // Task queue implements the exponential backoff
	    long jitter = (int) Math.random() * DATAMESSAGING_MAX_JITTER_MSEC;
	    url.countdownMillis(jitter);

	    TaskHandle add = dmQueue.add(url);
	} catch (UnsupportedEncodingException e) {
	    // Ignore - UTF8 should be supported
	    log.log(Level.SEVERE, "Unexpected error", e);
	}

    }

}
