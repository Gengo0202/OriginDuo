package io.github.yixing233.hyperduo;

/** One logical strip for signal, performance and type, ordered from the battery. */
final class OutRingLayout {
    private OutRingLayout() { }

    static final class Plan {
        /** Scale applied to the surviving views. 1 unless even the highest priority overflows. */
        final float scale;
        /** Start-side coordinate of each surviving view, in row pixels from the logical start. */
        final float[] near;
        /** Per-slot width to lay out, or 0 for a slot that was dropped this frame. */
        final int[] widths;
        /** Total width the surviving views want, before {@link #scale}. */
        final int total;
        /** Pixels to reserve at the row's start for the surviving views. */
        final int reserve;
        /** Which slots were dropped, so callers can tell "nothing there" from "no room". */
        final boolean[] dropped;

        Plan(float scale, float[] near, int[] widths, int total, boolean[] dropped) {
            this.scale = scale;
            this.near = near;
            this.widths = widths;
            this.total = total;
            this.dropped = dropped;
            this.reserve = (int) Math.ceil(total * scale);
        }
    }

    /**
     * Coordinates increase from the row's logical start, before RTL mirroring.
     *
     * <p>The island value is a start-side exclusion boundary, never a translation.
     *
     * <p><b>When the strip does not fit, items are dropped, not shrunk.</b> An earlier
     * version scaled all three together, which kept them on screen but made the readings
     * illegible the moment a wide notification island appeared - the font is a fixed dp
     * size, so scaling it is the one thing that cannot be absorbed. Dropping keeps every
     * surviving view at its designed size and only costs the least important slot.
     *
     * <p><b>Index order is priority order</b> (index 0 = most important). The caller passes
     * the slots as signal, performance, type, so the reading that changes every second
     * outlives the static "5G" label. When even the highest priority slot cannot fit, it is
     * scaled as a last resort: showing it small beats showing nothing.
     */
    static Plan solve(int rowWidth, int paddingStart, int paddingEnd,
                      int islandBoundary, int availableHeight,
                      int[] widths, int[] heights, int[] gaps, int outwardGap) {
        if (widths.length != heights.length || widths.length != gaps.length) {
            throw new IllegalArgumentException("strip metrics have different lengths");
        }
        final float end = Math.max(0, rowWidth - Math.max(0, paddingEnd));
        final float floor = Math.max(Math.max(0, paddingStart), islandBoundary);
        final float room = Math.max(0, end - floor);
        final float height = Math.max(0, availableHeight);

        final boolean[] dropped = new boolean[widths.length];
        // Only slots that have something to draw can be dropped; an empty slot is not a
        // victim, it simply contributes nothing.
        for (int i = 0; i < widths.length; i++) {
            dropped[i] = widths[i] <= 0;
        }

        int total;
        int tallest;
        while (true) {
            total = Math.max(0, outwardGap);
            tallest = 0;
            for (int i = 0; i < widths.length; i++) {
                if (!dropped[i]) {
                    total += widths[i] + Math.max(0, gaps[i]);
                    tallest = Math.max(tallest, heights[i]);
                }
            }
            // Height is a separate axis and cannot be fixed by dropping a sibling: all three
            // views are about one row tall, so dropping one would not make the rest shorter.
            final boolean fitsWidth = total <= room;
            final boolean fitsHeight = tallest <= height;
            if ((fitsWidth && fitsHeight) || dropLowest(dropped)) {
                break;
            }
        }

        // Last resort: the only surviving slot is wider or taller than the row allows.
        float scale = 1f;
        if (total > 0) {
            scale = Math.min(scale, room / total);
        }
        if (tallest > 0) {
            scale = Math.min(scale, height / tallest);
        }
        scale = Math.max(0f, Math.min(1f, scale));

        final int[] layoutWidths = new int[widths.length];
        final float[] near = new float[widths.length];
        float cursor = end;
        for (int i = 0; i < widths.length; i++) {
            layoutWidths[i] = dropped[i] ? 0 : widths[i];
        }
        for (int i = 0; i < layoutWidths.length; i++) {
            if (layoutWidths[i] > 0) {
                cursor -= Math.max(0, gaps[i]) * scale;
                near[i] = cursor;
                cursor -= layoutWidths[i] * scale;
            }
        }
        return new Plan(scale, near, layoutWidths, total, dropped);
    }

    /**
     * Drops the least important surviving slot and reports whether it dropped anything.
     *
     * <p>Scans from the end because index order is priority order, so the last surviving
     * index is always the next victim.
     */
    private static boolean dropLowest(boolean[] dropped) {
        for (int i = dropped.length - 1; i >= 0; i--) {
            if (!dropped[i]) {
                dropped[i] = true;
                return true;
            }
        }
        return false;
    }
}
