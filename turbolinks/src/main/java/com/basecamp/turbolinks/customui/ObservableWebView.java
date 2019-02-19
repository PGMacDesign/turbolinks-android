package com.basecamp.turbolinks.customui;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

import androidx.annotation.RequiresApi;

@RequiresApi(api = 23)
public class ObservableWebView extends WebView {
	
	//region Instance Vars
	private OnScrollChangedCallback onScrollChangedCallback;
	//endregion
	
	//region Constructors
	
	public ObservableWebView(Context context) {
		super(context);
	}
	
	public ObservableWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public ObservableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}
	
	//endregion
	
	//region Override Methods
	
	@Override
	protected void onScrollChanged(int xPosition, int yPosition, int oldXPosition, int oldYPosition) {
		super.onScrollChanged(xPosition, yPosition, oldXPosition, oldYPosition);
		if (onScrollChangedCallback != null) {
			onScrollChangedCallback.onScroll(this, xPosition, yPosition, oldXPosition, oldYPosition);
		}
	}
	
	//endregion
	
	//region Setters and Getters
	
	/**
	 * Get the current {@link OnScrollChangedCallback} set
	 * @return
	 */
	public OnScrollChangedCallback getOnScrollChangedCallback() {
		return this.onScrollChangedCallback;
	}
	
	/**
	 * Set the current {@link OnScrollChangedCallback}
	 * @param onScrollChangedCallback
	 */
	public void setOnScrollChangedCallback(final OnScrollChangedCallback onScrollChangedCallback) {
		this.onScrollChangedCallback = onScrollChangedCallback;
	}
	
	//endregion
	
}
