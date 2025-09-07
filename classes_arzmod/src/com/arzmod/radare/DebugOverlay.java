package com.arzmod.radare;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;

public class DebugOverlay {
    private static TextView overlayText;

    public static void show(final Activity activity, final String msg) {
        Handler handler = new Handler(activity != null ? activity.getMainLooper() : Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activity == null) {
                    Log.e("arzmod-debug-overlay", "Activity is null in delayed show");
                    return;
                }
                try {
                    if (overlayText == null) {
                        overlayText = new StrokeTextView(activity);
                        overlayText.setTextColor(Color.WHITE);
                        overlayText.setTextSize(18);
                        overlayText.setBackgroundColor(Color.TRANSPARENT);
                        ((StrokeTextView) overlayText).setStroke(Color.BLACK, 7f);
                        overlayText.setPadding(0, 0, 0, 0);
                    }

                    FrameLayout root = activity.findViewById(android.R.id.content);
                    if (root == null) {
                        Log.e("arzmod-debug-overlay", "Root content view is null");
                        return;
                    }
                    if (overlayText.getParent() == null) {
                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        );
                        params.gravity = Gravity.TOP | Gravity.START;
                        root.addView(overlayText, params);
                    }

                    overlayText.setText(msg);
                    overlayText.bringToFront();
                    overlayText.invalidate();
                    root.requestLayout();
                    root.invalidate();
                } catch (Throwable t) {
                    Log.e("arzmod-debug-overlay", "Error showing overlay: " + t.getMessage());
                }
            }
        }, 3000);
    }
}
    
class StrokeTextView extends android.widget.TextView {
    private int strokeColor = Color.BLACK;
    private float strokeWidth = 4f;

    public StrokeTextView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
    public StrokeTextView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }
    public StrokeTextView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setStroke(int color, float widthPx) {
        this.strokeColor = color;
        this.strokeWidth = widthPx;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TextPaint p = getPaint();
        p.setAntiAlias(true);
        p.setFakeBoldText(true);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeMiter(10f);
        final int fillColor = getCurrentTextColor();
        final int outlineColor = strokeColor;
        final float outlineWidthPx = Math.max(4f, strokeWidth);

        setTextColor(outlineColor);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(outlineWidthPx);
        super.onDraw(canvas);

        setTextColor(fillColor);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(0);
        super.onDraw(canvas);
    }
}