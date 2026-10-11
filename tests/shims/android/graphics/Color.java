package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.Color}. */
public class Color {

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;

    public static int red(int color) { return (color >> 16) & 0xFF; }

    public static int green(int color) { return (color >> 8) & 0xFF; }

    public static int blue(int color) { return color & 0xFF; }

    public static int alpha(int color) { return (color >>> 24) & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
