package io.github.yixing233.hyperduo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.yixing233.hyperduo.Prefs
import io.github.yixing233.hyperduo.R
import io.github.yixing233.hyperduo.TrioAppearance
import io.github.yixing233.hyperduo.TrioPreviewView
import io.github.yixing233.hyperduo.TrioSettings
import io.github.yixing233.hyperduo.TrioZones
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The drawing page: the glyph rendered large, with its three parts tappable.
 *
 * <p>This is the page that answers "which switch draws the thing I am looking
 * at". Everywhere else in this app the settings are a list of switches named after
 * pieces; here the pieces are the interface, and a switch is what a tap on one
 * does.
 *
 * <p>It is a page of its own rather than a mode of the General tab because it
 * needs room: the targets are thirds of the glyph, so the glyph has to be large
 * enough that a finger can land inside one third on purpose. The General tab's
 * preview strip is about forty dp a cell - a comparison aid, not an input surface.
 *
 * <h2>What a tap does</h2>
 *
 * <p>Tap a third and a list opens of the components that could be drawn there; pick
 * one and it is placed, displacing whatever was there. The list is not a fixed table -
 * it is whatever [TrioZones.choices] reports as placeable in that zone, and choosing
 * an entry writes the switch combination [TrioZones.assign] solved for. Both live in
 * {@code TrioZones} rather than here because they have to agree with where the pieces
 * are actually drawn, and both are checked against real rendered pixels by
 * {@code tests/drawing-zones.ps1}.
 *
 * <p>The first entry is "nothing here", which empties the zone.
 *
 * <p>Long-press on a row opens that component's own size and colour settings, from
 * the same row table the size page renders, so the two places cannot offer
 * different ranges for one setting.
 */

/** Row rhythm inside the editor card, matching the rest of the settings screen. */
private val DrawingItemMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp)

/**
 * Outline inks for the three zones.
 *
 * <p>Distinct hues rather than one colour, so the guide beside the glyph and the
 * outline over it identify a third by colour as well as by name. Drawn at a
 * fraction of full alpha because the outline sits over the glyph and must not
 * compete with its ink.
 */
private val ZoneInkTop = Color(0x8093C5FD)
private val ZoneInkCentre = Color(0x80A6E3A1)
private val ZoneInkBottom = Color(0x80F9E2AF)

/**
 * The battery and signal states the preview can be put into.
 *
 * <p>This exists because three of the six pieces are only reachable in a state the
 * phone is not usually in: the charging bolt needs a charger, the low and critical
 * colours need a low battery, and the dual rows need two answered SIMs. Without a
 * way to ask for those, those pieces would have no tappable presence at all on
 * this page.
 *
 * <p>Each carries the whole state rather than a flag, because they are mutually
 * exclusive: a frame cannot be both charging and critical, and modelling them as
 * independent toggles would allow combinations the renderer has no look for.
 */
private enum class PreviewScene(
    val labelRes: Int,
    val level: Int,
    val charging: Boolean = false,
    val quickCharging: Boolean = false,
    val powerSave: Boolean = false,
    val low: Boolean = false,
    val wifiLevel: Int = WIFI_ON_LEVEL,
    val slotLevels: IntArray? = null,
) {
    Normal(R.string.drawing_scene_normal, 64),
    Charging(R.string.drawing_scene_charging, 88, charging = true),
    QuickCharging(R.string.drawing_scene_quick, 88, charging = true, quickCharging = true),
    Low(R.string.drawing_scene_low, 24, powerSave = true, low = true),
    Critical(R.string.drawing_scene_critical, 12, low = true),
    NoWifi(R.string.drawing_scene_no_wifi, 79, wifiLevel = NO_WIFI_LEVEL),
}

/** The Wi-Fi level the preview shows: mid-strength, so arcs are drawn. */
private const val WIFI_ON_LEVEL = 3

/** What the renderer reads as "no Wi-Fi ink": the percentage drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/** Mobile level the preview shows: full, so the meter is legible. */
private const val MOBILE_LEVEL = 4

/**
 * Per-SIM levels, so the dual reading draws two rows of different heights.
 *
 * <p>SIM 1 one step down from SIM 2, matching the settings screen's own preview
 * strip. Equal levels would make the dual switch invisible in the rectangular
 * arrangement, where the reading only changes which levels the two columns carry.
 */
private val PREVIEW_SLOTS = intArrayOf(2, 4)

/** The same synthetic SIM readings feed the renderer and the placement solver. */
private val PreviewScene.previewSlots: IntArray
    get() = slotLevels ?: PREVIEW_SLOTS

/**
 * The network-type label the preview stands in for the real one.
 *
 * <p>Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so it is the only form in which that setting can be
 * seen at all.
 */
private const val PREVIEW_TYPE = "5GA"

/** Last placement, so a runtime override can be explained beside the preview. */
private data class ZoneSelection(val zone: TrioZones.Zone, val part: TrioZones.Part)

/**
 * The drawing page's body.
 *
 * @param settings the live snapshot, read at composition so a write is visible on
 *   the next frame: the page and the glyph have to agree about what is switched on,
 *   and the glyph is drawn from this same object.
 */
internal fun LazyListScope.drawingTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
) {
    item {
        DrawingEditorCard(
            settings = settings,
            update = update,
            onEditColor = onEditColor,
        )
    }
}

/**
 * The whole editor: what each third holds, the large glyph, and the state selector.
 *
 * <p>[settings] is the live snapshot rather than a local copy - a tap calls
 * straight through to the repository and the parent recomposes with the new value -
 * so there is no second model here to fall out of step with what the status bar
 * will draw.
 */
@Composable
private fun DrawingEditorCard(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEditColor: (RoleColor) -> Unit,
) {
    // Which component's settings dialog is open, if any. The named enum rather than
    // the raw part, so the dialog can be titled with the row name the rest of the
    // screen uses for it.
    var editing by remember { mutableStateOf<TrioSettingsPart?>(null) }
    // The zone whose picker is open, if any. One at a time: the picker is a dialog,
    // and two stacked pickers would be a state with no sensible dismissal order.
    var picking by remember { mutableStateOf<TrioZones.Zone?>(null) }
    // The third under the finger, so the guide can outline what a press would open.
    var pressed by remember { mutableStateOf<TrioZones.Zone?>(null) }
    var scene by remember { mutableStateOf(PreviewScene.Normal) }
    var lastSelection by remember { mutableStateOf<ZoneSelection?>(null) }

    val appearance = TrioAppearance.of(settings)
    val dark = isDarkTheme()

    // This page previews synthetic readings, independently of the switch being
    // edited. Counting the switch would make enabling the second row unreachable.
    val sims = scene.previewSlots.count { it >= 0 }
    val chooseZone: (TrioZones.Zone) -> Unit = { zone ->
        if (appearance.capsule && zone == TrioZones.Zone.TOP) {
            editing = TrioSettingsPart.Clock
        } else {
            picking = zone
        }
    }

    Card {
        Column(modifier = Modifier.padding(DrawingItemMargin)) {
            Text(
                text = stringResource(R.string.drawing_title),
                style = MiuixTheme.textStyles.subtitle,
            )
            Text(
                text = stringResource(R.string.drawing_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )

            DrawingGlyph(
                settings = settings,
                scene = scene,
                dark = dark,
                pressed = pressed,
                onPressChange = { pressed = it },
                // A press on the glyph opens that zone's picker: the request is not
                // "flip something" but "put something else here", so pointing at the
                // zone is choosing which slot to fill, and the list follows.
                onPickZone = chooseZone,
                onEditZone = { zone ->
                    val part = TrioZones.occupant(
                        zone, appearance, scene.level, scene.charging,
                        scene.wifiLevel, PREVIEW_TYPE, sims,
                    )
                    if (part == null) {
                        picking = zone
                    } else {
                        editing = TrioSettingsPart.of(part)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            // What each third holds, and the way to change it. This is the half of
            // the page a picture cannot carry: the glyph shows what is drawn, and
            // these rows name it - so "the middle is empty" and "the middle holds the
            // arcs" are distinguishable, which matters because the arcs move between
            // the notch and the middle as settings change.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ZoneRow(
                    TrioZones.Zone.TOP, zoneLabelRes(TrioZones.Zone.TOP, appearance), ZoneInkTop,
                    settings, appearance, scene, sims, pressed,
                    onPick = { chooseZone(TrioZones.Zone.TOP) },
                    onEdit = { part -> editing = TrioSettingsPart.of(part) },
                )
                ZoneRow(
                    TrioZones.Zone.CENTRE, zoneLabelRes(TrioZones.Zone.CENTRE, appearance), ZoneInkCentre,
                    settings, appearance, scene, sims, pressed,
                    onPick = { chooseZone(TrioZones.Zone.CENTRE) },
                    onEdit = { part -> editing = TrioSettingsPart.of(part) },
                )
                ZoneRow(
                    TrioZones.Zone.BOTTOM, zoneLabelRes(TrioZones.Zone.BOTTOM, appearance), ZoneInkBottom,
                    settings, appearance, scene, sims, pressed,
                    onPick = { chooseZone(TrioZones.Zone.BOTTOM) },
                    onEdit = { part -> editing = TrioSettingsPart.of(part) },
                )
            }

            lastSelection?.let { selection ->
                PlacementFeedback(selection, appearance, scene, sims)
            }

            if (!appearance.glyph) {
                // The preview keeps drawing the chosen arrangement with the master
                // switch off - that is why the glyph is not gated on it, and why the
                // rows above still report an occupancy - so the page has to say why
                // the status bar will not match what it is showing.
                Text(
                    text = stringResource(R.string.drawing_master_off),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                text = stringResource(R.string.drawing_state_title),
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = stringResource(R.string.drawing_state_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp),
            )
            SceneSelector(
                selected = scene,
                onSelect = { scene = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }

    val pickZone = picking
    if (pickZone != null) {
        ZonePickerDialog(
            zone = pickZone,
            settings = settings,
            appearance = appearance,
            scene = scene,
            sims = sims,
            onChoose = { part ->
                val solved = TrioZones.assign(pickZone, appearance.rect, part, settings, sims)
                if (solved != null) {
                    update { r -> applyZoneAssignment(solved.settings, r, settings) }
                    lastSelection = part?.let { ZoneSelection(pickZone, it) }
                }
                picking = null
            },
            onEdit = { part ->
                picking = null
                editing = TrioSettingsPart.of(part)
            },
            onDismiss = { picking = null },
        )
    }

    val current = editing
    if (current != null) {
        PartSettingsDialog(
            part = current,
            settings = settings,
            update = update,
            // The colour picker is the page's own, opened over this dialog: the
            // dialog closes and the picker takes its place, which is the stacking
            // the colour page already uses. Two dialogs at once would be a state
            // the rest of the screen has no way to present.
            onEditColor = { role ->
                editing = null
                onEditColor(role)
            },
            onDismiss = { editing = null },
        )
    }
}

/** Explain the saved component using the same runtime frame as the visible glyph. */
@Composable
private fun PlacementFeedback(
    selection: ZoneSelection,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
) {
    val actual = TrioZones.occupant(
        selection.zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    if (actual == selection.part) return

    val messageRes = when {
        scene.charging && selection.part != TrioZones.Part.BOLT -> R.string.drawing_feedback_charging
        selection.part == TrioZones.Part.BOLT && !scene.charging -> R.string.drawing_feedback_bolt
        selection.part == TrioZones.Part.WIFI && scene.wifiLevel < 0 -> R.string.drawing_feedback_wifi
        else -> R.string.drawing_feedback_other
    }
    Text(
        text = stringResource(
            messageRes,
            stringResource(occupantLabelRes(selection.part)),
            stringResource(zoneLabelRes(selection.zone, appearance)),
            stringResource(occupantLabelRes(actual)),
        ),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * Writes the switch combination already solved for the selected zone and component.
 *
 * <p>The combination is solved by [TrioZones.assign] against the real arbitration
 * rule, so nothing here decides which switches to move. That is deliberate: this
 * page asks for an outcome ("put the number in the middle") and the renderer decides
 * what that costs, which is the only arrangement that cannot drift.
 *
 * <p>Only the switches that differ are written, and they are written through the same
 * repository setters the rest of the screen uses - so the local commit, the push to
 * the hooked process and the broadcast all happen exactly once per changed switch.
 */
private fun applyZoneAssignment(
    next: TrioSettings,
    repository: SettingsRepository,
    settings: TrioSettings,
) {
    if (next.showWifi != settings.showWifi) repository.setShowWifi(next.showWifi)
    if (next.showMobile != settings.showMobile) repository.setShowMobile(next.showMobile)
    if (next.showValue != settings.showValue) repository.setShowValue(next.showValue)
    if (next.showBolt != settings.showBolt) repository.setShowBolt(next.showBolt)
    if (next.dualSim != settings.dualSim) repository.setDualSim(next.dualSim)
    if (next.valueCentred != settings.valueCentred) {
        repository.setValueCentred(next.valueCentred)
    }
    if (next.mobileTypeMode != settings.mobileTypeMode) {
        repository.setMobileTypeMode(next.mobileTypeMode)
    }
}

/**
 * The list of pieces that can be put in one zone.
 *
 * <p>Every entry comes from [TrioZones.choices], which answers by asking the solver
 * whether each piece is placeable - so the menu and the solver cannot disagree. A
 * piece the zone cannot hold at all is still listed, greyed out with the reason,
 * rather than hidden: a missing entry leaves the user wondering whether they opened
 * the wrong list, where a disabled one tells them why.
 *
 * <p>"Nothing here" is the first entry, because emptying a zone is a normal thing to
 * want and burying it at the bottom would push it out of sight on a long list.
 */
@Composable
private fun ZonePickerDialog(
    zone: TrioZones.Zone,
    settings: TrioSettings,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
    onChoose: (TrioZones.Part?) -> Unit,
    onEdit: (TrioZones.Part) -> Unit,
    onDismiss: () -> Unit,
) {
    // Asked of the frame the user is looking at, not of a fixed one: the title says
    // what is in the zone now, and the bolt only exists while charging - so a
    // hard-coded frame would have the title name a piece the user cannot see.
    val current = TrioZones.occupant(
        zone, appearance, scene.level, scene.charging,
        scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    val solvable = TrioZones.choices(zone, appearance.rect, settings, sims)
    val canEmpty = TrioZones.assign(zone, appearance.rect, null, settings, sims) != null

    OverlayDialog(
        show = true,
        title = stringResource(
            R.string.drawing_pick_title,
            stringResource(zoneLabelRes(zone, appearance)),
            stringResource(occupantLabelRes(current)),
        ),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            // Emptying first: it is a normal request, and putting it last would push
            // it off the bottom of a long list.
            PickerRow(
                title = stringResource(R.string.drawing_occupant_none),
                summary = stringResource(if (canEmpty) R.string.drawing_pick_none_summary else R.string.drawing_clock_fixed),
                selected = current == null,
                enabled = canEmpty,
                onClick = { if (canEmpty) onChoose(null) },
                onLongClick = null,
            )

            TrioZones.Part.entries.forEach { part ->
                val canPlace = solvable.contains(part)
                // Why it cannot be placed, in the user's terms. A piece that is
                // solvable somewhere but not here has nowhere to go in this slot; the
                // ring's bottom band is the level dots and nothing else.
                val reason = if (canPlace) null else stringResource(
                    R.string.drawing_pick_impossible,
                    stringResource(zoneLabelRes(zone, appearance)),
                )
                PickerRow(
                    title = stringResource(occupantLabelRes(part)),
                    summary = reason ?: stringResource(partSummaryRes(part)),
                    selected = current == part,
                    enabled = canPlace,
                    onClick = { if (canPlace) onChoose(part) },
                    // Long press goes straight to the piece's own size and colour,
                    // which is the way back to the settings for something already on
                    // screen without re-selecting it.
                    onLongClick = if (canPlace) {
                        { onEdit(part) }
                    } else {
                        null
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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

/** One entry in the picker. */
@Composable
private fun PickerRow(
    title: String,
    summary: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val content = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            )
            .pointerInput(title, enabled, onLongClick) {
                detectTapGestures(
                    onTap = { if (enabled) onClick() },
                    onLongPress = { if (enabled) onLongClick?.invoke() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else content,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (selected) {
            // A check rather than a highlight alone, so the current choice is legible
            // without relying on colour.
            Text(
                text = "✓",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * One zone: its name, what is in it now, and the two ways to change that.
 *
 * <p>The row is a button as well as a label. Pointing at the glyph is a shortcut for
 * the same thing, but the row is what a user who cannot reliably hit a third of an
 * icon will use, and it is the only place the zone's contents are spelled out in
 * words - so making it tappable keeps the page usable rather than decorative.
 */
@Composable
private fun ZoneRow(
    zone: TrioZones.Zone,
    labelRes: Int,
    ink: Color,
    settings: TrioSettings,
    appearance: TrioAppearance,
    scene: PreviewScene,
    sims: Int,
    pressed: TrioZones.Zone?,
    onPick: () -> Unit,
    onEdit: (TrioZones.Part) -> Unit,
) {
    val current = TrioZones.occupant(
        zone, appearance, scene.level, scene.charging, scene.wifiLevel, PREVIEW_TYPE, sims,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (pressed == zone) {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                } else {
                    Color.Transparent
                },
            )
            .pointerInput(zone, current, sims) {
                detectTapGestures(
                    onTap = { onPick() },
                    // Long press on the row goes to the current piece's own settings,
                    // which is the shortcut for "I do not want to move this, I want to
                    // resize it".
                    onLongPress = { current?.let(onEdit) },
                )
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (pressed == zone) ink.copy(alpha = 1f) else ink),
        )
        Text(
            text = stringResource(labelRes),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.22f),
        )
        Text(
            text = stringResource(occupantLabelRes(current)),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(0.44f),
        )
        Text(
            text = stringResource(if (current == TrioZones.Part.CLOCK) R.string.drawing_zone_edit else R.string.drawing_zone_change),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.34f),
        )
    }
}

/** The large glyph, with the three zones as touch targets and guides.
 *
 * <p>The zones are resolved by the <em>view</em>, because it is what knows the
 * transform the glyph is drawn with: the gestures here ask it for the third under
 * the finger rather than working one out from the touch coordinates. That is the
 * whole design of this composable - a second copy of the design-to-view mapping on
 * the Compose side is exactly how a hot spot ends up beside the piece it names.
 *
 * <p>The gestures are Compose's rather than a touch listener on the view, and that
 * is a deliberate correction: a listener returning true for `ACTION_DOWN` swallows
 * the event, so the page could not be scrolled by dragging over a glyph that fills
 * most of the screen. [detectTapGestures] instead gives up the moment the finger
 * moves past the touch slop, so a drag scrolls the list and a press that stays put
 * is a tap.
 *
 * <p>The handler reads its callbacks through [rememberUpdatedState] so the detector,
 * which is keyed on the settings rather than rebuilt per frame, always calls the
 * current lambda; one captured at composition would close over an older snapshot
 * and flip a switch based on what was on screen several edits ago.
 */
@Composable
private fun DrawingGlyph(
    settings: TrioSettings,
    scene: PreviewScene,
    dark: Boolean,
    pressed: TrioZones.Zone?,
    onPressChange: (TrioZones.Zone?) -> Unit,
    onPickZone: (TrioZones.Zone) -> Unit,
    onEditZone: (TrioZones.Zone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPress = rememberUpdatedState(onPressChange)
    val currentPick = rememberUpdatedState(onPickZone)
    val currentEdit = rememberUpdatedState(onEditZone)

    // The view the gestures interrogate. A plain holder rather than a MutableState:
    // it is written once, from `factory`, and read only later - when a finger
    // arrives - so there is nothing here that ought to trigger recomposition, and
    // writing state during the layout that creates the view would.
    val holder = remember { arrayOfNulls<TrioPreviewView>(1) }
    // Resolved against the view that is actually laid out, so a gesture arriving
    // before the first layout finds no zone instead of guessing one.
    val zoneAt: (Offset) -> TrioZones.Zone? = { offset ->
        holder[0]?.zoneAt(offset.x, offset.y)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(settings, scene) {
                detectTapGestures(
                    onPress = { offset ->
                        currentPress.value(zoneAt(offset))
                        // Waits for the press to resolve, which is also what lets a
                        // drag cancel this detector and be handled as a scroll.
                        tryAwaitRelease()
                        currentPress.value(null)
                    },
                    onTap = { offset -> zoneAt(offset)?.let { currentPick.value(it) } },
                    onLongPress = { offset -> zoneAt(offset)?.let { currentEdit.value(it) } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            // Fills the square Box above; the aspect ratio belongs to the Box so the
            // gesture detector covers exactly the area the glyph is drawn in.
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TrioPreviewView(context).also { holder[0] = it }
            },
            update = { view ->
                view.setSettings(settings)
                view.setOutTypeOnly(false)
                view.setOutSignalOnly(false)
                view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
                view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                view.setHighlight(pressed)
                view.setState(
                    scene.level, scene.charging, scene.quickCharging, scene.powerSave,
                    scene.low, scene.wifiLevel, MOBILE_LEVEL,
                    scene.previewSlots, PREVIEW_TYPE,
                )
            },
        )
    }
}

/** The short name for a piece, for the zone rows and the picker. */
private fun occupantLabelRes(part: TrioZones.Part?): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.drawing_occupant_wifi
    TrioZones.Part.MOBILE -> R.string.drawing_occupant_mobile
    TrioZones.Part.VALUE -> R.string.drawing_occupant_value
    TrioZones.Part.BOLT -> R.string.drawing_occupant_bolt
    TrioZones.Part.TYPE -> R.string.drawing_occupant_type
    TrioZones.Part.DUAL -> R.string.drawing_occupant_dual
    TrioZones.Part.CLOCK -> R.string.drawing_occupant_clock
    null -> R.string.drawing_occupant_none
}

/** The zone's own name, for the picker's title and its refusals. */
private fun zoneLabelRes(zone: TrioZones.Zone, a: TrioAppearance): Int = when (zone) {
    TrioZones.Zone.TOP -> if (a.capsule) R.string.drawing_hint_clock else R.string.drawing_hint_top
    TrioZones.Zone.CENTRE -> if (a.capsule) R.string.drawing_hint_reading else R.string.drawing_hint_centre
    TrioZones.Zone.BOTTOM -> if (a.capsule) R.string.drawing_hint_signal else R.string.drawing_hint_bottom
}

/** One line saying what a piece is, for the picker. */
private fun partSummaryRes(part: TrioZones.Part): Int = when (part) {
    TrioZones.Part.WIFI -> R.string.show_wifi_summary
    TrioZones.Part.MOBILE -> R.string.show_mobile_summary
    TrioZones.Part.VALUE -> R.string.show_value_summary
    TrioZones.Part.BOLT -> R.string.show_bolt_summary
    TrioZones.Part.TYPE -> R.string.show_mobile_type_summary
    TrioZones.Part.DUAL -> R.string.dual_sim_summary
    TrioZones.Part.CLOCK -> R.string.clock_size_summary
}

/**
 * The preview-state chips.
 *
 * <p>A horizontal strip rather than a dropdown: there are six, their labels are
 * short, and seeing the whole set is what tells the user that a charging frame is
 * reachable at all - which is the only way the bolt becomes tappable.
 */
@Composable
private fun SceneSelector(
    selected: PreviewScene,
    onSelect: (PreviewScene) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PreviewScene.entries, key = { it.name }) { entry ->
            SceneChip(
                label = stringResource(entry.labelRes),
                selected = entry == selected,
                onClick = { onSelect(entry) },
            )
        }
    }
}

/** One state chip. */
@Composable
private fun SceneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainerHigh
            },
            textAlign = TextAlign.Center,
        )
    }
}
