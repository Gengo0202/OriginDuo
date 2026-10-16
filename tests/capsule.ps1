# The capsule arrangement: the outline is the gauge, the dots interrupt it, and
# the two interior readings share it with the dot rows.
#
# What this covers that the other suites cannot: every one of them renders the
# glyph into a square host, where a shape that overflows vertically is invisible
# because the ink box is fitted by whichever axis binds. The capsule's real host
# is wider than it is tall, so it is fitted by WIDTH, and anything taller than the
# outline runs off the top and bottom of the host. That failure mode only appears
# at the real aspect ratio, which is what this renders at.
#
# The host is produced by the module rather than by MIUI: the bar measures the
# battery icon to 28dp (the width of MIUI's own narrow battery), and
# TrioGeometry.capHostWidthPx answers with the width the capsule's aspect ratio
# needs for the height on offer. Both sizes are rendered here, because the stock
# one is what a regression would fall back to and the widened one is what ships.
#
# ASCII only on purpose: PowerShell 5.1 reads BOM-less files as ANSI, so a script
# without a BOM must not contain non-ASCII bytes.
#
# Usage:  powershell -File tests\capsule.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root '.tools\jdk\jdk-21.0.12.1+1\bin'
$javac = Join-Path $jdk 'javac.exe'
$java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }

$trio = Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo'
$geomPath = Join-Path $trio 'TrioGeometry.java'
$prefsPath = Join-Path $trio 'Prefs.java'
$rendererPath = Join-Path $trio 'TrioRenderer.java'
$settingsPath = Join-Path $trio 'TrioSettings.java'

$geom = [System.IO.File]::ReadAllText($geomPath)
$prefs = [System.IO.File]::ReadAllText($prefsPath)
$renderer = [System.IO.File]::ReadAllText($rendererPath)
$settings = [System.IO.File]::ReadAllText($settingsPath)
$hooks = [System.IO.File]::ReadAllText((Join-Path $trio 'TrioHooks.java'))

# --- source contract pins ------------------------------------------------------
# The statements the pixel test below cannot see, because they are about numbers
# agreeing rather than about ink appearing.

# The host is MEASURED to the capsule's aspect ratio, not left at MIUI's stock
# 28dp. This is the one thing that decides whether the glyph is a wide capsule or
# a squashed pill, and no pixel test in a square host can see it: the ink box is
# fitted by whichever axis binds, so a too-narrow host just draws a smaller
# capsule and every "it fits" assertion still holds. The device is where it shows
# - 84x46px inside a 60px-tall bar - which is why the pin is here.
if (![regex]::IsMatch($hooks,
        'Refl\.method\(icon, "onMeasure", int\.class, int\.class\)')) {
    throw 'the capsule no longer hooks the icon view''s onMeasure'
}
if (![regex]::IsMatch($hooks, 'TrioGeometry\.capHostWidthPx\(height\)')) {
    throw 'the icon view no longer asks the capsule for its host width'
}
# Only the capsule may be re-measured. A pass-through for the other two keeps
# MIUI's own measurement standing, so this cannot move the ring or the rectangle.
if (![regex]::IsMatch($hooks,
        'if \(!a\.glyph \|\| !a\.capsule\) \{\s*\r?\n\s*return result;')) {
    throw 'the icon re-measure is no longer gated on the capsule alone'
}
# Resolved on View, not on the icon class: Refl.method uses getDeclaredMethod,
# which does not walk up the hierarchy, so asking the subclass returns null and
# the widening silently never happens.
if (![regex]::IsMatch($hooks,
        'Refl\.method\(View\.class, "setMeasuredDimension",\s*\r?\n\s*int\.class, int\.class\)')) {
    throw 'setMeasuredDimension is no longer resolved on View itself'
}
# The width must NOT be scaled by the overall-size setting: that multiplier is
# already applied to the drawing scale, so folding it in here as well would shrink
# the ink by the square of the setting.
if ([regex]::IsMatch($hooks, 'capHostWidthPx[\s\S]{0,500}glyphFit')) {
    throw 'the host width is scaled by the overall-size setting as well as the drawing'
}

# The dot rows are drawn on the outline's own path, not on the stroke's inner
# edge. The stroke straddles its path, so CAP_TOP IS the line's centre; adding
# half a stroke - the obvious-looking thing - hangs the dots below the line they
# are meant to be set into, which reads as beads under the outline rather than as
# dots interrupting it.
if (![regex]::IsMatch($geom,
        'static float capDotTopY\(\) \{\s*\r?\n\s*return CAP_TOP;')) {
    throw 'the top dot row no longer sits on the outline''s own centreline'
}
if (![regex]::IsMatch($geom,
        'static float capDotBottomY\(\) \{\s*\r?\n\s*return CAP_BOTTOM;')) {
    throw 'the bottom dot row no longer sits on the outline''s own centreline'
}

# Both interior readings are sized in one place, so the time and the percentage
# cannot disagree about how big they are. A second fitSize call with its own
# numbers is how that drifts.
$sizes = [regex]::Matches($renderer, 'capsuleTextSize\(')
if ($sizes.Count -lt 4) {
    throw "the capsule readings no longer share one size rule ($($sizes.Count) references)"
}

# The two interior readings are bounded by the height the dot rows leave, not
# only by the width the slot offers. That second bound is the one the other two
# arrangements never needed, and dropping it sends the digits over the outline.
if (![regex]::IsMatch($renderer, 'TrioGeometry\.capTextMaxSize\(stroke\)')) {
    throw 'the capsule readings are no longer bounded by the interior height'
}

# The style is honoured through the same clamp every other style goes through,
# and the clamp's ceiling is the capsule: a stored value above it still has to
# degrade to a drawable style rather than to a blank glyph.
$maxStyle = [regex]::Match($prefs, 'static final int MAX_TRIO_STYLE = STYLE_(\w+);')
if (!$maxStyle.Success -or $maxStyle.Groups[1].Value -ne 'CAPSULE') {
    throw 'MAX_TRIO_STYLE no longer reaches the capsule'
}
if (![regex]::IsMatch($settings,
        'p\.getInt\(Prefs\.KEY_TRIO_STYLE, Prefs\.DEF_TRIO_STYLE\),\s*\r?\n\s*Prefs\.MIN_TRIO_STYLE, Prefs\.MAX_TRIO_STYLE\)')) {
    throw 'the stored style is no longer clamped on read'
}

# The settings screen offers all three, in ordinal order - the dropdown stores an
# index, so a list that is short or misordered selects the wrong arrangement.
$screen = [System.IO.File]::ReadAllText((Join-Path $trio 'ui\SettingsScreen.kt'))
$styleList = [regex]::Match($screen,
    '(?s)val styles = listOf\((.*?)\r?\n\s*\)').Groups[1].Value
if (!$styleList) { throw 'could not read the style dropdown from SettingsScreen' }
# Order is the contract, so check the offsets rather than mere presence: the
# dropdown stores an index, and a list that is short or misordered selects the
# wrong arrangement without failing to compile or to render.
$iRing = $styleList.IndexOf('trio_style_ring')
$iRect = $styleList.IndexOf('trio_style_rect')
$iCaps = $styleList.IndexOf('trio_style_capsule')
if ($iRing -lt 0) { throw 'the style dropdown no longer offers trio_style_ring' }
if ($iRect -lt 0) { throw 'the style dropdown no longer offers trio_style_rect' }
if ($iCaps -lt 0) { throw 'the style dropdown no longer offers trio_style_capsule' }
if (!($iRing -lt $iRect -and $iRect -lt $iCaps)) {
    throw 'the style dropdown is no longer in ordinal order'
}

# --- generated probe -----------------------------------------------------------
$src = Join-Path $root 'work\capsule\src'
$classes = Join-Path $root 'work\capsule\classes'
if (Test-Path $src) { Remove-Item -Recurse -Force $src }
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path (Join-Path $src 'io\github\yixing233\hyperduo') | Out-Null
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$probe = @'
package io.github.yixing233.hyperduo;

import android.graphics.Canvas;
import java.awt.image.BufferedImage;

/**
 * Renders the real capsule at both host sizes and inspects the pixels.
 *
 * <p>The stock host is MIUI's battery icon, 28dp x 20dp - 84x60 at density 3. The
 * widened one is what the module asks for instead, and is what the device shows.
 */
final class CapsuleTest {

    /** MIUI's own battery icon slot, 28dp x 20dp at density 3. */
    private static final int HOST_W = 84;
    private static final int HOST_H = 60;

    /** The panel the glyph is drawn onto; its colour is the "no ink" reference. */
    private static final int BG_R = 0x1C;
    private static final int BG_G = 0x1B;
    private static final int BG_B = 0x1F;

    private static int checks;

    public static void main(String[] args) {
        final TrioSettings cfg = TrioSettings.defaults();
        cfg.trioStyle = Prefs.STYLE_CAPSULE;
        cfg.showWifi = true;
        cfg.showMobile = true;
        cfg.showValue = true;
        // The network type is off by default, so the percentage owns the
        // right-hand slot unless a case asks otherwise.
        TrioState.sClockText = "19:41";

        // ---- the outline itself is the gauge ---------------------------------
        // Charging is where the role colour is unambiguous green, so the growth is
        // measured there. The track is constant underneath and the fill goes over
        // it, so the coloured length has to rise with the level.
        final Stats c10 = stats(render(cfg, 10, true, 0, new int[] { 4, 4 }, ""));
        final Stats c50 = stats(render(cfg, 50, true, 0, new int[] { 4, 4 }, ""));
        final Stats c90 = stats(render(cfg, 90, true, 0, new int[] { 4, 4 }, ""));
        ok("the capsule paints something at all", c50.ink > 0);
        ok("the charged part grows with the level",
                c10.coloured < c50.coloured && c50.coloured < c90.coloured);

        // ---- green is the REMAINING charge -----------------------------------
        // The written spec: green is what is left, grey is the whole 100% track.
        // So the outline is drawn grey in full and the green part is the level
        // itself - a full battery is almost all green, a nearly flat one almost all
        // grey. Asserted on the pixels because it is exactly the inversion a later
        // "cleanup" would silently undo, and a comment cannot catch it.
        final Stats charged = stats(render(cfg, 100, true, 0, new int[] { 4, 4 }, ""));
        final Stats flat = stats(render(cfg, 1, true, 0, new int[] { 4, 4 }, ""));
        ok("a full battery fills more of the outline than a flat one",
                charged.coloured > flat.coloured);
        ok("both still show the track they are measured against",
                charged.track > 0 && flat.track > 0);
        // At 100% the fill SHOULD cover nearly the whole outline - that is what
        // "full" means for a gauge whose track is the shape itself - so the claim
        // worth pinning is the other end: a nearly flat battery has to leave most
        // of the outline in the track colour. An earlier version asserted
        // "the fill never covers the entire outline" at full, which the corrected
        // measurement above showed to be false of a correct drawing; it had been
        // passing only because the old luminance split was counting white text
        // rather than the fill.
        ok("a full battery does cover nearly all of it",
                charged.coloured > charged.track * 3);
        ok("while a flat battery leaves most of it as track",
                flat.track > flat.coloured * 3);
        // And it is monotone across the range, not just at the two ends.
        final Stats c25 = stats(render(cfg, 25, true, 0, new int[] { 4, 4 }, ""));
        final Stats c75 = stats(render(cfg, 75, true, 0, new int[] { 4, 4 }, ""));
        ok("the fill is monotone across the range",
                flat.coloured < c25.coloured && c25.coloured < c50.coloured
                        && c50.coloured < c75.coloured && c75.coloured < charged.coloured);

        // ---- it fits the host -------------------------------------------------
        final BufferedImage[] cases = {
                render(cfg, 100, false, 3, new int[] { 4, 4 }, ""),
                render(cfg, 73, false, 3, new int[] { 4, 3 }, ""),
                render(cfg, 45, true, 0, new int[] { 3, 2 }, ""),
                render(cfg, 8, false, 2, new int[] { 2, 1 }, ""),
                render(cfg, 61, false, 0, new int[] { -1, -1 }, ""),
        };
        for (int i = 0; i < cases.length; i++) {
            final Stats s = stats(cases[i]);
            ok("case " + i + " paints nothing outside the host",
                    s.minX >= 0 && s.minY >= 0 && s.maxX <= HOST_W - 1 && s.maxY <= HOST_H - 1);
            ok("case " + i + " keeps the whole reading inside the host",
                    s.height() <= HOST_H);
        }

        // ---- the widened host, which is the size it really gets --------------
        // The bar measures the icon to 28dp, and the module answers with the width
        // the capsule's aspect ratio needs for the height it was offered.
        //
        // Rendered here rather than left to the device because the difference is
        // visible and quantifiable: at the stock width the glyph is 46px tall in a
        // 60px bar, and the whole point of the re-measure is that it stops being.
        final int wideW = TrioGeometry.capHostWidthPx(HOST_H);
        ok("the requested host is wider than the stock icon slot", wideW > HOST_W);
        final BufferedImage wide = renderAt(cfg, wideW, HOST_H, 73, false, 3,
                new int[] { 4, 3 }, "");
        final Stats w = stats(wide);
        ok("the widened host paints nothing outside itself",
                w.minX >= 0 && w.minY >= 0 && w.maxX <= wideW - 1 && w.maxY <= HOST_H - 1);
        // The host is now exactly the ink box's aspect, so BOTH axes bind at once
        // and the glyph fills the bar's full height. That is the assertion the
        // stock-width host cannot make: there the height is left over.
        ok("and the glyph uses the host's full height", w.height() >= HOST_H - 2);
        ok("while still spanning its width", w.width() >= wideW - 2);
        // Growing the host must not change the shape's proportions: the capsule is
        // the same design either way, just drawn larger.
        final Stats stock = stats(cases[1]);
        close("the aspect is the same at both host sizes",
                w.width() / (double) w.height(),
                stock.width() / (double) stock.height(), 0.12);

        // ---- the outline is broken where the dots interrupt it ---------------
        // With the mobile switch on, the outline has a gap at 12 and 6 o'clock and
        // the four dots are drawn in it; with it off, one continuous stroke runs the
        // whole way.
        //
        // Measured only across the FLAT RUN between the end caps, not the whole
        // host: the caps curve away below the top edge, so even a closed outline
        // lights far fewer than all the columns on that row, and an assertion
        // written against the full width fails on a perfectly correct drawing.
        //
        // The row and the run both come from the geometry rather than from literal
        // pixels, because they move with the aspect ratio.
        final int hostW = TrioGeometry.capHostWidthPx(HOST_H);
        final float scale = HOST_H / TrioGeometry.CAP_INK_H;
        final float tx = (hostW - TrioGeometry.CAP_INK_W * scale) / 2f
                - TrioGeometry.CAP_INK_X * scale;
        final float ty = (HOST_H - TrioGeometry.CAP_INK_H * scale) / 2f
                - TrioGeometry.CAP_INK_Y * scale;
        final int rowY = Math.round(ty + TrioGeometry.CAP_TOP * scale);
        final int runFrom = (int) Math.ceil(tx + TrioGeometry.capFlatLeft() * scale) + 2;
        final int runTo = (int) Math.floor(tx + TrioGeometry.capFlatRight() * scale) - 2;
        final int runWidth = runTo - runFrom + 1;
        ok("the sampled run is a sensible width", runWidth > hostW / 3);

        // Both renders at the same host and the same reading, differing only in the
        // mobile switch - that is the whole variable under test.
        final BufferedImage broken = renderAt(cfg, hostW, HOST_H, 50, false, 0,
                new int[] { 4, 4 }, "");
        cfg.showMobile = false;
        final BufferedImage closed = renderAt(cfg, hostW, HOST_H, 50, false, 0,
                new int[] { 4, 4 }, "");
        cfg.showMobile = true;
        final int closedLit = litColumns(closed, rowY, runFrom, runTo);
        final int brokenLit = litColumns(broken, rowY, runFrom, runTo);
        // The closed outline is a continuous stroke, so every column of the run
        // carries ink. This is what would catch the cut being applied when it should
        // not be - the outline silently opening under a style that draws no rows.
        ok("the closed outline runs unbroken along its top edge", closedLit == runWidth);

        // With the rows on, the gap is where the closed row had stroke and the open
        // one has none. Counted per column rather than as a ratio of the run, because
        // the dots deliberately fill most of the gap: the reference leaves only a
        // narrow break between each dot and the line, so "mostly gap" is not true of
        // a correct drawing and an assertion written that way fails on one.
        int gapColumns = 0;
        for (int x = runFrom; x <= runTo; x++) {
            if (hasInk(closed, x, rowY) && !hasInk(broken, x, rowY)) {
                gapColumns++;
            }
        }
        ok("the rows cut the outline open", gapColumns > 0);
        ok("and the cut is a break, not the whole run", gapColumns < runWidth / 3);
        // The dots ride in that break, so the open row is still lit - an outline
        // that simply vanished would be near zero.
        ok("while the dots themselves carry ink", brokenLit > 0);
        ok("and the broken row is dimmer than the closed one", brokenLit < closedLit);

        // ---- the dots keep clear of the stroke ends --------------------------
        // The gap has to be WIDER than the dots. When it was not - the first cut
        // sized the cut to the dots' own span and nothing more - the outer two
        // merged into the ends of the stroke and the row read as a line with two
        // lumps on it rather than as four dots interrupting a line.
        //
        // Stated as geometry rather than as pixels: the cut and the dots' span are
        // both design-unit quantities, and the clearance is a fraction of the
        // capsule's height, so this holds at every stroke width without needing a
        // render to confirm it.
        final float dotSpan = 3f * TrioGeometry.CAP_DOT_PITCH + 2f * TrioGeometry.CAP_DOT_R;
        final float cut = 2f * TrioGeometry.capDotReach();
        ok("the outline is cut wider than the dots themselves", cut > dotSpan);
        ok("and the clearance is a real gap, not a rounding error",
                cut - dotSpan > 1f);
        // But not so wide that the outline reads as broken rather than as a row
        // sitting in it: the reference's clearance is a few percent of the height.
        ok("while the clearance stays small enough to read as one outline",
                (cut - dotSpan) / 2f < (TrioGeometry.CAP_BOTTOM - TrioGeometry.CAP_TOP) * 0.12f);

        // ---- the readings keep a margin from the outline's inner face ---------
        // The time sits in the left slot, right where the end cap curves back in
        // underneath it. With the slot starting at the interior's own edge the
        // digits begin immediately at the stroke's inner face, and at the largest
        // text size the first digit touches the line.
        final float strokePx = Prefs.DEF_RING_STROKE * TrioGeometry.CAP_STROKE_RATIO;
        final float height = TrioGeometry.CAP_BOTTOM - TrioGeometry.CAP_TOP;
        ok("the readings start inside the outline's inner face",
                TrioGeometry.capTextLeft(strokePx) > TrioGeometry.capInteriorLeft(strokePx));
        ok("and stop inside it on the far side",
                TrioGeometry.capTextRight(strokePx) < TrioGeometry.capInteriorRight(strokePx));
        // The margin is the reference's own share of the height - about 8.6%, which
        // is the number measured off it. Not "at least the stroke's width": the
        // reference's margin is in fact smaller than its stroke, so that assertion
        // would fail on a faithful drawing. It was written by mistake and this is
        // what replaced it.
        final float inset = TrioGeometry.capTextLeft(strokePx)
                - TrioGeometry.capInteriorLeft(strokePx);
        close("the margin matches the reference's share of the height",
                inset / height, 0.086, 0.01);
        // And it has to be big enough to see: a margin of a fraction of a pixel is
        // no margin at all, which is the state this replaced.
        ok("and is a visible margin, not a rounding error", inset > 2f);

        // ---- the charge is spent from the bottom of the right bar ------------
        // The two dot rows divide the outline into a left bar and a right bar, and
        // the reading is taken off them as two bars: full is entirely lit, and the
        // charge goes from the bottom of the RIGHT one, counterclockwise. So the
        // order the segments go dark is botR, capR, topR, topL, capL, botL.
        //
        // Asserted as that ORDER rather than as a set of levels, because the order
        // is the whole requirement and any of the six could be mis-wired on its
        // own. The earlier implementation ran the other way - counterclockwise from
        // the top gap - which spent the charge from the top left and lit the wrong
        // half of the shape at every level below full.
        final boolean[][] lit = new boolean[7][];
        final int[] probeLevels = { 100, 85, 60, 50, 40, 10 };
        for (int i = 0; i < probeLevels.length; i++) {
            lit[i] = segments(renderAt(cfg, wideW, HOST_H, probeLevels[i], true, 0,
                    new int[] { 4, 4 }, ""));
        }
        // Full: everything on.
        ok("a full battery lights the whole outline",
                lit[0][0] && lit[0][1] && lit[0][2] && lit[0][3] && lit[0][4] && lit[0][5]);
        // The right bar empties before the left one does: at half, the right bar is
        // dark and the left is still lit.
        final int half = 3;
        ok("at half the right bar is spent", !lit[half][3] && !lit[half][4] && !lit[half][5]);
        ok("and the left bar is not", lit[half][0] && lit[half][1] && lit[half][2]);
        // Segment by segment, in the order they must go dark.
        ok("the first to go is the bottom of the right bar", !lit[1][3]);
        ok("then the right cap", lit[1][4] && !lit[2][4]);
        ok("then the top of the right bar", lit[2][5] && !lit[half][5]);
        ok("then the top of the left bar", lit[half][0] && !lit[4][0]);
        ok("then the left cap", lit[4][1] && !lit[5][1]);
        // At the very bottom only the foot of the left bar is left, which is the
        // far end of the counterclockwise walk from where it started.
        ok("and the foot of the left bar is the last to go",
                lit[5][2] || lit[4][2]);

        // ---- the right-hand slot is arbitrated, not stacked -------------------
        // A charger takes the slot from the arcs and from the percentage, exactly as
        // it takes the ring's notch - so the Wi-Fi level must not change what a
        // charging frame looks like.
        ok("the charging bolt does not depend on the Wi-Fi level",
                same(render(cfg, 50, true, 3, new int[] { 4, 4 }, ""),
                     render(cfg, 50, true, 0, new int[] { 4, 4 }, "")));

        System.out.println("PASS: " + checks
                + " cases over the capsule arrangement at both host sizes");
    }

    /** Paints the capsule into a panel-coloured bitmap at MIUI's stock host size. */
    private static BufferedImage render(TrioSettings cfg, int level, boolean charging,
                                        int wifi, int[] slots, String type) {
        return renderAt(cfg, HOST_W, HOST_H, level, charging, wifi, slots, type);
    }

    /** The same, at an arbitrary host size - used for the widened measurement. */
    private static BufferedImage renderAt(TrioSettings cfg, int w, int h, int level,
                                          boolean charging, int wifi, int[] slots,
                                          String type) {
        final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(BG_R, BG_G, BG_B));
        g.fillRect(0, 0, w, h);
        final Canvas canvas = new Canvas(g);
        TrioRenderer.drawInto(canvas, w, h, level, charging, false, false,
                level <= 15, wifi, level, slots, type, 0xFFFFFFFF, cfg, false);
        g.dispose();
        return img;
    }

    /**
     * The ink summary of a whole render.
     *
     * <p>Over the image's own dimensions, not {@link #HOST_W}/{@link #HOST_H}: the
     * widened host is a different size, and measuring it inside the stock bounds
     * would silently ignore everything past column 83 - which is where most of the
     * widened glyph is, and would make both the "nothing outside the host" and the
     * "spans its width" assertions vacuous rather than wrong.
     */
    private static Stats stats(BufferedImage img) {
        return measure(img, 0, 0, img.getWidth(), img.getHeight());
    }

    /** Whether one pixel carries ink. */
    private static boolean hasInk(BufferedImage img, int x, int y) {
        final int argb = img.getRGB(x, y);
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        return Math.abs(r - BG_R) + Math.abs(g - BG_G) + Math.abs(b - BG_B) >= 24;
    }

    /**
     * Which of the outline's six pieces carry the charged colour, for one render.
     *
     * <p>Returns {@code {topL, capL, botL, botR, capR, topR}} - the left bar's three
     * pieces first, then the right bar's, because that is the order they are read
     * in and the order the fill's direction is stated against.
     *
     * <p>Each piece is sampled at its own midpoint, in host pixels, through the same
     * transform {@code TrioRenderer.drawInto} applies. The end caps are sampled at
     * their EXTREME x rather than at their centre: a stadium's cap is a half-circle,
     * so at the centre of that circle the stroke is at the top and the bottom and
     * the mid-height sample misses it entirely - which is how an earlier version of
     * this reported every cap unlit at every level.
     */
    private static boolean[] segments(BufferedImage img) {
        final float scale = Math.min(img.getWidth() / TrioGeometry.CAP_INK_W,
                img.getHeight() / TrioGeometry.CAP_INK_H);
        final float tx = (img.getWidth() - TrioGeometry.CAP_INK_W * scale) / 2f
                - TrioGeometry.CAP_INK_X * scale;
        final float ty = (img.getHeight() - TrioGeometry.CAP_INK_H * scale) / 2f
                - TrioGeometry.CAP_INK_Y * scale;
        final float reach = TrioGeometry.capDotReach();

        final float gapLeft = TrioGeometry.CAP_DOT_CX - reach;
        final float gapRight = TrioGeometry.CAP_DOT_CX + reach;
        final float runLCx = (TrioGeometry.capFlatLeft() + gapLeft) * 0.5f;
        final float runRCx = (gapRight + TrioGeometry.capFlatRight()) * 0.5f;
        final float my = (TrioGeometry.CAP_TOP + TrioGeometry.CAP_BOTTOM) * 0.5f;

        final int lx = Math.round(tx + runLCx * scale);
        final int rx = Math.round(tx + runRCx * scale);
        final int topY = Math.round(ty + TrioGeometry.CAP_TOP * scale);
        final int botY = Math.round(ty + TrioGeometry.CAP_BOTTOM * scale);
        final int capLY = Math.round(ty + my * scale);
        final int capLx = Math.round(tx + TrioGeometry.CAP_LEFT * scale);
        final int capRx = Math.round(tx + TrioGeometry.CAP_RIGHT * scale);

        return new boolean[] {
                charged(img, lx, topY),
                charged(img, capLx, capLY),
                charged(img, lx, botY),
                charged(img, rx, botY),
                charged(img, capRx, capLY),
                charged(img, rx, topY),
        };
    }

    /**
     * Whether the stroke at this point is a role colour rather than the track.
     *
     * <p>Chroma, for the reason given on {@link #measure}: brightness cannot tell
     * the charging green (142) from the track (77) with any threshold that is not a
     * magic number, and the green and the critical red land on opposite sides of
     * 150.
     */
    private static boolean charged(BufferedImage img, int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                final int xx = x + dx;
                final int yy = y + dy;
                if (xx < 0 || yy < 0 || xx >= img.getWidth() || yy >= img.getHeight()) {
                    continue;
                }
                final int argb = img.getRGB(xx, yy);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) >= 30) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * How many columns in {@code x0..x1} along row {@code y} carry ink.
     *
     * <p>Counting columns rather than sampling the centre column matters because the
     * centre is a dot when the rows are on and stroke when they are off - it is
     * painted either way, so a single-column probe would distinguish nothing.
     */
    private static int litColumns(BufferedImage img, int y, int x0, int x1) {
        int lit = 0;
        for (int x = Math.max(0, x0); x <= Math.min(img.getWidth() - 1, x1); x++) {
            if (hasInk(img, x, y)) {
                lit++;
            }
        }
        return lit;
    }

    /**
     * Counts ink, the coloured part of it, the track, and the ink's bounds.
     *
     * <p><b>Chroma, not brightness.</b> An earlier version split the two passes at
     * a luminance of 150, reasoning that the track is drawn dim and the fill at
     * full opacity. That is wrong, and measurably so: the charging green is
     * #34C759, whose luminance is 142 - under the threshold - and the critical red
     * is 116. Both were therefore counted as track, and "coloured" ended up
     * counting only the white readings and the arcs. The monotone-growth assertion
     * still passed, which is the dangerous part: it passed because more of the
     * white text's antialiased edge cleared 150 when green was behind it than when
     * the panel was. The number moved with the level, but for a reason unrelated
     * to the fill.
     *
     * <p>Chroma separates them properly. Every role colour is saturated - green
     * #34C759 spans 147, red #FF3B30 spans 207, amber #F2B900 spans 242 - while
     * the track is the foreground grey over the panel (rgb(77,77,80), spanning 3)
     * and the readings are white (spanning 0). So "coloured" is exactly the role
     * colour and nothing else, and `track` is neutral and dim, which keeps the
     * white readings out of both.
     */
    private static Stats measure(BufferedImage img, int x0, int y0, int x1, int y1) {
        final Stats s = new Stats();
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                final int argb = img.getRGB(x, y);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (Math.abs(r - BG_R) + Math.abs(g - BG_G) + Math.abs(b - BG_B) < 24) {
                    continue;
                }
                s.ink++;
                if (x < s.minX) { s.minX = x; }
                if (x > s.maxX) { s.maxX = x; }
                if (y < s.minY) { s.minY = y; }
                if (y > s.maxY) { s.maxY = y; }
                final int hi = Math.max(r, Math.max(g, b));
                final int lo = Math.min(r, Math.min(g, b));
                if (hi - lo >= 30) {
                    s.coloured++;
                } else if (hi < 150) {
                    // Neutral AND dim: the track. The white readings are neutral but
                    // bright, so they are excluded here rather than inflating it.
                    s.track++;
                }
            }
        }
        if (s.maxX < 0) { s.minX = 0; s.minY = 0; s.maxX = -1; s.maxY = -1; }
        return s;
    }

    /** One render's ink counts and bounds. */
    private static final class Stats {
        int ink;
        int coloured;
        int track;
        int minX = HOST_W;
        int minY = HOST_H;
        int maxX = -1;
        int maxY = -1;

        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }

    /** True when two renders painted the identical pixels in the identical place. */
    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < HOST_H; y++) {
            for (int x = 0; x < HOST_W; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void ok(String what, boolean held) {
        checks++;
        if (!held) {
            throw new AssertionError(what);
        }
    }

    private static void close(String what, double got, double want, double tol) {
        checks++;
        if (Math.abs(got - want) > tol) {
            throw new AssertionError(what + ": got <" + got + "> want <" + want
                    + "> within <" + tol + ">");
        }
    }
}
'@

[System.IO.File]::WriteAllText(
    (Join-Path $src 'io\github\yixing233\hyperduo\CapsuleTest.java'),
    $probe, (New-Object System.Text.UTF8Encoding($false)))

# --- compile and run -----------------------------------------------------------
$previewRoot = Join-Path $root 'tests\shims'
if (!(Test-Path (Join-Path $previewRoot 'android\graphics\Canvas.java'))) {
    throw "the desktop shims are missing: $previewRoot (see docs/DEVELOPMENT.md, the test section)"
}
$preview = Get-ChildItem -Recurse -Filter *.java $previewRoot | ForEach-Object { $_.FullName }
$shared = @(
    (Join-Path $trio 'Prefs.java'),
    (Join-Path $trio 'TrioSettings.java'),
    (Join-Path $trio 'TrioGeometry.java'),
    (Join-Path $trio 'TrioAppearance.java'),
    (Join-Path $trio 'TrioRenderer.java'))
$sources = $preview + $shared + @(Get-ChildItem -Recurse -Filter *.java $src |
    ForEach-Object { $_.FullName })

& $javac -nowarn -d $classes -encoding UTF-8 $sources
if ($LASTEXITCODE) { throw 'Compilation failed' }
& $java -Xmx128m -cp $classes io.github.yixing233.hyperduo.CapsuleTest
if ($LASTEXITCODE) { throw 'Test failed' }
