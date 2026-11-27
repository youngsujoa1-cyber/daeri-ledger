package com.youngsu.daeriledger.ui.settings

import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.youngsu.daeriledger.BuildConfig
import com.youngsu.daeriledger.ads.AdConfig
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.Payout
import com.youngsu.daeriledger.data.PlatformDef
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.databinding.ItemSettingRowBinding
import com.youngsu.daeriledger.databinding.ScreenSettingsBinding
import com.youngsu.daeriledger.settings.AppSettings
import com.youngsu.daeriledger.ui.MainActivity
import com.youngsu.daeriledger.ui.Screen
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Dialogs
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.color
import com.youngsu.daeriledger.util.dp
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import java.util.Locale

/** 설정 — 수수료율, 보험 & 세금, 영업일 기준, 목표, 테마, 백업 */
class SettingsScreen(private val activity: MainActivity) : Screen {

    private val binding = ScreenSettingsBinding.inflate(activity.layoutInflater)
    override val view: View get() = binding.root

    private val settings: AppSettings get() = activity.settings

    /** 설정 맨 아래 버전을 연속으로 누른 횟수 — 5번이면 운영자 모드 잠금 해제(v1.0.23) */
    private var versionTaps = 0
    private var lastVersionTapAt = 0L

    override fun refresh() {
        val column = binding.settingsColumn
        column.removeAllViews()

        // ---------------------------------------------------------- 영업일
        section("영업일 · 목표")
        row(
            icon = R.drawable.ic_clock,
            title = "영업일 기준시각",
            subtitle = "새벽 콜도 같은 영업일 장부로 묶습니다",
            value = BizTime.cutOffLabel(settings.cutoffHour)
        ) { showCutoffDialog() }

        row(
            icon = R.drawable.ic_flag,
            title = "일일 목표 총수입",
            subtitle = "오늘 탭의 목표 바(총수입 기준)에 반영돼요",
            value = Money.won(settings.dailyGoal)
        ) {
            Dialogs.amount(
                activity,
                title = "일일 목표 총수입",
                suffix = "원",
                initial = settings.dailyGoal.toString(),
                presets = listOf(
                    "100000" to "10만", "150000" to "15만", "200000" to "20만", "250000" to "25만", "300000" to "30만"
                )
            ) { value ->
                val amount = Money.parseAmount(value)
                if (amount <= 0) {
                    activity.toast("금액을 입력해 주세요")
                } else {
                    settings.dailyGoal = amount
                    activity.refreshAll()
                    refresh()
                }
            }
        }

        // ------------------------------------------------------ 플랫폼 수수료
        section("플랫폼 수수료")
        note(
            "카카오T·티맵·핸들은 앱에 이미 수수료가 빠진 금액이 보여서 받은 금액을 넣고 총매출은 수수료율로 역산해요. " +
                "로지·콜마너·아이콘과 새로 넣은 대구사랑·대구시민·대구세종·트리콜·수퍼드라이버·오픈마일·별대리는 총매출에서 20%를 뺍니다. " +
                "실제 정산 기준에 맞게 고치면 순수익이 정확해져요."
        )
        // 사용할 프로그램 선택 (v1.0.32) — 체크한 프로그램만 아래 수수료 목록과 운행 기록 칩에 보인다
        val enabledList = Platforms.visible(settings.enabledPlatformIds)
        row(
            icon = R.drawable.ic_check,
            title = "사용할 프로그램 선택",
            subtitle = "체크한 프로그램만 여기와 운행 기록 칩에 떠요 · ${Platforms.ALL.size}개 중 ${enabledList.size}개 사용 중",
            value = "${enabledList.size}개"
        ) { showPlatformPicker() }
        enabledList.forEach { platform -> platformRow(platform) }
        note(
            "체크를 끄면 설정 목록과 운행 기록 칩에서만 빠지고, 그 프로그램으로 적은 과거 운행 기록·통계·달력 집계는 그대로 남아요. " +
                "수정 화면에서 그런 기록을 열면 그 프로그램도 같이 보여요."
        )
        // 개인콜 바로 아래 — 팁·경유비는 플랫폼이 아니라 따로 %를 잡는다
        row(
            icon = R.drawable.ic_receipt,
            title = "팁 · 경유비",
            subtitle = "받은 팁을 이 %로 역산 · 모든 플랫폼 공통",
            value = String.format(Locale.KOREA, "%.0f%%", settings.tipFeePercent)
        ) { showTipFeeDialog() }
        note(
            "팁·경유비는 플랫폼과 상관없이 받은 금액을 이 %로 역산해 총액을 잡아요. " +
                "예) 로지 요금 45,000 + 받은 팁 10,000 (20%) → 총액 57,500 · 수수료 11,500 · 순수익 46,000. " +
                "0%로 두면 팁에 수수료를 떼지 않고, 손님이 현금으로 준 팁은 0%가 맞아요."
        )

        // ------------------------------------------------------- 보험 & 세금
        section("보험 & 세금")
        note(
            "카카오T·티맵은 건당 보험료가 빠지지 않아요. 두 플랫폼은 아래 고용·산재·세금(3.3%)만 빠지고, " +
                "그 외 플랫폼은 건당 보험료 + 3.3% 세금이 함께 빠집니다. 입력 화면의 `보험&세금` 줄에서 " +
                "건당 0원 + 3.3% 1,485원처럼 내역을 나눠 보여 줘요."
        )
        row(
            icon = R.drawable.ic_receipt,
            title = "대리 건당 보험료",
            subtitle = "대리 콜 한 건마다 빠지는 금액 · 카카오T·티맵 제외",
            value = Money.won(settings.insuranceDaeri)
        ) {
            Dialogs.amount(
                activity, "대리 건당 보험료", "원", settings.insuranceDaeri.toString(),
                presets = listOf("0" to "0원", "1000" to "1,000", "1500" to "1,500", "2000" to "2,000", "3000" to "3,000")
            ) { value ->
                settings.insuranceDaeri = Money.parseAmount(value)
                activity.refreshAll(); refresh()
            }
        }
        row(
            icon = R.drawable.ic_receipt,
            title = "탁송 건당 보험료",
            subtitle = "탁송 콜 한 건마다 빠지는 금액 · 카카오T·티맵 제외",
            value = Money.won(settings.insuranceTaksong)
        ) {
            Dialogs.amount(
                activity, "탁송 건당 보험료", "원", settings.insuranceTaksong.toString(),
                presets = listOf("0" to "0원", "2000" to "2,000", "2500" to "2,500", "3000" to "3,000", "5000" to "5,000")
            ) { value ->
                settings.insuranceTaksong = Money.parseAmount(value)
                activity.refreshAll(); refresh()
            }
        }
        row(
            icon = R.drawable.ic_receipt,
            title = "고용·산재·세금 (3.3%)",
            subtitle = "매출 기준으로 계산합니다 (선택)",
            value = String.format(Locale.KOREA, "%.1f%%", settings.employmentInsurancePercent)
        ) {
            Dialogs.amount(
                activity, "고용·산재·세금 (3.3%)", "%",
                String.format(Locale.KOREA, "%.1f", settings.employmentInsurancePercent),
                hint = "예: 3.3 (매출의 3.3%)",
                presets = listOf("0" to "0%", "0.5" to "0.5%", "1" to "1%", "3.3" to "3.3%"),
                allowDecimal = true
            ) { value ->
                val percent = value.replace(",", ".").toDoubleOrNull()
                if (percent == null || percent < 0) activity.toast("숫자를 입력해 주세요")
                else {
                    settings.employmentInsurancePercent = percent
                    activity.refreshAll(); refresh()
                }
            }
        }

        // -------------------------------------------------------------- 화면
        section("화면")
        row(
            icon = R.drawable.ic_settings,
            title = "테마",
            subtitle = "야간 운행 기본은 다크 모드",
            value = when (settings.themeMode) {
                AppSettings.THEME_LIGHT -> "라이트"
                AppSettings.THEME_SYSTEM -> "시스템"
                else -> "다크"
            }
        ) { showThemeDialog() }

        // -------------------------------------------------------------- 콜밭
        section("콜밭 통계")
        switchRow(
            icon = R.drawable.ic_map_pin,
            title = "운행 기록 위치 통계",
            subtitle = "새 운행을 저장할 때 지금 위치·출발지 이름·시간대만 익명으로 보태요",
            checked = settings.shareRideStats
        ) { on ->
            settings.shareRideStats = on
            if (on && !activity.hasLocationPermission()) {
                activity.toast("위치 권한을 허용해야 이 통계가 함께 쌓여요")
            }
        }
        note(
            "운행 기록의 도착지·요금·플랫폼·메모는 보내지 않아요. 같은 자리에서 밀린 기록을 한꺼번에 올리면 " +
                "첫 건만 반영되고, 같은 자리(반경 500m)에서 서로 다른 기기 3명 이상이 모이고 지금 시간대에 입력이 있으면 빨간 핫스팟 핀으로 보여요."
        )

        // -------------------------------------------------------------- 데이터
        section("데이터")
        val (rideCount, expenseCount) = activity.repo.counts()
        row(
            icon = R.drawable.ic_export,
            title = "CSV 내보내기",
            subtitle = "엑셀에서 열어 정산·세무 자료로",
            value = "${rideCount}건"
        ) { activity.exportCsv() }
        row(
            icon = R.drawable.ic_backup,
            title = "JSON 백업",
            subtitle = "기록 ${rideCount}건 · 지출 ${expenseCount}건 · 콜밭 설정 포함",
            value = ""
        ) { activity.backupJson() }
        row(
            icon = R.drawable.ic_restore,
            title = "백업 복원",
            subtitle = "백업 파일을 골라 기록 되살리기",
            value = ""
        ) { activity.restoreJson() }
        row(
            icon = R.drawable.ic_backup,
            title = "타 앱 백업 가져오기",
            subtitle = "대리 가계부(dcb_teras) .zip · .db 에서 운행 기록 옮기기",
            value = ""
        ) { activity.importOtherBackup() }
        note("기록은 이 기기 안에만 저장되고 서버로 보내지 않아요. 기기를 바꾸기 전에는 JSON 백업을 꼭 해두세요.")

        // -------------------------------------------------------------- 정보
        section("정보")
        // 운영자 모드가 켜져 있으면 여기서 바로 확인·해제한다 (버전을 다시 5번 눌러도 꺼진다)
        if (settings.isAdminMode) {
            row(
                icon = R.drawable.ic_check,
                title = "👑 운영자 모드",
                subtitle = "콜밭 등록·삭제가 3명 대기 없이 모든 기사님 앱에 바로 반영돼요",
                value = "켜짐"
            ) { confirmAdminOff() }
        }
        row(
            icon = R.drawable.ic_info,
            title = "앱 버전",
            subtitle = "대리장부 · 대리운전 순수익 장부",
            value = appVersionLabel(),
            showChevron = false
        ) { onVersionTap() }
        row(
            icon = R.drawable.ic_info,
            title = "광고",
            subtitle = if (AdConfig.isTestMode()) "AdMob 배너 (지금은 Google 테스트 ID)" else "AdMob 배너 · 통계 탭 전면 광고",
            value = if (AdConfig.isTestMode()) "테스트" else "게재 중",
            clickable = false
        )
        note("콜밭 정보는 지역 특성으로 정리한 참고 자료이며, 각 플랫폼의 실시간 콜 수량이 아닙니다.")
    }

    // ------------------------------------------------------------- helpers

    private fun section(title: String) {
        binding.settingsColumn.addView(
            TextView(activity).apply {
                text = title
                setTextColor(activity.color(R.color.text_secondary))
                textSize = 12f
                letterSpacing = 0.06f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(activity.dp(22f), activity.dp(18f), activity.dp(22f), activity.dp(4f))
            }
        )
    }

    /** 실제 설치된 APK의 버전을 읽어 표시한다 (build.gradle.kts 값이 그대로 반영) */
    private fun appVersionLabel(): String = try {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val name = info.versionName.orEmpty().removeSuffix("-debug")
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION") info.versionCode
        }
        "$name ($code)"
    } catch (e: Exception) {
        ""
    }

    private fun note(text: String) {
        binding.settingsColumn.addView(
            TextView(activity).apply {
                this.text = text
                setTextColor(activity.color(R.color.text_tertiary))
                textSize = 11f
                setLineSpacing(activity.dp(3f).toFloat(), 1f)
                setPadding(activity.dp(22f), activity.dp(2f), activity.dp(22f), activity.dp(6f))
            }
        )
    }

    private fun row(
        icon: Int,
        title: String,
        subtitle: String,
        value: String,
        clickable: Boolean = true,
        showChevron: Boolean = clickable,
        onClick: (() -> Unit)? = null
    ) {
        val b = ItemSettingRowBinding.inflate(LayoutInflater.from(activity), binding.settingsColumn, false)
        b.rowIcon.setImageResource(icon)
        b.rowTitle.text = title
        if (subtitle.isBlank()) b.rowSubtitle.visibility = View.GONE else b.rowSubtitle.text = subtitle
        if (value.isBlank()) b.rowValue.visibility = View.GONE else b.rowValue.text = value
        if (!showChevron) b.rowChevron.visibility = View.GONE else b.rowChevron.visibility = View.VISIBLE
        if (!clickable) {
            b.rowRoot.isClickable = false
            b.rowIconFrame.background = activity.getDrawable(R.drawable.bg_circle)
        } else {
            b.rowRoot.setOnClickListener { onClick?.invoke() }
        }
        binding.settingsColumn.addView(b.root)
    }

    // ------------------------------------------------------- 운영자 모드 (v1.0.23)

    /**
     * 앱 버전을 연속 5번 누르면 운영자 모드 잠금 해제 창을 띄운다.
     * 2초 넘게 쉬면 횟수가 0으로 돌아간다 — 일반 사용자가 우연히 여러 번 눌러도 걸리지 않는다.
     */
    private fun onVersionTap() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapAt > 2_000L) versionTaps = 0
        lastVersionTapAt = now
        versionTaps++
        if (versionTaps < 5) return
        versionTaps = 0
        if (settings.isAdminMode) confirmAdminOff() else promptAdminPass()
    }

    /** 운영자 암호 입력 창 — `BuildConfig.ADMIN_PASS` 와 맞으면 운영자 모드를 켠다 */
    private fun promptAdminPass() {
        if (BuildConfig.ADMIN_PASS.isBlank()) {
            activity.toast("이 빌드에는 운영자 암호가 없어요")
            return
        }
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "운영자 암호"
            setSingleLine()
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setTextColor(activity.color(R.color.text_primary))
            setHintTextColor(activity.color(R.color.text_tertiary))
            setPadding(activity.dp(18f), activity.dp(12f), activity.dp(18f), activity.dp(12f))
            background = activity.getDrawable(R.drawable.bg_amount_field)
        }
        val box = FrameLayout(activity).apply {
            setPadding(activity.dp(20f), activity.dp(6f), activity.dp(20f), 0)
        }
        box.addView(
            input,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(52f))
        )

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("👑 운영자 모드 잠금 해제")
            .setMessage("콜밭을 3명 대기 없이 바로 공개·삭제할 수 있는 모드입니다. 운영자 암호를 입력해 주세요.")
            .setView(box)
            .setNegativeButton("취소", null)
            .setPositiveButton("확인", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.color(R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim() == BuildConfig.ADMIN_PASS) {
                    settings.isAdminMode = true
                    dialog.dismiss()
                    refresh()
                    activity.toast("👑 운영자 모드를 켰어요 — 콜밭 등록·삭제가 바로 반영돼요")
                } else {
                    input.setText("")
                    activity.toast("암호가 맞지 않아요")
                }
            }
        }
        dialog.show()
    }

    /** 운영자 모드 끄기 — 다시 일반 3명(등록)·2명(삭제) 기준으로 돌아간다 */
    private fun confirmAdminOff() {
        Dialogs.confirm(
            activity,
            "운영자 모드 끄기",
            "콜밭 등록은 다시 3명 이상 모여야 공개되고, 삭제는 2명 기준으로 돌아갑니다.",
            positive = "끄기"
        ) {
            settings.isAdminMode = false
            refresh()
            activity.toast("운영자 모드를 껐어요")
        }
    }

    /** 스위치가 달린 설정 줄 — 토글 값은 [onToggle] 에서 저장한다 (v1.0.26) */
    private fun switchRow(
        icon: Int,
        title: String,
        subtitle: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit
    ) {
        val b = ItemSettingRowBinding.inflate(LayoutInflater.from(activity), binding.settingsColumn, false)
        b.rowIcon.setImageResource(icon)
        b.rowTitle.text = title
        if (subtitle.isBlank()) b.rowSubtitle.visibility = View.GONE else b.rowSubtitle.text = subtitle
        b.rowValue.visibility = View.GONE
        b.rowChevron.visibility = View.GONE
        val sw = MaterialSwitch(activity).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onToggle(value) }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = activity.dp(8f) }
        b.rowRoot.addView(sw, lp)
        // 줄을 눌러도 토글된다
        b.rowRoot.setOnClickListener { sw.isChecked = !sw.isChecked }
        binding.settingsColumn.addView(b.root)
    }

    // ------------------------------------------------- 사용할 프로그램 선택 (v1.0.32)

    /**
     * 14개 프로그램을 체크박스로 켜고 끄는 창. 체크를 바꾸면 바로 저장하고 뒤의 설정 목록을 다시 그린다
     * (켜면 `아이콘` 과 `개인콜` 사이에 바로 나타난다). 마지막 한 개는 끌 수 없다.
     */
    private fun showPlatformPicker() {
        val platforms = Platforms.ALL
        val labels = platforms.map { it.label }.toTypedArray()
        var current = settings.enabledPlatformIds
        val checked = platforms.map { it.id in current }.toBooleanArray()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("사용할 프로그램 선택")
            .setMultiChoiceItems(labels, checked) { d, which, isChecked ->
                val id = platforms[which].id
                val next = Platforms.toggleEnabled(current, id, isChecked)
                if (next == null) {
                    // 모두 해제하면 장부에 적을 프로그램이 없어지므로 마지막 한 개는 남긴다
                    activity.toast("프로그램은 최소 1개는 남겨 주세요")
                    (d as? AlertDialog)?.listView?.setItemChecked(which, true)
                } else {
                    current = next
                    settings.enabledPlatformIds = next
                    refresh()
                }
            }
            .setPositiveButton("완료", null)
            .setNeutralButton("전체 선택") { _, _ ->
                current = Platforms.ALL_IDS.toSet()
                settings.enabledPlatformIds = current
                refresh()
            }
            .setNegativeButton("기본 7종") { _, _ ->
                current = Platforms.DEFAULT_ENABLED_IDS.toSet()
                settings.enabledPlatformIds = current
                refresh()
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.color(R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(activity.color(R.color.text_secondary))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
        }
        dialog.show()
    }

    private fun platformRow(platform: PlatformDef) {
        val netShown = settings.platformNetShown(platform.id)
        val percent = settings.platformFeePercent(platform.id)
        row(
            icon = R.drawable.ic_wallet,
            title = platform.label,
            subtitle = if (netShown) "받은 금액 입력 · 총매출 역산"
            else String.format(Locale.KOREA, "%.0f%% 차감", percent),
            value = String.format(Locale.KOREA, "%.0f%%", percent)
        ) { showPlatformDialog(platform) }
    }

    // ------------------------------------------------------------- dialogs

    private fun showPlatformDialog(platform: PlatformDef) {
        val container = LayoutInflater.from(activity).inflate(R.layout.dialog_platform_fee, null)
        val switch = container.findViewById<MaterialSwitch>(R.id.netShownSwitch)
        val feeRow = container.findViewById<View>(R.id.feeInputRow)
        val feeInput = container.findViewById<EditText>(R.id.feePercentInput)
        val presets = container.findViewById<ChipGroup>(R.id.feePresets)
        val example = container.findViewById<TextView>(R.id.feeExample)

        switch.isChecked = settings.platformNetShown(platform.id)
        feeInput.setText(String.format(Locale.KOREA, "%.0f", settings.platformFeePercent(platform.id)))

        fun refreshExample() {
            val pct = feeInput.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val netShown = switch.isChecked
            // 두 모드 모두 수수료율은 그대로 쓰인다. 입력 금액의 뜻만 다르다.
            feeRow.alpha = 1f
            feeInput.isEnabled = true
            presets.visibility = View.VISIBLE
            val exampleInput = 20_000
            example.text = if (netShown) {
                val gross = Payout.grossFromNet(exampleInput, pct)
                "${Money.won(exampleInput)} 받음 → 총매출 ${Money.won(gross)} · 수수료 ${Money.won(gross - exampleInput)}"
            } else {
                val fee = (exampleInput * pct / 100).toInt()
                "총매출 ${Money.won(exampleInput)} → 수수료 ${Money.won(fee)}, 순수익 ${Money.won(exampleInput - fee)}"
            }
        }

        presets.setChips(listOf("0" to "0%", "10" to "10%", "15" to "15%", "20" to "20%", "25" to "25%"), null) {
            feeInput.setText(it)
            refreshExample()
        }
        switch.setOnCheckedChangeListener { _, _ -> refreshExample() }
        feeInput.doAfterTextChanged { refreshExample() }
        refreshExample()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("${platform.label} 수수료")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.color(R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pct = feeInput.text.toString().replace(",", ".").toDoubleOrNull()
                if (pct == null || pct < 0 || pct > 60) {
                    activity.toast("0~60 사이 수수료율을 입력해 주세요")
                    return@setOnClickListener
                }
                settings.setPlatformNetShown(platform.id, switch.isChecked)
                settings.setPlatformFeePercent(platform.id, pct)
                dialog.dismiss()
                activity.refreshAll()
                refresh()
            }
        }
        dialog.show()
    }

    private fun showTipFeeDialog() {
        Dialogs.amount(
            activity,
            title = "팁 · 경유비 수수료",
            suffix = "%",
            initial = String.format(Locale.KOREA, "%.0f", settings.tipFeePercent),
            hint = "받은 팁을 이 %로 역산해요. 0%면 팁에 수수료를 떼지 않아요.",
            presets = listOf("0" to "0%", "10" to "10%", "15" to "15%", "20" to "20%", "25" to "25%"),
            allowDecimal = true
        ) { value ->
            val percent = value.replace(",", ".").toDoubleOrNull()
            if (percent == null || percent < 0 || percent > 60) {
                activity.toast("0~60 사이 수수료율을 입력해 주세요")
            } else {
                settings.tipFeePercent = percent
                activity.refreshAll()
                refresh()
            }
        }
    }

    private fun showCutoffDialog() {
        val container = LayoutInflater.from(activity).inflate(R.layout.dialog_cutoff, null)
        val picker = container.findViewById<NumberPicker>(R.id.hourPicker)
        val preview = container.findViewById<TextView>(R.id.cutoffPreview)

        picker.minValue = 0
        picker.maxValue = 23
        picker.value = settings.cutoffHour
        picker.wrapSelectorWheel = true
        picker.setFormatter { hour -> BizTime.cutOffLabel(hour) }

        fun updatePreview(hour: Int) {
            preview.text = "예: 새벽 2시 30분 콜 → ${BizTime.dayTitle(
                BizTime.businessDayOf(
                    BizTime.millisOf(java.time.LocalDate.now(), java.time.LocalTime.of(2, 30)), hour
                )
            )} 영업일"
        }
        picker.setOnValueChangedListener { _, _, newVal -> updatePreview(newVal) }
        updatePreview(picker.value)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("영업일 기준시각")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                settings.cutoffHour = picker.value
                activity.refreshAll()
                refresh()
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(activity.color(R.color.accent))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.color(R.color.text_secondary))
        }
        dialog.show()
    }

    private fun showThemeDialog() {
        val labels = arrayOf("다크 (야간 운행)", "라이트", "시스템 설정 따르기")
        val current = when (settings.themeMode) {
            AppSettings.THEME_LIGHT -> 1
            AppSettings.THEME_SYSTEM -> 2
            else -> 0
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("테마")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val mode = when (which) {
                    1 -> AppSettings.THEME_LIGHT
                    2 -> AppSettings.THEME_SYSTEM
                    else -> AppSettings.THEME_DARK
                }
                if (mode != settings.themeMode) activity.applyTheme(mode)
            }
            .setNegativeButton("닫기", null)
            .show()
    }
}

/** EditText.textWatcher 짧은 헬퍼 */
private fun EditText.doAfterTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = action(s?.toString() ?: "")
    })
}
