package com.basecamp.turbolinks.customui;

import android.webkit.WebView;

/**
 * Used to blend together pre-API 23 classes and post-API 23 classes to fix the scroll view listener gap
 */
public interface OnScrollChangedCallback {
	/**
	 *
	 * @param xPosition    Current horizontal scroll origin.
	 * @param yPosition    Current vertical scroll origin.
	 * @param oldXPosition Previous horizontal scroll origin.
	 * @param oldYPosition Previous vertical scroll origin.
	 */
	public void onScroll(int xPosition, int yPosition, int oldXPosition, int oldYPosition);
	
	/**
	 *
	 * @param webview      Webview / View being observed
	 * @param xPosition    Current horizontal scroll origin.
	 * @param yPosition    Current vertical scroll origin.
	 * @param oldXPosition Previous horizontal scroll origin.
	 * @param oldYPosition Previous vertical scroll origin.
	 */
	public void onScroll(WebView v, int xPosition, int yPosition, int oldXPosition, int oldYPosition);
}
