package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.Rect}. */
public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {
    }

    public Rect(int l, int t, int r, int b) {
        set(l, t, r, b);
    }

    public void set(int l, int t, int r, int b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public void setEmpty() {
        set(0, 0, 0, 0);
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }
}
