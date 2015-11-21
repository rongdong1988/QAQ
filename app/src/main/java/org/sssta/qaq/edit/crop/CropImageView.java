/*
 * Copyright 2013, Edmodo, Inc. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this work except in compliance with the License.
 * You may obtain a copy of the License in the LICENSE file, or at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" 
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language 
 * governing permissions and limitations under the License. 
 */

package org.sssta.qaq.edit.crop;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.sssta.qaq.R;

/**
 * Custom view that provides cropping capabilities to an image.
 */
public class CropImageView extends FrameLayout {

    // Private Constants ///////////////////////////////////////////////////////

    private static final Rect EMPTY_RECT = new Rect();

    // Member Variables ////////////////////////////////////////////////////////

    // Sets the default image guidelines to show when resizing
    public static final int DEFAULT_GUIDELINES = 1;
    public static final boolean DEFAULT_FIXED_ASPECT_RATIO = false;
    public static final int DEFAULT_ASPECT_RATIO_X = 1;
    public static final int DEFAULT_ASPECT_RATIO_Y = 1;

    private static final int DEFAULT_IMAGE_RESOURCE = 0;

    private static final String DEGREES_ROTATED = "DEGREES_ROTATED";

    private ImageView mImageView;
    private CropOverlayView mCropOverlayView;

    private Bitmap mBitmap;
    private int mDegreesRotated = 0;

    private int mLayoutWidth;
    private int mLayoutHeight;

    // Instance variables for customizable attributes
    private int mGuidelines = DEFAULT_GUIDELINES;
    private boolean mFixAspectRatio = DEFAULT_FIXED_ASPECT_RATIO;
    private int mAspectRatioX = DEFAULT_ASPECT_RATIO_X;
    private int mAspectRatioY = DEFAULT_ASPECT_RATIO_Y;
    private int mImageResource = DEFAULT_IMAGE_RESOURCE;

    // Constructors ////////////////////////////////////////////////////////////

    public CropImageView(Context context) {
        super(context);
        init(context);
    }

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CropImageView, 0, 0);

        try {
            mGuidelines = ta.getInteger(R.styleable.CropImageView_guidelines, DEFAULT_GUIDELINES);
            mFixAspectRatio = ta.getBoolean(R.styleable.CropImageView_fixAspectRatio,
                                            DEFAULT_FIXED_ASPECT_RATIO);
            mAspectRatioX = ta.getInteger(R.styleable.CropImageView_aspectRatioX, DEFAULT_ASPECT_RATIO_X);
            mAspectRatioY = ta.getInteger(R.styleable.CropImageView_aspectRatioY, DEFAULT_ASPECT_RATIO_Y);
            mImageResource = ta.getResourceId(R.styleable.CropImageView_imageResource, DEFAULT_IMAGE_RESOURCE);
        } finally {
            ta.recycle();
        }

        init(context);
    }

    // View Methods ////////////////////////////////////////////////////////////

    @Override
    public Parcelable onSaveInstanceState() {

        final Bundle bundle = new Bundle();

        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putInt(DEGREES_ROTATED, mDegreesRotated);
       

        return bundle;

    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {

        if (state instanceof Bundle) {

            final Bundle bundle = (Bundle) state;

            if (mBitmap != null) {
                // Fixes the rotation of the image when orientation changes.
                mDegreesRotated = bundle.getInt(DEGREES_ROTATED);
                int tempDegrees = mDegreesRotated;
                Log.i("jarlen", " onRestoreInstanceState");
                rotateImage(mDegreesRotated);
                mDegreesRotated = tempDegrees;
            }

            super.onRestoreInstanceState(bundle.getParcelable("instanceState"));

        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {

        if (mBitmap != null) {
            final Rect bitmapRect = ImageViewUtil.getBitmapRectCenterInside(mBitmap, this);
            mCropOverlayView.setBitmapRect(bitmapRect);
        } else {
            mCropOverlayView.setBitmapRect(EMPTY_RECT);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (mBitmap != null) {

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            // Bypasses a baffling bug when used within a ScrollView, where
            // heightSize is set to 0.
            if (heightSize == 0)
                heightSize = mBitmap.getHeight();

            int desiredWidth;
            int desiredHeight;

            double viewToBitmapWidthRatio = Double.POSITIVE_INFINITY;
            double viewToBitmapHeightRatio = Double.POSITIVE_INFINITY;

            // Checks if either width or height needs to be fixed
            if (widthSize < mBitmap.getWidth()) {
                viewToBitmapWidthRatio = (double) widthSize / (double) mBitmap.getWidth();
            }
            if (heightSize < mBitmap.getHeight()) {
                viewToBitmapHeightRatio = (double) heightSize / (double) mBitmap.getHeight();
            }

            // If either needs to be fixed, choose smallest ratio and calculate
            // from there
            if (viewToBitmapWidthRatio != Double.POSITIVE_INFINITY || viewToBitmapHeightRatio != Double.POSITIVE_INFINITY) {
                if (viewToBitmapWidthRatio <= viewToBitmapHeightRatio) {
                    desiredWidth = widthSize;
                    desiredHeight = (int) (mBitmap.getHeight() * viewToBitmapWidthRatio);
                } else {
                    desiredHeight = heightSize;
                    desiredWidth = (int) (mBitmap.getWidth() * viewToBitmapHeightRatio);
                }
            }

            // Otherwise, the picture is within frame layout bounds. Desired
            // width is
            // simply picture size
            else {
                desiredWidth = mBitmap.getWidth();
                desiredHeight = mBitmap.getHeight();
            }

            int width = getOnMeasureSpec(widthMode, widthSize, desiredWidth);
            int height = getOnMeasureSpec(heightMode, heightSize, desiredHeight);

            mLayoutWidth = width;
            mLayoutHeight = height;

            final Rect bitmapRect = ImageViewUtil.getBitmapRectCenterInside(mBitmap.getWidth(),
                                                                            mBitmap.getHeight(),
                                                                            mLayoutWidth,
                                                                            mLayoutHeight);
            mCropOverlayView.setBitmapRect(bitmapRect);
           
            //jarlen
            mCropOverlayView.setBitmapSize(mBitmap.getWidth(), mBitmap.getHeight());

            // MUST CALL THIS
            setMeasuredDimension(mLayoutWidth, mLayoutHeight);

        } else {

            mCropOverlayView.setBitmapRect(EMPTY_RECT);
            setMeasuredDimension(widthSize, heightSize);
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {

        super.onLayout(changed, l, t, r, b);

        if (mLayoutWidth > 0 && mLayoutHeight > 0) {
            // Gets original parameters, and creates the new parameters
            final ViewGroup.LayoutParams origparams = this.getLayoutParams();
            origparams.width = mLayoutWidth;
            origparams.height = mLayoutHeight;
            setLayoutParams(origparams);
        }
    }

    // Public Methods //////////////////////////////////////////////////////////

    /**
     * Returns the integer of the imageResource
     * 
     */
    public int getImageResource() {
        return mImageResource;
    }

    /**
     * Sets a Bitmap as the content of the CropImageView.
     * 设置剪切资源图
     * 
     * @param bitmap
     * 剪切资源图
     * 
     */
    public void setImageBitmap(Bitmap bitmap) {

        mBitmap = bitmap;
        mImageView.setImageBitmap(mBitmap);

        if (mCropOverlayView != null) {
            mCropOverlayView.resetCropOverlayView();
        }
    }

    /**
     * Sets a Bitmap and initializes the image rotation according to the EXIT data.
     * <p>
     * The EXIF can be retrieved by doing the following:
     * <code>ExifInterface exif = new ExifInterface(path);</code>
     * 
     * @param bitmap the original bitmap to set; if null, this
     * @param exif the EXIF information about this bitmap; may be null
     */
    public void setImageBitmap(Bitmap bitmap, ExifInterface exif) {

        if (bitmap == null) {
            return;
        }

        if (exif == null) {
            setImageBitmap(bitmap);
            return;
        }

        final Matrix matrix = new Matrix();
        final int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        int rotate = -1;

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotate = 270;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotate = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotate = 90;
                break;
        }

        if (rotate == -1) {
            setImageBitmap(bitmap);
        } else {
            matrix.postRotate(rotate);
            final Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap,
                                                             0,
                                                             0,
                                                             bitmap.getWidth(),
                                                             bitmap.getHeight(),
                                                             matrix,
                                                             true);
            setImageBitmap(rotatedBitmap);
            bitmap.recycle();
        }
    }


    public void setCropFrame(float left,float top,float right,float bottom) {
        mCropOverlayView.setCropFrame(left,top, right, bottom);
    }

   /**
    * Sets a Drawable as the content of the CropImageView.
    * 设置剪切资源图
    * 
    * @param resId
    * 剪切资源图ID
    * 
    */
    public void setImageResource(int resId) {
        if (resId != 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), resId);
            setImageBitmap(bitmap);
        }
    }

  /**
   * Gets the cropped image based on the current crop window.
   * 获取剪切区图
   * 
   * @return
   * 
   * 剪切区域图
   */
    public Bitmap getCroppedImage() {

        final Rect displayedImageRect = ImageViewUtil.getBitmapRectCenterInside(mBitmap, mImageView);

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for width.
        final float actualImageWidth = mBitmap.getWidth();
        final float displayedImageWidth = displayedImageRect.width();
        final float scaleFactorWidth = actualImageWidth / displayedImageWidth;

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for height.
        final float actualImageHeight = mBitmap.getHeight();
        final float displayedImageHeight = displayedImageRect.height();
        final float scaleFactorHeight = actualImageHeight / displayedImageHeight;

        // Get crop window position relative to the displayed image.
        final float cropWindowX = Edge.LEFT.getCoordinate() - displayedImageRect.left;
        final float cropWindowY = Edge.TOP.getCoordinate() - displayedImageRect.top;
        final float cropWindowWidth = Edge.getWidth();
        final float cropWindowHeight = Edge.getHeight();

        // Scale the crop window position to the actual size of the Bitmap.
        final float actualCropX = cropWindowX * scaleFactorWidth;
        final float actualCropY = cropWindowY * scaleFactorHeight;
        final float actualCropWidth = cropWindowWidth * scaleFactorWidth;
        final float actualCropHeight = cropWindowHeight * scaleFactorHeight;

        // Crop the subset from the original Bitmap.
        final Bitmap croppedBitmap = Bitmap.createBitmap(mBitmap,
                                                         (int) actualCropX,
                                                         (int) actualCropY,
                                                         (int) actualCropWidth,
                                                         (int) actualCropHeight);

//        Bitmap bitmap = Bitmap.createBitmap(croppedBitmap.getWidth(),croppedBitmap.getHeight(),Bitmap.Config.ARGB_8888);
//
//
//        Canvas canvas = new Canvas(bitmap);
//
//        Paint paint = new Paint();
//        paint.setStyle(Paint.Style.FILL);
//        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
//        paint.setAlpha(0);
//        canvas.drawRect(0, 0, croppedBitmap.getWidth(), croppedBitmap.getHeight(), paint);
//
//        paint.setAlpha(255);
//        Path path = new Path();
//        float deltaWidth = croppedBitmap.getWidth()/3;
//        path.moveTo(deltaWidth, 0);
//        path.lineTo(croppedBitmap.getWidth() - deltaWidth, 0);
//        path.lineTo(croppedBitmap.getWidth(),croppedBitmap.getHeight()/9);
//        path.lineTo(croppedBitmap.getWidth(), (float) (croppedBitmap.getHeight()/1.5));
//        path.lineTo((float) (croppedBitmap.getWidth() / 1.5), croppedBitmap.getHeight());
//        path.lineTo(deltaWidth, croppedBitmap.getHeight());
//        path.lineTo(0, (float) (croppedBitmap.getHeight() / 1.5));
//        path.lineTo(0, croppedBitmap.getHeight() / 9);
//        path.close();
//
//        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
//        canvas.drawPath(path, paint);
////
//        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
//        canvas.drawBitmap(croppedBitmap,0,0,paint);

        return croppedBitmap;
    }

    /**
     * Gets the crop window's position relative to the source Bitmap (not the image
     * displayed in the CropImageView).
     * 
     * @return a RectF instance containing cropped area boundaries of the source Bitmap
     */
    public RectF getActualCropRect() {

        final Rect displayedImageRect = ImageViewUtil.getBitmapRectCenterInside(mBitmap, mImageView);

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for width.
        final float actualImageWidth = mBitmap.getWidth();
        final float displayedImageWidth = displayedImageRect.width();
        final float scaleFactorWidth = actualImageWidth / displayedImageWidth;

        // Get the scale factor between the actual Bitmap dimensions and the
        // displayed dimensions for height.
        final float actualImageHeight = mBitmap.getHeight();
        final float displayedImageHeight = displayedImageRect.height();
        final float scaleFactorHeight = actualImageHeight / displayedImageHeight;

        // Get crop window position relative to the displayed image.
        final float displayedCropLeft = Edge.LEFT.getCoordinate() - displayedImageRect.left;
        final float displayedCropTop = Edge.TOP.getCoordinate() - displayedImageRect.top;
        final float displayedCropWidth = Edge.getWidth();
        final float displayedCropHeight = Edge.getHeight();

        // Scale the crop window position to the actual size of the Bitmap.
        float actualCropLeft = displayedCropLeft * scaleFactorWidth;
        float actualCropTop = displayedCropTop * scaleFactorHeight;
        float actualCropRight = actualCropLeft + displayedCropWidth * scaleFactorWidth;
        float actualCropBottom = actualCropTop + displayedCropHeight * scaleFactorHeight;

        // Correct for floating point errors. Crop rect boundaries should not
        // exceed the source Bitmap bounds.
        actualCropLeft = Math.max(0f, actualCropLeft);
        actualCropTop = Math.max(0f, actualCropTop);
        actualCropRight = Math.min(mBitmap.getWidth(), actualCropRight);
        actualCropBottom = Math.min(mBitmap.getHeight(), actualCropBottom);

        final RectF actualCropRect = new RectF(actualCropLeft,
                                               actualCropTop,
                                               actualCropRight,
                                               actualCropBottom);

        return actualCropRect;
    }

    /**
     * 设置剪切类型
     * 
     * false: 自由剪切
     * true : 固定大小比例剪切
     * 
     * @param fixAspectRatio
     */
    public void setFixedAspectRatio(boolean fixAspectRatio) {
        mCropOverlayView.setFixedAspectRatio(fixAspectRatio);
    }

    /**
     * Sets the guidelines for the CropOverlayView to be either on, off, or to show when
     * resizing the application.
     * 
     * @param guidelines Integer that signals whether the guidelines should be on, off, or
     *            only showing when resizing.
     */
    public void setGuidelines(int guidelines) {
        mCropOverlayView.setGuidelines(guidelines);
    }

    /**
     * Sets the both the X and Y values of the aspectRatio.
     * 设置固定比例剪切的比例
     * 现将setFixedAspectRatio(true)设置
     * 
     * 例如：cropImage.setAspectRatio(40, 30);是以40:30的宽高比例剪切
     * 
     * @param 
     * aspectRatioX int that specifies the new X value of the aspect ratio
     * 宽度比例
     * 
     * @param aspectRatioX int that specifies the new Y value of the aspect ratio
     * 高度比例
     */
    public void setAspectRatio(int aspectRatioX, int aspectRatioY) {
        mAspectRatioX = aspectRatioX;
        mCropOverlayView.setAspectRatioX(mAspectRatioX);

        mAspectRatioY = aspectRatioY;
        mCropOverlayView.setAspectRatioY(mAspectRatioY);
    }

    /**
     * Rotates image by the specified number of degrees clockwise. Cycles from 0 to 360
     * degrees.
     * 顺时针度数旋转图片
     * 
     * 0 --- 360
     * 
     * @param degrees Integer specifying the number of degrees to rotate.
     * 
     * 旋转度数
     */
    public void rotateImage(int degrees) {

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
        setImageBitmap(mBitmap);

        mDegreesRotated += degrees;
        mDegreesRotated = mDegreesRotated % 360;
    }
    
    
    /**
     * 图片翻转
     * @author jarlen
     * @param type
     * 翻转类型
     * CropImageType.REVERSE_TYPE.UP_DOWN
     * CropImageType.REVERSE_TYPE.LEFT_RIGHT
     */
    public void reverseImage(CropImageType.REVERSE_TYPE type)
    {
    	Matrix matrix = new Matrix();
    	
    	if(type == CropImageType.REVERSE_TYPE.UP_DOWN)
    	{
    		matrix.postScale(1, -1);//上下翻转
    	}
    	else if(type == CropImageType.REVERSE_TYPE.LEFT_RIGHT)
    	{
    		matrix.postScale(-1, 1);;//左右翻转
    	}
    	
    	mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
        setImageBitmap(mBitmap);
    }
    
    /**
     * 更改剪切框四角的样式
     * 
     * @param bit
     * 样式图片
     * bit = null 时，默认为白色角边线
     * 
     */
    public void setCropOverlayCornerBitmap(Bitmap bit)
    {
    	mCropOverlayView.setCropOverlayCornerBitmap(bit);
    }

    // Private Methods /////////////////////////////////////////////////////////

    private void init(Context context) {

        final LayoutInflater inflater = LayoutInflater.from(context);
        final View v = inflater.inflate(R.layout.crop_image_view, this, true);

        mImageView = (ImageView) v.findViewById(R.id.ImageView_image);

        setImageResource(mImageResource);
        mCropOverlayView = (CropOverlayView) v.findViewById(R.id.CropOverlayView);
        mCropOverlayView.setInitialAttributeValues(mGuidelines, mFixAspectRatio, mAspectRatioX, mAspectRatioY);
    }

    /**
     * Determines the specs for the onMeasure function. Calculates the width or height
     * depending on the mode.
     * 
     * @param measureSpecMode The mode of the measured width or height.
     * @param measureSpecSize The size of the measured width or height.
     * @param desiredSize The desired size of the measured width or height.
     * @return The final size of the width or height.
     */
    private static int getOnMeasureSpec(int measureSpecMode, int measureSpecSize, int desiredSize) {

        // Measure Width
        int spec;
        if (measureSpecMode == MeasureSpec.EXACTLY) {
            // Must be this size
            spec = measureSpecSize;
        } else if (measureSpecMode == MeasureSpec.AT_MOST) {
            // Can't be bigger than...; match_parent value
            spec = Math.min(desiredSize, measureSpecSize);
        } else {
            // Be whatever you want; wrap_content
            spec = desiredSize;
        }

        return spec;
    }

}
