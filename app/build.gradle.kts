import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.util.Properties

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

val productionSigningPropertyFile = rootProject.file("release-signing.properties")
val productionSigningProperties = Properties().apply {
    if (productionSigningPropertyFile.isFile) {
        productionSigningPropertyFile.inputStream().use(::load)
    }
}
val productionSigningKeys = listOf(
    "LYRICS_CARD_STORE_FILE",
    "LYRICS_CARD_STORE_PASSWORD",
    "LYRICS_CARD_KEY_ALIAS",
    "LYRICS_CARD_KEY_PASSWORD",
)
val productionSigningValues = productionSigningKeys.associateWith { key ->
    providers.environmentVariable(key).orNull
        ?.takeIf(String::isNotBlank)
        ?: productionSigningProperties.getProperty(key)?.takeIf(String::isNotBlank)
}
val configuredProductionSigningKeys = productionSigningValues.filterValues { it != null }.keys
if (configuredProductionSigningKeys.isNotEmpty() && configuredProductionSigningKeys.size != productionSigningKeys.size) {
    val missing = productionSigningKeys.filterNot(configuredProductionSigningKeys::contains)
    throw GradleException(
        "Incomplete production signing configuration. Missing: ${missing.joinToString()}",
    )
}
val hasProductionSigning = configuredProductionSigningKeys.size == productionSigningKeys.size
val productionStoreFile = productionSigningValues["LYRICS_CARD_STORE_FILE"]?.let(rootProject::file)
if (hasProductionSigning && productionStoreFile?.isFile != true) {
    throw GradleException("The configured production signing store file does not exist or is not a file.")
}

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
        versionCode = 10103
        versionName = "1.1.3"

        testInstrumentationRunner = "com.qrzzzz.lyricscard.ui.ReleaseEvidenceTestRunner"
        testProguardFiles("test-proguard-rules.pro")
        vectorDrawables.useSupportLibrary = true
        buildConfigField("int", "RENDERER_SCHEMA_VERSION", "1")
        buildConfigField("String", "RENDERER_VERSION", "\"android-alpha-renderer-1\"")
        buildConfigField("String", "BASELINE_COMMIT", "\"b894db9e121848122a16ddcdaaab1283ffab1e27\"")
    }

    flavorDimensions += "channel"
    val productionReleaseSigning = if (hasProductionSigning) {
        signingConfigs.create("productionRelease") {
            storeFile = productionStoreFile
            storePassword = productionSigningValues.getValue("LYRICS_CARD_STORE_PASSWORD")
            keyAlias = productionSigningValues.getValue("LYRICS_CARD_KEY_ALIAS")
            keyPassword = productionSigningValues.getValue("LYRICS_CARD_KEY_PASSWORD")
        }
    } else {
        null
    }
    productFlavors {
        create("alpha") {
            dimension = "channel"
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha03"
        }
        create("production") {
            dimension = "channel"
            productionReleaseSigning?.let { signingConfig = it }
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
        testBuildType = "release"
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.named("main") {
        assets.srcDir(rendererAssetsRoot)
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        variant.unitTest?.configureTestTask { testTask ->
            testTask.inputs.file(mergedManifest).withPropertyName("backupRulesMergedManifest")
            testTask.jvmArgumentProviders.add(
                CommandLineArgumentProvider {
                    listOf("-Dlyricscard.mergedManifest=${mergedManifest.get().asFile.absolutePath}")
                },
            )
        }
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

val bundletoolCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isVisible = false
}

val unifiedTestPlatformConfigurationPrefix = "_internal-unified-test-platform-"
val utpNettyAlignment by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
    isVisible = false
}

configurations.configureEach {
    if (name.startsWith(unifiedTestPlatformConfigurationPrefix)) {
        extendsFrom(utpNettyAlignment)
    }
}

dependencies {
    add(bundletoolCli.name, libs.android.bundletool)
    add(utpNettyAlignment.name, platform("io.netty:netty-bom:4.1.138.Final"))
    constraints {
        add(bundletoolCli.name, "org.bitbucket.b_c:jose4j:0.9.6")
    }

    implementation(platform(libs.androidx.compose.bom))
    testImplementation(platform("org.bouncycastle:bc-jdk18on-bom:1.84"))
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
    releaseImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.accessibility)
    androidTestCompileOnly(libs.guava.atf)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

val minimumBouncyCastleVersion = "1.84"
val bouncyCastleBomModule = "bc-jdk18on-bom"
val requiredBuildscriptBouncyCastleModules =
    setOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on")

fun parseStableVersion(version: String): List<Int>? {
    if (!version.matches(Regex("\\d+(?:\\.\\d+)*"))) return null
    return version.split('.').map(String::toInt)
}

fun isVersionAtLeast(version: String, minimum: String): Boolean {
    val actualParts = parseStableVersion(version) ?: return false
    val minimumParts = parseStableVersion(minimum) ?: return false
    val componentCount = maxOf(actualParts.size, minimumParts.size)

    for (index in 0 until componentCount) {
        val actual = actualParts.getOrElse(index) { 0 }
        val required = minimumParts.getOrElse(index) { 0 }
        if (actual != required) return actual > required
    }

    return true
}

fun resolvedBouncyCastleModules(
    configuration: org.gradle.api.artifacts.Configuration,
): Map<String, String> {
    val resolution = configuration.incoming.resolutionResult
    val unresolved = resolution.allDependencies
        .filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
    check(unresolved.isEmpty()) {
        "Cannot verify Bouncy Castle in unresolved configuration ${configuration.name}: " +
            unresolved.joinToString { it.attempted.displayName }
    }
    return resolution.allComponents
        .mapNotNull { it.moduleVersion }
        .filter { it.group == "org.bouncycastle" && it.name != bouncyCastleBomModule }
        .associate { it.name to it.version }
}

tasks.register("verifyBouncyCastleResolution") {
    group = "verification"
    description = "Verifies fixed Bouncy Castle versions and scope isolation."

    doLast {
        val pluginClasspath = resolvedBouncyCastleModules(
            rootProject.buildscript.configurations.getByName("classpath"),
        )
        val unitTestRuntime = resolvedBouncyCastleModules(
            configurations.getByName("productionReleaseUnitTestRuntimeClasspath"),
        )
        val appRuntime = resolvedBouncyCastleModules(
            configurations.getByName("productionReleaseRuntimeClasspath"),
        )
        val testApkRuntime = resolvedBouncyCastleModules(
            configurations.getByName("productionReleaseAndroidTestRuntimeClasspath"),
        )

        fun checkFixedFamily(scope: String, modules: Map<String, String>) {
            if (modules.isEmpty()) return
            check(modules.values.toSet().size == 1) {
                "$scope resolved a mixed Bouncy Castle family: $modules"
            }
            check(modules.values.all { isVersionAtLeast(it, minimumBouncyCastleVersion) }) {
                "$scope resolved Bouncy Castle below $minimumBouncyCastleVersion: $modules"
            }
        }

        check(pluginClasspath.keys.containsAll(requiredBuildscriptBouncyCastleModules)) {
            "Buildscript classpath is missing expected Bouncy Castle modules: $pluginClasspath"
        }
        check("bcprov-jdk18on" in unitTestRuntime) {
            "JVM unit-test runtime is missing bcprov-jdk18on: $unitTestRuntime"
        }
        checkFixedFamily("Buildscript classpath", pluginClasspath)
        checkFixedFamily("JVM unit-test runtime", unitTestRuntime)
        check(appRuntime.isEmpty()) {
            "Bouncy Castle modules leaked into the app runtime: $appRuntime"
        }
        check(testApkRuntime.isEmpty()) {
            "Bouncy Castle modules leaked into the instrumentation test APK: $testApkRuntime"
        }

        logger.lifecycle(
            "Verified Bouncy Castle resolution: buildscript={}, unitTestRuntime={}, appRuntime={}, testApkRuntime={}",
            pluginClasspath,
            unitTestRuntime,
            appRuntime,
            testApkRuntime,
        )
    }
}

val minimumNettyPatchVersion = 138
val nettyBomModule = "netty-bom"
val expectedNettyUtpConfigurations = setOf(
    "${unifiedTestPlatformConfigurationPrefix}core",
    "${unifiedTestPlatformConfigurationPrefix}android-test-plugin-host-emulator-control",
    "${unifiedTestPlatformConfigurationPrefix}android-test-plugin-result-listener-gradle",
)
val requiredNettyHostModules = setOf(
    "netty-handler",
    "netty-codec-http",
    "netty-codec-http2",
)

fun resolvedNettyModules(
    configuration: org.gradle.api.artifacts.Configuration,
): Map<String, String> {
    val resolution = configuration.incoming.resolutionResult
    val unresolved = resolution.allDependencies
        .filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
    check(unresolved.isEmpty()) {
        "Cannot verify Netty in unresolved configuration ${configuration.name}: " +
            unresolved.joinToString { it.attempted.displayName }
    }
    return resolution.allComponents
        .mapNotNull { it.moduleVersion }
        .filter { it.group == "io.netty" && it.name != nettyBomModule }
        .associate { it.name to it.version }
}

fun checkSafeNettyFamily(scope: String, modules: Map<String, String>) {
    if (modules.isEmpty()) return
    check(modules.values.toSet().size == 1) {
        "$scope resolved a mixed Netty family: $modules"
    }
    check(modules.values.all { version ->
        Regex("4\\.1\\.(\\d+)\\.Final").matchEntire(version)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { it >= minimumNettyPatchVersion } == true
    }) {
        "$scope resolved Netty outside the safe 4.1.x baseline " +
            "(minimum 4.1.$minimumNettyPatchVersion.Final): $modules"
    }
}

tasks.register("verifyNettyResolution") {
    group = "verification"
    description = "Verifies fixed host-tool Netty versions and product scope isolation."

    doLast {
        val buildscriptNetty = resolvedNettyModules(
            rootProject.buildscript.configurations.getByName("classpath"),
        )
        check(buildscriptNetty.isNotEmpty()) {
            "Buildscript classpath is missing the expected Netty family."
        }
        check(buildscriptNetty.keys.containsAll(requiredNettyHostModules)) {
            "Buildscript classpath is missing expected Netty modules: $buildscriptNetty"
        }
        checkSafeNettyFamily("Buildscript classpath", buildscriptNetty)

        val utpConfigurations = configurations
            .filter { it.name.startsWith(unifiedTestPlatformConfigurationPrefix) }
            .sortedBy { it.name }
        val missingExpectedConfigurations = expectedNettyUtpConfigurations -
            utpConfigurations.mapTo(mutableSetOf()) { it.name }
        check(missingExpectedConfigurations.isEmpty()) {
            "Missing expected Unified Test Platform configurations: $missingExpectedConfigurations"
        }
        val utpNetty = utpConfigurations.associate { configuration ->
            configuration.name to resolvedNettyModules(configuration)
        }
        expectedNettyUtpConfigurations.forEach { configurationName ->
            val modules = utpNetty.getValue(configurationName)
            check(modules.keys.containsAll(requiredNettyHostModules)) {
                "$configurationName is missing expected Netty modules: $modules"
            }
        }
        utpNetty.forEach { (configurationName, modules) ->
            checkSafeNettyFamily("UTP configuration $configurationName", modules)
        }

        val productConfigurations = listOf(
            "productionReleaseCompileClasspath",
            "productionReleaseRuntimeClasspath",
            "productionReleaseUnitTestRuntimeClasspath",
            "productionReleaseAndroidTestCompileClasspath",
            "productionReleaseAndroidTestRuntimeClasspath",
            bundletoolCli.name,
        )
        val productNetty = productConfigurations.associateWith { configurationName ->
            resolvedNettyModules(configurations.getByName(configurationName))
        }
        val leakedNetty = productNetty.filterValues { it.isNotEmpty() }
        check(leakedNetty.isEmpty()) {
            "Netty modules leaked outside host-tool configurations: $leakedNetty"
        }

        logger.lifecycle(
            "Verified Netty resolution: buildscript={}, utp={}, product={}",
            buildscriptNetty,
            utpNetty,
            productNetty,
        )
    }
}

val minimumHostParserVersions = mapOf(
    "org.jdom:jdom2" to "2.0.6.1",
    "org.bitbucket.b_c:jose4j" to "0.9.6",
)

fun resolvedHostParserModules(
    configuration: org.gradle.api.artifacts.Configuration,
): Map<String, String> {
    val resolution = configuration.incoming.resolutionResult
    val unresolved = resolution.allDependencies
        .filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
    check(unresolved.isEmpty()) {
        "Cannot verify host parser dependencies in unresolved configuration ${configuration.name}: " +
            unresolved.joinToString { it.attempted.displayName }
    }
    return resolution.allComponents
        .mapNotNull { it.moduleVersion }
        .map { "${it.group}:${it.name}" to it.version }
        .filter { (coordinate, _) -> coordinate in minimumHostParserVersions }
        .toMap()
}

fun checkPatchedHostParserModules(
    scope: String,
    modules: Map<String, String>,
    requiredCoordinates: Set<String>,
) {
    val missing = requiredCoordinates - modules.keys
    check(missing.isEmpty()) {
        "$scope is missing expected host parser dependencies: $missing"
    }
    modules.forEach { (coordinate, version) ->
        val minimum = minimumHostParserVersions.getValue(coordinate)
        check(isVersionAtLeast(version, minimum)) {
            "$scope resolved $coordinate below $minimum or to an unstable version: $version"
        }
    }
}

tasks.register("verifyHostParserResolution") {
    group = "verification"
    description = "Verifies patched host parser dependencies and product scope isolation."

    doLast {
        val buildscriptModules = resolvedHostParserModules(
            rootProject.buildscript.configurations.getByName("classpath"),
        )
        checkPatchedHostParserModules(
            "Buildscript classpath",
            buildscriptModules,
            minimumHostParserVersions.keys,
        )

        val bundletoolModules = resolvedHostParserModules(bundletoolCli)
        checkPatchedHostParserModules(
            "Bundletool CLI",
            bundletoolModules,
            setOf("org.bitbucket.b_c:jose4j"),
        )

        val productConfigurations = listOf(
            "productionReleaseCompileClasspath",
            "productionReleaseRuntimeClasspath",
            "productionReleaseAndroidTestCompileClasspath",
            "productionReleaseAndroidTestRuntimeClasspath",
        )
        val productModules = productConfigurations.associateWith { configurationName ->
            resolvedHostParserModules(configurations.getByName(configurationName))
        }
        val leakedModules = productModules.filterValues { it.isNotEmpty() }
        check(leakedModules.isEmpty()) {
            "Host parser dependencies leaked into product configurations: $leakedModules"
        }

        logger.lifecycle(
            "Verified host parser resolution: buildscript={}, bundletool={}, product={}",
            buildscriptModules,
            bundletoolModules,
            productModules,
        )
    }
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

val productionReleaseBundle =
    layout.buildDirectory.file("outputs/bundle/productionRelease/app-production-release.aab")
val productionReleaseBundleManifest =
    layout.buildDirectory.file("reports/production-release-bundle-manifest.xml")

tasks.register<JavaExec>("dumpProductionReleaseBundleManifest") {
    group = "verification"
    description = "Extracts the production release manifest from the built AAB with pinned bundletool."
    dependsOn("bundleProductionRelease")

    classpath = bundletoolCli
    mainClass.set("com.android.tools.build.bundletool.BundleToolMain")
    inputs.file(productionReleaseBundle)
    outputs.file(productionReleaseBundleManifest)

    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "dump",
                "manifest",
                "--bundle=${productionReleaseBundle.get().asFile.absolutePath}",
                "--module=base",
            )
        },
    )

    val manifestOutput = ByteArrayOutputStream()
    standardOutput = manifestOutput
    doFirst {
        manifestOutput.reset()
    }
    doLast {
        val outputFile = productionReleaseBundleManifest.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeBytes(manifestOutput.toByteArray())
        check(outputFile.length() > 0L) {
            "bundletool did not emit a production release manifest."
        }
    }
}
