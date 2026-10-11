$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac=Join-Path $jdk 'javac.exe'; $java=Join-Path $jdk 'java.exe'
if(!(Test-Path $javac)){$javac='javac';$java='java'}

$trio="$root/app/src/main/java/io/github/yixing233/hyperduo"
$hooks=Get-Content "$trio/TrioHooks.java" -Raw
$rend=Get-Content "$trio/TrioRenderer.java" -Raw
$geom=Get-Content "$trio/TrioGeometry.java" -Raw
$appear=Get-Content "$trio/TrioAppearance.java" -Raw
$prefs=Get-Content "$trio/Prefs.java" -Raw

# The real production bodies, spliced in unchanged. A rename or a re-shape
# fails loudly here rather than silently testing a copy that drifted. Every
# pattern requires the whole signature and the closing brace of the method, so a
# match can never be whitespace-only and slip past the guard below.
$perfTextSize=[regex]::Match($hooks,'(?s)private static float perfTextSize\(String text, View container, View host\) \{.*?\n    \}').Value
if(!$perfTextSize){throw 'Production perfTextSize missing'}
$perfLineSpacing=[regex]::Match($hooks,'(?s)private static float perfLineSpacing\(TextView view, String text,\s*\n\s*View container\) \{.*?\n    \}').Value
if(!$perfLineSpacing){throw 'Production perfLineSpacing missing'}
$perfLines=[regex]::Match($hooks,'(?s)private static int perfLines\(String text\) \{.*?\n    \}').Value
if(!$perfLines){throw 'Production perfLines missing'}
$perfLineHeight=[regex]::Match($hooks,'(?s)private static float perfLineHeight\(float size, int weight\) \{.*?\n    \}').Value
if(!$perfLineHeight){throw 'Production perfLineHeight missing'}
$outTypeSizePx=[regex]::Match($hooks,'(?s)private static float outTypeSizePx\(View container\) \{.*?\n    \}').Value
if(!$outTypeSizePx){throw 'Production outTypeSizePx missing'}

# The overall-size multiplier, spliced rather than restated. A probe that wrote
# its own `perfScale / 100f` would keep passing if production's clamp were
# dropped or its scale factor changed, and the multiplier is the whole point of
# this round. The three bounds come from Prefs for the same reason.
$perfFit=[regex]::Match($appear,'(?s)public float perfFit\(\) \{.*?\n    \}').Value
if(!$perfFit){throw 'Production perfFit missing'}
$clamp=[regex]::Match($prefs,'(?s)static int clamp\(int value, int min, int max\) \{.*?\n    \}').Value
if(!$clamp){throw 'Production Prefs.clamp missing'}
$minPerfScale=[regex]::Match($prefs,'static final int MIN_PERF_SCALE = ([0-9]+);').Groups[1].Value
$maxPerfScale=[regex]::Match($prefs,'static final int MAX_PERF_SCALE = ([0-9]+);').Groups[1].Value
if(!$minPerfScale -or !$maxPerfScale){throw 'could not read the perf-scale bounds from Prefs'}

# The renderer's four measurement methods, so the probe measures with the same
# arithmetic the glyph is drawn with rather than with a re-implementation.
$inkScale1=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h\) \{.*?\n    \}').Value
if(!$inkScale1){throw 'Production inkScale(w,h) missing'}
$inkScale2=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h, boolean rect\) \{.*?\n    \}').Value
if(!$inkScale2){throw 'Production inkScale(w,h,rect) missing'}
$inkScale3=[regex]::Match($rend,'(?s)static float inkScale\(int w, int h, float inkW, float inkH\) \{.*?\n    \}').Value
if(!$inkScale3){throw 'Production inkScale(w,h,inkW,inkH) missing'}
$typefaceFor=[regex]::Match($rend,'(?s)static Typeface typefaceFor\(int weight\) \{.*?\n    \}').Value
if(!$typefaceFor){throw 'Production typefaceFor missing'}

# The geometry and the two floors, read out of the sources so a deliberate
# change propagates and an accidental one shows up as a failed invariant.
$inkW=[regex]::Match($geom,'static final float INK_W = ([0-9.]+)f;').Groups[1].Value
$inkH=[regex]::Match($geom,'static final float INK_H = ([0-9.]+)f;').Groups[1].Value
$rectInkW=[regex]::Match($geom,'static final float RECT_INK_W = ([0-9.]+)f;').Groups[1].Value
$rectInkH=[regex]::Match($geom,'static final float RECT_INK_H = ([0-9.]+)f;').Groups[1].Value
if(!$inkW -or !$inkH -or !$rectInkW -or !$rectInkH){throw 'could not read the ink boxes from TrioGeometry'}
$minSize=[regex]::Match($hooks,'static final float PERF_MIN_SIZE_RATIO = ([0-9.]+)f;').Groups[1].Value
$minSpace=[regex]::Match($hooks,'static final float PERF_MIN_LINE_SPACING = ([0-9.]+)f;').Groups[1].Value
if(!$minSize -or !$minSpace){throw 'could not read the perf floors from TrioHooks'}

# The separator itself, read out of the production source rather than restated
# in the generated test. The separator IS the feature - two readings print one
# per line only because this character is a break - so a probe that hard-coded
# '\n' would keep passing after the join was turned back into a space. The
# matched text is the Java escape as written (e.g. the two characters \ and n),
# which is exactly what has to be emitted inside the char literal below.
$perfBreak=[regex]::Match($hooks,"static final char PERF_BREAK = '(.+?)';").Groups[1].Value
if(!$perfBreak){throw 'could not read PERF_BREAK from TrioHooks'}

# What the pure functions above cannot reach: the wiring only a layout pass
# visits. perfLineSpacing is spliced and tested directly, but nothing on a
# desktop can call refreshPerfLabel - it walks a real view tree - so the two
# statements that keep the leading in step, and the type weight that decides
# which face the reading is measured in, are pinned by their shape instead of by
# a call. Each is a real failure: dropping any one of them leaves the reading
# drawn with stale leading, or with the wrong weight, and no other case here
# would notice.
$perfWeightWired=[regex]::IsMatch($perfLineHeight,
    'PERF_PROBE\.setTypeface\(TrioRenderer\.typefaceFor\(weight\)\);')
$perfResyncWired=[regex]::IsMatch($hooks,
    'perf\.getLineSpacingMultiplier\(\)\s*\n\s*!= perfLineSpacing\(perf, text, container\)')
$perfApplyWired=[regex]::IsMatch($hooks,
    'if \(perf\.getLineSpacingMultiplier\(\) != spacing\) \{\s*\n\s*perf\.setLineSpacing\(0f, spacing\);')
if(!$perfWeightWired){throw 'perfLineHeight no longer applies the type weight'}
if(!$perfResyncWired){throw 'the re-sync test no longer compares the line spacing'}
if(!$perfApplyWired){throw 'the line spacing is no longer written conditionally'}
foreach($v in @($minSize,$minSpace)){
    if([double]$v -le 0 -or [double]$v -ge 1){throw "a perf floor is outside (0,1): $v"}
}

$src="$root/work/perflayout/src"
$classes="$root/work/perflayout/classes"
# Both trees are wiped, not just the classes. The compile step below takes every
# .java under $src, so a file emitted by an earlier version of this script and no
# longer written would stay there and still be compiled - a stale definition that
# can shadow a live one and let the probe pass against code that is not here
# anymore. Re-emitting the whole tree each run is what makes that impossible.
if(Test-Path $src){Remove-Item -Recurse -Force $src}
if(Test-Path $classes){Remove-Item -Recurse -Force $classes}
New-Item -ItemType Directory -Force $classes | Out-Null

function Emit([string]$rel,[string]$text){
    $p=Join-Path $src $rel
    New-Item -ItemType Directory -Force (Split-Path $p -Parent) | Out-Null
    [System.IO.File]::WriteAllText($p,$text,(New-Object System.Text.UTF8Encoding($false)))
}

# The typeface already has a real desktop stand-in and is copied, so a family
# the renderer asks for resolves the same way it does in the preview workbenches.
$gfx=Join-Path $src 'android/graphics'
New-Item -ItemType Directory -Force $gfx | Out-Null
Copy-Item (Join-Path $root 'work/preview/src/android/graphics/Typeface.java') (Join-Path $gfx 'Typeface.java') -Force

# The paint is written here rather than copied. The preview's stand-in returns
# AWT's ascent, which is positive; Android's FontMetrics.ascent is negative, and
# both the size fit and the leading measure a line as descent - ascent. With the
# AWT sign that expression comes out negative, every caller takes its
# "cannot measure" fallback, and the probe would pass while measuring nothing.
Emit 'android/graphics/Paint.java' @'
package android.graphics;

import java.awt.Font;

/** Desktop paint whose font metrics follow Android's sign convention. */
public class Paint {

    public static class FontMetrics {
        public float ascent;
        public float descent;
    }

    private float textSize = 12f;
    private String family = "SansSerif";
    private int typefaceStyle = Font.PLAIN;

    public void setTextSize(float s) { textSize = s; }

    public float getTextSize() { return textSize; }

    public void setTypeface(Typeface t) {
        if (t != null) {
            family = t.family();
            typefaceStyle = t.style();
        }
    }

    public Font font() {
        return new Font(family, typefaceStyle, (int) REFERENCE);
    }

    public FontMetrics getFontMetrics() {
        // Measured once at REFERENCE and scaled, because AWT rounds a font to
        // whole points: at 36.9 and 37 it reports the same metrics, while
        // Android's are exactly linear in the text size. The size fit divides
        // a line height taken at the base size into a size taken at another, so
        // a stub that quantises makes the identity look broken when it is not.
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        final float scale = textSize / REFERENCE;
        FontMetrics out = new FontMetrics();
        // Negated on purpose: Android reports the ascent as the negative
        // distance from the baseline, so a line is descent - ascent.
        out.ascent = -fm.getAscent() * scale;
        out.descent = fm.getDescent() * scale;
        return out;
    }

    /** Font size the metrics are measured at, before scaling to textSize. */
    private static final float REFERENCE = 1000f;
}
'@

Emit 'android/view/View.java' @'
package android.view;

import android.content.res.Resources;

/** A view with a frame, a frame's padding, and a density to measure dp with. */
public class View {

    private int width;
    private int height;
    private int paddingTop;
    private int paddingBottom;
    private final Resources resources = new Resources();

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public int getPaddingTop() { return paddingTop; }

    public int getPaddingBottom() { return paddingBottom; }

    public Resources getResources() { return resources; }

    public void setSize(int w, int h) { width = w; height = h; }

    public void setPadding(int top, int bottom) { paddingTop = top; paddingBottom = bottom; }
}
'@

Emit 'android/view/TextView.java' @'
package android.view;

import android.graphics.Paint;

/**
 * The two things the readout needs from a text view: its paint, and a line
 * spacing multiplier that reads back exactly what was written.
 *
 * <p>Android stores the multiplier verbatim and returns it unchanged from
 * getLineSpacingMultiplier(), so the real one round-trips bit for bit. Storing
 * it here the same way is what makes the fixpoint case below meaningful: the
 * re-sync test compares the stored value against a freshly computed one, and if
 * that comparison could never settle the status bar would post a layout on every
 * pass. A stub that returned a rounded or rewritten number would hide exactly
 * that hazard.
 */
public class TextView extends View {

    private final Paint paint = new Paint();
    private float spacingMultiplier = 1f;

    public Paint getPaint() { return paint; }

    public void setLineSpacing(float add, float mult) { spacingMultiplier = mult; }

    public float getLineSpacingMultiplier() { return spacingMultiplier; }
}
'@

Emit 'android/content/res/Resources.java' @'
package android.content.res;

import android.util.DisplayMetrics;

public class Resources {

    private final DisplayMetrics metrics = new DisplayMetrics();

    public DisplayMetrics getDisplayMetrics() { return metrics; }
}
'@

Emit 'android/util/DisplayMetrics.java' @'
package android.util;

public class DisplayMetrics {
    public float density = 1f;
}
'@

Emit 'io/github/yixing233/hyperduo/TrioGeometry.java' @"
package io.github.yixing233.hyperduo;

/** The four ink boxes the fit is measured against, read from the real source. */
final class TrioGeometry {
    static final float INK_W = $($inkW)f;
    static final float INK_H = $($inkH)f;
    static final float RECT_INK_W = $($rectInkW)f;
    static final float RECT_INK_H = $($rectInkH)f;
}
"@

Emit 'io/github/yixing233/hyperduo/Prefs.java' @"
package io.github.yixing233.hyperduo;

/** The two bounds the fit multiplier clamps itself to, read from the real Prefs. */
final class Prefs {
    static final int MIN_PERF_SCALE = $minPerfScale;
    static final int MAX_PERF_SCALE = $maxPerfScale;

    $clamp
}
"@

Emit 'io/github/yixing233/hyperduo/TrioAppearance.java' @"
package io.github.yixing233.hyperduo;

/** Only the fields the size fit reads, plus the real multiplier. */
final class TrioAppearance {
    boolean rect;
    int typeWeight = 400;
    int outTypeSize = 14;
    int perfScale = 100;

    $perfFit
}
"@

Emit 'io/github/yixing233/hyperduo/TrioConfig.java' @'
package io.github.yixing233.hyperduo;

final class TrioConfig {

    private static final TrioAppearance SNAPSHOT = new TrioAppearance();

    static TrioAppearance appearance() { return SNAPSHOT; }
}
'@

Emit 'io/github/yixing233/hyperduo/TrioRenderer.java' @"
package io.github.yixing233.hyperduo;

import android.graphics.Typeface;

/** The renderer's own measurement methods, spliced so there is one arithmetic. */
final class TrioRenderer {

    private static final Typeface TEXT_BASE = Typeface.create("sans-serif", Typeface.NORMAL);

    $inkScale1

    $inkScale2

    $inkScale3

    $typefaceFor
}
"@

Emit 'io/github/yixing233/hyperduo/PerfLayoutTest.java' @"
package io.github.yixing233.hyperduo;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.TextView;
import android.view.View;

public class PerfLayoutTest {

    static final float PERF_MIN_SIZE_RATIO = $($minSize)f;
    static final float PERF_MIN_LINE_SPACING = $($minSpace)f;
    static final char PERF_BREAK = '$perfBreak';
    private static final Paint PERF_PROBE = new Paint();

    $perfLines

    $perfLineHeight

    $perfTextSize

    $perfLineSpacing

    $outTypeSizePx

    static int checks;

    static void eq(String what, Object got, Object want){
        checks++;
        if(!String.valueOf(got).equals(String.valueOf(want))){
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
    }

    static void ok(String what, boolean fine){
        checks++;
        if(!fine){
            throw new AssertionError(what);
        }
    }

    static void close(String what, float got, float want, float tol){
        checks++;
        if(Math.abs(got - want) > tol){
            throw new AssertionError(what + ": got <" + got + "> want <" + want + ">");
        }
    }

    static View box(int w, int h){
        View v = new View();
        v.setSize(w, h);
        return v;
    }

    public static void main(String[] args){
        TrioConfig.appearance().outTypeSize = 14;
        TrioConfig.appearance().rect = false;
        TrioConfig.appearance().typeWeight = 400;

        View container = box(491, 88);
        container.getResources().getDisplayMetrics().density = 3f;
        final float base = outTypeSizePx(container);
        eq("the base size is the dp setting times the density", base, 42f);

        // ---- one reading keeps the type size whatever the row does.
        final String single = "31\u00B0C";
        final String pair = "6.1W" + PERF_BREAK + "31\u00B0C";
        // The separator is spliced out of the production source above, so the
        // cases below are built from whatever it really is. This is the lock on
        // its value: the feature is that the two readings sit on separate
        // lines, so the separator has to be a line break and nothing else. A
        // change back to ' ' would make every two-line case below a one-line
        // case while still "passing" a probe that had restated '\n' itself.
        eq("the two readings are separated by a line break", (int) PERF_BREAK, (int) '\n');
        eq("a pair is two lines", perfLines(pair), 2);
        eq("a single reading is one", perfLines(single), 1);
        eq("one line keeps the base size", perfTextSize(single, container, box(10, 10)), base);
        eq("one line keeps it even with no host", perfTextSize(single, container, null), base);

        // ---- the pair: shrink to one icon height, but never disappear.
        eq("a pair with no host falls back to the base size", perfTextSize(pair, container, null), base);
        eq("a pair in an unmeasured host falls back too", perfTextSize(pair, container, box(0, 0)), base);
        eq("a host far taller than the pair is clamped at the base size", perfTextSize(pair, container, box(1000, 1000)), base);

        final float lhBase = perfLineHeight(base, TrioConfig.appearance().typeWeight);
        ok("the base line height is positive", lhBase > 0f);

        // The identity below holds only while the fit is deciding: the host has
        // to want a size strictly between the base and the floor, i.e. an icon
        // height between 1.2 and 2 base line heights. Derive the host from the
        // measured line height instead of hard-coding pixel sizes, so the case
        // stays in that window whatever line-height ratio the desktop font has
        // (AWT's SansSerif is taller than the device's face, and a hard-coded
        // host that is fine on one is inside the floor's reach on the other).
        final int iconPx = Math.round(lhBase * 1.8f);
        final View sized = box(1000, iconPx);
        final float iconHeight = iconPx;
        ok("the host is in the window where the fit decides, not the clamps",
                iconHeight > lhBase * 1.2f && iconHeight < lhBase * 2f);

        final float size = perfTextSize(pair, container, sized);
        ok("a pair beside a real icon is smaller than a single reading", size < base);
        ok("but not below the legibility floor", size >= base * PERF_MIN_SIZE_RATIO);
        close("two lines at the chosen size are one icon tall",
                2f * perfLineHeight(size, TrioConfig.appearance().typeWeight), iconHeight, 0.05f);
        close("and the chosen size is the fit's own answer, unclamped",
                size, base * (iconHeight / 2f) / lhBase, 0.05f);

        // A host so small that the fit wants an illegible size: the floor wins.
        final float floored = perfTextSize(pair, container, box(10, 10));
        close("a tiny host hits the floor", floored, base * PERF_MIN_SIZE_RATIO, 0.001f);
        // The floor's own value, stated here as an external fact. The case above
        // compares the answer against the same constant production clamped to, so
        // it holds however that constant moves; this one can fail, and it is what
        // catches the floor being lowered to the point where the reading stops
        // being legible.
        eq("the legibility floor is 0.6 of the type size", PERF_MIN_SIZE_RATIO, 0.6f);
        ok("and the unclamped fit really was below it", perfTextSize(single, container, box(10, 10)) == base);
        // More readings share the same icon, so each line gets less of it. The
        // host is the SAME one the pair was measured in: comparing two line
        // counts in different hosts compares two different fits. The original
        // shape here measured the trio in a tiny host of its own, where
        // perfTextSize answers with the floor whatever the line count is, so
        // hard-coding production's line count to two still passed. In this host
        // the pair fits and the trio does not, so the two answers differ
        // precisely because the count is read.
        final String trio = "6.1W" + PERF_BREAK + "31\u00B0C" + PERF_BREAK + "40\u00B0C";
        eq("three readings are three lines", perfLines(trio), 3);
        final float size3 = perfTextSize(trio, container, sized);
        ok("three lines are smaller than two in the same host", size3 < size);

        // The rect arrangement has a shorter ink box (114.4 against 117.5), so
        // in a host whose width limits the fit it comes out a little smaller.
        // The host is wide enough that the result stays above the floor; a
        // narrower one would put both arrangements on the floor and compare
        // two equal clamped values instead of the arithmetic.
        TrioConfig.appearance().rect = true;
        final float rectSize = perfTextSize(pair, container, box(84, 1000));
        TrioConfig.appearance().rect = false;
        final float roundSize = perfTextSize(pair, container, box(84, 1000));
        ok("both arrangements are off the floor here",
                rectSize > base * PERF_MIN_SIZE_RATIO && roundSize > base * PERF_MIN_SIZE_RATIO);
        ok("the rect arrangement's shorter box fits a smaller reading", rectSize < roundSize);
        ok("the two ink boxes are actually different",
                TrioGeometry.INK_H != TrioGeometry.RECT_INK_H && TrioGeometry.INK_W != TrioGeometry.RECT_INK_W);

        // ---- the leading: only a stack is squeezed, and only as far as the floor.
        final TextView perf = new TextView();
        perf.getPaint().setTextSize(base);
        final Paint.FontMetrics fm = perf.getPaint().getFontMetrics();
        final float lh = fm.descent - fm.ascent;
        ok("the probe font has a positive line height", lh > 0f);

        eq("no break, no squeeze", perfLineSpacing(perf, single, container), 1f);
        eq("an empty container leaves the leading alone", perfLineSpacing(perf, pair, box(491, 0)), 1f);
        eq("a row taller than the stack leaves it alone",
                perfLineSpacing(perf, pair, box(491, Math.round(lh * 2.5f))), 1f);
        // The stack is two lines tall, so this is the room at which the
        // multiplier first reaches 1. One line's worth of room is not "the
        // stack's height" - it is half of it, and the pair cannot fit there at
        // any leading, which is why the floor answers below.
        eq("a row exactly the stack's height leaves it alone",
                perfLineSpacing(perf, pair, box(491, (int) Math.ceil(lh * 2f))), 1f);
        eq("a row of one line's height cannot fit two and hits the floor",
                perfLineSpacing(perf, pair, box(491, Math.round(lh))), PERF_MIN_LINE_SPACING);
        // One pixel under the height at which the multiplier reaches exactly 1:
        // the one case where the answer is decided by "what is left after the
        // first line, shared between the rest" rather than by either clamp. A
        // room rounded down another way can land on the same integer as the case
        // above (about 51px at these metrics), which would test the same value
        // twice and leave the whole near-boundary region unexercised.
        final int justUnder = (int) Math.ceil(lh * 2f) - 1;
        final float nearBoundary = perfLineSpacing(perf, pair, box(491, justUnder));
        ok("a row one pixel under the stack's height still squeezes", nearBoundary < 1f);
        ok("and it squeezes only just", nearBoundary > 0.9f);

        final int snug = Math.round(lh * 1.8f);
        final float m = perfLineSpacing(perf, pair, box(491, snug));
        ok("a snug row squeezes the pair", m < 1f);
        ok("but never past the floor", m >= PERF_MIN_LINE_SPACING);
        close("and the squeezed stack then fits the room", lh + m * lh, snug, 1f);
        // The bug this guards: dividing the room by the line count spreads the
        // first line's full height over all of them and leaves the stack taller
        // than the row. Dividing what is left after the first line does not.
        ok("the squeeze is not just the room over the line count",
                Math.abs(m - (snug / 2f) / lh) > 0.01f);

        final int tight = Math.round(lh * 1.2f);
        close("a row too short for any squeeze hits the leading floor",
                perfLineSpacing(perf, pair, box(491, tight)), PERF_MIN_LINE_SPACING, 0.001f);
        close("a row not much taller than one line hits it too",
                perfLineSpacing(perf, pair, box(491, Math.round(lh * 1.05f))), PERF_MIN_LINE_SPACING, 0.001f);

        // The padding belongs to the row, so it is taken out of the room.
        final View padded = box(491, snug + 40);
        padded.setPadding(20, 20);
        close("the row's own padding is not part of the room",
                perfLineSpacing(perf, pair, padded), m, 0.02f);

        // Three lines: more of the same room between more lines.
        final float m3 = perfLineSpacing(perf, trio, box(491, snug));
        ok("three readings squeeze harder than two in the same row", m3 < m);
        ok("and they still respect the floor", m3 >= PERF_MIN_LINE_SPACING);
        // In this row the unclamped multiplier would be below the floor, so the
        // floor answers and the stack is taller than the row. That is the
        // documented trade: legibility wins over fitting, and the size fit -
        // not the leading - is what normally keeps the stack inside the row.
        ok("a stack the floor cannot fit overflows the row rather than shrinking past it",
                lh + 2f * m3 * lh > snug);

        // ---- the leading has to settle. refreshPerfLabel re-posts a sync when
        // the view's stored multiplier differs from a freshly computed one, so if
        // writing the computed value did not then read back equal, every layout
        // pass would post another and the status bar would relayout forever. The
        // pure function is idempotent by construction; what is pinned here is the
        // round trip through the view, which is the half a desktop probe can
        // reach. The stub stores the multiplier verbatim the way Android does,
        // so a rounded or rewritten value shows up as a failure rather than a
        // silently passable no-op.
        perf.setLineSpacing(0f, perfLineSpacing(perf, pair, box(491, snug)));
        eq("writing the computed leading reads back unchanged, so the re-sync settles",
                perf.getLineSpacingMultiplier(), perfLineSpacing(perf, pair, box(491, snug)));
        // The value really was squeezed, so the round trip above is not the
        // trivial one where 1f is stored and 1f read back.
        ok("the settled leading is a real squeeze, not the untouched 1",
                perf.getLineSpacingMultiplier() < 1f);

        // ---- the identity measured a second way. perfLineHeight is production's
        // own answer, so comparing 2 * perfLineHeight(size) against the icon is a
        // consistency check: it catches a size formula that stopped matching the
        // host, but it would also hold if perfLineHeight itself carried a factor
        // that the size formula cancelled out. Measuring the same two lines
        // straight off a paint that never went through perfLineHeight is what
        // makes the claim independent.
        final Paint raw = new Paint();
        raw.setTextSize(size);
        final Paint.FontMetrics rawMetrics = raw.getFontMetrics();
        final float rawLine = rawMetrics.descent - rawMetrics.ascent;
        ok("the raw line height is positive", rawLine > 0f);
        close("two lines measured without perfLineHeight are one icon tall too",
                2f * rawLine, iconHeight, 0.05f);
        close("and the spliced line height agrees with the raw one",
                perfLineHeight(size, TrioConfig.appearance().typeWeight), rawLine, 0.01f);

        // ---- the overall-size setting multiplies whatever the rules settled on.
        // It is applied last and to every answer, which is what these cases pin:
        // a lone reading scales too, so moving the slider with one switch on does
        // something instead of waiting for the second switch to make it jump, and
        // the fallback branches scale with it rather than staying at the base.
        // The test value is the production floor, not an arbitrary 50: the
        // multiplier clamps, so 50 is not reachable and asking for it would only
        // re-test the clamp below.
        final float low = Prefs.MIN_PERF_SCALE / 100f;
        TrioConfig.appearance().perfScale = Prefs.MIN_PERF_SCALE;
        eq("the setting scales a lone reading", perfTextSize(single, container, box(10, 10)), base * low);
        eq("and its no-host fallback", perfTextSize(single, container, null), base * low);
        final float half = perfTextSize(pair, container, sized);
        close("a pair scales from the fit's own answer", half, size * low, 0.05f);
        close("a floored pair scales from the floor", perfTextSize(pair, container, box(10, 10)),
                base * PERF_MIN_SIZE_RATIO * low, 0.001f);
        // The bounds are clamped rather than trusted. The stored number is
        // clamped on read, but the broadcast path builds an appearance straight
        // from extras and this multiplier reaches the canvas, so an out-of-range
        // percentage has to be brought back rather than obeyed. Both directions
        // are checked against the values read out of Prefs, so moving a bound is
        // an explicit change and dropping the clamp is a failure.
        TrioConfig.appearance().perfScale = 1000;
        eq("a percentage over the ceiling clamps to it",
                perfTextSize(single, container, box(10, 10)), base * (Prefs.MAX_PERF_SCALE / 100f));
        TrioConfig.appearance().perfScale = 1;
        eq("a percentage under the floor clamps to it",
                perfTextSize(single, container, box(10, 10)), base * (Prefs.MIN_PERF_SCALE / 100f));
        TrioConfig.appearance().perfScale = 100;
        eq("and 100 restores the unscaled answer, so the shipped look is the default",
                perfTextSize(pair, container, sized), size);

        System.out.println("PASS: " + checks + " cases over perfTextSize, perfLineSpacing and inkScale");
    }
}
"@

$sources = Get-ChildItem -Recurse -Filter *.java $src | ForEach-Object { $_.FullName }
& $javac -nowarn -d $classes -encoding UTF-8 $sources
if($LASTEXITCODE){throw 'Compilation failed'}
& $java -Xmx64m -cp $classes io.github.yixing233.hyperduo.PerfLayoutTest
if($LASTEXITCODE){throw 'Test failed'}
