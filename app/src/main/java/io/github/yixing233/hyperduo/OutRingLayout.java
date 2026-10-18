package io.github.yixing233.hyperduo;

/** One logical strip for signal, performance and type, ordered from the battery. */
final class OutRingLayout {
    private OutRingLayout() { }

    static final class Plan {
        final float scale;
        final float[] near;
        final int[] widths;
        final int total;
        final int reserve;

        Plan(float scale, float[] near, int[] widths, int total) {
            this.scale = scale;
            this.near = near;
            this.widths = widths;
            this.total = total;
            this.reserve = (int) Math.ceil(total * scale);
        }
    }

    /**
     * Coordinates increase from the row's logical start, before RTL mirroring.
     * The island value is a start-side exclusion boundary, never a translation.
     * Shrink the entire strip together when constrained; clamping each child
     * independently would stack them on the same edge.
     */
    static Plan solve(int rowWidth, int paddingStart, int paddingEnd,
                      int islandBoundary, int availableHeight,
                      int[] widths, int[] heights, int[] gaps, int outwardGap) {
        if (widths.length != heights.length || widths.length != gaps.length) {
            throw new IllegalArgumentException("strip metrics have different lengths");
        }
        final float end = Math.max(0, rowWidth - Math.max(0, paddingEnd));
        final float floor = Math.max(Math.max(0, paddingStart), islandBoundary);
        int total = Math.max(0, outwardGap);
        int tallest = 0;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                total += widths[i] + Math.max(0, gaps[i]);
                tallest = Math.max(tallest, heights[i]);
            }
        }
        float scale = total == 0 ? 1f : Math.min(1f, Math.max(0f, end - floor) / total);
        if (tallest > 0) {
            scale = Math.min(scale, Math.max(0, availableHeight) / (float) tallest);
        }
        final float[] near = new float[widths.length];
        float cursor = end;
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] > 0) {
                cursor -= Math.max(0, gaps[i]) * scale;
                near[i] = cursor;
                cursor -= widths[i] * scale;
            }
        }
        return new Plan(scale, near, widths, total);
    }
}
