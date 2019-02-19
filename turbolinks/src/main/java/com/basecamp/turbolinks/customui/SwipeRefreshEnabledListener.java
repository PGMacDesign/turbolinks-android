package com.basecamp.turbolinks.customui;

/**
 * This interface is used to fix the issue described here:
 * https://stackoverflow.com/a/33159603/2480714 where vertical scrolling becomes broken on
 * a webview. This should help to manage that issue
 */
public interface SwipeRefreshEnabledListener {
	/**
	 *
	 * @param shouldEnable If true, set the {@link androidx.swiperefreshlayout.widget.SwipeRefreshLayout}
	 *                     to true, else, set it to false
	 */
	public void shouldEnableSwipeRefresh(boolean shouldEnable);
}
