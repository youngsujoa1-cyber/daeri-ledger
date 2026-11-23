package com.youngsu.daeriledger

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.youngsu.daeriledger.ads.AdManager
import com.youngsu.daeriledger.data.LedgerRepository
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.settings.AppSettings

class DaeriLedgerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        applyThemeMode(AppSettings.get(this).themeMode)
        AdManager.initialize(this)
        migrateNetShownGrossOnce()
    }

    /**
     * 1.0.0 까지는 카카오T·티맵·핸들에 입력한 금액을 그대로 총매출로 저장했다.
     * 1.0.1 부터는 그 금액이 실수령액(수수료 차감 후)이므로, 기존 기록의 총매출을 한 번만 역산해 맞춘다.
     * 순수익은 바뀌지 않는다.
     */
    private fun migrateNetShownGrossOnce() {
        val settings = AppSettings.get(this)
        if (settings.netGrossMigrationDone) return
        val ok = runCatching {
            // 예전 설정 화면은 차감 후 금액 플랫폼의 수수료율을 0%로 저장해 두었다.
            // 1.0.1 부터는 그 율로 총매출을 역산하므로, 0%로 남아 있으면 기본값으로 되돌린다.
            Platforms.ALL
                .filter { it.defaultNetShown && it.defaultFeePercent > 0 && settings.platformFeePercent(it.id) <= 0.0 }
                .forEach { settings.setPlatformFeePercent(it.id, it.defaultFeePercent) }
            val rates = Platforms.ALL
                .filter { settings.platformNetShown(it.id) }
                .associate { it.id to settings.platformFeePercent(it.id) }
            LedgerRepository(this).migrateNetShownGross(rates)
        }.isSuccess
        if (ok) settings.netGrossMigrationDone = true
    }

    companion object {
        /** 테마 설정(다크/라이트/시스템)을 AppCompat 전역 모드로 적용 */
        fun applyThemeMode(mode: Int) {
            val nightMode = when (mode) {
                AppSettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                AppSettings.THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_YES
            }
            if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }
    }
}
