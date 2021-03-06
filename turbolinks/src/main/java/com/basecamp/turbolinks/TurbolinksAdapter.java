package com.basecamp.turbolinks;

/**
 * <p>Defines callbacks that Turbolinks makes available to your app. This interface is required, and
 * should be implemented in an activity (or similar class).</p>
 *
 * <p>Often these callbacks handle error conditions, but there are also some convenient timing events
 * where you can do things like routing, inject custom Javascript, etc.</p>
 */
public interface TurbolinksAdapter {
    
    /**
     * Called whenever the page both does not support turbolinks and the bridge injection failed.
     * This specifically runs after the `onPageSupportsTurbolinks(false)` runs and runs on the Main
     * Thread
     */
    void bridgeInjectionFailed();
    
    /**
     * Called whenever the page is reloaded via a pull to refresh
     */
    void reloadPageViaRefreshTriggered();
    
    /**
     * Called when the page loaded does or does not support turbolinks. If there is
     * an issue instantiating turbolinks_bridge.js, it will send false, else, true
     */
    void onPageSupportsTurbolinks(boolean doesSupport);
    
    /**
     * <p>Called after the Turbolinks Javascript bridge has been injected into the webView, during the
     * Android WebViewClient's standard onPageFinished callback.
     */
    void onPageFinished();

    /**
     * <p>Called when the Android WebViewClient's standard onReceivedError callback is fired.</p>
     *
     * @param errorCode Passed through error code returned by the Android WebViewClient.
     */
    void onReceivedError(int errorCode);

    /**
     * <p>Called when Turbolinks detects that the page being visited has been invalidated, typically
     * by new resources in the the page HEAD.</p>
     */
    void pageInvalidated();

    /**
     *<p>Called when Turbolinks receives an HTTP error from a Turbolinks request.</p>
     *
     * @param statusCode HTTP status code returned by the request.
     */
    void requestFailedWithStatusCode(int statusCode);

    /**
     * <p>Called when Turbolinks considers the visit fully completed -- the request fulfilled
     * successfully and page rendered.</p>
     */
    void visitCompleted();

    /**
     * <p>Called when Turbolinks completes a visit to a location and it is handled by the webview.</p>
     * <p>This can occur if the site does not support turbolinks and is instead handled by the webview</p>
     *
     * @param location URL that was loaded by the webview.
     */
    void visitCompletedByWebview(String location);
    
    /**
     * <p>Called when Turbolinks first starts a visit, typically from a link inside a webView.</p>
     *
     * @param location URL to be visited.
     * @param action Whether to treat the request as an advance (navigating forward) or a replace (back).
     */
    void visitProposedToLocationWithAction(String location, String action);
}
