package dev.claude.oneui7pill;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * One UI 7 style battery pill: rounded track, level fill, percentage INSIDE the pill
 * (text flips colour where it crosses the fill edge), bolt while charging.
 */
public class PillDrawable extends Drawable {

    public interface ColorSource { int foreground(); }

    // ---- tweakables ----
    public static final float WIDTH_DP = 26f;
    public static final float HEIGHT_DP = 14f;
    private static final int COLOR_CHARGING = 0xFF3DDC84;
    private static final int COLOR_POWER_SAVE = 0xFFFFC83D;
    private static final int COLOR_LOW = 0xFFFF4D4D;
    private static final int LOW_LEVEL = 15;

    private final float density;
    private final ColorSource colorSource;
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path roundPath = new Path();
    private final Path boltPath = new Path();
    private final RectF rect = new RectF();

    private int level = 100;
    private boolean charging = false;
    private boolean powerSave = false;
    private int alpha = 255;

    public PillDrawable(float density, ColorSource colorSource) {
        this.density = density;
        this.colorSource = colorSource;
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public int widthPx() { return Math.round(WIDTH_DP * density); }
    public int heightPx() { return Math.round(HEIGHT_DP * density); }

    public void setState(int level, boolean charging, boolean powerSave) {
        int l = Math.max(0, Math.min(100, level));
        if (l != this.level || charging != this.charging || powerSave != this.powerSave) {
            this.level = l;
            this.charging = charging;
            this.powerSave = powerSave;
            invalidateSelf();
        }
    }

    @Override public int getIntrinsicWidth() { return widthPx(); }
    @Override public int getIntrinsicHeight() { return heightPx(); }

    private static boolean isLight(int c) {
        double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
        return lum > 0.6;
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;

        int fg = colorSource != null ? colorSource.foreground() : Color.WHITE;
        if (Color.alpha(fg) == 0) fg = Color.WHITE;
        fg |= 0xFF000000;

        int fillColor = charging ? COLOR_CHARGING
                : powerSave ? COLOR_POWER_SAVE
                : level <= LOW_LEVEL ? COLOR_LOW
                : fg;
        int onFill = isLight(fillColor) ? Color.BLACK : Color.WHITE;

        float w = b.width();
        float h = b.height();
        float r = h / 2f;
        rect.set(b.left, b.top, b.right, b.bottom);

        roundPath.reset();
        roundPath.addRoundRect(rect, r, r, Path.Direction.CW);

        // track
        trackPaint.setStyle(Paint.Style.FILL);
        trackPaint.setColor(fg);
        trackPaint.setAlpha((int) (90 * alpha / 255f));
        canvas.drawPath(roundPath, trackPaint);

        float fillRight = b.left + w * (level / 100f);

        // fill (clipped to pill shape)
        canvas.save();
        canvas.clipPath(roundPath);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(alpha);
        canvas.drawRect(b.left, b.top, fillRight, b.bottom, fillPaint);
        canvas.restore();

        // content: bolt + number, drawn twice with different clips/colours
        textPaint.setTextSize(h * 0.68f);
        String s = String.valueOf(level);
        float textW = textPaint.measureText(s);
        float boltW = charging ? h * 0.32f : 0f;
        float gap = charging ? h * 0.06f : 0f;
        float total = boltW + gap + textW;
        float startX = b.left + (w - total) / 2f;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = b.top + h / 2f - (fm.ascent + fm.descent) / 2f;

        if (charging) buildBolt(startX, b.top + h * 0.20f, boltW, h * 0.60f);

        // part over the fill
        canvas.save();
        canvas.clipRect(b.left, b.top, fillRight, b.bottom);
        drawContent(canvas, s, startX + boltW + gap, baseline, onFill);
        canvas.restore();

        // part over the empty track
        canvas.save();
        canvas.clipRect(fillRight, b.top, b.right, b.bottom);
        drawContent(canvas, s, startX + boltW + gap, baseline, fg);
        canvas.restore();
    }

    private void drawContent(Canvas canvas, String s, float x, float baseline, int color) {
        textPaint.setColor(color);
        textPaint.setAlpha(alpha);
        canvas.drawText(s, x, baseline, textPaint);
        if (charging) {
            boltPaint.setStyle(Paint.Style.FILL);
            boltPaint.setColor(color);
            boltPaint.setAlpha(alpha);
            canvas.drawPath(boltPath, boltPaint);
        }
    }

    private void buildBolt(float x, float y, float w, float h) {
        boltPath.reset();
        boltPath.moveTo(x + w * 0.60f, y);
        boltPath.lineTo(x, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.46f, y + h * 0.56f);
        boltPath.lineTo(x + w * 0.38f, y + h);
        boltPath.lineTo(x + w, y + h * 0.40f);
        boltPath.lineTo(x + w * 0.54f, y + h * 0.40f);
        boltPath.close();
    }

    @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { /* colours are managed internally */ }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
