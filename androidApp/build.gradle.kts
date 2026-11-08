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
    }

    androidResources {
        // The model is memory-mapped straight out of the APK, so it must stay uncompressed.
        noCompress += "tflite"
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
    debugImplementation(libs.compose.ui.tooling)
}

// Privacy guarantee, enforced by the build: no variant may ship with network access
// or advertising permissions, even if a library tries to sneak one in.
abstract class VerifyPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbidden: ListProperty<String>

    @TaskAction
    fun verify() {
        val text = mergedManifest.get().asFile.readText()
        val found = forbidden.get().filter { text.contains("\"$it\"") }
        if (found.isNotEmpty()) throw GradleException("Forbidden permissions in merged manifest: $found")
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
        }
        tasks.named("check") { dependsOn(check) }
    }
}
