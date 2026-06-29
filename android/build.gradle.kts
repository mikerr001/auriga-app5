plugins {
    id("com.android.application") version "8.5.2" apply false
    // Upgraded from 1.9.24 → 2.1.0 to match litertlm:latest.release (0.13.1),
    // which was compiled with Kotlin 2.3 metadata. 2.1.0 + -Xskip-metadata-version-check
    // bridges the remaining gap without requiring the full 2.3 toolchain.
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    // KSP version MUST match the first two components of the Kotlin version.
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}
