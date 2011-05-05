package com.geatte.app.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.android.c2dm.server.C2DMessaging;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class RegisterServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(RegisterServlet.class.getName());
    private static final String OK_STATUS = "OK";
    private static final String ERROR_STATUS = "ERROR";

    private static int MAX_DEVICES = 10;

    /**
     * For debug - and possibly show the info, allow device selection.
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

	RequestInfo reqInfo = RequestInfo.processRequest(req, resp, getServletContext());
	if (reqInfo == null) {
	    return;
	}

	resp.setContentType("application/json");
	JSONObject regs = new JSONObject();
	try {
	    regs.put("user", reqInfo.userName);

	    JSONArray devices = new JSONArray();
	    for (DeviceInfo di : reqInfo.devices) {
		JSONObject dijson = new JSONObject();
		dijson.put("key", di.getKey().toString());
		dijson.put("name", di.getDeviceName());
		dijson.put("type", di.getType());
		dijson.put("regid", di.getDeviceRegistrationID());
		dijson.put("ts", di.getRegistrationTimestamp());
		devices.put(dijson);
	    }
	    regs.put("devices", devices);

	    PrintWriter out = resp.getWriter();
	    regs.write(out);
	} catch (JSONException e) {
	    throw new IOException(e);
	}

    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
	log.log(Level.INFO, "RegisterServlet.doPOST() : START RegisterServlet.doPOST()");

	resp.setContentType("text/plain");

	RequestInfo reqInfo = RequestInfo.processRequest(req, resp, getServletContext());
	if (reqInfo == null) {
	    log.severe("RegisterServlet.doPOST() : can not load RequestInfo!!");
	    return;
	}

	// Because the deviceRegistrationId isn't static, we use a static
	// identifier for the device. (Can be null in older clients)
	String deviceId = reqInfo.getParameter("deviceId");

	if (deviceId == null) {
	    resp.getWriter().println(ERROR_STATUS + "(Must specify deviceId)");
	    log.severe("RegisterServlet.doPOST() : Missing device id, deviceId is null");
	    return;
	} else {
	    log.log(Level.INFO, "RegisterServlet.doPOST() : user sent a deviceId = " + deviceId);
	}

	String phoneNumber = reqInfo.getParameter("phoneNumber");

	if (phoneNumber == null) {
	    resp.getWriter().println(ERROR_STATUS + "(Must specify phoneNumber)");
	    log.severe("RegisterServlet.doPOST() : Missing phone number, phoneNumber is null");
	    return;
	} else {
	    log.log(Level.INFO, "RegisterServlet.doPOST() : user sent a phoneNumber = " + phoneNumber);
	}

	if (reqInfo.deviceRegistrationID == null) {
	    resp.setStatus(400);
	    resp.getWriter().println(ERROR_STATUS + "(Must specify devregid)");
	    log.severe("RegisterServlet.doPOST() : Missing registration id, reqInfo.deviceRegistrationID is null");
	    return;
	} else {
	    log.log(Level.INFO, "RegisterServlet.doPOST() : reqInfo.deviceRegistrationID = "
		    + reqInfo.deviceRegistrationID);
	}

	String deviceName = reqInfo.getParameter("deviceName");

	if (deviceName == null) {
	    deviceName = "Phone";
	    log.log(Level.INFO, "RegisterServlet.doPOST() : use default deviceName = " + deviceName);
	} else {
	    log.log(Level.INFO, "RegisterServlet.doPOST() : user sent a deviceName = " + deviceName);
	}
	// TODO: generate the device name by adding a number suffix for multiple
	// devices of same type. Change android app to send model/type.

	String deviceType = reqInfo.getParameter("deviceType");

	if (deviceType == null) {
	    deviceType = DeviceInfo.TYPE_AC2DM;
	    log.log(Level.INFO, "RegisterServlet.doPOST() : use default deviceType = " + deviceType);
	} else {
	    log.log(Level.INFO, "RegisterServlet.doPOST() : user sent a deviceType = " + deviceType);
	}

	// Context-shared PMF.
	PersistenceManager pm = C2DMessaging.getPMF(getServletContext()).getPersistenceManager();

	try {
	    List<DeviceInfo> registrations = reqInfo.devices;

	    if (registrations.size() > MAX_DEVICES) {
		log.log(Level.INFO, "RegisterServlet.doPOST() : user has too many devices, registrations.size = "
			+ registrations.size());
		// we could return an error - but user can't handle it yet.
		// we can't let it grow out of bounds.
		// TODO: we should also define a 'ping' message and
		// expire/remove
		// unused registrations
		DeviceInfo oldest = registrations.get(0);
		if (oldest.getRegistrationTimestamp() == null) {
		    log.log(Level.INFO,
			    "RegisterServlet.doPOST() : user has too many devices, trying to remove old one = "
			    + oldest.getDeviceRegistrationID());
		    pm.deletePersistent(oldest);
		} else {
		    long oldestTime = oldest.getRegistrationTimestamp().getTime();
		    for (int i = 1; i < registrations.size(); i++) {
			if (registrations.get(i).getRegistrationTimestamp().getTime() < oldestTime) {
			    oldest = registrations.get(i);
			    oldestTime = oldest.getRegistrationTimestamp().getTime();
			}
		    }
		    log.log(Level.INFO,
			    "RegisterServlet.doPOST() : user has too many devices, trying to remove old one = "
			    + oldest.getDeviceRegistrationID());
		    pm.deletePersistent(oldest);
		}
	    }

	    // Get device if it already exists, else create
	    //String suffix = (deviceId != null ? "#" + Long.toHexString(Math.abs(deviceId.hashCode())) : "");
	    //Key key = KeyFactory.createKey(DeviceInfo.class.getSimpleName(), reqInfo.userName + suffix);
	    Key key = KeyFactory.createKey(DeviceInfo.class.getSimpleName(), deviceId);

	    DeviceInfo device = null;
	    try {
		device = pm.getObjectById(DeviceInfo.class, key);
	    } catch (JDOObjectNotFoundException e) {
	    }
	    if (device == null) {
		log.log(Level.INFO, "RegisterServlet.doPOST() : can not find a deviceInfo by key = " + key
			+ ", create a new one");

		device = new DeviceInfo(key, reqInfo.deviceRegistrationID);
		device.setType(deviceType);
	    } else {
		log.log(Level.INFO, "RegisterServlet.doPOST() : find a deviceInfo by key = " + key
			+ ", update deviceRegistrationID to " + reqInfo.deviceRegistrationID);

		// update registration id
		device.setDeviceRegistrationID(reqInfo.deviceRegistrationID);
		device.setRegistrationTimestamp(new Date());
	    }

	    device.setPhoneNumber(phoneNumber); // update phoneNumber
	    device.setUserEmail(reqInfo.userName); // update user email
	    device.setDeviceName(deviceName); // update display name
	    // TODO: only need to write if something changed, for chrome nothing
	    // changes, we just create a new channel
	    pm.makePersistent(device);

	    log.log(Level.INFO, "RegisterServlet.doPOST() : Registered device userEmail = " + reqInfo.userName
		    + ", deviceType = " + deviceType + ", deviceRegistrationID = " + reqInfo.deviceRegistrationID + ", key = " + key);

	    // TODO
	    // if (device.getType().equals(DeviceInfo.TYPE_CHROME)) {
	    // String channelId = ChannelServiceFactory.getChannelService()
	    // .createChannel(reqInfo.deviceRegistrationID);
	    // resp.getWriter().println(OK_STATUS + " " + channelId);
	    // } else {
	    resp.getWriter().println(OK_STATUS);
	    // }

	    log.log(Level.INFO, "RegisterServlet.doPOST() : END RegisterServlet.doPOST()");
	} catch (Exception e) {
	    resp.setStatus(500);
	    resp.getWriter().println(ERROR_STATUS + " (Error registering device)");
	    log.log(Level.WARNING, "RegisterServlet.doPOST() : Error registering device.", e);
	} finally {
	    pm.close();
	}
    }
}
