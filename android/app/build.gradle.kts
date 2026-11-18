plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Assinatura: se o GitHub tiver a chave oficial guardada em segredo (para a Play Store), usa ela;
// senão, usa a chave de teste deste repositório (serve para instalar direto na TV e no celular).
val chaveOficial = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "br.faixalivre.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.faixalivre.tv"
        minSdk = 21
        targetSdk = 35
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.0.$run"
    }

    signingConfigs {
        create("app") {
            if (chaveOficial != null) {
                storeFile = file(chaveOficial)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            } else {
                storeFile = file("faixalivretv-teste.keystore")
                storePassword = "faixalivretv-teste"
                keyAlias = "faixalivretv"
                keyPassword = "faixalivretv-teste"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
