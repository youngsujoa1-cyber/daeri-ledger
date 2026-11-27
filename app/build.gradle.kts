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

// 카카오 REST API 키(선택) — 없으면 앱이 이미 쓰는 JavaScript 키로 같은 coord2address 를 부른다.
// JS 키는 카카오 SDK 와 같은 KA 헤더(등록 도메인 origin)가 있어야 통과한다.
val kakaoRestKey: String =
    localProperties.getProperty("KAKAO_REST_KEY")
        ?: System.getenv("KAKAO_REST_KEY")
        ?: ""

// 운영자(관리자) 모드 암호 — 콜밭 등록·삭제를 5명 대기 없이 바로 반영하는 잠금 해제 열쇠.
// 소스에 적지 않고 local.properties(저장소 제외)의 `ADMIN_PASS=...` 에서만 읽어 BuildConfig 로 넣는다.
// 값이 비어 있는 오픈소스 빌드에서는 설정 화면 버전을 5번 눌러도 운영자 모드가 켜지지 않는다.
val adminPass: String =
    localProperties.getProperty("ADMIN_PASS")
        ?: System.getenv("ADMIN_PASS")
        ?: ""

// 업로드 키스토어(Play App Signing) — 저장소 밖(projects/keystores)에 두고 keystore.properties 로만 읽는다.
// 파일이 없으면 release 빌드는 서명 없이 만들어지고(로컬 배포용 아님), 저장소에는 아무 키도 들어가지 않는다.
val keystorePropertiesFile: File? = listOf(
    rootProject.file("../keystores/keystore.properties"),
    rootProject.file("keystore.properties")
).firstOrNull { it.exists() }
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile != null) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.youngsu.daeriledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.youngsu.daeriledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.0.31"

        // AdMob 앱 ID — 기본(debug)은 Google 공식 테스트 ID, release 는 buildTypes 에서 실제 ID 로 바꾼다.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"

        // 키가 비어 있으면 앱은 공용 제보를 조용히 건너뛰고 로컬 콜밭만 쓴다
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")

        // 키가 비어 있으면 지도 피커는 OpenStreetMap 타일 지도로 폴백한다
        buildConfigField("String", "KAKAO_JS_KEY", "\"$kakaoJsKey\"")

        // 비어 있으면 현위치 역지오코딩은 JS 키 + KA 헤더로 부른다 (v1.0.28)
        buildConfigField("String", "KAKAO_REST_KEY", "\"$kakaoRestKey\"")

        // 비어 있으면 운영자 모드 잠금 해제가 아예 되지 않는다 (오픈소스 빌드 안전장치)
        buildConfigField("String", "ADMIN_PASS", "\"$adminPass\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile != null) {
            create("release") {
                val storeFileName = keystoreProperties.getProperty("storeFile")
                val candidates = listOf(
                    rootProject.file(storeFileName),
                    keystorePropertiesFile.parentFile.resolve(storeFileName)
                )
                storeFile = candidates.firstOrNull { it.exists() }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
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
            // Play 스토어 업로드용 서명 — keystore.properties 가 있을 때만 붙는다.
            signingConfigs.findByName("release")?.let { signingConfig = it }

            // release 빌드는 실제 AdMob 앱 ID (대리장부) 사용
            manifestPlaceholders["admobAppId"] = "ca-app-pub-4832055633016363~1323543030"
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
