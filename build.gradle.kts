// Top-level build file (project/build.gradle.kts)
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}