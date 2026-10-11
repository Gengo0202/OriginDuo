package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.Typeface}. */
public class Typeface {

    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    public static final Typeface DEFAULT = new Typeface("SansSerif", NORMAL);
    public static final Typeface DEFAULT_BOLD = new Typeface("SansSerif", BOLD);
    public static final Typeface SANS_SERIF = DEFAULT;

    private final String family;
    private final int style;

    private Typeface(String family, int style) {
        this.family = family;
        this.style = style;
    }

    /**
     * Maps the Android family names used by the renderer onto desktop fonts.
     * MIUI ships Roboto; a metric-compatible substitute is close enough for a
     * geometry check.
     */
    public static Typeface create(String family, int style) {
        String mapped = "SansSerif";
        if (family != null && family.startsWith("sans-serif")) {
            mapped = "SansSerif";
        }
        return new Typeface(mapped, style);
    }

    /**
     * Numeric-weight overload (API 28+). The desktop substitute only has a
     * plain and a bold face, so the 100..900 range is bucketed: the renderer
     * exposes nine weights but a geometry check only needs to see that heavier
     * text is bolder and therefore measured wider.
     */
    public static Typeface create(Typeface base, int weight, boolean italic) {
        int style = weight >= 600 ? BOLD : NORMAL;
        if (italic) {
            style |= ITALIC;
        }
        return new Typeface(base == null ? "SansSerif" : base.family, style);
    }

    String family() { return family; }

    int style() {
        int out = java.awt.Font.PLAIN;
        if ((style & BOLD) != 0) {
            out |= java.awt.Font.BOLD;
        }
        if ((style & ITALIC) != 0) {
            out |= java.awt.Font.ITALIC;
        }
        return out;
    }
}
