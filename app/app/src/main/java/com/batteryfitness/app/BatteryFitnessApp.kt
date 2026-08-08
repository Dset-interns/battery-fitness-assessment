package com.batteryfitness.app

import android.app.Application

/**
 * Application entry point.
 *
 * Deliberately holds no network, analytics or crash-reporting client. Constraint
 * C1 (Concept Note §7.1) forbids athlete video reaching any commercial cloud,
 * and C8 (Submission §2.1) forbids depending on Google Play Services, so the
 * usual Firebase/Crashlytics bootstrap that would live here must not exist.
 */
class BatteryFitnessApp : Application()
