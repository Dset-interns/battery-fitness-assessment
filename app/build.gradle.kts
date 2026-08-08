// Version set verified resolvable against Google Maven / Maven Central on
// 2026-08-08.
//
// Two toolchain facts drive these choices:
//  1. AGP 9.0+ has BUILT-IN Kotlin support, so 'org.jetbrains.kotlin.android'
//     must NOT be applied — AGP rejects it outright.
//  2. KSP lags Kotlin: newest KSP is 2.3.11, so the Kotlin side stays on 2.3.x.
//     Room's annotation processor needs KSP, so this pair governs the toolchain.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
