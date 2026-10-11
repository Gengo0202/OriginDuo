package android.view;

/** Minimal desktop stand-in for the parts of {@code android.view.View} used by {@code TrioRenderer}. */
public class View {

    private int width;
    private int height;

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }
}
