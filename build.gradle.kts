// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath(libs.objectbox)
        classpath("com.google.gms:google-services:4.4.0")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.9")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.24" apply false
}