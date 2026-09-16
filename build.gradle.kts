buildscript {
    repositories {
        maven {
            url = uri("https://storage.googleapis.com/r8-releases/raw")
            content { includeModule("com.android.tools", "r8") }
        }
        google()
        mavenCentral()
    }
    dependencies {
        // Kotlin 2.4 metadata requires R8 9.1.29; retain the existing AGP/Gradle line.
        classpath("com.android.tools:r8:9.1.29")
        constraints {
            classpath("org.apache.commons:commons-compress:1.26.0")
            classpath("org.apache.commons:commons-lang3:3.18.0")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("com.google.protobuf:protobuf-java:3.25.5")
            classpath("com.google.protobuf:protobuf-kotlin:3.25.5")
        }
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

