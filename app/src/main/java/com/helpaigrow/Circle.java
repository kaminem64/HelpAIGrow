package com.helpaigrow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class Circle extends View {

    private float x = 25;
    private float y = 25;
    private int r = 25;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(x, y, r, mPaint);
    }


    public Circle(Context context) {
        super(context);
        init();
    }

    public Circle(Context context, float x, float y, int r, int red, int green, int blue) {
        super(context);
        init();
        this.x = x;
        this.y = y;
        this.r = r;
        mPaint.setARGB(255, red, green, blue);
    }

    public Circle(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Circle(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void init() {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x000000);
    }

    void recolor(int red, int green, int blue) {
        mPaint.setColor(Color.BLUE);
        mPaint.setARGB(255, red, green, blue);
        this.invalidate();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

}