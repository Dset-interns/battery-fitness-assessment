package com.batteryfitness.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.batteryfitness.app.R
import com.batteryfitness.app.domain.BatteryTest

/**
 * Display names for the ten tests, resolved through Android resources.
 *
 * Kept in the UI layer on purpose: `BatteryTest` is a pure domain enum with no
 * Android dependency, which is what lets it be unit-tested on the JVM without
 * an emulator.
 *
 * Routing every name through `strings.xml` is what makes Concept Note §7.3's
 * "Hindi and English at a minimum" real rather than merely packaged — the
 * device locale now switches the whole test list. Additional Indian languages
 * (bonus #8) become a matter of adding a values-xx/ directory, with no code
 * change.
 */
@get:StringRes
val BatteryTest.nameRes: Int
    get() = when (this) {
        BatteryTest.HEIGHT -> R.string.test_height
        BatteryTest.WEIGHT -> R.string.test_weight
        BatteryTest.SIT_AND_REACH -> R.string.test_sit_and_reach
        BatteryTest.VERTICAL_JUMP -> R.string.test_vertical_jump
        BatteryTest.BROAD_JUMP -> R.string.test_broad_jump
        BatteryTest.MEDICINE_BALL_THROW -> R.string.test_medicine_ball_throw
        BatteryTest.SPRINT_30M -> R.string.test_sprint_30m
        BatteryTest.SHUTTLE_RUN -> R.string.test_shuttle_run
        BatteryTest.SIT_UPS -> R.string.test_sit_ups
        BatteryTest.ENDURANCE_RUN -> R.string.test_endurance_run
    }

/** Localised test name for use inside composables. */
@Composable
fun BatteryTest.label(): String = stringResource(nameRes)
