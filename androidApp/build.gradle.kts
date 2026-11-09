import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.moobehgames.snoresafe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moobehgames.snoresafe"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Shared debug key (standard Android test key, not a secret) so test builds from any
        // machine or CI install over each other without wiping data. Never used for Play.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    androidResources {
        // The model is memory-mapped straight out of the APK, so it must stay uncompressed.
        noCompress += "tflite"
    }

    testOptions {
        unitTests {
            // Robolectric renders real Compose screens on the JVM (screenshots + smoke tests).
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "3g" }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.litert)
    implementation(libs.billing)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Privacy guarantee, enforced by the build: no variant may ship with network access
// or advertising permissions, even if a library tries to sneak one in.
abstract class VerifyPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbidden: ListProperty<String>

    /** Telemetry / analytics components, matched anywhere in the merged manifest. */
    @get:Input
    abstract val forbiddenComponents: ListProperty<String>

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val found = forbidden.get().filter { text.contains("\"$it\"") }
        if (found.isNotEmpty()) throw GradleException("Forbidden permissions in merged manifest: $found")
        val components = forbiddenComponents.get().filter { text.contains(it) }
        if (components.isNotEmpty()) throw GradleException("Telemetry components in merged manifest: $components")
    }
}

androidComponents {
    onVariants { variant ->
        val check = tasks.register<VerifyPermissionsTask>("verify${variant.name.replaceFirstChar { it.uppercase() }}Permissions") {
            group = "verification"
            description = "Fails if the merged manifest requests a forbidden permission."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            forbidden.set(
                listOf(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "com.google.android.gms.permission.AD_ID",
                    "android.permission.ACCESS_ADSERVICES_AD_ID",
                    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                ),
            )
            forbiddenComponents.set(
                listOf(
                    "com.google.android.datatransport",
                    "com.google.firebase",
                    "com.google.android.gms.measurement",
                    "com.google.android.gms.ads",
                ),
            )
        }
        tasks.named("check") { dependsOn(check) }
    }
}

// Screen render tests need the debug-only Compose test activity; release runs the plain unit tests.
tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
    filter.excludeTestsMatching("*ScreenshotTest")
}
