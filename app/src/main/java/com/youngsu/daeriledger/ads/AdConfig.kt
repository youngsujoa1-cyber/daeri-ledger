package com.youngsu.daeriledger.ads

/**
 * 광고 설정을 한 곳에 모아 둔다.
 * 지금은 Google 공식 테스트 ID를 쓰고, 실제 서비스로 바꿀 때
 * 이 파일과 AndroidManifest.xml 의 APPLICATION_ID 만 교체하면 된다.
 */
object AdConfig {

    /** Google 공식 테스트 앱 ID — AndroidManifest.xml 의 APPLICATION_ID 와 같아야 한다. */
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /** Google 공식 테스트 배너 단위 ID */
    const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /** Google 공식 테스트 전면(Interstitial) 단위 ID */
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    /**
     * 출시용 전면 광고 단위 ID 자리.
     * AdMob 에서 '대리장부 전면 광고'(Interstitial) 단위를 만든 뒤 이 값을 실제 ID 로 바꾼다.
     * (아직 만들지 않았으므로 0 으로 채운 자리 표시자)
     */
    const val PROD_INTERSTITIAL_UNIT_ID = "ca-app-pub-4832055633016363/0000000000"

    /** 출시 전 false 로 바꾸고 BANNER_UNIT_ID 를 실제 단위 ID 로 교체한다. */
    const val USE_TEST_ADS: Boolean = true

    /** 하단 배너 단위 ID */
    val BANNER_UNIT_ID: String
        get() = if (USE_TEST_ADS) TEST_BANNER_UNIT_ID else TEST_BANNER_UNIT_ID

    /** 전면 광고 단위 ID (통계 탭 진입 시) */
    fun interstitialUnitId(): String =
        if (USE_TEST_ADS) TEST_INTERSTITIAL_UNIT_ID else PROD_INTERSTITIAL_UNIT_ID

    /** 테스트 모드 여부 — 설정 화면 안내 문구에 사용 */
    fun isTestMode(): Boolean = USE_TEST_ADS
}
