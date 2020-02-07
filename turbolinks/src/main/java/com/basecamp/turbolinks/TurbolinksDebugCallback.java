package com.basecamp.turbolinks;

import android.util.Log;

/**
 * this class is used for debug messages callback. If null, it will simply not be used. If it is
 * valid, it will send back all of the logged data along the adapter in addition to printing it.
 */
public interface TurbolinksDebugCallback {
	
	/**
	 * Log Event callback.
	 * @param logLevel The loglevel (IE {@link Log#DEBUG})
	 * @param tag The tag used
	 * @param msg The message logged
	 */
	public void logEvent(int logLevel, String tag, String msg);
}
