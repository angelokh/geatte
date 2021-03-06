package com.google.android.c2dm.server;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import com.geatte.app.server.GoogleAuthClient;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

/**
 * Stores config information related to data messaging.
 * 
 */
public class C2DMConfigLoader {
    private final PersistenceManagerFactory PMF;
    private static final Logger log = Logger.getLogger(C2DMConfigLoader.class.getName());

    String currentToken;
    String c2dmUrl;

    C2DMConfigLoader(PersistenceManagerFactory pmf) {
	this.PMF = pmf;
    }

    /**
     * Update the token.
     * 
     * Called on "Update-Client-Auth" or when admins set a new token.
     * 
     * @param token
     */
    public void updateToken(String token) {
	if (token != null) {
	    currentToken = token;
	    PersistenceManager pm = PMF.getPersistenceManager();
	    try {
		getDataMessagingConfig(pm).setAuthToken(token);
	    } finally {
		pm.close();
	    }
	}
    }

    /**
     * Token expired
     */
    public void invalidateCachedToken() {
	currentToken = null;
    }

    /**
     * Return the auth token from the database. Should be called only if the old
     * token expired.
     * 
     * @return
     */
    public String getToken() {
	if (currentToken == null) {
	    currentToken = getDataMessagingConfig().getAuthToken();
	}
	return currentToken;
    }

    public String getC2DMUrl() {
	if (c2dmUrl == null) {
	    c2dmUrl = getDataMessagingConfig().getC2DMUrl();
	}
	return c2dmUrl;
    }

    public C2DMConfig getDataMessagingConfig() {
	PersistenceManager pm = PMF.getPersistenceManager();
	try {
	    C2DMConfig dynamicConfig = getDataMessagingConfig(pm);
	    return dynamicConfig;
	} finally {
	    pm.close();
	}
    }

    public static C2DMConfig getDataMessagingConfig(PersistenceManager pm) {
	Key key = KeyFactory.createKey(C2DMConfig.class.getSimpleName(), 1);
	log.log(Level.INFO, "C2DMConfigLoader.getDataMessagingConfig() : trying to load C2DMConfig key = " + key);

	C2DMConfig dmConfig = null;
	try {
	    dmConfig = pm.getObjectById(C2DMConfig.class, key);
	    if (dmConfig != null) {
		if (dmConfig.getAuthToken() == null || dmConfig.getAuthToken().equals("")) {
		    log.log(Level.INFO,
		    "C2DMConfigLoader.getDataMessagingConfig() : loaded C2DMConfig getAuthToken() is null or empty");

		    String token = GoogleAuthClient.getAuthToken("geatte@gmail.com", "angelo66");
		    if (token != null) {
			dmConfig.setAuthToken(token);
			pm.makePersistent(dmConfig);
			log.log(Level.INFO, "C2DMConfigLoader.getDataMessagingConfig() : reloaded C2DMConfig token = "
				+ token);
		    } else {
			log.log(Level.SEVERE, "C2DMConfigLoader.getDataMessagingConfig() : Can't load initial token");
		    }
		}
	    } else {
		log.log(Level.INFO, "C2DMConfigLoader.getDataMessagingConfig() : loaded C2DMConfig is null");
	    }
	} catch (JDOObjectNotFoundException e) {
	    // Create a new JDO object
	    dmConfig = new C2DMConfig();
	    dmConfig.setKey(key);
	    // Must be in classpath, before sending. Do not checkin !
	    // try {
	    // InputStream is =
	    // C2DMConfigLoader.class.getClassLoader().getResourceAsStream("/dataMessagingToken.txt");
	    // if (is != null) {
	    // BufferedReader reader = new BufferedReader(new
	    // InputStreamReader(is));
	    // String token = reader.readLine();
	    // dmConfig.setAuthToken(token);
	    // }
	    // } catch (Throwable t) {
	    // log.log(Level.SEVERE,
	    // "Can't load initial token, use admin console", t);
	    // }

	    String token = GoogleAuthClient.getAuthToken("geatte@gmail.com", "angelo66");
	    if (token != null) {
		dmConfig.setAuthToken(token);
		pm.makePersistent(dmConfig);
	    } else {
		log.log(Level.SEVERE, "Can't load initial token");
	    }
	}
	return dmConfig;
    }
}
