package com.geatte.android.view;

import com.geatte.android.app.CommonUtils;
import com.geatte.android.app.Config;
import com.geatte.android.app.R;

import greendroid.widget.AsyncImageView;
import greendroid.widget.item.Item;
import greendroid.widget.itemview.ItemView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ThumbnailAsyncBitmapItemView extends RelativeLayout implements ItemView {

    private TextView mTitleView;
    private TextView mDescView;
    private AsyncImageView mThumbnailView;

    public ThumbnailAsyncBitmapItemView(Context context) {
	this(context, null);
    }

    public ThumbnailAsyncBitmapItemView(Context context, AttributeSet attrs) {
	this(context, attrs, 0);
    }

    public ThumbnailAsyncBitmapItemView(Context context, AttributeSet attrs, int defStyle) {
	super(context, attrs, defStyle);
    }

    public void prepareItemView() {
	mTitleView = (TextView) findViewById(R.id.geatte_async_item_title);
	mDescView = (TextView) findViewById(R.id.geatte_async_subtitle);
	mThumbnailView = (AsyncImageView) findViewById(R.id.geatte_async_image);
    }

    public void setObject(Item object) {
	final ThumbnailAsyncBitmapItem item = (ThumbnailAsyncBitmapItem) object;
	mTitleView.setText(item.text);
	mDescView.setText(item.subtitle);

	int sampleSize = CommonUtils.getResizeRatio(item.imagePath, 250, 2);
	if(Config.LOG_DEBUG_ENABLED) {
	    Log.d(Config.LOGTAG, " ThumbnailAsyncBitmapItemView:setObject() resize image with sampleSize = " + sampleSize);
	}
	BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
	bitmapOptions.inSampleSize = sampleSize;
	Bitmap imgBitmap = BitmapFactory.decodeFile(item.imagePath, bitmapOptions);
	if (imgBitmap == null) {
	    mThumbnailView.setImageResource(R.drawable.thumb_missing);
	} else {
	    mThumbnailView.setImageBitmap(imgBitmap);
	}
    }

}
