package io.github.yixing233.hyperduo;

/**
 * The single source of truth for every setting shared between the settings app
 * and the hooked SystemUI process.
 *
 * <p>Both sides talk over the Xposed framework's remote preferences: the
 * settings app writes, the hooked process only reads. Because the framework
 * casts stored values to the requested type without conversion, every key is
 * read and written with exactly one type for the whole lifetime of the module.
 * Keep the {@code DEF_*} defaults here as well so both sides agree when the
 * framework cannot provide remote preferences (embedded mode, or a first run
 * before the user ever opened the settings screen).
 */
public final class Prefs {

    private Prefs() {
    }

    /**
     * The display density the retired raw-pixel sizes were authored against -
     * the test device's density 3. Only the one-time migration consults this,
     * when it has no real display to ask (the desktop workbenches).
     */
    public static final float AUTHORED_DENSITY = 3f;

    /** Remote-preference group name. Also the settings app's file name. */
    public static final String NAME = "hyperduo_settings";

    /**
     * Explicit broadcast the settings app sends to SystemUI after every write,
     * carrying the whole snapshot as extras.
     *
     * <p>Remote preferences remain the store of record, but the framework's
     * "a value changed" callback has been observed to never reach the hooked
     * process on this device: the daemon database held the new {@code enabled}
     * value while SystemUI kept drawing from the old one. The broadcast is a
     * second, callback-independent path for exactly that case.
     *
     * <p>Lives here rather than in {@code TrioConfig} because the settings app
     * must be able to name it: {@code TrioConfig} references
     * {@code io.github.libxposed.api}, which is {@code compileOnly} and so is
     * not on the app's runtime class path.
     */
    public static final String ACTION_RELOAD = "io.github.yixing233.hyperduo.action.RELOAD";

    /**
     * Package the reload broadcast is addressed to. SystemUI registers the
     * receiver, so an explicit package keeps the intent away from every other
     * process on the device.
     */
    public static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    // ------------------------------------------------------------------ keys
    public static final String KEY_ENABLED = "enabled";
    /**
     * Overall size of the trio glyph, as a percentage of the size the host box
     * already implies.
     *
     * <p><em>Shrink-only</em> - the range stops at 100 - because the bar's own
     * box is the limit: MIUI measures the battery icon at
     * {@code battery_meter_width} x {@code status_bar_icon_height} (28dp by 20dp
     * on the test device) and the glyph is fitted inside it. Asking for more than
     * the box holds cannot make the box grow, so the only honest thing a
     * percentage above 100 could do is draw past the view's bounds, and whether
     * that is clipped is MIUI's decision rather than this module's.
     *
     * <p>A percentage rather than a dp: the drawing already scales to whatever
     * box it is handed, so the setting that means anything is "how much of that
     * fit to use". A dp size would have to be converted back into a share of a
     * box the module does not own.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_GLYPH_SCALE = "glyph_scale_pct";
    public static final String KEY_SHOW_WIFI = "show_wifi";
    public static final String KEY_SHOW_MOBILE = "show_mobile";
    public static final String KEY_SHOW_VALUE = "show_value";
    public static final String KEY_SHOW_BOLT = "show_bolt";
    /**
     * Where the mobile network type ("5G", "4G"...) is drawn: hidden, inside the
     * ring, or outside it.
     *
     * <p>An {@code int} rather than the boolean it replaced. The framework does
     * no type conversion, so {@link #KEY_SHOW_MOBILE_TYPE} could not simply be
     * re-read as an int: on an existing install the stored boolean would make
     * {@code getInt} throw {@code ClassCastException}. A new key keeps every
     * installed value readable, and the old one is consulted once during
     * migration - see {@code TrioSettings.readMobileTypeMode}.
     */
    public static final String KEY_MOBILE_TYPE_MODE = "mobile_type_mode";
    /**
     * Legacy, read-only for migration.
     *
     * <p>Superseded by {@link #KEY_MOBILE_TYPE_MODE}. Never write it again; the
     * only remaining reader is the one-time migration in
     * {@code TrioSettings.from(SharedPreferences)}, which maps an existing
     * "on" to {@link #MOBILE_TYPE_IN_RING} while the new key is absent.
     */
    public static final String KEY_SHOW_MOBILE_TYPE = "show_mobile_type";
    /**
     * Draws the battery percentage enlarged in the middle of the ring, with the
     * Wi-Fi arcs shrunk into the 12 o'clock notch.
     *
     * <p>The stored name is frozen at {@code "swap_wifi_value"} and must never
     * change: it is a boolean the user may already have set, and rewriting the
     * key would silently reset that choice on every existing install. Only the
     * <em>label</em> was renamed - the setting used to be presented as a swap
     * between the two slots, which described the mechanism rather than what the
     * user gets.
     */
    public static final String KEY_VALUE_CENTRED = "swap_wifi_value";
    /**
     * Which of the three-in-one layouts is drawn: the ring arrangement or the
     * rectangular one.
     *
     * <p>An {@code int} holding one of the {@code STYLE_*} constants below. Like
     * every other key it keeps exactly one type for the whole lifetime of the
     * module, so the value is only ever read back with {@code getInt}.
     */
    public static final String KEY_TRIO_STYLE = "trio_style";
    /**
     * The ring layout: an ordered list of pieces per zone, as one string.
     *
     * <p>Format is {@code "top|middle|bottom"} where each section is a
     * comma-separated list of part ids in <b>priority order</b>, and an empty section
     * means "nothing placed here". For example {@code "3,1|2|7,8"} is
     * top = [percentage, arcs], middle = [mobile meter], bottom = [SIM 1, SIM 2].
     *
     * <p>A string rather than a packed int, for three reasons:
     *
     * <ul>
     *   <li>Order is the whole point now - the first piece that has something to draw
     *       wins the zone - and a bitmask cannot carry it;
     *   <li>a zone may hold any number of candidates, so there is no width to pick;
     *   <li>it is legible in the prefs XML, which the packed encodings never were.
     * </ul>
     *
     * <p>The framework does no type conversion, so this key is only ever read and written
     * as a String. {@code SharedPreferences} supports it, and the broadcast extras carry
     * it the same way.
     */
    public static final String KEY_RING_LAYOUT = "ring_layout";

    /**
     * The retired one-piece-per-zone encodings, read once by
     * {@link #migrateLayout(String)}.
     */
    public static final String KEY_RING_SLOTS_V2 = "ring_slots_v2";
    public static final String KEY_RING_SLOTS = "ring_slots";

    /**
     * A fresh install starts EMPTY: three zones with nothing placed.
     *
     * <p>The user builds the glyph themselves. This is deliberately not "the shipped
     * look" - with priority lists there is no single arrangement to ship, and an empty
     * ring is an honest starting point that says "add something" instead of guessing.
     */
    public static final String EMPTY_LAYOUT = "||";

    /**
     * Part ids. {@link #PART_NONE} never appears in a stored layout - an empty zone is an
     * empty section - so it only means "this id is not a piece" in decoded lists.
     */
    public static final int PART_NONE = 0, PART_WIFI = 1, PART_MOBILE = 2,
            PART_VALUE = 3, PART_BOLT = 4, PART_TYPE = 5, PART_CLOCK = 6,
            /**
             * SIM 1's own row of four dots.
             *
             * <p>A piece of its own rather than a mode of {@link #PART_MOBILE}: the two
             * cards are placed independently, so each needs its own id.
             */
            PART_SIM_1 = 7,
            /** SIM 2's own row of four dots; see {@link #PART_SIM_1}. */
            PART_SIM_2 = 8;

    /** The highest valid part id. */
    public static final int PART_MAX = PART_SIM_2;

    /** How many parts there are, counting {@link #PART_NONE}. */
    public static final int PART_COUNT = PART_MAX + 1;

    /** The three zones, in the order the layout string stores them. */
    public static final int ZONE_COUNT = 3;

    /**
     * Splits a layout into its three sections, each an ordered id list.
     *
     * <p>Never returns null and never throws: a corrupt or missing layout decodes to
     * {@link #EMPTY_LAYOUT}. Unlike the old packed encodings there is no "corrupt" state
     * worth rejecting wholesale - an unreadable section is simply empty, so one damaged
     * zone cannot take the other two with it.
     */
    public static int[][] decodeLayout(String layout) {
        final int[][] out = new int[ZONE_COUNT][];
        if (layout == null || layout.isEmpty()) {
            for (int z = 0; z < ZONE_COUNT; z++) {
                out[z] = new int[0];
            }
            return out;
        }
        final String[] sections = layout.split("\\|", -1);
        for (int z = 0; z < ZONE_COUNT; z++) {
            out[z] = decodeSection(z < sections.length ? sections[z] : "");
        }
        return out;
    }

    /** One section of {@link #KEY_RING_LAYOUT}: a comma-separated id list, deduplicated. */
    private static int[] decodeSection(String section) {
        if (section == null || section.isEmpty()) {
            return new int[0];
        }
        final java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (String piece : section.split(",", -1)) {
            final String trimmed = piece.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int id;
            try {
                id = Integer.parseInt(trimmed);
            } catch (NumberFormatException malformed) {
                continue;
            }
            if (id <= PART_NONE || id > PART_MAX) {
                continue;
            }
            // A piece can only be in a zone once; a duplicate would draw the same thing
            // twice and make the priority list ambiguous about which copy wins.
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        final int[] out = new int[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    /** The inverse of {@link #decodeLayout}, with the same tolerance for gaps. */
    public static String encodeLayout(int[][] zones) {
        final StringBuilder sb = new StringBuilder();
        for (int z = 0; z < ZONE_COUNT; z++) {
            if (z > 0) {
                sb.append('|');
            }
            final int[] ids = zones != null && z < zones.length ? zones[z] : null;
            if (ids == null) {
                continue;
            }
            boolean first = true;
            for (int id : ids) {
                if (id <= PART_NONE || id > PART_MAX) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                sb.append(id);
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Converts a layout written by the retired one-piece-per-zone encodings.
     *
     * <p>Four-bit first, then the original three-bit, exactly as
     * {@code TrioSettings.readLayout} consults them. Each stored id becomes a
     * single-entry priority list, so an existing install keeps every piece where it was
     * and simply gains the ability to add more behind it.
     *
     * <p>Returns {@link #EMPTY_LAYOUT} for anything unreadable, including the {@code -1}
     * an even older automatic layout stored.
     */
    public static String migrateLayout(String v2, String legacy) {
        final Integer fourBit = parseIntOrNull(v2);
        if (fourBit != null) {
            return fromPacked(fourBit, RING_PART_BITS);
        }
        final Integer threeBit = parseIntOrNull(legacy);
        if (threeBit != null) {
            return fromPacked(threeBit, 3);
        }
        return EMPTY_LAYOUT;
    }

    private static Integer parseIntOrNull(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    /**
     * Unpacks a zone id from an old encoding into a one-piece priority list.
     *
     * <p>The two widths differ (four bits, and three before it), so the shift is a
     * parameter rather than a constant: reading a three-bit value with four-bit shifts
     * would scatter the user's pieces into the wrong zones, which is the defect the
     * migration exists to avoid.
     */
    private static String fromPacked(int packed, int bits) {
        final int mask = (1 << bits) - 1;
        final int[][] zones = new int[ZONE_COUNT][];
        for (int z = 0; z < ZONE_COUNT; z++) {
            final int id = (packed >> (z * bits)) & mask;
            zones[z] = id > PART_NONE && id <= PART_MAX ? new int[] { id } : new int[0];
        }
        return encodeLayout(zones);
    }

    /** Bits per zone in {@link #KEY_RING_SLOTS_V2}; three zones fit in twelve. */
    public static final int RING_PART_BITS = 4;
    private static final int RING_PART_MASK = (1 << RING_PART_BITS) - 1;
    /** The largest value a three-zone, four-bit layout can hold. */
    public static final int RING_SLOTS_MAX = (1 << (3 * RING_PART_BITS)) - 1;

    /**
     * The first piece of one zone of a packed layout.
     *
     * <p>Kept because the packed value is still what the migration reads and what several
     * harness assertions describe. New code asks {@link #decodeLayout(String)} instead -
     * a packed int can only ever name one piece per zone, which is exactly the limitation
     * the priority lists remove.
     */
    public static int ringPart(int slots, int zone) {
        if (slots < 0 || slots > RING_SLOTS_MAX) {
            return PART_NONE;
        }
        return (slots >> (zone * RING_PART_BITS)) & RING_PART_MASK;
    }

    /**
     * Packs three parts into one value, for the migration and for test fixtures.
     *
     * <p>This is the one place the shift amounts live, so the packing follows
     * {@link #RING_PART_BITS} rather than a literal that a width change would miss.
     */
    public static int packRingParts(int top, int middle, int bottom) {
        return top | (middle << RING_PART_BITS) | (bottom << (2 * RING_PART_BITS));
    }

    /**
     * The layout a fresh install used to start from, kept for the migration tests.
     *
     * <p>The live default is now {@link #EMPTY_LAYOUT}: with priority lists there is no
     * single arrangement to ship, and an empty ring says "add something" instead of
     * guessing.
     */
    public static int RING_DEFAULT_SLOTS() {
        return packRingParts(PART_VALUE, PART_WIFI, PART_MOBILE);
    }

    /**
     * Rejects corrupt packed layouts and drops duplicate pieces.
     *
     * <p>Returns the old shipped layout for anything unreadable, so the migration has a
     * defined answer. Not used for the live layout any more - that is a string.
     */
    public static int normalizeRingSlots(int slots) {
        if (slots < 0 || slots > RING_SLOTS_MAX) return RING_DEFAULT_SLOTS();
        int result = 0, seen = 0;
        for (int zone = 0; zone < 3; zone++) {
            int part = (slots >> (zone * RING_PART_BITS)) & RING_PART_MASK;
            if (part > PART_MAX) return RING_DEFAULT_SLOTS();
            if (part != PART_NONE && (seen & (1 << part)) == 0) {
                result |= part << (zone * RING_PART_BITS);
                seen |= 1 << part;
            }
        }
        return result;
    }

    /**
     * Converts a packed layout into the priority-list form.
     *
     * <p>Each piece becomes the sole entry of its zone's list, so an existing install
     * keeps everything where it was. Delegates to {@link #migrateLayout(String, String)}
     * after stringifying, which is where the width rules live.
     */
    public static String migratePackedLayout(int oldSlots) {
        return fromPacked(oldSlots, RING_PART_BITS);
    }

    /**
     * Converts the original three-bit layout straight to a packed four-bit value.
     *
     * <p>Kept for the migration fixtures, which describe the width change in isolation.
     * The live path does not need the intermediate form: it goes from three-bit to a
     * priority list in one step via {@link #migrateLayout(String, String)}.
     *
     * <p>An old value naming {@code 7} was never producible - the three-bit encoder
     * capped at {@link #PART_CLOCK} - so it is treated as corrupt rather than promoted to
     * SIM 1.
     */
    public static int migrateRingSlots(int oldSlots) {
        if (oldSlots < 0 || oldSlots > 511) return RING_DEFAULT_SLOTS();
        final int top = oldSlots & 7;
        final int middle = (oldSlots >> 3) & 7;
        final int bottom = (oldSlots >> 6) & 7;
        if (top > PART_CLOCK || middle > PART_CLOCK || bottom > PART_CLOCK) {
            return RING_DEFAULT_SLOTS();
        }
        return normalizeRingSlots(packRingParts(top, middle, bottom));
    }
    /**
     * Draws the mobile level as two rows of four dots - one SIM each - instead of
     * the single row of the current data SIM.
     *
     * <p>Only honoured while there is no Wi-Fi ink and no charging bolt: with the
     * arcs or the bolt on screen the glyph's top and bottom slots are taken, and
     * the user asked for the two-SIM reading precisely as the alternative to
     * those. The top row is SIM 1 (slot 0) and the bottom row SIM 2 (slot 1).
     * With a single SIM, or while the per-SIM levels cannot be read, the single
     * row stays and keeps showing the current data SIM.
     *
     * <p>A brand-new key with a brand-new name. The framework does no type
     * conversion, so a key may only ever be read back with the type it was
     * written with - see the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_DUAL_SIM = "dual_sim_signal";
    /**
     * Draws SIM 1's own row of four dots, as a component the user places in the
     * ring like any other.
     *
     * <p>Independent of {@link #KEY_DUAL_SIM} and of its SIM 2 twin. The dual
     * switch describes the <em>aggregate</em> meter and its two-row shape; these
     * two describe the cards themselves, and the reason they are separate is that
     * the placed rows are arranged by the user rather than by a rule: SIM 1 can
     * sit in the notch while SIM 2 sits at the bottom, which one switch could not
     * express.
     *
     * <p>Off by default, and the shipped look is unchanged: with both off the
     * glyph draws the aggregate meter exactly as before. On a single-SIM device
     * SIM 2 has no reading, so its row draws nothing - which is what an empty
     * reading should look like, rather than a full one.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_SHOW_SIM_1 = "show_sim_1";
    /** Draws SIM 2's own row of four dots; see {@link #KEY_SHOW_SIM_1}. */
    public static final String KEY_SHOW_SIM_2 = "show_sim_2";
    /**
     * Where the mobile signal is drawn: inside the glyph, or as a status-bar view
     * outside it.
     *
     * <p>An {@code int} holding one of the {@code SIGNAL_*} constants below.
     * Outside the glyph the module stops drawing the level dots entirely and -
     * depending on {@link #KEY_STACKED_SIGNAL} - either hands the native signal
     * icon back or draws one of its own next to the battery.
     *
     * <p>A brand-new key with a brand-new name: the framework does no type
     * conversion, so a key may only ever be read back with the type it was
     * written with - see the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_SIGNAL_MODE = "signal_mode";
    /**
     * Draws the out-of-ring signal as four ascending capsule bars for one SIM
     * over four dots for the other, instead of leaving the native icon in place.
     *
     * <p>Only ever honoured while {@link #KEY_SIGNAL_MODE} is
     * {@link #SIGNAL_OUT_RING}: inside the glyph the rings' own level dots are the
     * signal, and there is nothing here for this switch to change. Turning it off
     * outside the glyph is not "draw the default dots" but "let MIUI draw its own
     * signal icon", which is what makes it a meaningful choice rather than a
     * second way of saying the same thing.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_STACKED_SIGNAL = "stacked_signal";
    /**
     * Draws only the current data SIM's out-of-ring signal row, leaving the other
     * SIM out entirely.
     *
     * <p>Gated on {@link #KEY_STACKED_SIGNAL} rather than on anything of its own:
     * while the native icon is in place the module draws no signal at all, so
     * there would be nothing for this to narrow. Being switched off by
     * <em>another</em> row is fine; a row that switches itself off is not.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_DATA_SIM_ONLY = "data_sim_only";
    /**
     * Draws the battery's charging power - watts, with one decimal below 10W - in
     * the status bar, in the same out-of-ring strip as the network type.
     *
     * <p>Only drawn while an external source is attached: an unplugged phone has
     * no charging power, and the segment is dropped rather than shown as 0W. The
     * number is the power actually going into the battery - its own current times
     * its own voltage - which tapers as the battery fills, so it is deliberately
     * not the charger's negotiated maximum, a value that stays put while the real
     * power falls away.
     *
     * <p>Independent of {@link #KEY_PERF_TEMP}: the two readouts share one view,
     * but each segment is drawn only while its own switch asks for it, and the
     * pair is the reason this is two keys rather than one three-state choice.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_PERF_POWER = "perf_power";
    /**
     * Draws the battery's temperature in degrees Celsius in the status bar, beside
     * the charging power.
     *
     * <p>Drawn whether or not a charger is attached: temperature is a property of
     * the battery rather than of the charger, so this switch alone is enough to
     * make the reading appear. Rounded to whole degrees on purpose - the tenths
     * the platform reports move by a degree or more between samples, and a steady
     * number is what makes a status-bar-sized value readable.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_PERF_TEMP = "perf_temp";
    /**
     * Overall size of the two performance readouts, as a percentage of the size
     * {@link #KEY_OUT_TYPE_SIZE_DP} and the adaptive fit settle on.
     *
     * <p>A multiplier rather than a second absolute size: the pair is already
     * sized by rule - two lines together come to one icon height - and an
     * absolute setting would throw that rule away instead of tuning it. Because
     * the multiplier is applied last, the reading still follows the icon it
     * stands beside and still follows {@link #KEY_OUT_TYPE_SIZE_DP}; this only
     * says how much of the answer to use.
     *
     * <p>Independent of {@link #KEY_GLYPH_SCALE}: the two resize different
     * things and a user who wants both smaller should not have to work out which
     * one moved.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_PERF_SCALE = "perf_scale_pct";
    /**
     * How heavy the two performance readouts are drawn, on the same 100..900
     * scale {@link #KEY_TYPE_WEIGHT} uses.
     *
     * <p>A key of its own rather than a second reader of {@code type_weight}:
     * the readout stands in the status bar next to MIUI's own text, while the
     * network type it borrows its size from is also drawn inside the ring, and
     * the two want different answers - the ring's label is artwork and the
     * strip's is text at 43dp. Sharing one key is exactly what this key fixes,
     * the same reasoning {@link #KEY_OUT_TYPE_SIZE_DP} records for size.
     *
     * <p>The weight also decides the line height - see the {@code PERF_}-weight
     * path in {@code TrioHooks} - so it is a layout input as well as a look.
     *
     * <p>A brand-new key with a brand-new name.
     */
    public static final String KEY_PERF_WEIGHT = "perf_weight";

    public static final String KEY_ROLE_COLORS = "role_colors";
    public static final String KEY_COLOR_CRITICAL_ON_DARK = "color_critical_on_dark";
    public static final String KEY_COLOR_CRITICAL_ON_LIGHT = "color_critical_on_light";
    public static final String KEY_COLOR_CHARGING_ON_DARK = "color_charging_on_dark";
    public static final String KEY_COLOR_CHARGING_ON_LIGHT = "color_charging_on_light";
    public static final String KEY_COLOR_LOW_ON_DARK = "color_low_on_dark";
    public static final String KEY_COLOR_LOW_ON_LIGHT = "color_low_on_light";
    public static final String KEY_LOW_THRESHOLD = "low_threshold";

    public static final String KEY_RING_STROKE = "ring_stroke";
    public static final String KEY_ARC_STROKE = "arc_stroke";
    public static final String KEY_VALUE_SIZE = "value_size";
    public static final String KEY_VALUE_WEIGHT = "value_weight";
    /**
     * Font size of the time, in the capsule's left-hand slot.
     *
     * <p>Its own key rather than a share of {@link #KEY_VALUE_SIZE}: the two
     * readings sit in two slots that are drawn from the same width but hold
     * different strings - "19:41" against "100" - and the time is the one the user
     * looks at while the percentage is the one they check. Sharing a size means a
     * change made for either moves both, and the pair cannot be balanced.
     *
     * <p>A brand-new key name and never a reuse: the framework does no type
     * conversion, so every key has exactly one type for the module's lifetime.
     * Nothing writes it until the user moves its own slider, so an install that
     * predates it keeps the look it had - the default below reproduces it.
     */
    public static final String KEY_CLOCK_SIZE = "clock_size";
    /** Font weight of the time, on the same reasoning as {@link #KEY_CLOCK_SIZE}. */
    public static final String KEY_CLOCK_WEIGHT = "clock_weight";
    /** Font size of the network type drawn inside the ring. */
    public static final String KEY_TYPE_SIZE = "type_size";
    /**
     * Font size of the network type when it is drawn <em>outside</em> the ring.
     *
     * <p>Deliberately a separate key from {@link #KEY_TYPE_SIZE}: the in-ring
     * label lives in the ring's 120x120 design space and its size is tuned
     * against the ring geometry, whereas the out-of-ring label is a plain
     * status-bar TextView laid out in real pixels next to MIUI's own icons, so
     * it is tuned on its own scale - and its range reaches higher accordingly.
     * Sharing one key between the two spaces is what this key fixes.
     *
     * <p>An {@code int}, like {@link #KEY_TYPE_SIZE}. A brand-new key name and
     * never a reuse of the old one: the framework does no type conversion, so
     * every key has exactly one type for the whole lifetime of the module.
     *
     * @deprecated Superseded by {@link #KEY_OUT_TYPE_SIZE_DP}. This key held raw
     * pixels, which made the same slider value a different physical size on
     * every density - the exact defect that moved {@link #KEY_OUT_SIGNAL_SIZE}
     * to a dp a release earlier. Kept readable only for the one-time migration;
     * never written again.
     */
    @Deprecated
    public static final String KEY_OUT_TYPE_SIZE = "out_type_size";
    /**
     * Font size of the network type outside the ring, in density pixels.
     *
     * <p>Replaces the retired raw-pixel key above. The label is a real view in
     * the status bar's pixel space, but the <em>setting</em> is a length the
     * user reasons about, and only a dp means the same slider value lands on the
     * same physical size on every screen - same reasoning as
     * {@link #KEY_OUT_SIGNAL_SIZE}, which moved to a dp for exactly this. The
     * conversion to pixels happens at the point of use, where a {@code Resources}
     * is at hand; this class stays Android-free.
     *
     * <p>Migration follows the {@link #KEY_MOBILE_TYPE_MODE} pattern: the new
     * key wins whenever present, and only an install predating it - which may
     * hold a raw-pixel value in the old key - has that value converted once
     * (px &divide; density) into the new key's terms. Users who never touched the
     * old slider get the new default, which equals the old default at the
     * density it was authored on.
     *
     * <p>An {@code int}, a brand-new name, never a reuse - see
     * {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_TYPE_SIZE_DP = "out_type_size_dp";
    /**
     * The stacked out-of-ring signal's height, in density pixels.
     *
     * <p>A dp rather than a percentage of anything, because the row this reading
     * stands in does not keep one height. At rest the battery container measures
     * one way, and the moment the control centre is pulled down MIUI lays the
     * same container out through the whole {@code statusBars} inset instead - on
     * the test device 88px becomes 134px, so a reading sized as a share of its
     * row grew by half just because a shade was opened. A dp is resolved against
     * the display's density and never against whoever is laying the row out.
     *
     * <p>Named {@code _dp} rather than reusing the shorter key a percentage once
     * used: the unit changed, and an install that still holds a percentage in the
     * old key would have it read as a dp. Nothing shipped with that key, so no
     * migration is owed and the old name is simply retired.
     *
     * <p>An {@code int}, like {@link #KEY_OUT_TYPE_SIZE}: the framework does no
     * type conversion, so every key has exactly one type for the whole lifetime
     * of the module.
     */
    public static final String KEY_OUT_SIGNAL_SIZE = "out_signal_size_dp";
    /**
     * The gap the out-of-ring network type keeps on its two sides, in density
     * pixels.
     *
     * <p>Two keys rather than one: in the status bar the label is flanked by
     * different things on each side - the native icon row on the outside, the
     * out-of-ring signal reading (or the battery) on the inside - and the two
     * gaps are tuned against those different neighbours.
     *
     * <p>Both are dplike rather than pixels for the same reason as
     * {@link #KEY_OUT_SIGNAL_SIZE}: the label lives in the status bar's real
     * pixel space, whose row height MIUI changes under the control centre, so a
     * length measured in pixels would drift with the shade.
     *
     * <p>Each defaults to the single gap the label used to have hard-coded, so
     * an install that never touches these keeps exactly the spacing it had.
     *
     * <p>New names, never a reuse: the framework does no type conversion, so
     * every key has exactly one type for the whole lifetime of the module - see
     * the note on {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_TYPE_MARGIN_LEFT = "out_type_margin_left_dp";
    public static final String KEY_OUT_TYPE_MARGIN_RIGHT = "out_type_margin_right_dp";
    /**
     * The gap between the out-of-ring signal reading and the battery meter, in
     * density pixels.
     *
     * <p>A margin rather than a position. This length is reserved out of the
     * native icon row exactly like the label margins above, so widening it moves
     * the reading away from the battery <em>and</em> pushes the icons over by the
     * same amount. An earlier version of this setting was a free position offset
     * instead: it moved the view without reserving anything, so the reading slid
     * out of the room set aside for it and painted over the neighbouring icons.
     *
     * <p>dp for the same reason as the label margins: the status bar's real pixel
     * space, whose row height MIUI changes under the control centre.
     *
     * <p>Defaults to the gap that was hard-coded before it was settable, so an
     * install that never touches it keeps exactly the spacing it had.
     *
     * <p>A brand-new name, never a reuse of the retired offset keys - see
     * {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_OUT_SIGNAL_MARGIN = "out_signal_margin_dp";
    /**
     * How large a trailing "A" is drawn against the rest of the network type, as
     * a percentage.
     *
     * <p>The reference draws "5GA" with the "A" noticeably smaller than the
     * "5G", sitting on the same baseline; 65 reproduces the reference's
     * {@code A height / main height} of about 56/86. 100 turns the shrink off
     * and restores the single-size label.
     *
     * <p>One key for both places the type is drawn - the ring canvas and the
     * out-of-ring status-bar label - because the reference's proportion is a
     * property of the label, not of where it happens to sit.
     *
     * <p>A new name, never a reuse - see {@link #KEY_MOBILE_TYPE_MODE}.
     */
    public static final String KEY_TYPE_SUFFIX_SCALE = "type_suffix_scale";
    public static final String KEY_TYPE_WEIGHT = "type_weight";
    public static final String KEY_TRACK_ALPHA = "track_alpha";

    public static final String KEY_DEBUG_LOG = "debug_log";

    // -------------------------------------------------------------- defaults
    public static final boolean DEF_ENABLED = true;
    public static final boolean DEF_SHOW_WIFI = true;
    public static final boolean DEF_SHOW_MOBILE = true;
    public static final boolean DEF_SHOW_VALUE = true;
    public static final boolean DEF_SHOW_BOLT = true;
    /**
     * Legacy, read-only for migration.
     *
     * <p>Superseded by {@link #DEF_MOBILE_TYPE_MODE}; kept only so the migration
     * can tell what an installed user had before the int key existed.
     */
    public static final boolean DEF_SHOW_MOBILE_TYPE = false;

    /** Off: the network type is not drawn at all. */
    public static final int MOBILE_TYPE_OFF = 0;
    /** Draw the network type inside the ring, in the middle when Wi-Fi is absent. */
    public static final int MOBILE_TYPE_IN_RING = 1;
    /** Draw the network type outside the ring. */
    public static final int MOBILE_TYPE_OUT_RING = 2;
    /**
     * Off by default: the shipment look keeps the battery number in the centre
     * when there is no Wi-Fi, and draws no network type at all.
     */
    public static final int DEF_MOBILE_TYPE_MODE = MOBILE_TYPE_OFF;
    /**
     * Off by default: the shipped look keeps the Wi-Fi arcs in the middle of the
     * ring and the battery reading small in the 12 o'clock notch.
     *
     * <p>Turning this on moves the percentage into the middle, drawn at
     * {@link TrioGeometry#CENTRE_SIZE_RATIO} scale, and shrinks the arcs into the
     * notch.
     */
    public static final boolean DEF_VALUE_CENTRED = false;

    /** The original arrangement: a battery ring with the content inside it. */
    public static final int STYLE_RING = 0;
    /**
     * The rectangular arrangement: a column of four level dots down each side,
     * the network state in the top slot between them, an enlarged percentage
     * below, and a full-width battery bar along the bottom. There is no ring.
     */
    public static final int STYLE_RECT = 1; // Retired storage ID; never reuse for another style.
    /**
     * The capsule arrangement: a wide stadium outline whose stroke is the battery
     * gauge, broken at the top and bottom centre by two four-dot rows, with the
     * percentage and the network state side by side inside.
     *
     * <p>The difference from the other two is what the outline <em>means</em>. The
     * ring draws an arc whose lit fraction is the level; here the level is the
     * fraction of the outline's own length that is drawn in the role colour, and
     * the rest is drawn as the track - so the shape fills up rather than
     * containing a gauge.
     */
    public static final int STYLE_CAPSULE = 2;

    /** Keep capsule's stored ID; retired rectangular configurations return to the ring. */
    public static int normalizeTrioStyle(int style) {
        return style == STYLE_CAPSULE ? STYLE_CAPSULE : STYLE_RING;
    }
    /**
     * The ring, for every install that has never chosen.
     *
     * <p>Only the styles themselves are honoured: the settings screen offers no
     * further entry, so an unrecognised stored value degrades to the ring rather
     * than to a blank glyph.
     */
    public static final int DEF_TRIO_STYLE = STYLE_RING;

    /** The signal is the glyph's own level dots, exactly as shipped. */
    public static final int SIGNAL_IN_RING = 0;
    /**
     * The signal leaves the glyph and is drawn beside the battery instead.
     *
     * <p>What stands there depends on {@link #KEY_STACKED_SIGNAL}: off means
     * MIUI's own signal icon keeps its place, on means the module draws the
     * stacked bars-and-dots reading of its own.
     */
    public static final int SIGNAL_OUT_RING = 1;
    /**
     * Inside the glyph, for every install that has never chosen: the shipped look
     * keeps the level dots in the ring's lower opening.
     */
    public static final int DEF_SIGNAL_MODE = SIGNAL_IN_RING;

    /**
     * Off by default: outside the glyph the shipped behaviour is to hand the
     * signal back to MIUI rather than draw a second version of it.
     */
    public static final boolean DEF_STACKED_SIGNAL = false;
    /** Off by default: the stacked reading shows both SIMs unless told otherwise. */
    public static final boolean DEF_DATA_SIM_ONLY = false;

    /**
     * Off by default: the status bar keeps the shipped size it had before the
     * module drew a charging-power reading, and the reading is an opt-in.
     */
    public static final boolean DEF_PERF_POWER = false;
    /** Off by default, for the same reason as {@link #DEF_PERF_POWER}. */
    public static final boolean DEF_PERF_TEMP = false;

    /**
     * 100%: the shipped glyph keeps exactly the fit it had before it was
     * settable, so an install that never touches the slider draws what it drew.
     */
    public static final int DEF_GLYPH_SCALE = 100;
    /**
     * 100%: the adaptive fit already balances the two readings against the icon,
     * so the shipped state is that answer unscaled.
     */
    public static final int DEF_PERF_SCALE = 100;
    /**
     * 700 = {@code Typeface.BOLD} at the platform's own mapping, the weight the
     * reference status bar draws its figures at, and the same default the type
     * label uses. The readout borrows the label's look, so it borrows its
     * default too.
     */
    public static final int DEF_PERF_WEIGHT = 700;

    /**
     * Off by default: the shipped look draws one row of dots for the current data
     * SIM, and the second row is an opt-in reading for a dual-SIM device.
     */
    public static final boolean DEF_DUAL_SIM = false;
    /**
     * Off by default: the per-card rows are an opt-in reading. With both off, the
     * aggregate meter draws exactly what it always drew, so an install that never
     * opens these switches sees no change at all.
     */
    public static final boolean DEF_SHOW_SIM_1 = false;
    /** Off by default; see {@link #DEF_SHOW_SIM_1}. */
    public static final boolean DEF_SHOW_SIM_2 = false;

    public static final boolean DEF_ROLE_COLORS = true;
    public static final int DEF_COLOR_CRITICAL_ON_DARK = 0xFFFF3B30;
    public static final int DEF_COLOR_CRITICAL_ON_LIGHT = 0xFFFF3B30;
    public static final int DEF_COLOR_CHARGING_ON_DARK = 0xFF34C759;
    public static final int DEF_COLOR_CHARGING_ON_LIGHT = 0xFF1F8F3D;
    public static final int DEF_COLOR_LOW_ON_DARK = 0xFFF2B900;
    public static final int DEF_COLOR_LOW_ON_LIGHT = 0xFFC99700;
    public static final int DEF_LOW_THRESHOLD = 20;

    public static final int DEF_RING_STROKE = 14;
    public static final int DEF_ARC_STROKE = 12;
    public static final int DEF_VALUE_SIZE = 36;
    /**
     * 700 is {@code Typeface.BOLD}, the weight the reference glyph uses. The
     * whole 100..900 range is exposed so thin skinning and heavy digits are both
     * reachable.
     */
    public static final int DEF_VALUE_WEIGHT = 700;
    /**
     * Font size of the time. Set to {@link #DEF_VALUE_SIZE} so the default look is
     * exactly what it was when the two readings shared a size - an install that
     * never opens the new slider sees no change at all.
     */
    public static final int DEF_CLOCK_SIZE = DEF_VALUE_SIZE;
    /** Font weight of the time, matching {@link #DEF_VALUE_WEIGHT} for the same reason. */
    public static final int DEF_CLOCK_WEIGHT = DEF_VALUE_WEIGHT;
    /**
     * Font size of the centred network type. Deliberately an absolute size rather
     * than a ratio of {@link #DEF_VALUE_SIZE}: the type is a standalone label the
     * user tunes on its own, and 32 is what the old 0.9 x 36 ratio produced, so
     * the default look is unchanged.
     */
    public static final int DEF_TYPE_SIZE = 32;
    /**
     * Font size of the network type when it is drawn outside the ring, at the
     * status bar's own scale. 32 matches {@link #DEF_TYPE_SIZE}, so a user who
     * never touches the new slider keeps exactly the look they had.
     *
     * @deprecated Raw pixels, superseded by {@link #DEF_OUT_TYPE_SIZE_DP}; read
     * only by the one-time migration. 32px was authored on the density-3 test
     * device, where it is 10.67dp - the new default rounds to the same look.
     */
    @Deprecated
    public static final int DEF_OUT_TYPE_SIZE = 32;
    /**
     * Font size of the network type outside the ring, in density pixels.
     *
     * <p>14dp is the default (about 42px on density 3), clearly visible and
     * proportionate to the battery icon and status bar text.
     */
    public static final int DEF_OUT_TYPE_SIZE_DP = 14;
    /**
     * The stacked out-of-ring signal's height, in density pixels.
     *
     * <p>15dp: MIUI's own four signal bars measure 44px on the test device at
     * density 3, and 15dp is 45px there - the reading ships at the size of the
     * native icon it replaces. Being a dp, it lands on the same physical size on
     * any density instead of only matching on this one.
     */
    public static final int DEF_OUT_SIGNAL_SIZE = 15;
    /**
     * The gap the out-of-ring label keeps on each side, in dp.
     *
     * <p>2dp is the value the single hard-coded {@code OUT_LABEL_GAP_DP} used to
     * hold, so the shipped spacing is unchanged and only a user who moves the
     * sliders sees anything different.
     */
    public static final int DEF_OUT_TYPE_MARGIN_LEFT = 2;
    public static final int DEF_OUT_TYPE_MARGIN_RIGHT = 2;
    /**
     * 2dp: the gap the reading kept from the battery before it was settable
     * (the same value the label margins default to, since one constant served
     * both), so the shipped spacing is unchanged.
     */
    public static final int DEF_OUT_SIGNAL_MARGIN = 2;
    /**
     * 65% for the trailing "A": the reference's {@code 56/86} ratio, measured
     * from {@code docs/ref-5ga.png}. 100 turns the shrink off.
     */
    public static final int DEF_TYPE_SUFFIX_SCALE = 65;
    public static final int DEF_TYPE_WEIGHT = 700;
    public static final int DEF_TRACK_ALPHA = 56;

    public static final boolean DEF_DEBUG_LOG = false;

    // --------------------------------------------------------------- bounds
    public static final int MIN_RING_STROKE = 4;
    public static final int MAX_RING_STROKE = 16;
    public static final int MIN_ARC_STROKE = 3;
    public static final int MAX_ARC_STROKE = 16;
    public static final int MIN_VALUE_SIZE = 16;
    /**
     * The percentage's size span.
     *
     * <p>44 stays the ceiling for the arrangements that have a fixed slot to fill - the
     * capsule's right-hand reading and the rectangle's centre - because their geometry
     * already decides how much room there is, and a larger setting could only be clipped.
     *
     * <p>The <b>ring</b> is different: a placed reading is sized by the third it sits in,
     * and the middle third is the ring's interior, which accepts far more than 44 units of
     * ink. That is what {@link #MAX_RING_VALUE_SIZE} is for; the slot fit clamps to it on
     * its own. Keeping the two numbers apart is the point - raising this one would let the
     * capsule and rectangle ask for a size they cannot draw, which is a setting that
     * silently does nothing.
     */
    public static final int MAX_VALUE_SIZE = 44;
    /**
     * The percentage's ceiling when it is <em>placed in a ring slot</em>.
     *
     * <p>56: at the ring's interior the height cap is about 90pt and the width cap about 74,
     * so 56 is comfortably reachable, and 44 - the old ceiling - filled only about a third
     * of the inner diameter, which read as slightly small beside the reference. Past about
     * 60 the digits look as if they touch the arc even though they measure inside it.
     *
     * <p>Not a stored setting of its own: the slider's range stays
     * {@code MIN_VALUE_SIZE..MAX_VALUE_SIZE} in the arrangements with a fixed slot, and the
     * ring's own slot fit is what lets a value up to this be drawn.
     */
    public static final int MAX_RING_VALUE_SIZE = 56;

    /**
     * The percentage ceiling for {@code trioStyle}.
     *
     * <p>One place for the rule, so the stored read, the broadcast read and the slider's
     * range cannot disagree about how large a reading may be: the ring's slots size a piece
     * by the third it sits in and accept {@link #MAX_RING_VALUE_SIZE}; the capsule and the
     * rectangle have fixed slots and stop at {@link #MAX_VALUE_SIZE}.
     */
    public static int maxValueSize(int trioStyle) {
        return trioStyle == STYLE_RING ? MAX_RING_VALUE_SIZE : MAX_VALUE_SIZE;
    }
    public static final int MIN_VALUE_WEIGHT = 100;
    public static final int MAX_VALUE_WEIGHT = 900;
    /** The time's own size span. Wider at the top than the value's: "19:41" is the longer string and the left slot is the one with room to spare. */
    public static final int MIN_CLOCK_SIZE = 16;
    public static final int MAX_CLOCK_SIZE = 44;
    public static final int MIN_CLOCK_WEIGHT = 100;
    public static final int MAX_CLOCK_WEIGHT = 900;
    public static final int MIN_TYPE_SIZE = 16;
    public static final int MAX_TYPE_SIZE = 44;
    public static final int MIN_OUT_TYPE_SIZE = 16;
    /**
     * Higher than {@link #MAX_TYPE_SIZE} on purpose: the out-of-ring label sits
     * in the status bar's real pixel space rather than the ring's design space,
     * so it needs headroom the in-ring label does not.
     *
     * @deprecated Raw-pixel bounds of the retired {@link #KEY_OUT_TYPE_SIZE};
     * read only by the one-time migration, which clamps the converted value
     * into the new dp range.
     */
    @Deprecated
    public static final int MAX_OUT_TYPE_SIZE = 64;
    /**
     * The span the out-of-ring type size offers, in density pixels.
     *
     * <p>8..26dp, cleanly covering everything from the stock ~7.2dp label up to
     * a prominent label without overflowing the status bar row. Default is 14dp
     * (~42px at density 3). The 40..80dp range this briefly held was a
     * misreading of the size report - what the user was seeing was the label
     * drawn through the bars, not a label that was too small - so it is back to
     * a span that keeps the label inside the row.
     */
    public static final int MIN_OUT_TYPE_SIZE_DP = 8;
    public static final int MAX_OUT_TYPE_SIZE_DP = 26;
    /**
     * The physical span the slider offers, in density pixels: 6dp (18px at
     * density 3) is where the four bars stop being distinguishable, and 20dp
     * (60px) is the tallest the status bar row shows before the reading starts
     * pushing the icons around it.
     *
     * <p>Narrow on purpose. The point of the setting is to match or gently
     * nudge MIUI's own signal bars, which measure 44px - about 14.7dp - so the
     * shipped 15dp sits in the middle of the range and a quarter of the slider
     * either way is already a visible change.
     */
    public static final int MIN_OUT_SIGNAL_SIZE = 6;
    public static final int MAX_OUT_SIGNAL_SIZE = 20;
    /**
     * The span the two label margins offer, in dp.
     *
     * <p>0 lets the label butt right up against its neighbour and 16 is already
     * wider than the gap between the native icons, so the whole useful range is
     * inside it. The default 2 sits near the low end, where the shipped look is.
     */
    public static final int MIN_OUT_TYPE_MARGIN = 0;
    public static final int MAX_OUT_TYPE_MARGIN = 16;
    /**
     * The span the signal margin offers, in dp.
     *
     * <p>Non-negative on purpose: unlike the retired position offset, this is a
     * reserved gap. A negative value would mean "overlap the battery", which is
     * the very thing the reservation exists to prevent. 0 lets the reading butt
     * up against the battery; 16 is a comfortable separation without eating the
     * status bar.
     */
    public static final int MIN_OUT_SIGNAL_MARGIN = 0;
    public static final int MAX_OUT_SIGNAL_MARGIN = 16;
    /**
     * The trailing "A" may be the same size as the rest (100) or a little over
     * half of it (50). Below 50 the suffix stops reading as a letter at status
     * bar sizes, and above 100 it would be the main glyph's larger twin.
     */
    public static final int MIN_TYPE_SUFFIX_SCALE = 50;
    public static final int MAX_TYPE_SUFFIX_SCALE = 100;
    public static final int MIN_TYPE_WEIGHT = 100;
    public static final int MAX_TYPE_WEIGHT = 900;
    public static final int MIN_TRACK_ALPHA = 0;
    public static final int MAX_TRACK_ALPHA = 255;
    /**
     * The glyph-size span, in percent of the fit the host box implies.
     *
     * <p>60..100: the ceiling is the box - see {@link #KEY_GLYPH_SCALE} for why
     * growing past it was refused - and 60 is as small as the glyph goes while
     * the level dots and the ring stroke are still separable at status-bar size.
     */
    public static final int MIN_GLYPH_SCALE = 60;
    public static final int MAX_GLYPH_SCALE = 100;
    /**
     * The performance-readout size span, in percent of the size the adaptive fit
     * settles on.
     *
     * <p>Wider than the glyph's span and allowed past 100 because this multiplies
     * a <em>text</em> size rather than a fitted drawing: the pair has already been
     * brought down to one icon height, so a user who wants it larger has room
     * below the row's ceiling, and one who wants it smaller is going below a fit
     * that is itself already below the lone reading's size. 60 matches the
     * glyph's floor so the two sliders bottom out together; 150 is where the pair
     * starts to reach the native icons beside it at the shipped type size.
     */
    public static final int MIN_PERF_SCALE = 60;
    public static final int MAX_PERF_SCALE = 150;
    /**
     * The performance readout's weight span, matching the type label's.
     *
     * <p>100..900 is the range {@code Typeface.create(base, weight, false)}
     * understands on every API this module runs on; the two ends are the
     * platform's thinnest and heaviest. The readout is two lines at 43dp, so a
     * user can take it lighter than the reference's bold when the strip is
     * crowded, which is the reason the setting exists at all.
     */
    public static final int MIN_PERF_WEIGHT = 100;
    public static final int MAX_PERF_WEIGHT = 900;
    public static final int MIN_LOW_THRESHOLD = 5;
    public static final int MAX_LOW_THRESHOLD = 50;

    public static final int MIN_MOBILE_TYPE_MODE = MOBILE_TYPE_OFF;
    public static final int MAX_MOBILE_TYPE_MODE = MOBILE_TYPE_OUT_RING;

    public static final int MIN_TRIO_STYLE = STYLE_RING;
    public static final int MAX_TRIO_STYLE = STYLE_CAPSULE;

    public static final int MIN_SIGNAL_MODE = SIGNAL_IN_RING;
    public static final int MAX_SIGNAL_MODE = SIGNAL_OUT_RING;

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
