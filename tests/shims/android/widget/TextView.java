package android.widget;

import android.graphics.Paint;

/**
 * Desktop stand-in for the parts of {@code android.widget.TextView} that
 * {@code TrioRenderer} touches.
 *
 * <p>The renderer dresses the real labels beside the glyph - the out-of-ring
 * network type and the performance readouts - and the offline probes compile it,
 * so this exists to make that link, not to model a TextView. Only the three calls
 * the styling helper makes are replayed: the face, the paint (for synthetic
 * emboldening), and the re-layout a changed face needs.
 */
public class TextView {

    private android.graphics.Typeface typeface;
    private final Paint paint = new Paint();
    private int layoutRequests;

    public void setTypeface(android.graphics.Typeface t) {
        typeface = t;
    }

    public android.graphics.Typeface getTypeface() {
        return typeface;
    }

    public Paint getPaint() {
        return paint;
    }

    public void requestLayout() {
        layoutRequests++;
    }

    /** How many times the styling helper asked this view to re-measure. */
    public int layoutRequests() {
        return layoutRequests;
    }
}
