import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release CI derives both numbers from the pushed tag and passes them in, so the
// version in the APK, the version in the About card and the version the updater
// compares against are all the same value. A local build falls back to the
// checked-in defaults below.
//
// OriginDuo versioning: 1.x.x feature releases, x.0.x fix releases, x.x.1 test
// builds, at most three digits, starting from 1.0.1 (same scheme as VivoDuoBar).
val tagVersionName = (findProperty("originduoVersionName") as String?)?.takeIf { it.isNotBlank() }
val tagVersionCode = (findProperty("originduoVersionCode") as String?)?.toIntOrNull()

// Signing: reuse the VivoDuoBar release key so both vivo adaptations come from
// the same author identity. Not checked in - see keystore.properties (gitignored).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    // io.github.* because LSPosed's module repo verifies a reverse-domain id
    // against a DNS TXT record for the root domain: anything under a domain
    // registered by someone else would fail the check. The io.github.<user>
    // form needs no domain and no TXT record.
    //
    // The Java package / namespace stays io.github.yixing233.hyperduo (the fork
    // upstream) so the xposed entry class in META-INF does not move; only the
    // installed app id changes, which is what makes this a separate module that
    // can be installed next to the original HyperDuo.
    namespace = "io.github.yixing233.hyperduo"
    compileSdk = 37

    defaultConfig {
        // Changing this makes the module a different app to Android: an existing
        // io.github.yixing233.hyperduo install cannot be updated in place, and its
        // settings and LSPosed scope entry do not carry over.
        applicationId = "io.github.gengo0202.originduo"
        minSdk = 29
        targetSdk = 36
        versionCode = tagVersionCode ?: 3
        versionName = tagVersionName ?: "1.0.3"
        // Diagnostic build flag: true only when the build is invoked with
        // -PoriginduoDiag=true. See buildFeatures.buildConfig above.
        buildConfigField(
            "boolean", "DIAG",
            if ((findProperty("originduoDiag") as String?)?.toBoolean() == true) "true" else "false"
        )
    }

    signingConfigs {
        create("originduo") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile", "../vivoduobar.jks"))
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("originduo")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("originduo")
        }
    }

    compileOptions {
        // Must match the Kotlin target below: AGP fails the build outright on a
        // mismatch. 21 rather than 17 because Miuix ships Java 21 bytecode and
        // miuix-nav's entry points are inline functions, which cannot be inlined
        // into 17-targeted output.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        // The diagnostic kill-switch (persist.sys.originduo_test) is compiled in
        // only for the diag build: -PoriginduoDiag=true. The public build has
        // DIAG=false, which R8 folds away together with all the reflection
        // behind it.
        buildConfig = true
    }

    // Keep META-INF/xposed/{java_init.list,scope.list,module.prop} in the APK.
    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Provided by the Xposed framework at runtime, never bundled.
    compileOnly("io.github.libxposed:api:102.0.0")
    // Needed by the settings app to talk to the framework service.
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // The updater does its network work off the main thread and reports state
    // back on it; Compose alone has no scheduling primitive for that.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // @Serializable on the nav routes. miuix-nav only exposes it as a runtime
    // dependency, so the annotation and the inlined serializer() lookup need it
    // declared here to be on the compile classpath.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Backdrop and squircle shapes. These are NOT Miuix: they are kyant0's libraries, and
    // they are what gives the frosted-glass surfaces their moving, layered look -
    // Miuix's own miuix-blur is the lower-level primitive that `textureBlur` wraps.
    //
    // Sampled from HyperChanger, which is where the visual reference came from. Its pins
    // are older (backdrop 2.0.0, shapes 1.2.0); these are the current releases of the same
    // libraries, and both still publish under io.github.kyant0.
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    // Lucide's icon set, for the six glyphs the module shows as UI affordances (the two
    // tab icons and the four row/action icons). Miuix ships its own icon font, but its
    // set has no matching outline for every affordance and its `Tune`/`Info` pair is the
    // only tab vocabulary it offers. Lucide is Apache-2.0, one artifact, and depends on
    // nothing but the Compose version already here.
    implementation("com.composables:icons-lucide-android:1.1.0")
    // Blurred top bar. The artifact declares minSdk 33, but every effect in it
    // is gated on isRuntimeShaderSupported() and degrades to plain drawing
    // below that, so the manifest override keeps our own minSdk at 29.
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    // Only for the isRuntimeShaderSupported() gate the settings screen applies
    // before it touches any blur API. Blur pulls it in transitively, but the
    // code imports from it directly, so it is declared directly.
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")
    // Compose navigation with a continuous back stack. Exists only from 0.9.4,
    // which is why the whole Miuix stack above moved up with it. Its aar
    // declares minSdk 24, so it needs no manifest override of its own.
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4")
}
