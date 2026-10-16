package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One switchable component, paired with the title the settings screen shows for
 * it. The drawing page needs the name as well as the identity, because the dialog
 * it opens is titled with it.
 */
internal enum class TrioSettingsPart(val part: TrioZones.Part, val labelRes: Int) {
    Wifi(TrioZones.Part.WIFI, R.string.show_wifi_title),
    Mobile(TrioZones.Part.MOBILE, R.string.show_mobile_title),
    Value(TrioZones.Part.VALUE, R.string.show_value_title),
    Bolt(TrioZones.Part.BOLT, R.string.show_bolt_title),
    Type(TrioZones.Part.TYPE, R.string.show_mobile_type_title),
    Dual(TrioZones.Part.DUAL, R.string.dual_sim_title),
    ;

    companion object {
        /** The entry for {@code part}, or null when it has no row of its own. */
        fun of(part: TrioZones.Part?): TrioSettingsPart? =
            entries.firstOrNull { it.part == part }
    }
}

/**
 * The sections of the size page. The drawing page's per-part dialog reuses the
 * same rows, so the two places that can change a size share one definition or
 * they drift - which is the failure this file exists to prevent.
 */
internal enum class GeometryGroup(val titleRes: Int) {
    Scale(R.string.group_scale),
    Stroke(R.string.group_stroke),
    Text(R.string.group_text),
    Type(R.string.group_type),
    OutSignal(R.string.group_out_signal),
    Track(R.string.group_track),
}

/**
 * Font weights snap in hundreds: 100, 200, ... 900.
 *
 * <p>Lives here rather than on the settings screen because the weight rows do: the
 * three sliders that use it are rows of [geometrySliders], and a step restated at
 * each of them is three copies of one number. It was `WEIGHT_STEP` beside the old
 * hand-written `geometryTab`, which left it dead the moment the rows moved here.
 */
private const val WEIGHT_STEP = 100

/**
 * One size row, described once.
 *
 * <p>Every field is a function of the appearance rather than a captured value,
 * because several rows rename themselves for the rectangular arrangement and
 * every row's gate has to follow the current snapshot.
 *
 * @param part which component this row sizes, or null when it belongs to the
 *   glyph as a whole rather than to one switchable piece. The drawing page's
 *   long-press dialog filters on this.
 */
internal class SliderSpec(
    val group: GeometryGroup,
    val part: TrioZones.Part?,
    val min: Int,
    val max: Int,
    val read: (TrioSettings) -> Int,
    val write: (SettingsRepository, Int) -> Unit,
    val titleRes: (TrioAppearance) -> Int,
    val summaryRes: (TrioAppearance) -> Int,
    val enabled: (TrioAppearance) -> Boolean,
    /** The gates whose titles explain a greyed-out row, in reading order. */
    val hints: (TrioAppearance) -> List<Pair<Boolean, Int>> = { emptyList() },
    val step: Int = 1,
    val suffix: String? = null,
)

/**
 * Every size row in the module, as one table.
 *
 * <p>Both the size page and the drawing page's part dialog render from this list,
 * so a new slider is added here rather than in two places. The order within a
 * group is the order the size page shows.
 *
 * <p>The gates are the rule the whole settings screen follows: a row may be gated
 * by <em>other</em> switches but never by its own value, or switching it off would
 * disable the row and it could never be turned back on. See the note on the
 * percentage-weight row, which is gated on the percentage switch rather than on
 * the rule that decides whether a percentage is drawn.
 */
internal fun geometrySliders(): List<SliderSpec> {
    val value = TrioZones.Part.VALUE
    val wifi = TrioZones.Part.WIFI
    val mobile = TrioZones.Part.MOBILE
    val type = TrioZones.Part.TYPE
    return listOf(
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_GLYPH_SCALE,
            max = Prefs.MAX_GLYPH_SCALE,
            read = { it.glyphScale },
            write = { r, v -> r.setGlyphScale(v) },
            titleRes = { R.string.glyph_scale_title },
            summaryRes = { R.string.glyph_scale_summary },
            // Gated on the master switch and nothing else: it sizes the glyph
            // itself, so with the glyph off there is nothing for it to act on. No
            // row is gated by it, so it can never be the reason another is shut.
            enabled = { it.glyph },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_SCALE,
            max = Prefs.MAX_PERF_SCALE,
            read = { it.perfScale },
            write = { r, v -> r.setPerfScale(v) },
            titleRes = { R.string.perf_scale_title },
            summaryRes = { R.string.perf_scale_summary },
            // Gated on the two readout switches rather than on their own values:
            // a reading that is not drawn has no size to set. Gating on
            // perfAnywhere() is safe because that is the OR of two other rows,
            // never this one's own.
            enabled = { it.glyph && it.perfAnywhere() },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.perfAnywhere() to R.string.perf_pair_title)
            },
            suffix = "%",
        ),
        SliderSpec(
            group = GeometryGroup.Scale,
            part = null,
            min = Prefs.MIN_PERF_WEIGHT,
            max = Prefs.MAX_PERF_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.perfWeight },
            write = { r, v -> r.setPerfWeight(v) },
            titleRes = { R.string.perf_weight_title },
            summaryRes = { R.string.perf_weight_summary },
            enabled = { it.glyph && it.perfAnywhere() },
            hints = { a ->
                listOf(a.glyph to R.string.master_title, a.perfAnywhere() to R.string.perf_pair_title)
            },
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = null,
            min = Prefs.MIN_RING_STROKE,
            max = Prefs.MAX_RING_STROKE,
            read = { it.ringStroke },
            write = { r, v -> r.setRingStroke(v) },
            // One stroke setting, two names: the rectangle spends it on the bar's
            // thickness, the ring on the outline's width.
            titleRes = { if (it.rect) R.string.stroke_title_rect else R.string.stroke_title },
            summaryRes = { if (it.rect) R.string.stroke_summary_rect else R.string.stroke_summary },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Stroke,
            part = wifi,
            min = Prefs.MIN_ARC_STROKE,
            max = Prefs.MAX_ARC_STROKE,
            read = { it.arcStroke },
            write = { r, v -> r.setArcStroke(v) },
            titleRes = { R.string.arc_stroke_title },
            // In the rectangle the arc group hangs off the top slot by its own
            // stroke, so this slider moves the arcs as well as thickening them.
            summaryRes = {
                if (it.rect) R.string.arc_stroke_summary_rect else R.string.arc_stroke_summary
            },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_SIZE,
            max = Prefs.MAX_VALUE_SIZE,
            read = { it.valueSize },
            write = { r, v -> r.setValueSize(v) },
            titleRes = { R.string.value_size_title },
            summaryRes = { R.string.value_size_summary },
            enabled = { it.glyph },
        ),
        SliderSpec(
            group = GeometryGroup.Text,
            part = value,
            min = Prefs.MIN_VALUE_WEIGHT,
            max = Prefs.MAX_VALUE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.valueWeight },
            write = { r, v -> r.setValueWeight(v) },
            titleRes = { R.string.value_weight_title },
            summaryRes = { R.string.value_weight_summary },
            // Gated on the percentage switch, deliberately not on drawsBolt() or
            // any rule that folds this row's own value in: a self-gate would lock
            // the row shut the moment it was switched off.
            enabled = { it.glyph && it.value },
            hints = { a -> listOf(a.glyph to R.string.master_title, a.value to R.string.show_value_title) },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SIZE,
            max = Prefs.MAX_TYPE_SIZE,
            read = { it.typeSize },
            write = { r, v -> r.setTypeSize(v) },
            titleRes = { R.string.type_size_title },
            summaryRes = { R.string.type_size_summary },
            enabled = { it.glyph && it.typeInRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeInRing to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_SIZE_DP,
            max = Prefs.MAX_OUT_TYPE_SIZE_DP,
            read = { it.outTypeSize },
            write = { r, v -> r.setOutTypeSize(v) },
            titleRes = { R.string.out_type_size_title },
            summaryRes = { R.string.out_type_size_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_SUFFIX_SCALE,
            max = Prefs.MAX_TYPE_SUFFIX_SCALE,
            read = { it.typeSuffixScale },
            write = { r, v -> r.setTypeSuffixScale(v) },
            titleRes = { R.string.type_suffix_scale_title },
            summaryRes = { R.string.type_suffix_scale_summary },
            // A property of the label itself rather than of either position, so
            // "a network type is drawn somewhere" is the right gate.
            enabled = { it.glyph && it.typeAnywhere() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeAnywhere() to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginLeft },
            write = { r, v -> r.setOutTypeMarginLeft(v) },
            titleRes = { R.string.out_type_margin_left_title },
            summaryRes = { R.string.out_type_margin_left_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_OUT_TYPE_MARGIN,
            max = Prefs.MAX_OUT_TYPE_MARGIN,
            read = { it.outTypeMarginRight },
            write = { r, v -> r.setOutTypeMarginRight(v) },
            titleRes = { R.string.out_type_margin_right_title },
            summaryRes = { R.string.out_type_margin_right_summary },
            enabled = { it.glyph && it.typeOutOfRing },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeOutOfRing to R.string.show_mobile_type_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Type,
            part = type,
            min = Prefs.MIN_TYPE_WEIGHT,
            max = Prefs.MAX_TYPE_WEIGHT,
            step = WEIGHT_STEP,
            read = { it.typeWeight },
            write = { r, v -> r.setTypeWeight(v) },
            titleRes = { R.string.type_weight_title },
            summaryRes = { R.string.type_weight_summary },
            enabled = { it.glyph && it.typeAnywhere() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.typeAnywhere() to R.string.show_mobile_type_title,
                )
            },
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_SIZE,
            max = Prefs.MAX_OUT_SIGNAL_SIZE,
            read = { it.outSignalSize },
            write = { r, v -> r.setOutSignalSize(v) },
            titleRes = { R.string.out_signal_size_title },
            summaryRes = { R.string.out_signal_size_summary },
            // The knob is the rule that there is a reading to size, rather than a
            // second copy of the same three conditions. Its hint names the four
            // upstream switches individually - none of them is this row itself.
            enabled = { it.stackedOut() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.mobile to R.string.show_mobile_title,
                    a.signalOutOfRing to R.string.signal_mode_title,
                    a.stackedSignal to R.string.stacked_signal_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.OutSignal,
            part = mobile,
            min = Prefs.MIN_OUT_SIGNAL_MARGIN,
            max = Prefs.MAX_OUT_SIGNAL_MARGIN,
            read = { it.outSignalMargin },
            write = { r, v -> r.setOutSignalMargin(v) },
            titleRes = { R.string.out_signal_margin_title },
            summaryRes = { R.string.out_signal_margin_summary },
            enabled = { it.stackedOut() },
            hints = { a ->
                listOf(
                    a.glyph to R.string.master_title,
                    a.mobile to R.string.show_mobile_title,
                    a.signalOutOfRing to R.string.signal_mode_title,
                    a.stackedSignal to R.string.stacked_signal_title,
                )
            },
            suffix = "dp",
        ),
        SliderSpec(
            group = GeometryGroup.Track,
            part = null,
            min = Prefs.MIN_TRACK_ALPHA,
            max = Prefs.MAX_TRACK_ALPHA,
            read = { it.trackAlpha },
            write = { r, v -> r.setTrackAlpha(v) },
            titleRes = { R.string.track_alpha_title },
            summaryRes = { R.string.track_alpha_summary },
            enabled = { it.glyph },
        ),
    )
}

/** The rows that size one component, for the drawing page's long-press dialog. */
internal fun slidersForPart(part: TrioZones.Part): List<SliderSpec> =
    geometrySliders().filter { it.part == part }

/**
 * The role colours a component's ink takes, so the dialog can offer them.
 *
 * <p>Only two pieces are coloured by role: the percentage takes the low and
 * critical colours, and the bolt takes the charging one. The arcs and the level
 * dots follow the status bar's foreground, so they have no colour of their own to
 * offer and say so instead of showing a row that would do nothing.
 */
internal fun roleColorsForPart(part: TrioZones.Part): List<RoleColor> = when (part) {
    TrioZones.Part.VALUE -> listOf(RoleColor.Low, RoleColor.Critical)
    TrioZones.Part.BOLT -> listOf(RoleColor.Charging)
    else -> emptyList()
}

/**
 * The long-press dialog: the rows that size the tapped component, plus the role
 * colours it takes, both editable in place.
 *
 * <p>It renders [SliderSpec]s from the same table the size page uses, so a change
 * to a row's range, gate or wording lands in both places at once. The rows keep
 * their own gates rather than being hidden: a greyed-out row that names what is
 * holding it shut tells the user why the value they came to change has no effect,
 * where a missing row would leave them wondering whether they had opened the wrong
 * dialog. A component with no size and no colour of its own says so outright.
 *
 * <p>The colour rows reuse the page's own picker rather than a second one, which
 * is why [onEditColor] is a callback: the picker is a dialog of its own and two
 * dialogs stacked is a state the page already models, so the parent keeps owning
 * it.
 */
@Composable
internal fun PartSettingsDialog(
    part: TrioSettingsPart,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
    onDismiss: () -> Unit,
) {
    val a = TrioAppearance.of(settings)
    val rows = slidersForPart(part.part)
    val colors = roleColorsForPart(part.part)

    OverlayDialog(
        show = true,
        title = stringResource(R.string.drawing_settings_title, stringResource(part.labelRes)),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            if (rows.isEmpty() && colors.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawing_settings_none),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            rows.forEach { spec ->
                // The hint is resolved in a composable scope and passed down, the
                // same shape the size page uses: a row that is live has nothing to
                // explain, and a tooltip saying nothing would still eat the press.
                val hint = gateHintFor(spec, a)
                if (hint == null) {
                    SliderRow(spec, settings, update)
                } else {
                    TooltipBox(text = hint, enabled = true) {
                        SliderRow(spec, settings, update)
                    }
                }
            }

            colors.forEach { role ->
                ArrowPreference(
                    title = stringResource(role.labelRes),
                    summary = stringResource(R.string.color_field_summary),
                    insideMargin = SettingsItemMargin,
                    startAction = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Swatch(colorFor(settings, role, onDark = true))
                            Swatch(colorFor(settings, role, onDark = false))
                        }
                    },
                    // Gated on the same two switches the colour page uses: with
                    // per-role colours off these values are not read at all, so
                    // editing them here would change nothing on screen.
                    enabled = a.glyph && a.roleColors,
                    onClick = { onEditColor(role) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // One action, given the full width rather than paired with a second
                // button of the same name: this dialog has nothing to cancel, since
                // every row in it applies as it is changed. Two buttons both called
                // "close" would offer a choice that does not exist.
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.drawing_close))
                }
            }
        }
    }
}

/** One size row, rendered from its spec. Shared by the page and this dialog. */
@Composable
private fun SliderRow(
    spec: SliderSpec,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val a = TrioAppearance.of(settings)
    IntSlider(
        value = spec.read(settings),
        min = spec.min,
        max = spec.max,
        step = spec.step,
        title = stringResource(spec.titleRes(a)),
        summary = stringResource(spec.summaryRes(a)),
        enabled = spec.enabled(a),
        valueTextSuffix = spec.suffix,
        onValueChange = { v -> update { r -> spec.write(r, v) } },
    )
}

/**
 * The reason a row in the dialog is greyed out, or null when it is live.
 *
 * <p>The first dependency that is switched off wins, so a row needing two names
 * the one the user is likeliest to fix first. Same rule and same wording as the
 * size page's own gate hint.
 */
@Composable
private fun gateHintFor(spec: SliderSpec, a: TrioAppearance): String? {
    val missing = spec.hints(a).firstOrNull { !it.first } ?: return null
    return stringResource(R.string.gate_hint, stringResource(missing.second))
}
