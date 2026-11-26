plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.vkardin.koly"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.vkardin.koly"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.6.0"
    }

    androidResources {
        generateLocaleConfig = false
    }

    // Release signing comes from the environment (CI secrets); without it the
    // release build is signed with the debug key so it can be tried locally.
    val keystore = System.getenv("KOLY_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("KOLY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KOLY_KEY_ALIAS")
                keyPassword = System.getenv("KOLY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            optimization {
                enable = true
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The power map is the same module the website uses (web/powermap.*), shipped as assets in map/.
abstract class CopyMapAssets : DefaultTask() {
    @get:InputFiles
    abstract val web: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("map")
        target.deleteRecursively()
        target.mkdirs()
        web.files.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val copyMapAssets = tasks.register<CopyMapAssets>("copyMapAssets") {
    web.from(
        listOf("powermap.js", "powermap.css", "ua-regions.json", "vendor/maplibre-gl-5.24.1.js", "vendor/maplibre-gl-5.24.1.css")
            .map { rootProject.file("../web/$it") },
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyMapAssets, CopyMapAssets::outputDir)
    }
}

base {
    archivesName.set("koly-${android.defaultConfig.versionName}")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:places"))
    implementation(project(":feature:settings"))
    implementation(project(":widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
}
