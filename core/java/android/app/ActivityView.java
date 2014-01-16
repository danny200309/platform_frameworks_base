/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.ViewGroup;
import android.view.WindowManager;

public class ActivityView extends ViewGroup {
    private final String TAG = "ActivityView";

    private final TextureView mTextureView;
    private IActivityContainer mActivityContainer;
    private Activity mActivity;
    private boolean mAttached;
    private int mWidth;
    private int mHeight;
    private Surface mSurface;

    public ActivityView(Context context) {
        this(context, null);
    }

    public ActivityView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActivityView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                mActivity = (Activity)context;
                break;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        if (mActivity == null) {
            throw new IllegalStateException("The ActivityView's Context is not an Activity.");
        }

        mTextureView = new TextureView(context);
        mTextureView.setSurfaceTextureListener(new ActivityViewSurfaceTextureListener());
        addView(mTextureView);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mTextureView.layout(l, t, r, b);
    }

    @Override
    protected void onAttachedToWindow() {
        try {
            final IBinder token = mActivity.getActivityToken();
            mActivityContainer =
                    ActivityManagerNative.getDefault().createActivityContainer(token, null);
        } catch (RemoteException e) {
            throw new IllegalStateException("ActivityView: Unable to create ActivityContainer. "
                    + e);
        }

        final SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if (surfaceTexture != null) {
            attachToSurface(surfaceTexture);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        detachFromSurface();
    }

    @Override
    public boolean isAttachedToWindow() {
        return mAttached;
    }

    public void startActivity(Intent intent) {
        if (mActivityContainer != null && mAttached) {
            try {
                mActivityContainer.startActivity(intent);
            } catch (RemoteException e) {
                throw new IllegalStateException("ActivityView: Unable to startActivity. " + e);
            }
        }
    }

    /** Call when both mActivityContainer and mTextureView's SurfaceTexture are not null */
    private void attachToSurface(SurfaceTexture surfaceTexture) {
        WindowManager wm = (WindowManager)mActivity.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        mSurface = new Surface(surfaceTexture);
        try {
            mActivityContainer.attachToSurface(mSurface, mWidth, mHeight,
                    metrics.densityDpi);
        } catch (RemoteException e) {
            mActivityContainer = null;
            mSurface.release();
            mSurface = null;
            mAttached = false;
            throw new IllegalStateException(
                    "ActivityView: Unable to create ActivityContainer. " + e);
        }
        mAttached = true;
    }

    private void detachFromSurface() {
        if (mActivityContainer != null) {
            try {
                mActivityContainer.detachFromDisplay();
            } catch (RemoteException e) {
            }
            mActivityContainer = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        mAttached = false;
    }

    private class ActivityViewSurfaceTextureListener implements SurfaceTextureListener {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width,
                int height) {
            mWidth = width;
            mHeight = height;
            if (mActivityContainer != null) {
                attachToSurface(surfaceTexture);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width,
                int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: w=" + width + " h=" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            detachFromSurface();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
//            Log.d(TAG, "onSurfaceTextureUpdated");
        }

    }
}
