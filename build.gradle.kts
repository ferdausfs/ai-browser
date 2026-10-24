// Root build configuration for AI Browser.
//
// Version policy (all mutually compatible, all stable releases):
// - AGP 8.4.2 requires Gradle 8.6+ and JDK 17+
// - Kotlin 1.9.24 pairs with Compose compiler 1.5.14
// - KSP 1.9.24-1.0.20 matches Kotlin 1.9.24 (used by Room)
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
