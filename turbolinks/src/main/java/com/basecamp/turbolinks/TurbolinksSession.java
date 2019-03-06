package com.basecamp.turbolinks;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Date;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * <p>The main concrete class to use Turbolinks 5 in your app.</p>
 */
public class TurbolinksSession implements TurbolinksScrollUpCallback {

    private static final String JAVASCRIPT_GET_WEB_PAGE_HEIGHT =
            "document.body.scrollHeight";
    
    // ---------------------------------------------------
    // Package public vars (allows for greater flexibility and access for testing)
    // ---------------------------------------------------

    boolean bridgeInjectionInProgress; // Ensures the bridge is only injected once
    boolean coldBootInProgress;
    boolean restoreWithCachedSnapshot;
    boolean turbolinksIsReady; // Script finished and TL fully instantiated
    boolean screenshotsEnabled;
    boolean pullToRefreshEnabled;
    boolean webViewAttachedToNewParent;
    boolean isAtTop;
    int xPosition, yPosition, heightOfPage;
    long previousOverrideTime;
    Activity activity;
    HashMap<String, Object> javascriptInterfaces = new HashMap<>();
    HashMap<String, String> restorationIdentifierMap = new HashMap<>();
    String location;
    String currentVisitIdentifier;
    TurbolinksAdapter turbolinksAdapter;
    TurbolinksView turbolinksView;
//    View progressView;
//    View progressIndicator;

    static volatile TurbolinksSession defaultInstance;

    // ---------------------------------------------------
    // Final vars
    // ---------------------------------------------------

    static final String ACTION_ADVANCE = "advance";
    static final String ACTION_RESTORE = "restore";
    static final String ACTION_REPLACE = "replace";
    static final String ACTION_RELOAD = "reload";
    static final String JAVASCRIPT_INTERFACE_NAME = "TurbolinksNative";
    static final int PROGRESS_INDICATOR_DELAY = 500;

    final Context applicationContext;
    WebView webView; //Removed final prefix on 20192-27

    // ---------------------------------------------------
    // Constructor
    // ---------------------------------------------------

    /**
     * Private constructor called to return a new Turbolinks instance.
     *
     * @param context Any Android context.
     */
    private TurbolinksSession(@NonNull final Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }
        this.isAtTop = false;
        this.pullToRefreshEnabled = true;
        this.applicationContext = context.getApplicationContext();
        this.screenshotsEnabled = true;
//        this.pullToRefreshEnabled = false;
        this.webViewAttachedToNewParent = false;

        
        this.webView = TurbolinksHelper.createWebView(applicationContext);
        this.webView.addJavascriptInterface(this, JAVASCRIPT_INTERFACE_NAME);
        this.webView.setWebViewClient(new MyWebViewClient());
        this.setWebviewScrollListener();
    }

    // ---------------------------------------------------
    // Initialization
    // ---------------------------------------------------

    
    /**
     * Creates a brand new TurbolinksSession that the calling application will be responsible for
     * managing.
     *
     * @param context Any Android context.
     * @return TurbolinksSession to be managed by the calling application.
     */
    public static TurbolinksSession getNew(Context context) {
        TurbolinksLog.d("TurbolinksSession getNew called");

        return new TurbolinksSession(context);
    }

    /**
     * Convenience method that returns a default TurbolinksSession. This is useful for when an
     * app only needs one instance of a TurbolinksSession.
     *
     * @param context Any Android context.
     * @return The default, static instance of a TurbolinksSession, guaranteed to not be null.
     */
    public static TurbolinksSession getDefault(Context context) {
        if (defaultInstance == null) {
            synchronized (TurbolinksSession.class) {
                if (defaultInstance == null) {
                    TurbolinksLog.d("Default instance is null, creating new");
                    defaultInstance = TurbolinksSession.getNew(context);
                }
            }
        }

        return defaultInstance;
    }

    /**
     * Resets the default TurbolinksSession instance to null in case you want a fresh session.
     */
    public static void resetDefault() {
        defaultInstance = null;
    }

    /**
     * <p>Tells the logger whether to allow logging in debug mode.</p>
     *
     * @param enabled If true debug logging is enabled.
     */
    public static void setDebugLoggingEnabled(boolean enabled) {
        TurbolinksLog.setDebugLoggingEnabled(enabled);
    }
    
    //region Custom Setters
    
    /**
     * Set a custom cookie String
     * @param baseUrl
     * @param cookieString
     * @return
     */
    public boolean setCookie(String baseUrl, String cookieString){
        if(baseUrl == null || cookieString == null){
            return false;
        }
        if(baseUrl.isEmpty() || cookieString.isEmpty()){
            return false;
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            CookieManager.getInstance().setAcceptThirdPartyCookies(this.getWebView(), true);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
        }
        CookieManager.getInstance().setCookie(baseUrl, cookieString);
        return true;
    }
    
    /**
     * Override the User agent String at a webview level as opposed to the header level
     * @param newUserAgentString
     */
    public boolean replaceUserAgentString(String newUserAgentString){
        return this.adjustUserAgentString(newUserAgentString, false);
    }
    
    /**
     * replace the User agent String at a webview level as opposed to the header level
     * @param newUserAgentString
     * @param appendToExisting boolean, if false, it will replace the user agent string entirely,
     *                         if true, it will append it to the existing one
     */
    public boolean adjustUserAgentString(String newUserAgentString, boolean appendToExisting){
        try {
            String userAgent = (appendToExisting)
                    ? (this.getWebView().getSettings().getUserAgentString() + newUserAgentString)
                    : newUserAgentString;
            this.getWebView().getSettings().setUserAgentString(userAgent);
            return true;
        } catch (Exception e){
            return false;
        }
    }
    
    //endregion
    
    // ---------------------------------------------------
    // Required chained methods
    // ---------------------------------------------------

    /**
     * <p><b>REQUIRED</b> Turbolinks requires a context for a variety of uses, and for maximum clarity
     * we ask for an Activity context instead of a generic one. (On occassion, we've run into WebView
     * bugs where an Activity is the only fix).</p>
     *
     * <p>It's best to pass a new activity to Turbolinks for each new visit for clarity. This ensures
     * there is a one-to-one relationship maintained between internal activity IDs and visit IDs.</p>
     *
     * @param activity An Android Activity, one per visit.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession activity(Activity activity) {
        this.activity = activity;

        Context webViewContext = webView.getContext();
        if (webViewContext instanceof MutableContextWrapper) {
            ((MutableContextWrapper) webViewContext).setBaseContext(this.activity);
        }

        return this;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksAdapter} implementation is required so that callbacks
     * during the Turbolinks event lifecycle can be passed back to your app.</p>
     *
     * @param turbolinksAdapter Any class that implements {@link TurbolinksAdapter}.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession adapter(TurbolinksAdapter turbolinksAdapter) {
        this.turbolinksAdapter = turbolinksAdapter;
        return this;
    }

    /**
     * <p><b>REQUIRED</b> A {@link TurbolinksView} object that's been inflated in a custom layout is
     * required so the library can manage various view-related tasks: attaching/detaching the
     * internal webView, showing/hiding a progress loading view, etc.</p>
     *
     * @param turbolinksView An inflated TurbolinksView from your custom layout.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession view(TurbolinksView turbolinksView) {
        this.turbolinksView = turbolinksView;
        this.turbolinksView.getRefreshLayout().setCallback(this);
        this.turbolinksView.getRefreshLayout().setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                turbolinksAdapter.reloadPageViaRefreshTriggered();
//                visitLocationWithAction(location, ACTION_ADVANCE);
                visitLocationWithAction(location, ACTION_REPLACE);
            }
        });
        //Callback function on refresh == 'visitLocationWithAction(location, ACTION_ADVANCE);'
        this.webViewAttachedToNewParent = this.turbolinksView.attachWebView(webView, screenshotsEnabled, pullToRefreshEnabled);

        return this;
    }

    /**
     * <p><b>REQUIRED</b> Executes a Turbolinks visit. Must be called at the end of the chain --
     * all required parameters will first be validated before firing.</p>
     *
     * @param location The URL to visit.
     */
    public void visit(String location) {
        TurbolinksLog.d("visit called");

        this.location = location;

        validateRequiredParams();

        if (!turbolinksIsReady || webViewAttachedToNewParent) {
            //todo Would normally have a callback function here for progress bar
        }

        if (turbolinksIsReady) {
            visitCurrentLocationWithTurbolinks();
        }

        if (!turbolinksIsReady && !coldBootInProgress) {
            TurbolinksLog.d("Cold booting: " + location);
            webView.loadUrl(location);
        }

        // Reset so that cached snapshot is not the default for the next visit
        restoreWithCachedSnapshot = false;

        /*
        if (!turbolinksIsReady && coldBootInProgress), we don't fire a new visit. This is
        typically a slow connection load. This allows the previous cold boot to finish (inject TL).
        No matter what, if new requests are sent to Turbolinks via Turbolinks.location, we'll
        always have the last desired location. And when setTurbolinksIsReady(true) is called,
        we open that last location.
        */
    }
    
    /**
     * Public accessor to stop the refresh layout indicator
     */
    public void stopRefreshingManual(){
        this.stopRefreshing();
    }
    
    
    /**
     * Manual setter for refreshing
     * {@link androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener} manually
     * @param isRefreshing
     */
    public void setRefreshingManual(boolean isRefreshing){
        try {
            this.turbolinksView.getRefreshLayout().setRefreshing(isRefreshing);
        } catch (Exception e){}
    }
    
    // ---------------------------------------------------
    // Optional chained methods
    // ---------------------------------------------------

    /**
     * <p><b>Optional</b> By default Turbolinks will "advance" to the next page and scroll position
     * will not be restored. Optionally calling this method allows you to set the behavior on a
     * per-visitbasis. This will be reset to "false" after each visit.</p>
     *
     * @param restoreWithCachedSnapshot If true, will restore scroll position. If false, will not restore
     *                                  scroll position.
     * @return The TurbolinksSession to continue the chained calls.
     */
    public TurbolinksSession restoreWithCachedSnapshot(boolean restoreWithCachedSnapshot) {
        this.restoreWithCachedSnapshot = restoreWithCachedSnapshot;
        return this;
    }

    // ---------------------------------------------------
    // TurbolinksNative adapter methods
    // ---------------------------------------------------

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit is initiated from a
     * webView link.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param location URL to be visited.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitProposedToLocationWithAction(final String location, final String action) {
        TurbolinksLog.d("visitProposedToLocationWithAction called");
        try {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.visitProposedToLocationWithAction(location, action);
                }
            });
        } catch (Exception e){
            TurbolinksLog.d("Exception within visitProposedToLocationWithAction: " + e.getMessage());
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when a new visit has just started.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier        A unique identifier for the visit.
     * @param visitHasCachedSnapshot Whether the visit has a cached snapshot available.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitStarted(String visitIdentifier, boolean visitHasCachedSnapshot) {
        TurbolinksLog.d("visitStarted called");

        currentVisitIdentifier = visitIdentifier;

        runJavascript("webView.changeHistoryForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.issueRequestForVisitWithIdentifier", visitIdentifier);
        runJavascript("webView.loadCachedSnapshotForVisitWithIdentifier", visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has been
     * completed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestCompleted(String visitIdentifier) {
        TurbolinksLog.d("visitRequestCompleted called");

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            runJavascript("webView.loadResponseForVisitWithIdentifier", visitIdentifier);
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the HTTP request has failed.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     * @param statusCode      The HTTP status code that caused the failure.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRequestFailedWithStatusCode(final String visitIdentifier, final int statusCode) {
        TurbolinksLog.d("visitRequestFailedWithStatusCode called");
        hideProgressView(visitIdentifier);

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.requestFailedWithStatusCode(statusCode);
                }
            });
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks once the page has been fully rendered
     * in the webView.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitRendered(String visitIdentifier) {
        TurbolinksLog.d("visitRendered called, hiding progress view for identifier: " + visitIdentifier);
        hideProgressView(visitIdentifier);
    }

    /**
     * <p><b>JavascriptInterface only</b> Called by Turbolinks when the visit is fully completed --
     * request successful and page rendered.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     *
     * @param visitIdentifier       A unique identifier for the visit.
     * @param restorationIdentifier A unique identifier for restoring the page and scroll position
     *                              from cache.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void visitCompleted(String visitIdentifier, String restorationIdentifier) {
        TurbolinksLog.d("visitCompleted called");

        addRestorationIdentifierToMap(restorationIdentifier);

        if (TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    turbolinksAdapter.visitCompleted();
                    stopRefreshing();
                }
            });
        }
    }
   
    
    /**
     * <p><b>JavascriptInterface only</b> Called when Turbolinks detects that the page being visited
     * has been invalidated, typically by new resources in the the page HEAD.</p>
     *
     * <p>Warning: This method is public so it can be used as a Javascript Interface. you should
     * never call this directly as it could lead to unintended behavior.</p>
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void pageInvalidated() {
        TurbolinksLog.d("pageInvalidated called");

        resetToColdBoot();

        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() { // route through normal chain so progress view is shown, regular logging, etc.
                turbolinksAdapter.pageInvalidated();

                visit(location);
            }
        });
    }

    // ---------------------------------------------------
    // TurbolinksNative helper methods
    // ---------------------------------------------------

    /**
     * <p><b>JavascriptInterface only</b> Hides the progress view when the page is fully rendered.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param visitIdentifier A unique identifier for the visit.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void hideProgressView(final String visitIdentifier) {
        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() {
                /**
                 * pageInvalidated will cold boot, but another in-flight response from
                 * visitResponseLoaded could attempt to hide the progress view. Checking
                 * turbolinksIsReady ensures progress view isn't hidden too soon by the non cold boot.
                 */
                if (turbolinksIsReady && TextUtils.equals(visitIdentifier, currentVisitIdentifier)) {
                    TurbolinksLog.d("Hiding progress view for visitIdentifier: " + visitIdentifier + ", currentVisitIdentifier: " + currentVisitIdentifier);
                    turbolinksView.hideProgress();
                } else {
                    stopRefreshing();
                }
            }
        });
    }

    /**
     * <p><b>JavascriptInterface only</b> Sets internal flags that indicate whether Turbolinks in
     * the webView is ready for use.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     *
     * @param turbolinksIsReady The Javascript bridge checks the current page for Turbolinks, and
     *                          sends the results of that check here.
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void setTurbolinksIsReady(boolean turbolinksIsReady) {
        this.turbolinksIsReady = turbolinksIsReady;
        if (turbolinksIsReady) {
            this.turbolinksAdapter.onPageSupportsTurbolinks(true);
            this.bridgeInjectionInProgress = false;

            TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
                @Override
                public void run() {
                    TurbolinksLog.d("TurbolinksSession is ready");
                    visitCurrentLocationWithTurbolinks();
                }
            });
    
            this.coldBootInProgress = false;
        } else {
            TurbolinksLog.d("TurbolinksSession is not ready. Resetting and throw error.");
            resetToColdBoot();
            visitRequestFailedWithStatusCode(currentVisitIdentifier, 500);
        }
    }

    /**
     * <p><b>JavascriptInterface only</b> Handles the error condition when reaching a page without
     * Turbolinks.</p>
     *
     * <p>Note: This method is public so it can be used as a Javascript Interface. For all practical
     * purposes, you should never call this directly.</p>
     */
    @SuppressWarnings("unused")
    @android.webkit.JavascriptInterface
    public void turbolinksDoesNotExist() {
        TurbolinksLog.d("turbolinksDoesNotExist on this page, going to cold boot");
        turbolinksAdapter.onPageSupportsTurbolinks(false);
        TurbolinksHelper.runOnMainThread(applicationContext, new Runnable() {
            @Override
            public void run() {
                TurbolinksLog.d("Error instantiating turbolinks_bridge.js - resetting to cold boot.");
                resetToColdBoot();
                turbolinksView.hideProgress();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Public
    // -----------------------------------------------------------------------
    
    /**
     * <p>Provides the ability to add an arbitrary number of custom Javascript Interfaces to the built-in
     * Turbolinks webView.</p>
     *
     * @param object The object with annotated JavascriptInterface methods
     * @param name   The unique name for the interface (must not use the reserved name "TurbolinksNative")
     */
    @SuppressLint("JavascriptInterface")
    public void addJavascriptInterface(Object object, String name) {
        if (TextUtils.equals(name, JAVASCRIPT_INTERFACE_NAME)) {
            throw new IllegalArgumentException(JAVASCRIPT_INTERFACE_NAME + " is a reserved Javascript Interface name.");
        }

        if (javascriptInterfaces.get(name) == null) {
            javascriptInterfaces.put(name, object);
            webView.addJavascriptInterface(object, name);

            TurbolinksLog.d("Adding JavascriptInterface: " + name + " for " + object.getClass().toString());
        }
    }

    /**
     * <p>Returns the activity attached to the Turbolinks call.</p>
     *
     * @return The attached activity.
     */
    public Activity getActivity() {
        return activity;
    }

    /**
     * <p>Returns the internal WebView used by Turbolinks.</p>
     *
     * @return The WebView used by Turbolinks.
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * <p>Resets the TurbolinksSession to go through the full cold booting sequence (full page load)
     * on the next Turbolinks visit.</p>
     */
    public void resetToColdBoot() {
        bridgeInjectionInProgress = false;
        turbolinksIsReady = false;
        coldBootInProgress = false;
    }

    /**
     * <p>Runs a Javascript function with any number of arbitrary params in the Turbolinks webView.</p>
     *
     * @param functionName The name of the function, without any parenthesis or params
     * @param params       A comma delimited list of params. Params will be automatically JSONified.
     */
    public void runJavascript(final String functionName, final Object... params) {
        TurbolinksHelper.runJavascript(applicationContext, webView, functionName, params);
    }

    /**
     * <p>Runs raw Javascript in webView. Simply wraps the loadUrl("javascript: methodName()") call.</p>
     *
     * @param rawJavascript The full Javascript string that will be executed by the WebView.
     */
    public void runJavascriptRaw(String rawJavascript) {
        TurbolinksHelper.runJavascriptRaw(applicationContext, webView, rawJavascript);
    }

    /**
     * <p>Determines whether screenshots are displayed (instead of a progress view) when resuming
     * an activity. Default is true.</p>
     *
     * @param enabled If true automatic screenshotting is enabled.
     */
    public void setScreenshotsEnabled(boolean enabled) {
        screenshotsEnabled = enabled;
    }
    
    /**
     * <p>Determines whether WebViews can be refreshed by pulling/swiping from the top
     * of the WebView. Default is true.</p>
     *
     * @param enabled If true pulling to refresh the WebView is enabled
     */
    public void setPullToRefreshEnabled(boolean enabled) {
        pullToRefreshEnabled = enabled;
    }
    
    /**
     * <p>Provides the status of whether Turbolinks is initialized and ready for use.</p>
     *
     * @return True if Turbolinks has been fully loaded and detected on the page.
     */
    public boolean turbolinksIsReady() {
        return turbolinksIsReady;
    }

    /**
     * <p>A convenience method to fire a Turbolinks visit manually.</p>
     *
     * @param location URL to visit.
     * @param action   Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    public void visitLocationWithAction(String location, String action) {
        TurbolinksLog.d("call to visitLocationWithAction: loc = " + location + ", action = " + action);
        this.location = location;
        runJavascript("webView.visitLocationWithActionAndRestorationIdentifier",
                TurbolinksHelper.encodeUrl(location), action, getRestorationIdentifierFromMap());
    }

    // ---------------------------------------------------
    // Private
    // ---------------------------------------------------

    /**
     * <p>Adds the restoration (cached scroll position) identifier to the local Hashmap.</p>
     *
     * @param value Restoration ID provided by Turbolinks.
     */
    private void addRestorationIdentifierToMap(String value) {
        if (activity != null) {
            restorationIdentifierMap.put(activity.toString(), value);
        }
    }

    /**
     * <p>Gets the restoration ID for the current activity.</p>
     *
     * @return Restoration ID for the current activity.
     */
    private String getRestorationIdentifierFromMap() {
        return restorationIdentifierMap.get(activity.toString());
    }

    /**
     * <p>Convenience method to simply revisit the current location in the TurbolinksSession. Useful
     * so that different visit logic can be wrappered around this call in {@link #visit} or
     * {@link #setTurbolinksIsReady(boolean)}</p>
     */
    private void visitCurrentLocationWithTurbolinks() {
        TurbolinksLog.d("Visiting current stored location: " + location);

        String action = restoreWithCachedSnapshot ? ACTION_RESTORE : ACTION_ADVANCE;
        visitLocationWithAction(location, action);
    }

    /**
     * <p>Ensures all required chained calls/parameters ({@link #activity}, {@link #turbolinksView},
     * and location}) are set before calling {@link #visit(String)}.</p>
     */
    private void validateRequiredParams() {
        if (activity == null) {
            throw new IllegalArgumentException("TurbolinksSession.activity(activity) must be called with a non-null object.");
        }

        if (turbolinksAdapter == null) {
            throw new IllegalArgumentException("TurbolinksSession.adapter(turbolinksAdapter) must be called with a non-null object.");
        }

        if (turbolinksView == null) {
            throw new IllegalArgumentException("TurbolinksSession.view(turbolinksView) must be called with a non-null object.");
        }

        if (TextUtils.isEmpty(location)) {
            throw new IllegalArgumentException("TurbolinksSession.visit(location) location value must not be null.");
        }
    }

    //region Getters
    public int getWebviewXPosition(){
        return this.xPosition;
    }
    
    public int getWebviewYPosition(){
        return this.yPosition;
    }
    
    public int getWebviewPageHeight(){
        return this.heightOfPage;
    }
    //endregion
    
    // ---------------------------------------------------
    // Interfaces
    // ---------------------------------------------------

    /**
     * <p>Determines if the user can scroll up, or if the WebView is at the top</p>
     *
     * @return True if the WebView can be scrolled up. False if the WebView is at the top.
     */
    @Override
    public boolean canChildScrollUp() {
        return !TurbolinksSession.this.isAtTop;
//        return this.webView.getScrollY() > 0;
    }
    
    //region CustoWebview scroll changedm Web View Client
    
    /**
     * Custom Webview Client
     */
    class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            coldBootInProgress = true;
        }
    
        @Override
        public void onPageFinished(WebView view, final String location) {
            TurbolinksLog.d("onPageFinished, loc == " + location);
            String jsCall = "window.webView == null";
            webView.evaluateJavascript(jsCall, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    if (Boolean.parseBoolean(s) && !bridgeInjectionInProgress) {
                        bridgeInjectionInProgress = true;
                        TurbolinksHelper.injectTurbolinksBridge(TurbolinksSession.this, applicationContext, webView);
                        TurbolinksLog.d("Bridge injected");
                    
                        turbolinksAdapter.onPageFinished();
                    } else {
                        turbolinksAdapter.onPageFinished();
                    }
                }
            });
            try {
//                view.loadUrl(JAVASCRIPT_GET_WEB_PAGE_HEIGHT);
//                TurbolinksHelper.runJavascript(applicationContext, webView, functionName, params);
                webView.evaluateJavascript(JAVASCRIPT_GET_WEB_PAGE_HEIGHT, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        try {
                            float f = Float.valueOf(value);
                            float density = applicationContext.getResources().getDisplayMetrics().density;
                            float x = (f * density);
                            TurbolinksSession.this.heightOfPage = (int) x;
                        } catch (Exception e){}
                    }
                });
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    
        /**
         * Turbolinks will not call adapter.visitProposedToLocationWithAction in some cases,
         * like target=_blank or when the domain doesn't match. We still route those here.
         * This is mainly only called when links within a webView are clicked and not during
         * loadUrl. However, a redirect on a cold boot can also cause this to fire, so don't
         * override in that situation, since Turbolinks is not yet ready.
         * http://stackoverflow.com/a/6739042/3280911
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String location) {
            if (!turbolinksIsReady || coldBootInProgress) {
                try {
//                    TurbolinksLog.d("Changing normal behavior, passing back up the adapter callback as reload");
//                    turbolinksAdapter.visitProposedToLocationWithAction(location, ACTION_RELOAD);
//                    return true;
                    view.loadUrl(location);
                    TurbolinksSession.this.turbolinksAdapter.visitCompletedByWebview(location);
                    return false;
                } catch (Exception e){
                    TurbolinksLog.d("Exception in shouldOverrideUrlLoading: " + e.getMessage());
                }
                return false;
            }
        
            /**
             * Prevents firing twice in a row within a few milliseconds of each other, which
             * happens. So we check for a slight delay between requests, which is plenty of time
             * to allow for a user to click the same link again.
             */
            long currentOverrideTime = new Date().getTime();
            if ((currentOverrideTime - previousOverrideTime) > 500) {
                previousOverrideTime = currentOverrideTime;
                TurbolinksLog.d("Overriding load: " + location);
                visitProposedToLocationWithAction(location, ACTION_ADVANCE);
            }
        
            return true;
        }
    
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            resetToColdBoot();
        
            turbolinksAdapter.onReceivedError(errorCode);
            TurbolinksLog.d("onReceivedError: " + errorCode);
        }
    
        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
        
            if (request.isForMainFrame()) {
                resetToColdBoot();
                turbolinksAdapter.onReceivedError(errorResponse.getStatusCode());
                TurbolinksLog.d("onReceivedHttpError: " + errorResponse.getStatusCode());
            }
        }
    }
    
    //endregion
    
    //region Private Custom Methods
    
    private void stopRefreshing(){
        try {
            this.turbolinksView.getRefreshLayout().setRefreshing(false);
        } catch (Exception e){}
    }
    
    
    /**
     * Set the webview scroll listener
     */
    private void setWebviewScrollListener(){
        if(this.webView == null){
            TurbolinksLog.d("Attempted to setWebviewScrollListener, but webview null");
            return;
        }
        TurbolinksLog.d("Setting setWebviewScrollListener");
        this.webView.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        if(webView.getScrollY() == 0){
                            //At top
                            try {
                                TurbolinksSession.this.turbolinksView.getRefreshLayout().setEnabled(true);
                                TurbolinksSession.this.isAtTop = true;
                            } catch (Exception e){}
                        } else {
                            //Not at top
                            try {
                                TurbolinksSession.this.turbolinksView.getRefreshLayout().setEnabled(false);
                                TurbolinksSession.this.isAtTop = false;
                            } catch (Exception e){}
                        }
                    }
                });
    }
    
    //endregion
}
