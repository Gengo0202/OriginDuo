package io.github.yixing233.hyperduo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import io.github.yixing233.hyperduo.Prefs
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/**
 * Content controls shared by General and the visual editor.
 *
 * <p><b>Declaration order is the order they appear in a component's sheet</b>, and it
 * follows one rule: <em>where a thing goes, before how it looks</em>. A row that decides
 * placement comes first, because it answers the question the user opened the sheet with
 * ("where is this drawn?"). Size, weight and colour rows live in [geometrySliders] and
 * come after these, in their own groups.
 *
 * <p>Only one placement choice is left, and only one piece has it: the network type's
 * "show it beside the ring". Every other piece is placed or released - add it to a third
 * and it is drawn there, do not and MIUI keeps its own icon - so the old "signal in ring /
 * out of ring" switch, and the "stacked signal" style that went with it, were asking the
 * user to choose between states the layout already decides.
 */
internal enum class ContentControl(val owner: TrioSettingsPart) {
    // --- placement: where the piece lives -------------------------------------
    /** Master switch, then layout: both decide what exists at all. */
    Enabled(TrioSettingsPart.Appearance),
    Style(TrioSettingsPart.Appearance),
    // --- per component: its own switch ----------------------------------------
    Wifi(TrioSettingsPart.Wifi),
    Mobile(TrioSettingsPart.Mobile),
    DataSim(TrioSettingsPart.Mobile),
    Sim1(TrioSettingsPart.Mobile),
    Sim2(TrioSettingsPart.Mobile),
    Value(TrioSettingsPart.Value),
    Bolt(TrioSettingsPart.Bolt),
    /**
     * Network type, and the ONE piece that has a position choice of its own.
     *
     * <p>Every other piece is placed or released: add it to a third and it is drawn there,
     * do not and MIUI keeps its own icon. There is no third option, which is why the old
     * "signal in ring / out of ring" switch is gone. The type is different - it is text with
     * no native counterpart to hand back - so "show it beside the ring" is a real choice
     * rather than a way of expressing the same thing twice.
     */
    Type(TrioSettingsPart.Type),
    // --- readouts and fixed pieces --------------------------------------------
    Power(TrioSettingsPart.Readouts),
    Temperature(TrioSettingsPart.Readouts),
    Clock(TrioSettingsPart.Clock),
}

internal fun contentControlsFor(part: TrioSettingsPart): List<ContentControl> =
    ContentControl.entries.filter {
        // The two per-card rows and the aggregate meter are one group of settings,
        // so a dialog opened from any of the three shows all of them. That is the
        // point of "one shared size row": the cards differ in placement and in
        // switch, never in the ink their dots are drawn with.
        it.owner == if (part == TrioSettingsPart.Sim1
            || part == TrioSettingsPart.Sim2) TrioSettingsPart.Mobile else part
    }

@Composable
internal fun ContentControlRow(
    control: ContentControl,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    ringSettings: TrioSettings = settings,
) {
    val a = TrioAppearance.of(settings)
    val master = listOf(a.glyph to R.string.master_title)
    val positioned = when (control) {
        ContentControl.Wifi -> TrioZones.Part.WIFI
        ContentControl.Value -> TrioZones.Part.VALUE
        ContentControl.Bolt -> TrioZones.Part.BOLT
        ContentControl.Type -> TrioZones.Part.TYPE
        ContentControl.Clock -> TrioZones.Part.CLOCK
        else -> null
    }
    if (a.ring && positioned != null) {
        RingPositionControl(positioned, ringSettings, update)
        if (control == ContentControl.Type || control == ContentControl.Clock) return
    }
    if (control == ContentControl.Mobile && a.ring) {
        RingPositionControl(TrioZones.Part.MOBILE, ringSettings, update)
    }
    when (control) {
        ContentControl.Enabled -> ContentToggle(settings.enabled, R.string.master_title,
            R.string.master_summary, emptyList()) { value -> update { it.setEnabled(value) } }
        ContentControl.Style -> {
            val styles = listOf(Prefs.STYLE_RING, Prefs.STYLE_CAPSULE)
            ContentChoice(styles.indexOf(a.style).coerceAtLeast(0), R.string.trio_style_title,
                R.string.trio_style_summary,
                listOf(R.string.trio_style_ring, R.string.trio_style_capsule),
                master) { index -> update { it.setTrioStyle(styles[index]) } }
        }
        ContentControl.Wifi -> ContentToggle(a.wifi, R.string.show_wifi_title,
            R.string.show_wifi_summary, master) { value -> update { it.setShowWifi(value) } }
        ContentControl.Mobile -> ContentToggle(a.mobile, R.string.show_mobile_title,
            R.string.show_mobile_summary, master) { value -> update { it.setShowMobile(value) } }
        ContentControl.Sim1 -> ContentToggle(a.showSim1, R.string.show_sim_1_title,
            R.string.show_sim_1_summary, master + (a.mobile to R.string.show_mobile_title)) {
            value -> update { it.setShowSim1(value) }
        }
        ContentControl.Sim2 -> ContentToggle(a.showSim2, R.string.show_sim_2_title,
            R.string.show_sim_2_summary, master + (a.mobile to R.string.show_mobile_title)) {
            value -> update { it.setShowSim2(value) }
        }
        ContentControl.Value -> ContentToggle(a.value, R.string.show_value_title,
            R.string.ring_value_summary,
            master) { value -> update { it.setShowValue(value) } }
        // The signal's position is no longer a switch: a card placed in a third is drawn
        // there, and a card that is not placed hands the reading back to MIUI. There is no
        // third state to choose between, so the old row is gone.
        ContentControl.DataSim -> ContentToggle(a.dataSimOnly, R.string.data_sim_only_title,
            R.string.data_sim_only_summary, master) { value -> update { it.setDataSimOnly(value) } }
        ContentControl.Bolt -> ContentToggle(a.boltWanted, R.string.show_bolt_title,
            R.string.ring_bolt_summary,
            master) {
            value -> update { it.setShowBolt(value) }
        }
        ContentControl.Type -> ContentChoice(a.typeMode, R.string.show_mobile_type_title,
            R.string.show_mobile_type_summary,
            listOf(R.string.mobile_type_off, R.string.mobile_type_inside, R.string.mobile_type_outside),
            master) { value -> update { it.setMobileTypeMode(value) } }
        ContentControl.Power -> ContentToggle(settings.perfPower, R.string.perf_power_title,
            R.string.perf_power_summary, master) { value -> update { it.setPerfPower(value) } }
        ContentControl.Temperature -> ContentToggle(settings.perfTemp, R.string.perf_temp_title,
            R.string.perf_temp_summary, master) { value -> update { it.setPerfTemp(value) } }
        ContentControl.Clock -> Unit // Capsule time has a fixed slot.
    }
}

@Composable
private fun RingPositionControl(
    part: TrioZones.Part,
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    val id = TrioZones.encodePart(part)
    // Which zone's list currently holds this piece, or 0 for "not placed".
    //
    // The dropdown is a shortcut into the priority lists, not a second source of truth: it
    // reads where the piece sits and writes the same layout the editor's list edits. A piece
    // in more than one zone cannot happen (placing moves it), so one answer is enough.
    val zones = settings.ringZones
    val placedAt = zones.indexOfFirst { it.contains(id) } + 1
    val selected = if (part == TrioZones.Part.TYPE
        && settings.mobileTypeMode == Prefs.MOBILE_TYPE_OUT_RING) 4 else placedAt
    // The row's title is the SLOT it picks, never the component's own switch name.
    // Reusing the switch title put "显示移动信号点" above "显示移动信号点" - two rows
    // with the same name and different meanings, which reads as a duplicate bug rather
    // than as "choose the slot, then switch it on".
    val title = when (part) {
        TrioZones.Part.WIFI -> R.string.ring_position_wifi
        TrioZones.Part.MOBILE -> R.string.ring_position_mobile
        TrioZones.Part.SIM_1 -> R.string.ring_position_sim_1
        TrioZones.Part.SIM_2 -> R.string.ring_position_sim_2
        TrioZones.Part.VALUE -> R.string.ring_position_value
        TrioZones.Part.BOLT -> R.string.ring_position_bolt
        TrioZones.Part.TYPE -> R.string.ring_position_type
        TrioZones.Part.CLOCK -> R.string.ring_position_clock
    }
    ContentChoice(selected, title,
        if (part == TrioZones.Part.MOBILE) R.string.ring_signal_position_summary else R.string.ring_position_summary,
        listOf(R.string.ring_position_native, R.string.drawing_hint_top,
            R.string.drawing_hint_centre, R.string.drawing_hint_bottom) +
            if (part == TrioZones.Part.TYPE) listOf(R.string.mobile_type_outside) else emptyList(),
        listOf(settings.enabled to R.string.master_title)) { value ->
        update { it.setRingPartPosition(part, value) }
    }
}

@Composable
private fun ContentToggle(
    checked: Boolean,
    title: Int,
    summary: Int,
    dependencies: List<Pair<Boolean, Int>>,
    onChange: (Boolean) -> Unit,
) {
    ContentGate(dependencies) {
        SwitchPreference(
            checked = checked,
            title = stringResource(title),
            summary = stringResource(summary),
            insideMargin = SettingsItemMargin,
            enabled = dependencies.all { it.first },
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun ContentChoice(
    selected: Int,
    title: Int,
    summary: Int,
    options: List<Int>,
    dependencies: List<Pair<Boolean, Int>>,
    onChange: (Int) -> Unit,
) {
    ContentGate(dependencies) {
        OverlayDropdownPreference(
            items = options.map { stringResource(it) },
            selectedIndex = selected,
            title = stringResource(title),
            summary = stringResource(summary),
            insideMargin = SettingsItemMargin,
            enabled = dependencies.all { it.first },
            onSelectedIndexChange = onChange,
        )
    }
}

@Composable
private fun ContentGate(dependencies: List<Pair<Boolean, Int>>, content: @Composable () -> Unit) {
    val missing = dependencies.firstOrNull { !it.first }
    val hint = missing?.let { stringResource(R.string.gate_hint, stringResource(it.second)) }
    TooltipBox(text = hint.orEmpty(), enabled = hint != null, content = content)
}
