import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 공용 콜밭 제보 서버 키 — 소스에 적지 않고 local.properties(저장소 제외)에서 읽어 BuildConfig 로 넣는다.
// local.properties 에 `FIREBASE_API_KEY=...` 한 줄을 두거나, 같은 이름의 환경 변수를 쓰면 된다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val firebaseApiKey: String =
    localProperties.getProperty("FIREBASE_API_KEY")
        ?: System.getenv("FIREBASE_API_KEY")
        ?: ""

// 카카오맵 JavaScript 키 — 소스에 적지 않고 local.properties(저장소 제외)에서 읽어 BuildConfig 로 넣는다.
// local.properties 에 `KAKAO_JS_KEY=...` 한 줄을 두거나, 같은 이름의 환경 변수를 쓰면 된다.
// 값이 비어 있으면(오픈소스 빌드) 지도 피커는 예전 OpenStreetMap 타일 지도로 동작한다.
val kakaoJsKey: String =
    localProperties.getProperty("KAKAO_JS_KEY")
        ?: System.getenv("KAKAO_JS_KEY")
        ?: ""

android {
    namespace = "com.youngsu.daeriledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.youngsu.daeriledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.0.20"

        // 키가 비어 있으면 앱은 공용 제보를 조용히 건너뛰고 로컬 콜밭만 쓴다
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")

        // 키가 비어 있으면 지도 피커는 OpenStreetMap 타일 지도로 폴백한다
        buildConfigField("String", "KAKAO_JS_KEY", "\"$kakaoJsKey\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // TODO(PlayStore): add your own signingConfig here (upload key / keystore).
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.play.services.ads)
    implementation(libs.guava)
}
