package com.youngsu.daeriledger.ads

import com.youngsu.daeriledger.BuildConfig

/**
 * 광고 설정을 한 곳에 모아 둔다.
 *
 * - debug 빌드: Google 공식 테스트 ID (실기기에서 눌러도 계정에 문제 없음)
 * - release 빌드: AdMob 에 등록한 실제 ID (Play 스토어 배포용)
 */
object AdConfig {

    /** Google 공식 테스트 앱 ID — AndroidManifest.xml 의 APPLICATION_ID 와 같아야 한다. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /** Google 공식 테스트 배너 단위 ID */
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /** Google 공식 테스트 전면(Interstitial) 단위 ID */
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /** 실제 AdMob 앱 ID (대리장부) — release 빌드의 APPLICATION_ID 값과 같다. */
    const val PROD_APP_ID = "ca-app-pub-4832055633016363~1323543030"

    /** 실제 배너 단위 ID — `대리장부 하단 배너` (하단 고정) */
    const val PROD_BANNER_UNIT_ID = "ca-app-pub-4832055633016363/9174112899"

    /** 실제 전면 광고 단위 ID — `대리장부 전면 광고` (통계 탭 진입 시 3분 쿨다운) */
    const val PROD_INTERSTITIAL_UNIT_ID = "ca-app-pub-4832055633016363/1809758654"

    /** 테스트 광고 사용 여부 — debug 빌드에서만 true, release 빌드는 실제 ID 를 쓴다. */
    val USE_TEST_ADS: Boolean = BuildConfig.DEBUG

    /** 하단 배너 단위 ID */
    val BANNER_UNIT_ID: String
        get() = if (USE_TEST_ADS) TEST_BANNER_UNIT_ID else PROD_BANNER_UNIT_ID

    /** 전면 광고 단위 ID (통계 탭 진입 시) */
    fun interstitialUnitId(): String =
        if (USE_TEST_ADS) TEST_INTERSTITIAL_UNIT_ID else PROD_INTERSTITIAL_UNIT_ID

    /** 테스트 모드 여부 — 설정 화면 안내 문구에 사용 */
    fun isTestMode(): Boolean = USE_TEST_ADS
}
