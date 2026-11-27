package com.youngsu.daeriledger.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.ads.AdManager
import com.youngsu.daeriledger.ads.InterstitialHelper
import com.youngsu.daeriledger.data.BackupManager
import com.youngsu.daeriledger.data.LedgerRepository
import com.youngsu.daeriledger.databinding.ActivityMainBinding
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.settings.AppSettings
import com.youngsu.daeriledger.ui.calendar.CalendarScreen
import com.youngsu.daeriledger.ui.entry.AddRideActivity
import com.youngsu.daeriledger.ui.settings.SettingsScreen
import com.youngsu.daeriledger.ui.spots.SpotsScreen
import com.youngsu.daeriledger.ui.stats.StatsScreen
import com.youngsu.daeriledger.ui.today.TodayScreen
import com.youngsu.daeriledger.ui.unpaid.UnpaidActivity
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.toast
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
        private set

    val repo: LedgerRepository by lazy { LedgerRepository(this) }
    val settings: AppSettings by lazy { AppSettings.get(this) }
    val backup: BackupManager by lazy { BackupManager(this, repo, settings) }

    private val screens = HashMap<Int, Screen>()
    private var currentTab = R.id.nav_today
    private var bottomInset = 0

    private val addRideLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAll() }

    private val unpaidLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAll() }

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { exportCsvTo(it) } }

    private val createJsonLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportJsonTo(it) } }

    private val openJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { askRestore(it) } }

    private val openOtherBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { askOtherBackup(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootColumn) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.contentContainer.updatePadding(top = bars.top)
            bottomInset = bars.bottom
            applyBottomInset()
            insets
        }
        // 배너가 로드 성공/실패로 보였다 숨었다 해도 하단 시스템 바 여백은 항상 화면 맨 아래 뷰가 갖는다.
        binding.rootColumn.viewTreeObserver.addOnGlobalLayoutListener { applyBottomInset() }

        binding.bottomNav.setOnItemSelectedListener { item ->
            selectTab(item.itemId)
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_today
        selectTab(R.id.nav_today)

        onBackPressedDispatcher.addCallback(this) {
            if (currentTab != R.id.nav_today) {
                binding.bottomNav.selectedItemId = R.id.nav_today
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        AdManager.attachBanner(this, binding.adContainer)
        // 통계 탭용 전면 광고를 미리 받아 둔다.
        InterstitialHelper.attach(this)
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보이면(탭 전환·잠금 해제·다른 앱에서 복귀) 지금 탭을 새로 그린다 —
        // 콜밭 화면은 이때 새 GPS 측정을 다시 건다 (v1.0.31)
        screens[currentTab]?.refresh()
    }

    override fun onPause() {
        // 화면이 가려지면 화면이 켜 둔 GPS 감시를 멈춘다 — 백그라운드로 위치를 계속 받지 않는다
        screens[currentTab]?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        InterstitialHelper.detach(this)
        super.onDestroy()
    }

    /**
     * 하단 시스템 바(제스처/내비게이션 바) 여백은 화면 맨 아래 뷰가 갖는다.
     * 광고 배너가 보이면 배너가, 광고가 없으면 하단 내비게이션이 그 여백을 맡는다.
     */
    private fun applyBottomInset() {
        val adVisible = binding.adContainer.visibility == android.view.View.VISIBLE
        binding.bottomNav.updatePadding(bottom = if (adVisible) 0 else bottomInset)
        binding.adContainer.updatePadding(bottom = if (adVisible) bottomInset else 0)
    }

    private fun selectTab(itemId: Int) {
        // 탭을 떠나는 화면은 GPS 감시를 멈춘다 (돌아오면 refresh 에서 다시 건다)
        if (itemId != currentTab) screens[currentTab]?.onPause()
        currentTab = itemId
        val screen = screens.getOrPut(itemId) { createScreen(itemId) }
        if (binding.contentContainer.childCount == 1 &&
            binding.contentContainer.getChildAt(0) === screen.view
        ) {
            screen.refresh()
            afterTabShown(itemId)
            return
        }
        binding.contentContainer.removeAllViews()
        binding.contentContainer.addView(
            screen.view,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        screen.refresh()
        afterTabShown(itemId)
    }

    /**
     * 탭 화면이 이미 자리를 잡은 뒤에 부른다.
     * 통계 탭이면 전면 광고를 띄우되(3분 쿨다운), 광고가 없거나 준비 중이면 그냥 화면을 보여준다.
     */
    private fun afterTabShown(itemId: Int) {
        if (itemId == R.id.nav_stats) InterstitialHelper.showOnStatsTab(this)
    }

    private fun createScreen(itemId: Int): Screen = when (itemId) {
        R.id.nav_calendar -> CalendarScreen(this)
        R.id.nav_spots -> SpotsScreen(this)
        R.id.nav_stats -> StatsScreen(this)
        R.id.nav_settings -> SettingsScreen(this)
        else -> TodayScreen(this)
    }

    fun refreshAll() {
        // 지금 보고 있는 탭만 바로 다시 그린다. 다른 탭은 그 탭으로 넘어갈 때 selectTab 에서 refresh 된다.
        // (전체 기간 통계처럼 무거운 화면이 기록 한 건마다 다시 계산되지 않도록)
        screens[currentTab]?.refresh()
    }

    fun openAddRide(date: LocalDate? = null, rideId: Long = 0L) {
        val intent = Intent(this, AddRideActivity::class.java).apply {
            if (rideId != 0L) putExtra(AddRideActivity.EXTRA_RIDE_ID, rideId)
            date?.let { putExtra(AddRideActivity.EXTRA_DATE, BizTime.dayKey(it)) }
        }
        addRideLauncher.launch(intent)
    }

    fun openUnpaid() {
        unpaidLauncher.launch(Intent(this, UnpaidActivity::class.java))
    }

    fun exportCsv() {
        createCsvLauncher.launch(BackupManager.csvFileName())
    }

    fun backupJson() {
        createJsonLauncher.launch(BackupManager.jsonFileName())
    }

    fun restoreJson() {
        openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    /** 다른 앱(대리 가계부) 백업 가져오기 — dcb_teras.zip 또는 .db */
    fun importOtherBackup() {
        openOtherBackupLauncher.launch(
            arrayOf(
                "application/zip",
                "application/octet-stream",
                "application/x-sqlite3",
                "application/vnd.sqlite3",
                "*/*"
            )
        )
    }

    private fun exportCsvTo(uri: Uri) {
        runCatching { backup.exportCsv(uri) }
            .onSuccess { count -> toast("CSV로 내보냈어요 · ${count}건") }
            .onFailure { toast("내보내기에 실패했어요: ${it.message}") }
    }

    private fun exportJsonTo(uri: Uri) {
        runCatching { backup.backupJson(uri) }
            .onSuccess { (rides, expenses) -> toast("백업 완료 · 운행 ${rides}건 / 지출 ${expenses}건") }
            .onFailure { toast("백업에 실패했어요: ${it.message}") }
    }

    private fun askRestore(uri: Uri) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("백업 복원")
            .setMessage("기존 기록에 추가할까요, 아니면 기존 기록을 지우고 백업 내용으로 교체할까요?")
            .setNegativeButton("취소", null)
            .setNeutralButton("교체") { _, _ -> doRestore(uri, replace = true) }
            .setPositiveButton("추가") { _, _ -> doRestore(uri, replace = false) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(com.youngsu.daeriledger.R.color.accent))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(getColor(com.youngsu.daeriledger.R.color.loss))
        }
        dialog.show()
    }

    private fun doRestore(uri: Uri, replace: Boolean) {
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle("백업을 불러오는 중")
            .setMessage("기록을 넣고 있어요. 잠시만 기다려 주세요.")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            val result = runCatching { backup.restoreJson(uri, replace) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                result
                    .onSuccess { restored ->
                        refreshAll()
                        val lines = ArrayList<String>()
                        lines.add("운행 ${restored.rides}건, 지출 ${restored.expenses}건을 불러왔어요.")
                        if (restored.offDays > 0) lines.add("휴무일 ${restored.offDays}일도 함께 가져왔어요.")
                        if (restored.skipped > 0) lines.add("중복 ${restored.skipped}건은 건너뛰었어요.")
                        MaterialAlertDialogBuilder(this)
                            .setTitle("복원 완료")
                            .setMessage(lines.joinToString("\n"))
                            .setPositiveButton("확인", null)
                            .show()
                    }
                    .onFailure { toast("복원에 실패했어요: ${it.message}") }
            }
        }.start()
    }

    /** 다른 앱(대리 가계부) 백업 — 추가할지 교체할지 먼저 물어본다 */
    private fun askOtherBackup(uri: Uri) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("타 앱 백업 가져오기")
            .setMessage(
                "대리 가계부(dcb_teras) 백업에서 운행 기록을 읽어요.\n" +
                    "기존 운행에 추가할까요, 아니면 기존 운행을 지우고 백업 내용으로 교체할까요?\n" +
                    "(이 앱에서 적은 지출은 그대로 남아요)"
            )
            .setNegativeButton("취소", null)
            .setNeutralButton("교체") { _, _ -> doImportOtherBackup(uri, replace = true) }
            .setPositiveButton("추가") { _, _ -> doImportOtherBackup(uri, replace = false) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getColor(com.youngsu.daeriledger.R.color.accent))
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(getColor(com.youngsu.daeriledger.R.color.loss))
        }
        dialog.show()
    }

    private fun doImportOtherBackup(uri: Uri, replace: Boolean) {
        // 1만 건이 넘어도 화면이 멈추지 않도록 백그라운드에서 읽고 넣는다
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle("백업을 가져오는 중")
            .setMessage("운행 기록을 넣고 있어요. 잠시만 기다려 주세요.")
            .setCancelable(false)
            .create()
        progress.show()
        Thread {
            val result = runCatching { backup.importDcbBackup(uri, replace) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                result
                    .onSuccess { imported ->
                        refreshAll()
                        val headline = when {
                            imported.rides > 0 -> "${imported.rides}건 가져왔어요!"
                            imported.offDays > 0 -> "가져올 운행은 없었어요."
                            else -> "새로 가져올 기록이 없어요."
                        }
                        val lines = ArrayList<String>()
                        lines.add(headline)
                        if (imported.offDays > 0) lines.add("휴무일 ${imported.offDays}일을 함께 표시했어요.")
                        if (imported.skipped > 0) lines.add("이미 있던 ${imported.skipped}건은 건너뛰었어요.")
                        MaterialAlertDialogBuilder(this)
                            .setTitle(
                                when {
                                    imported.rides > 0 -> "가져왔어요"
                                    imported.offDays > 0 -> "휴무일을 가져왔어요"
                                    else -> "가져오기 완료"
                                }
                            )
                            .setMessage(lines.joinToString("\n"))
                            .setPositiveButton("확인", null)
                            .show()
                    }
                    .onFailure { toast("가져오기에 실패했어요: ${it.message}") }
            }
        }.start()
    }

    fun applyTheme(mode: Int) {
        settings.themeMode = mode
        com.youngsu.daeriledger.DaeriLedgerApp.applyThemeMode(mode)
        recreate()
    }

    fun hasLocationPermission(): Boolean = LocationHelper.hasPermission(this)

    fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ),
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (!granted) toast("위치 권한 없이도 지역별로 볼 수 있어요")
            refreshAll()
        }
    }

    companion object {
        private const val REQUEST_LOCATION = 2101
    }
}
