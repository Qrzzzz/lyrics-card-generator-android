buildscript {
    dependencies {
        classpath(platform("org.bouncycastle:bc-jdk18on-bom:1.84"))
        classpath(platform("io.netty:netty-bom:4.1.138.Final"))
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.room) apply false
}

