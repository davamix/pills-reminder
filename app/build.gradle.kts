import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Release signing (P8, plan PQ-3). The keystore and its passwords are user-level Gradle properties
// (~/.gradle/gradle.properties), never in the repo. Without all four, as in CI, the release build is left unsigned.
val releaseSigning =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .associateWith { providers.gradleProperty("pillsReminder.release.$it").orNull }

android {
    namespace = "io.github.davamix.pillsreminder"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.davamix.pillsreminder"
        // Android 13, the phone (plan D-11).
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigning.values.all { it != null }) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile")!!)
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets debug and release builds live side by side, each with its own data (P1-D2).
            applicationIdSuffix = ".debug"
        }
        release {
            // Not minified: the release runs the code the tests ran (P8 Q5).
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG: only debug builds log (plan §3).
        buildConfig = true
    }

    androidResources {
        // The UI is in English (NF-8). Without this, the libraries' own texts, such as Material's date picker title,
        // follow the phone's language (Spanish on the phone) in an English app (P6). A translation adds its language.
        localeFilters += "en"
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    testOptions {
        // :app unit tests use JUnit Jupiter, like :core.
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.core)
    // The store: one JSON document in DataStore (plan D-4, P3).
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

// The only permissions the merged manifest may request (plan §3, P1-D6). Allowing one doesn't declare it: P5 adds the
// reminder permissions to the manifest. A new permission needs a change to the plan first.
val allowedPermissions =
    listOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    )

// Fails `check` if a merged manifest requests a permission outside the allowlist, for example because a library added
// it. INTERNET gets its own message, because NF-3 forbids it outright.
abstract class CheckPermissionAllowlist : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val allowed: ListProperty<String>

    @get:Input
    abstract val variantName: Property<String>

    @TaskAction
    fun check() {
        val manifest = mergedManifest.get().asFile.readText()
        val requested =
            Regex("""<uses-permission(?:-sdk-23)?\b[^>]*?android:name="([^"]+)"""")
                .findAll(manifest)
                .map { it.groupValues[1] }
                .toSortedSet()
        if ("android.permission.INTERNET" in requested) {
            throw GradleException(
                "The merged ${variantName.get()} manifest requests android.permission.INTERNET, which NF-3 forbids. " +
                    "Find the dependency that adds it.",
            )
        }
        val extra = requested - allowed.get().toSet()
        if (extra.isNotEmpty()) {
            throw GradleException(
                "The merged ${variantName.get()} manifest requests permissions outside the allowlist of plan §3: " +
                    "${extra.joinToString()}. Find what adds them, or change the plan first.",
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val checkTask =
            tasks.register<CheckPermissionAllowlist>("check${name}PermissionAllowlist") {
                group = "verification"
                description = "Fails if the merged ${variant.name} manifest requests a permission outside plan §3."
                mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                variantName.set(variant.name)
                // AndroidX declares this one and grants it to the app itself; it gives access to nothing.
                allowed.set(variant.applicationId.map { allowedPermissions + "$it.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" })
            }
        tasks.named("check") { dependsOn(checkTask) }
    }
}
