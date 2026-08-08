import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

val rendererAssetsRoot = layout.buildDirectory.dir("generated/renderer/assets")
val rendererOutputDirectory = rendererAssetsRoot.map { it.dir("renderer") }

android {
    namespace = "com.qrzzzz.lyricscard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qrzzzz.lyricscard"
        minSdk = 26
        targetSdk = 36
        versionCode = 10000
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("int", "RENDERER_SCHEMA_VERSION", "1")
        buildConfigField("String", "RENDERER_VERSION", "\"android-alpha-renderer-1\"")
        buildConfigField("String", "BASELINE_COMMIT", "\"b894db9e121848122a16ddcdaaab1283ffab1e27\"")
    }

    flavorDimensions += "channel"
    productFlavors {
        create("alpha") {
            dimension = "channel"
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha03"
            resValue("string", "app_name", "歌词卡片 Alpha")
        }
        create("production") {
            dimension = "channel"
            resValue("string", "app_name", "歌词卡片")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    androidResources {
        noCompress += "otf"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.named("main") {
        assets.srcDir(rendererAssetsRoot)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.webkit)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.accessibility)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

val buildRenderer by tasks.registering(Exec::class) {
    group = "renderer"
    description = "Builds the trusted local web renderer into generated Android assets."
    workingDir(rootProject.file("renderer"))
    commandLine(
        if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm",
        "run",
        "build",
    )
    environment("RENDERER_OUT_DIR", rendererOutputDirectory.get().asFile.absolutePath)
    inputs.files(
        rootProject.file("renderer/package.json"),
        rootProject.file("renderer/package-lock.json"),
        rootProject.file("renderer/tsconfig.json"),
        rootProject.file("renderer/vite.config.ts"),
        rootProject.file("renderer/index.html"),
        rootProject.file("renderer/renderer-manifest.json"),
    )
    inputs.dir(rootProject.file("renderer/src"))
    inputs.dir(rootProject.file("renderer/scripts"))
    inputs.dir(rootProject.file("renderer/public"))
    inputs.dir(rootProject.file("renderer/schema"))
    outputs.dir(rendererOutputDirectory)
}

tasks.named("preBuild").configure {
    dependsOn(buildRenderer)
}
