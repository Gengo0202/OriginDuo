/*
 * OriginDuo - vivo OriginOS adaptation of HyperDuo.
 *
 * Released under the GNU General Public License v3.0, inheriting the
 * upstream project's license. See the LICENSE file at the repository
 * root, or <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.yixing233.hyperduo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.TextView;

/**
 * OriginOS foreground-colour poller.
 *
 * <p>On every other ROM the ink the glyph should draw in is learned from
 * something the system calls - MIUI's {@code updateLightDarkTint}, AOSP's
 * {@code StatusBarIconView.onDarkChanged}. OriginOS drives none of those: the
 * tint is written straight into the state of its own views without a dispatch
 * any hook can hear (established on the same device by the VivoDuoBar
 * project - polling was the only channel that tracked the real colour).
 *
 * <p>The status bar clock is the one view whose colour is both readable and
 * truthful: it is a plain {@code TextView} whose {@code currentTextColor}
 * vivo rewrites whenever the bar swaps between light and dark artwork, and it
 * sits in the same row the glyph draws in. This poller reads that colour
 * twice a second while the screen is on and hands it to
 * {@link TrioHooks#noteVivoInk(int)}, which files it against the row the
 * glyph hosts live in - exactly what the (never-firing) MIUI tint hook would
 * have done.
 *
 * <p>The tick also re-asserts the clock's visibility: OriginOS re-shows the
 * clock on its own schedule, and the capsule arrangement needs the hiding to
 * survive that. Both jobs are idempotent and cheap - one field read and one
 * comparison each.
 *
 * <p>Screen-off stops the loop entirely: nothing repaints the bar while the
 * panel is dark, and a poller that ran anyway would only burn the battery it
 * is drawing a ring for.
 */
final class TintPoll {

    /** How often the colour is read while the screen is on. */
    private static final long INTERVAL_MS = 500L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile boolean started;
    private static volatile boolean interactive = true;

    /** The last colour handed to the row; 0 before the first real reading. */
    private static volatile int lastInk;

    private TintPoll() {
    }

    /**
     * Records an ink learned from a real system event (vivo's
     * {@code onDarkChanged} dispatch), so the next poll does not immediately
     * override it with a clock reading taken a moment earlier. The poll still
     * wins later - the clock is the truth the user sees - but a same-frame
     * fight between the two sources is what this prevents.
     */
    static void noteInk(int ink) {
        if (ink != 0) {
            lastInk = ink;
        }
    }

    /**
     * Starts polling, at most once per process. The receiver keeps
     * {@link #interactive} current so the loop pauses with the panel.
     */
    static void start(final Context context) {
        if (started || context == null) {
            return;
        }
        started = true;
        try {
            final PowerManager pm =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                interactive = pm.isInteractive();
            }
        } catch (Throwable ignored) {
            // assume interactive; the receiver corrects this on the first event
        }
        try {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    final String action = intent.getAction();
                    interactive = Intent.ACTION_SCREEN_ON.equals(action);
                    if (interactive) {
                        // Repaint immediately: the colour the bar comes back
                        // up with is the one the user notices first.
                        MAIN.post(TICK);
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                // These are protected system broadcasts; NOT_EXPORTED drops
                // anything a third party might try to spoof.
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Throwable ignored) {
            // Without the receiver the loop still runs; it just never sleeps.
        }
        if (interactive) {
            MAIN.postDelayed(TICK, INTERVAL_MS);
        }
    }

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Throwable ignored) {
                // a poller must never take the looper down
            }
            if (interactive) {
                MAIN.postDelayed(this, INTERVAL_MS);
            }
        }
    };

    private static void tick() {
        if (TrioHooks.testMode() == 3) {
            // Diagnostic: the poller is disabled; the clock visibility
            // re-assertion below is skipped with it.
            return;
        }
        final View clock = TrioHooks.statusClock();
        if (clock instanceof TextView) {
            final int ink = ((TextView) clock).getCurrentTextColor();
            if (ink != 0 && ink != lastInk) {
                lastInk = ink;
                TrioHooks.noteVivoInk(ink);
            }
        }
        // OriginOS re-shows the clock on its own schedule; the capsule's
        // hiding has to be re-asserted often enough to outlast it.
        TrioHooks.applyClockVisibility();
    }
}
