package com.basecamp.turbolinks.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.basecamp.turbolinks.TurbolinksAdapter;
import com.basecamp.turbolinks.TurbolinksDebugCallback;
import com.basecamp.turbolinks.TurbolinksSession;
import com.basecamp.turbolinks.TurbolinksView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements TurbolinksAdapter {
    // Change the BASE_URL to an address that your VM or device can hit.
//    private static final String BASE_URL = "http://10.0.1.100:9292";
    //For Testing a long scrollview page
//    private static final String BASE_URL = "https://stackoverflow.com/questions/27515236/how-to-determine-the-content-size-of-a-wkwebview";
    //For testing page made with Turbolinks
    private static final String BASE_URL = "https://cookpad.com/us";
    private static final String INTENT_URL = "intentUrl";

    private String location;
    private TurbolinksView turbolinksView;

    // -----------------------------------------------------------------------
    // Activity overrides
    // -----------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find the custom TurbolinksView object in your layout
        turbolinksView = (TurbolinksView) findViewById(R.id.turbolinks_view);
	
	    Log.d("Demo", "about to enable logging");
        // For this demo app, we force debug logging on. You will only want to do
        // this for debug builds of your app (it is off by default)
        TurbolinksSession.setDebugLoggingEnabled(true);
	    Log.d("Demo", "Enabled logging");
			    // For this example we set a default location, unless one is passed in through an intent
        location = getIntent().getStringExtra(INTENT_URL) != null ? getIntent().getStringExtra(INTENT_URL) : BASE_URL;
	
	    TurbolinksDebugCallback debugCallback = new TurbolinksDebugCallback() {
		    @Override
		    public void logEvent(int logLevel, String tag, String msg) {
				Log.d("Demo", "Received callback from debug callback. Log level = "
						+ logLevel + ", Tag == " + tag + ", Msg == " + msg);
		    }
	    };
        // Execute the visit
        TurbolinksSession.getDefault(this)
            .activity(this)
            .adapter(this)
            .view(turbolinksView)
		    .debugCallback(debugCallback)
            .visit(location);
	    Log.d("Demo", "Finished all initialization");
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // Since the webView is shared between activities, we need to tell Turbolinks
        // to load the location from the previous activity upon restarting
        TurbolinksSession.getDefault(this)
            .activity(this)
            .adapter(this)
            .restoreWithCachedSnapshot(true)
            .view(turbolinksView)
            .visit(location);
    }

    // -----------------------------------------------------------------------
    // TurbolinksAdapter interface
    // -----------------------------------------------------------------------

    @Override
    public void onPageFinished() {

    }
    
    @Override
    public void onPageSupportsTurbolinks(boolean bool) {
    
    }
    
    @Override
    public void reloadPageViaRefreshTriggered() {
    
    }
    
    @Override
    public void onReceivedError(int errorCode) {
        handleError(errorCode);
    }

    @Override
    public void pageInvalidated() {

    }

    @Override
    public void requestFailedWithStatusCode(int statusCode) {
        handleError(statusCode);
    }

    @Override
    public void visitCompleted() {

    }
    
    @Override
    public void visitCompletedByWebview(String location) {
    
    }
    
    // The starting point for any href clicked inside a Turbolinks enabled site. In a simple case
    // you can just open another activity, or in more complex cases, this would be a good spot for
    // routing logic to take you to the right place within your app.
    @Override
    public void visitProposedToLocationWithAction(String location, String action) {
        Log.d("Turbolinks Lib", "Within MainActivity, visitProposedToLocationWithAction. loc == " + location + ", action == " + action);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(INTENT_URL, location);

        this.startActivity(intent);
    }

    // -----------------------------------------------------------------------
    // Private
    // -----------------------------------------------------------------------

    // Simply forwards to an error page, but you could alternatively show your own native screen
    // or do whatever other kind of error handling you want.
    private void handleError(int code) {
        if (code == 404) {
            TurbolinksSession.getDefault(this)
                .activity(this)
                .adapter(this)
                .restoreWithCachedSnapshot(false)
                .view(turbolinksView)
                .visit(BASE_URL + "/error");
        }
    }
}
