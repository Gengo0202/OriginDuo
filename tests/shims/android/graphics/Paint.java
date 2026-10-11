package android.graphics;

import java.awt.BasicStroke;
import java.awt.Font;

/** Minimal desktop stand-in for {@code android.graphics.Paint}. */
public class Paint {

    public static final int ANTI_ALIAS_FLAG = 0x01;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public enum Cap { BUTT, ROUND, SQUARE }

    public enum Join { MITER, ROUND, BEVEL }

    public enum Align { LEFT, CENTER, RIGHT }

    public static class FontMetrics {
        public float ascent;
        public float descent;
    }

    private Style style = Style.FILL;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private Align align = Align.LEFT;
    private int color = 0xFF000000;
    private float strokeWidth = 1f;
    private float textSize = 12f;
    private String family = "SansSerif";
    private int typefaceStyle = Font.PLAIN;
    private float letterSpacing = 0f;

    public Paint() {
    }

    public Paint(int flags) {
    }

    public void setStyle(Style s) { style = s; }
    public Style getStyle() { return style; }

    public void setStrokeCap(Cap c) { cap = c; }
    public Cap getStrokeCap() { return cap; }

    public void setStrokeJoin(Join j) { join = j; }
    public Join getStrokeJoin() { return join; }

    public void setStrokeWidth(float w) { strokeWidth = w; }
    public float getStrokeWidth() { return strokeWidth; }

    public void setTextAlign(Align a) { align = a; }
    public Align getTextAlign() { return align; }

    public void setColor(int c) { color = c; }
    public int getColor() { return color; }

    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }

    public void setLetterSpacing(float s) { letterSpacing = s; }
    public float getLetterSpacing() { return letterSpacing; }

    public void setTypeface(Typeface t) {
        if (t != null) {
            family = t.family();
            typefaceStyle = t.style();
        }
    }

    public Font font() {
        return new Font(family, typefaceStyle, 12).deriveFont(textSize);
    }

    public BasicStroke stroke() {
        int c = cap == Cap.ROUND ? BasicStroke.CAP_ROUND
                : (cap == Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
        int j = join == Join.ROUND ? BasicStroke.JOIN_ROUND
                : (join == Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER);
        return new BasicStroke(strokeWidth, c, j);
    }

    /**
     * Emulates Android's tracking-aware advance. Android measures a whole
     * string including its letter spacing, which matters here because
     * {@code TrioRenderer} uses this to shrink an oversized value.
     */
    public float measureText(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            total += fm.charWidth(text.charAt(i));
            if (i < text.length() - 1) {
                total += letterSpacing * textSize;
            }
        }
        return total;
    }

    public FontMetrics getFontMetrics() {
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        FontMetrics out = new FontMetrics();
        out.ascent = fm.getAscent();
        out.descent = fm.getDescent();
        return out;
    }
}
