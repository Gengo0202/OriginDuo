# The capsule arrangement: the outline is the gauge, the dots interrupt it, and
# the two interior readings share it with the dot rows.
#
# What this covers that the other suites cannot: every one of them renders the
# glyph into a square host, where a shape that overflows vertically is invisible
# because the ink box is fitted by whichever axis binds. The capsule's real host
# is 28dp x 20dp - wider than it is tall - so it is fitted by WIDTH, and anything
# taller than the outline runs off the top and bottom of the host. That failure
# mode only appears at the real aspect ratio, which is what this renders at.
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

# --- source contract pins ------------------------------------------------------
# The statements the pixel test below cannot see, because they are about numbers
# agreeing rather than about ink appearing.

# The dot rows are drawn on the outline's own path, not on the stroke's inner
# edge. The stroke straddles its path, so CAP_TOP IS the line's centre; adding
# half a stroke - the obvious-looking thing - hangs the dots below the line they
# are meant to be set into, which at 84x60 reads as beads under the outline
# rather than as dots interrupting it.
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
 * Renders the real capsule at its real host size and inspects the pixels.
 *
 * <p>The host is 28dp x 20dp, which is 84x60 at density 3 - the measurement the
 * whole arrangement is designed against, and the size the device reported.
 */
final class CapsuleTest {

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
        // Charging is where the role colour is unambiguous green, so the growth
        // is measured there. The track is constant underneath and the fill goes
        // over it, so the coloured length has to rise with the level.
        final Stats c10 = stats(render(cfg, 10, true, 0, new int[] { 4, 4 }, ""));
        final Stats c50 = stats(render(cfg, 50, true, 0, new int[] { 4, 4 }, ""));
        final Stats c90 = stats(render(cfg, 90, true, 0, new int[] { 4, 4 }, ""));
        ok("the capsule paints something at all", c50.ink > 0);
        ok("the charged part grows with the level",
                c10.coloured < c50.coloured && c50.coloured < c90.coloured);

        // ---- green is the REMAINING charge -----------------------------------
        // The written spec: green is what is left, grey is the whole 100% track.
        // So the outline is drawn grey in full and the green part is the level
        // itself - a full battery is almost all green, a nearly flat one almost
        // all grey. Asserted on the pixels because it is exactly the inversion a
        // later "cleanup" would silently undo, and a comment cannot catch it.
        final Stats charged = stats(render(cfg, 100, true, 0, new int[] { 4, 4 }, ""));
        final Stats flat = stats(render(cfg, 1, true, 0, new int[] { 4, 4 }, ""));
        ok("a full battery fills more of the outline than a flat one",
                charged.coloured > flat.coloured);
        ok("both still show the track they are measured against",
                charged.track > 0 && flat.track > 0);
        // The two together are the whole outline, so the coloured part has to be
        // strictly less than the total: a fill that ran the entire outline would
        // leave no track to run down, which is the shape being a gauge rather
        // than a decoration. Measured against the ink, not against itself.
        ok("the fill never covers the entire outline",
                charged.coloured < charged.ink - charged.coloured);
        // And it is monotone across the whole range, not just at the two ends.
        final Stats c25 = stats(render(cfg, 25, true, 0, new int[] { 4, 4 }, ""));
        final Stats c75 = stats(render(cfg, 75, true, 0, new int[] { 4, 4 }, ""));
        ok("the fill is monotone across the range",
                flat.coloured < c25.coloured && c25.coloured < c50.coloured
                        && c50.coloured < c75.coloured && c75.coloured < charged.coloured);

        // ---- it fits the host -------------------------------------------------
        // The failure this catches: a reading sized by the width the slot allows
        // but not by the height the dot rows leave. At 84x60 the capsule is only
        // ~44px tall, so a width-only fit sends the digits and the arcs past the
        // host's top and bottom, where the platform clips them.
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
        // The capsule fills the width it is given, which is what "fitted by
        // width" means - and what makes the vertical extent the real constraint.
        ok("the outline spans the host's width", stats(cases[0]).width() >= HOST_W - 1);

        // ---- the outline is broken where the dots interrupt it ---------------
        // With the mobile switch on, the outline has a gap at 12 and 6 o'clock;
        // with it off the outline is closed. Sampling the top-centre column just
        // above the centreline - where the gap is, and where the dots are not -
        // tells the two apart: closed puts stroke ink there, broken leaves it
        // empty for the dots.
        final BufferedImage broken = render(cfg, 50, false, 0, new int[] { 4, 4 }, "");
        final Stats brokenTop = topCentreInk(broken);
        cfg.showMobile = false;
        final BufferedImage closed = render(cfg, 50, false, 0, new int[] { 4, 4 }, "");
        final Stats closedTop = topCentreInk(closed);
        cfg.showMobile = true;
        ok("the closed outline crosses the top centre", closedTop.ink > 0);
        ok("and the broken one leaves that gap to the dots", brokenTop.ink == 0);

        // ---- the right-hand slot is arbitrated, not stacked -------------------
        // A charger takes the slot from the arcs and from the percentage, exactly
        // as it takes the ring's notch - so the Wi-Fi level must not change what
        // a charging frame looks like.
        ok("the charging bolt does not depend on the Wi-Fi level",
                same(render(cfg, 50, true, 3, new int[] { 4, 4 }, ""),
                     render(cfg, 50, true, 0, new int[] { 4, 4 }, "")));

        System.out.println("PASS: " + checks
                + " cases over the capsule arrangement at 84x60");
    }

    /** Paints the capsule into a panel-coloured bitmap at the real host size. */
    private static BufferedImage render(TrioSettings cfg, int level, boolean charging,
                                        int wifi, int[] slots, String type) {
        final BufferedImage img =
                new BufferedImage(HOST_W, HOST_H, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(BG_R, BG_G, BG_B));
        g.fillRect(0, 0, HOST_W, HOST_H);
        final Canvas canvas = new Canvas(g);
        TrioRenderer.drawInto(canvas, HOST_W, HOST_H, level, charging, false, false,
                level <= 15, wifi, level, slots, type, 0xFFFFFFFF, cfg, false);
        g.dispose();
        return img;
    }

    /** The ink summary of a whole render. */
    private static Stats stats(BufferedImage img) {
        return measure(img, 0, 0, HOST_W, HOST_H);
    }

    /**
     * The ink on the top-centre column, sampled in a band just above the
     * outline's centreline.
     *
     * <p>The band deliberately excludes the centreline itself, where the dot row
     * sits: this is asking about the stroke, and the dots would answer yes for
     * both the broken and the closed outline.
     */
    private static Stats topCentreInk(BufferedImage img) {
        final int cx = HOST_W / 2;
        return measure(img, cx - 1, 12, cx + 2, 18);
    }

    /**
     * Counts ink, the coloured part of it, the track, and the ink's bounds.
     *
     * <p>Brightness, not hue, separates the two passes: the track is the same
     * colour as the fill at {@code trackAlpha}, which on this dark panel lands as
     * a dim grey, while the fill is drawn at full opacity. Counting by hue would
     * miss every role colour that is not green - white for a normal battery, red
     * below critical, yellow in power saving.
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
                final int lum = (r * 299 + g * 587 + b * 114) / 1000;
                if (lum >= 150) {
                    s.coloured++;
                } else {
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
