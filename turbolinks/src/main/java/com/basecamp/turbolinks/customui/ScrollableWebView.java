package com.basecamp.turbolinks.customui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebView;
import android.widget.AbsListView;

/**
 * Scrollable list view to support vertical scroll listeners. Code pulled from:
 * https://stackoverflow.com/questions/14752523/how-to-make-a-scroll-listener-for-webview-in-android
 * @author patrickmacdowell
 */
public class ScrollableWebView extends WebView {
	
	//region Instance Vars
	
	private OnScrollChangedCallback mOnScrollChangedCallback;
	private Context context;
	private AttributeSet attributes;
	
	//endregion
	
	//region Constructors
	
	public ScrollableWebView(Context context) {
		super(context);
		this.context = context;
	}
	
	public ScrollableWebView(Context context, AttributeSet atters){
		super(context, atters);
		this.context = context;
		attributes = atters;
	}
	
	//endregion
	
	//region Override Methods
	
	@Override
	protected void onScrollChanged(int xPosition, int yPosition, int oldXPosition, int oldYPosition) {
		super.onScrollChanged(xPosition, yPosition, oldXPosition, oldYPosition);
		if(this.mOnScrollChangedCallback != null){
			this.mOnScrollChangedCallback.onScroll(xPosition, yPosition, oldXPosition, oldYPosition);
		}
	}
	
	//endregion
	
	//region Setters and Getters
	
	/**
	 * Get the current {@link OnScrollChangedCallback} set
	 * @return
	 */
	public OnScrollChangedCallback getOnScrollChangedCallback() {
		return this.mOnScrollChangedCallback;
	}
	
	/**
	 * Set the current {@link OnScrollChangedCallback}
	 * @param onScrollChangedCallback
	 */
	public void setOnScrollChangedCallback(final OnScrollChangedCallback onScrollChangedCallback) {
		this.mOnScrollChangedCallback = onScrollChangedCallback;
	}
	
	//endregion
	
	
}
