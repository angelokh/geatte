package com.geatte.android.view;

import greendroid.util.Config;
import greendroid.widget.ActionBar;
import android.app.ListActivity;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * An equivalent to {@link ListActivity} that manages a ListView.
 * 
 * @see {@link ListActivity}
 */
public abstract class ListActionBarActivity extends AppFooterActionbarActivity {

    private static final String LOG_TAG = ListActionBarActivity.class.getSimpleName();

    private ListAdapter mAdapter;
    private ListView mListView;
    private View mEmptyView;

    private Handler mHandler = new Handler();
    private boolean mFinishedStart = false;

    private Runnable mRequestFocus = new Runnable() {
	public void run() {
	    mListView.focusableViewAvailable(mListView);
	}
    };

    public ListActionBarActivity() {
	super();
    }

    public ListActionBarActivity(ActionBar.Type actionBarType) {
	super(actionBarType);
    }

    /**
     * This method will be called when an item in the list is selected.
     * Subclasses should override. Subclasses can call
     * getListView().getItemAtPosition(position) if they need to access the data
     * associated with the selected item.
     * 
     * @param l The ListView where the click happened
     * @param v The view that was clicked within the ListView
     * @param position The position of the view in the list
     * @param id The row id of the item that was clicked
     */
    protected void onListItemClick(ListView l, View v, int position, long id) {
    }

    /**
     * Set the currently selected list item to the specified position with the
     * adapter's data
     * 
     * @param position The position to select in the managed {@link ListView}
     */
    public void setSelection(int position) {
	mListView.setSelection(position);
    }

    /**
     * Get the position of the currently selected list item.
     * 
     * @return The position of the currently selected {@link ListView} item.
     */
    public int getSelectedItemPosition() {
	return mListView.getSelectedItemPosition();
    }

    /**
     * Get the {@link ListAdapter} row ID of the currently selected list item.
     * 
     * @return The identifier of the selected {@link ListView} item.
     */
    public long getSelectedItemId() {
	return mListView.getSelectedItemId();
    }

    /**
     * Get the activity's {@link ListView} widget.
     * 
     * @return The {@link ListView} managed by the current
     *         {@link ListActionBarActivity}
     */
    public ListView getListView() {
	ensureLayout();
	return mListView;
    }

    /**
     * Get the {@link ListAdapter} associated with this activity's
     * {@link ListView}.
     * 
     * @return The {@link ListAdapter} currently associated to the underlying
     *         {@link ListView}
     */
    public ListAdapter getListAdapter() {
	return mAdapter;
    }

    /**
     * Provides the Adapter for the ListView handled by this
     * {@link ListActionBarActivity}
     * 
     * @param adapter The {@link ListAdapter} to set.
     */
    public void setListAdapter(ListAdapter adapter) {
	synchronized (this) {
	    ensureLayout();
	    mAdapter = adapter;
	    mListView.setAdapter(adapter);
	    if (mListView.getEmptyView() == null && mEmptyView != null) {
		mListView.setEmptyView(mEmptyView);
	    }
	}
    }

    @Override
    public int createLayout() {
	if (Config.GD_INFO_LOGS_ENABLED) {
	    Log.i(LOG_TAG, "No layout specified : creating the default layout");
	}

	return super.createLayout();
    }

    @Override
    protected boolean verifyLayout() {
	return super.verifyLayout() && mListView != null;
    }

    @Override
    public void onPreContentChanged() {
	super.onPreContentChanged();

	mEmptyView = findViewById(android.R.id.empty);
	mListView = (ListView) findViewById(android.R.id.list);
	if (mListView == null) {
	    throw new RuntimeException("Your content must have a ListView whose id attribute is " + "'android.R.id.list'");
	}
    }

    @Override
    public void onPostContentChanged() {
	super.onPostContentChanged();

	if (mFinishedStart) {
	    setListAdapter(mAdapter);
	}

	mListView.setOnItemClickListener(mOnItemClickListener);
	mHandler.post(mRequestFocus);
	mFinishedStart = true;
    }

    @Override
    public void setActionBarContentView(int resID) {
	throwSetActionBarContentViewException();
    }

    @Override
    public void setActionBarContentView(View view, LayoutParams params) {
	throwSetActionBarContentViewException();
    }

    @Override
    public void setActionBarContentView(View view) {
	throwSetActionBarContentViewException();
    }

    private void throwSetActionBarContentViewException() {
	throw new UnsupportedOperationException(
	"The setActionBarContentView method is not supported for GDListActivity. In order to get a custom layout you must return a layout identifier in createLayout");

    }

    private AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
	@SuppressWarnings("unused")
	public void onNothingSelected(AdapterView<?> arg0) {
	}
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
	    onListItemClick((ListView) parent, v, position, id);
	}
    };

}
