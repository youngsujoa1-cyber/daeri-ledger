package com.youngsu.daeriledger.ui.entry

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.LedgerRepository
import com.youngsu.daeriledger.data.PaymentType
import com.youngsu.daeriledger.data.Payout
import com.youngsu.daeriledger.data.PlaceNamer
import com.youngsu.daeriledger.data.PlaceTarget
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.data.RideSpotPing
import com.youngsu.daeriledger.data.ServiceType
import com.youngsu.daeriledger.databinding.ActivityAddRideBinding
import com.youngsu.daeriledger.location.KakaoGeocoder
import com.youngsu.daeriledger.location.LocationHelper
import com.youngsu.daeriledger.settings.AppSettings
import com.youngsu.daeriledger.util.BizTime
import com.youngsu.daeriledger.util.Dialogs
import com.youngsu.daeriledger.util.Money
import com.youngsu.daeriledger.util.selectChip
import com.youngsu.daeriledger.util.hideKeyboard
import com.youngsu.daeriledger.util.setAmountChips
import com.youngsu.daeriledger.util.setChips
import com.youngsu.daeriledger.util.toast
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** 3초 입력 화면 — 플랫폼·금액만 찍고 저장하면 순수익이 바로 계산된다. */
class AddRideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RIDE_ID = "ride_id"
        const val EXTRA_DATE = "date"

        /** `📍 현위치` 가 믿고 쓰는 마지막 좌표의 나이 — 이보다 오래됐으면 새로 잡는다 */
        private const val FRESH_FIX_MS = 3 * 60 * 1000L
    }

    private lateinit var binding: ActivityAddRideBinding
    private lateinit var repo: LedgerRepository
    private lateinit var settings: AppSettings

    private var rideId = 0L

    /** `📍 현위치` 가 채울 칸 — 마지막으로 손댄 출발지·도착지·경유 (기본 출발지) */
    private var locationTargetSlot = PlaceTarget.FROM
    private var timeMillis = System.currentTimeMillis()
    private var platformId = "kakao"
    private var service = ServiceType.DAERI
    private var payment = PaymentType.CASH
    private var existing: Ride? = null
    private var insuranceTouched = false
    private var applyingDefaultInsurance = false

    /** `📍 현위치` 로 잡아 둔 좌표 — 저장할 때 익명 콜밭 집계에도 그대로 쓴다 */
    private var gpsFix: RideSpotPing.Fix? = null
    private var locating = false

    /** 위치 권한 요청 — 허락하면 누르자마자 잡던 현위치를 이어서 잡는다 */
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (LocationHelper.hasPermission(this)) {
            startLocating()
        } else {
            toast("위치 권한을 허용해야 현위치를 채울 수 있어요")
        }
    }

    private val quickAmounts = listOf(
        15_000 to "1.5만", 20_000 to "2만", 25_000 to "2.5만",
        30_000 to "3만", 40_000 to "4만", 50_000 to "5만"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime.bottom))
            insets
        }

        repo = LedgerRepository(this)
        settings = AppSettings.get(this)

        rideId = intent.getLongExtra(EXTRA_RIDE_ID, 0L)
        // 수정 중이면 그 기록을 먼저 읽는다 — 설정에서 체크를 끈 프로그램으로 적은 기록도 고칠 수 있게
        // 플랫폼 칩에 그 한 종을 임시로 함께 보여 주기 위해서다 (v1.0.32)
        existing = if (rideId != 0L) repo.rideById(rideId) else null
        if (rideId != 0L && existing == null) { finish(); return }
        platformId = existing?.platformId ?: defaultPlatformId()
        service = existing?.service ?: ServiceType.from(settings.lastServiceType)

        setupInsetsAndBar()
        setupChips()
        setupInputs()

        if (existing != null) {
            prefill(existing!!)
        } else {
            intent.getStringExtra(EXTRA_DATE)?.let { key ->
                BizTime.parseDay(key)?.let { date ->
                    var time = LocalTime.now()
                    if (BizTime.businessDayOf(BizTime.millisOf(date, time), settings.cutoffHour) != date) {
                        time = LocalTime.of(settings.cutoffHour, 0)
                    }
                    timeMillis = BizTime.millisOf(date, time)
                }
            }
            applyDefaultInsurance()
        }

        renderTime()
        renderChips()
        renderPlatformNote()
        updatePreview()
    }

    // ---------------------------------------------------------------- 구성

    private fun setupInsetsAndBar() {
        binding.closeButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { save(closeAfter = true) }
        binding.saveAndNextButton.setOnClickListener { save(closeAfter = false) }
        binding.deleteButton.setOnClickListener {
            val ride = existing ?: return@setOnClickListener
            Dialogs.confirm(
                this,
                "기록 삭제",
                "${BizTime.dayTitle(ride.businessDay)} ${BizTime.clockLabel(ride.timeMillis)} · " +
                    "${ride.platformLabel} ${Money.won(ride.total)} 기록을 지울까요?",
                positive = "삭제",
                destructive = true
            ) {
                repo.deleteRide(ride.id)
                setResult(RESULT_OK)
                toast("기록을 지웠어요")
                finish()
            }
        }
        binding.timeRow.setOnClickListener { pickDateTime() }
    }

    private fun setupChips() {
        binding.platformChips.isSingleSelection = true
        binding.serviceChips.isSingleSelection = true
        binding.paymentChips.isSingleSelection = true
        binding.amountChips.isSingleSelection = true

        // 플랫폼 칩은 설정에서 체크한 프로그램만 — 수정 중이면 그 기록의 플랫폼도 함께 (v1.0.32)
        val chipPlatforms = Platforms.visibleWith(
            includeId = existing?.platformId,
            enabledIds = settings.enabledPlatformIds
        )
        binding.platformChips.setChips(chipPlatforms.map { it.id to it.label }, platformId) { id ->
            platformId = id
            renderPlatformNote()
            // 카카오T·티맵은 건당 보험료 0원, 나머지는 손대지 않았을 때 설정값으로 되돌린다
            applyDefaultInsurance()
            updatePreview()
        }

        binding.serviceChips.setChips(
            listOf(ServiceType.DAERI.id to "대리", ServiceType.TAKSONG.id to "탁송"),
            service.id
        ) { id ->
            service = ServiceType.from(id)
            applyDefaultInsurance()
            updatePreview()
        }

        binding.paymentChips.setChips(
            PaymentType.entries.map { it.id to it.label },
            payment.id
        ) { id ->
            payment = PaymentType.from(id)
        }

        binding.amountChips.setAmountChips(
            quickAmounts.map { it.first.toString() to it.second },
            null
        ) { value ->
            binding.amountInput.setText(value)
            binding.amountInput.setSelection(binding.amountInput.text.length)
        }
    }

    /**
     * 처음 열 때 고를 프로그램 — 마지막에 쓴 프로그램이 체크돼 있으면 그대로,
     * 아니면 체크한 프로그램 중 첫 번째(`카카오T` → … → `개인콜` 순서).
     */
    private fun defaultPlatformId(): String {
        val enabled = settings.enabledPlatformIds
        val last = settings.lastPlatformId
        if (last in enabled) return last
        return Platforms.visible(enabled).firstOrNull()?.id ?: Platforms.DEFAULT_ENABLED_IDS.first()
    }

    private fun setupInputs() {
        binding.amountInput.addTextChangedListener(simpleWatcher {
            syncAmountChip()
            updatePreview()
        })
        binding.tipInput.addTextChangedListener(simpleWatcher { updatePreview() })
        binding.insuranceInput.addTextChangedListener(simpleWatcher {
            if (!applyingDefaultInsurance) insuranceTouched = true
            updatePreview()
        })
        binding.advancedToggle.setOnClickListener { toggleAdvanced() }
        binding.currentLocationChip.setOnClickListener { useCurrentLocation() }
        trackLocationTargets()
    }

    private fun simpleWatcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = action()
    }

    private fun toggleAdvanced(forceOpen: Boolean? = null) {
        val open = forceOpen ?: (binding.advancedPanel.visibility != View.VISIBLE)
        binding.advancedPanel.visibility = if (open) View.VISIBLE else View.GONE
        binding.advancedToggle.text = if (open) "▴ 상세 입력 접기" else "▾ 상세 입력 (건당 보험료)"
    }

    // ------------------------------------------------------------ 칩/표시

    private fun renderChips() {
        binding.platformChips.selectChip(platformId)
        binding.serviceChips.selectChip(service.id)
        binding.paymentChips.selectChip(payment.id)
        applyDefaultInsurance()
        syncAmountChip()
    }

    private fun syncAmountChip() {
        val input = Money.parseAmount(binding.amountInput.text.toString())
        val match = quickAmounts.firstOrNull { it.first == input }?.first?.toString()
        if (match != null) binding.amountChips.selectChip(match) else {
            for (i in 0 until binding.amountChips.childCount) {
                (binding.amountChips.getChildAt(i) as? Chip)?.isChecked = false
            }
        }
    }

    private fun renderPlatformNote() {
        val platform = Platforms.byId(platformId)
        val pct = settings.platformFeePercent(platformId)
        val tipPct = settings.tipFeePercent
        val pctLabel = String.format(java.util.Locale.KOREA, "%.0f", pct)
        val tipPctLabel = String.format(java.util.Locale.KOREA, "%.0f", tipPct)
        // 팁·경유비는 플랫폼과 상관없이 '받은 금액'을 적는다 (설정의 팁·경유비 %로 역산)
        binding.tipLabel.text = "팁 · 경유비 (받은 금액)"
        val tipNote = if (tipPct > 0) {
            "팁 · 경유비는 받은 금액을 ${tipPctLabel}%로 역산해요. " +
                "(받은 ${Money.won(10_000)} → 총액 ${Money.won(Payout.grossFromNet(10_000, tipPct))}, " +
                "수수료 ${Money.won(Payout.grossFromNet(10_000, tipPct) - 10_000)})"
        } else {
            "팁 · 경유비 수수료 0% — 받은 팁을 그대로 더해요. 설정에서 %를 넣으면 역산해요."
        }
        // 설정에서 체크를 끈 프로그램의 기록을 고칠 때 — 왜 칩에 함께 보이는지 알려 준다 (v1.0.32)
        val uncheckedNote = if (settings.isPlatformEnabled(platformId)) "" else
            "\n※ 설정에서 체크를 끈 프로그램이에요 — 이 기록을 고치는 동안만 칩에 같이 보여요."
        if (settings.platformNetShown(platformId)) {
            binding.amountSectionLabel.text = "금액 (수수료 떼고 받은 금액)"
            binding.platformFeeNote.text = (if (pct > 0) {
                "${platform.label}은(는) 수수료 ${pctLabel}%를 떼고 금액이 보여요. " +
                    "받은 금액을 넣으면 총매출을 역산해서 잡아요. " +
                    "(받은 ${Money.won(20_000)} → 총매출 ${Money.won(Payout.grossFromNet(20_000, pct))})"
            } else {
                "${platform.label}은(는) 앱에 수수료가 빠진 금액이 보여요. " +
                    "수수료율을 넣으면 총매출을 역산해서 잡아요."
            }) + "\n" + tipNote + uncheckedNote
        } else {
            binding.amountSectionLabel.text = "금액 (총매출)"
            binding.platformFeeNote.text = (if (pct <= 0) {
                "${platform.label} 수수료 0% — 설정에서 실제 수수료율을 넣으면 순수익이 더 정확해져요."
            } else {
                "${platform.label}은(는) 금액의 ${pct.toInt()}%를 수수료로 뺍니다. 설정에서 바꿀 수 있어요."
            }) + "\n" + tipNote + uncheckedNote
        }
    }

    private fun renderTime() {
        val cutoff = settings.cutoffHour
        val businessDay = BizTime.businessDayOf(timeMillis, cutoff)
        val date = BizTime.dateOf(timeMillis)
        binding.timeText.text = "${BizTime.dayTitle(date)} ${BizTime.clockLabel(timeMillis)}"
        binding.businessDayChip.text = "영업일 ${businessDay.monthValue}/${businessDay.dayOfMonth}"
    }

    /**
     * 건당 보험료 칸과 구분 줄의 안내를 플랫폼에 맞춘다.
     * - 카카오T·티맵: 건당 보험료 없음 → 항상 0원으로 두고 안내에도 그렇게 적는다(v1.0.20).
     * - 개인콜: 건당 보험료도 3.3% 세금도 없음 → 둘 다 0원으로 잠그고 그렇게 적는다(v1.0.33).
     * - 그 외: 직접 고친 값이 있으면 그대로 두고, 아니면 설정의 건당 보험료로 채운다.
     */
    private fun applyDefaultInsurance() {
        val perCallExempt = !Platforms.chargesPerCallInsurance(platformId)
        val taxExempt = !Platforms.chargesEmploymentTax(platformId)
        applyingDefaultInsurance = true
        when {
            perCallExempt -> binding.insuranceInput.setText("0")
            !insuranceTouched -> binding.insuranceInput.setText(settings.insuranceFor(service).toString())
        }
        applyingDefaultInsurance = false
        if (perCallExempt) insuranceTouched = false
        // 카카오T·티맵·개인콜은 건당 보험료가 붙지 않으므로 칸도 0원으로 잠근다
        binding.insuranceInput.isEnabled = !perCallExempt
        binding.insuranceHintText.text = when {
            perCallExempt && taxExempt -> "${Platforms.label(platformId)} · 보험료·세금 제외"
            perCallExempt -> "${Platforms.label(platformId)}은(는) 건당 보험 없음"
            else -> "건당 보험료 ${Money.won(settings.insuranceFor(service))} 자동"
        }
    }

    private fun prefill(ride: Ride) {
        timeMillis = ride.timeMillis
        platformId = ride.platformId
        service = ride.service
        payment = ride.payment
        binding.titleText.text = "기록 수정"
        binding.deleteButton.visibility = View.VISIBLE
        // 카카오T·티맵·핸들은 저장된 총매출·수수료에서 손님이 보낸(수수료 떼고 받은) 금액을 도로 찾는다.
        // 팁·경유비도 같은 방식 — 저장된 팁은 총액 기준이므로 두 번 역산되지 않게 입력값으로 돌려 둔다.
        val enteredInputs = Payout.inputsFrom(
            gross = ride.gross,
            tip = ride.tip,
            feeAmount = ride.feeAmount,
            feePercent = ride.feePercent,
            netShown = settings.platformNetShown(ride.platformId)
        )
        binding.amountInput.setText(enteredInputs.amount.toString())
        binding.tipInput.setText(if (enteredInputs.tip > 0) enteredInputs.tip.toString() else "")
        binding.fromInput.setText(ride.fromPlace)
        binding.viaInput.setText(ride.viaPlace)
        binding.toInput.setText(ride.toPlace)
        binding.memoInput.setText(ride.memo)
        // 저장된 보험&세금 합계에서 건당 몫만 되돌린다(3.3% 세금은 다시 계산) — 두 번 붙지 않게
        // 개인콜(v1.0.33)·카카오T·티맵처럼 건당 보험료가 없는 플랫폼은 그냥 0원이다
        val rule = Payout.insuranceRuleFor(
            ride.platformId,
            settings.insuranceFor(ride.service),
            settings.employmentInsurancePercent
        )
        val perCallExempt = !Platforms.chargesPerCallInsurance(ride.platformId)
        val perCallStored = if (perCallExempt) 0 else Payout.perCallFromStored(
            ride.gross,
            ride.insuranceAmount,
            rule.employmentPercent
        )
        binding.insuranceInput.setText(perCallStored.toString())
        insuranceTouched = true
        // 출발·도착·특이사항은 항상 보이므로, 건당 보험료가 기본값과 다를 때만 상세를 편다.
        if (!perCallExempt && perCallStored != rule.perCall) {
            toggleAdvanced(forceOpen = true)
        }
        renderChips()
        renderPlatformNote()
        renderTime()
    }

    // ----------------------------------------------------------- 현위치 GPS

    /** `📍 현위치` — 권한이 있으면 바로 잡고, 없으면 허락을 받는다 */
    private fun useCurrentLocation() {
        if (locating) return
        if (!LocationHelper.hasPermission(this)) {
            locationPermission.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
            return
        }
        startLocating()
    }

    private fun startLocating() {
        if (locating) return
        setLocating(true)
        // 3분 안에 잡힌 좌표는 바로 쓰고, 더 오래된 좌표(다른 동네일 수 있다)는 새로 잡는다 (최대 6초)
        LocationHelper.request(this, maxCacheAgeMs = FRESH_FIX_MS) { location ->
            if (location == null) {
                setLocating(false)
                toastLocationHelp()
                return@request
            }
            resolveNeighborhood(
                RideSpotPing.Fix(
                    lat = location.latitude,
                    lng = location.longitude,
                    atMillis = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * `📍 현위치` 가 들어갈 칸 — 출발지·도착지·경유 중 마지막으로 손댄 칸 (기본 출발지, v1.0.28).
     * 금액·메모를 누른 뒤에도 마지막으로 고른 위치 칸을 그대로 쓴다.
     */
    private fun trackLocationTargets() {
        val slots = listOf(
            binding.fromInput to PlaceTarget.FROM,
            binding.toInput to PlaceTarget.TO,
            binding.viaInput to PlaceTarget.VIA
        )
        for ((field, slot) in slots) {
            field.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) locationTargetSlot = slot
            }
            // 포커스가 그대로인 칸을 다시 눌러도 기억한다
            field.setOnClickListener { locationTargetSlot = slot }
        }
    }

    /** 지금 넣을 칸 — 3개 중 하나에 포커스가 있으면 그 칸, 아니면 마지막으로 손댄 칸 */
    private fun locationTarget(): PlaceTarget {
        val focused = when {
            binding.fromInput.hasFocus() -> PlaceTarget.FROM
            binding.toInput.hasFocus() -> PlaceTarget.TO
            binding.viaInput.hasFocus() -> PlaceTarget.VIA
            else -> null
        }
        return PlaceTarget.choose(focused, locationTargetSlot)
    }

    private fun locationField(slot: PlaceTarget): EditText = when (slot) {
        PlaceTarget.TO -> binding.toInput
        PlaceTarget.VIA -> binding.viaInput
        PlaceTarget.FROM -> binding.fromInput
    }

    private fun locationLabel(slot: PlaceTarget): String = when (slot) {
        PlaceTarget.TO -> "도착지"
        PlaceTarget.VIA -> "경유"
        PlaceTarget.FROM -> "출발지"
    }

    /**
     * 좌표 → 동네 이름(정자동·역삼동·박곡리…)을 기다렸다가 **마지막으로 고른 위치 칸**에 넣는다 (백그라운드에서).
     *
     * 한국 Android `Geocoder` 는 도로명·시 단위만 주는 일이 많아 `성남시` 로 떨어졌다(v1.0.27 실기기).
     * 그래서 **카카오 `coord2address`(동·리·가) → Android `Geocoder`** 순서로 시도한다.
     * 콜밭 집계 좌표는 **출발지에 넣었을 때만** 남긴다 — 도착지·경유 좌표가 콜밭으로 새지 않게.
     */
    private fun resolveNeighborhood(fix: RideSpotPing.Fix) {
        Thread {
            val name = KakaoGeocoder.lookup(fix.lat, fix.lng) ?: geocoderName(fix.lat, fix.lng)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setLocating(false)
                if (name.isNullOrBlank()) {
                    toast("동네 이름을 못 찾았어요 — 직접 적어 주세요")
                } else {
                    val slot = locationTarget()
                    val field = locationField(slot)
                    field.setText(name)
                    field.setSelection(name.length)
                    locationTargetSlot = slot
                    // 콜밭 집계 좌표는 출발지에 넣었을 때만 — 도착지·경유 좌표가 콜밭으로 새지 않게
                    gpsFix = if (slot.keepsSpotFix) fix else null
                    toast("현위치를 ${locationLabel(slot)}에 넣었어요 · $name")
                }
            }
        }.start()
    }

    /** 카카오가 답하지 않을 때의 마지막 수단 — 기기 `Geocoder` + {@link PlaceNamer} */
    private fun geocoderName(lat: Double, lng: Double): String? = runCatching {
        val address = Geocoder(applicationContext, Locale.KOREAN)
            .getFromLocation(lat, lng, 1)
            ?.firstOrNull()
            ?: return@runCatching null
        PlaceNamer.shortName(
            locality = address.locality,
            subLocality = address.subLocality,
            subAdminArea = address.subAdminArea,
            thoroughfare = address.thoroughfare,
            addressLines = (0..address.maxAddressLineIndex)
                .mapNotNull { address.getAddressLine(it) },
            adminArea = address.adminArea
        )
    }.getOrNull()

    /** 버튼에 잠깐 `확인 중...` 을 보여 준다 */
    private fun setLocating(on: Boolean) {
        locating = on
        binding.currentLocationChip.text = if (on) "확인 중..." else "📍 현위치"
        binding.currentLocationChip.isEnabled = !on
    }

    /** 위치가 꺼져 있는지 / 못 잡았는지에 맞춰 안내한다 */
    private fun toastLocationHelp() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val off = manager != null &&
            !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        toast(
            if (off) "위치(GPS)가 꺼져 있어요 — 켜고 다시 눌러 주세요"
            else "위치를 못 잡았어요 — 잠시 뒤 다시 눌러 주세요"
        )
    }

    // ----------------------------------------------------------- 날짜·시간

    private fun pickDateTime() {
        val date = BizTime.dateOf(timeMillis)
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val chosen = LocalDate.of(year, month + 1, dayOfMonth)
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        var millis = BizTime.millisOf(chosen, LocalTime.of(hour, minute))
                        if (chosen != BizTime.businessDayOf(millis, settings.cutoffHour) &&
                            chosen == BizTime.dateOf(millis)
                        ) {
                            // 영업일이 전날로 밀리면 그대로 둔다 (새벽 콜은 전날 영업일이 맞음)
                        }
                        timeMillis = millis
                        renderTime()
                    },
                    BizTime.timeOf(timeMillis).hour,
                    BizTime.timeOf(timeMillis).minute,
                    true
                ).show()
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth
        ).show()
    }

    // --------------------------------------------------------------- 계산

    private fun inputAmount(): Int = Money.parseAmount(binding.amountInput.text.toString())

    private fun tip(): Int = Money.parseAmount(binding.tipInput.text.toString())

    private fun insuranceValue(): Int = Money.parseAmount(binding.insuranceInput.text.toString())

    /** 지금 고른 플랫폼에 실제로 적용되는 보험&세금 규칙 — 카카오T·티맵은 건당 0원, 개인콜은 둘 다 0원 */
    private fun insuranceRule(): Payout.InsuranceRule = Payout.insuranceRuleFor(
        platformId = platformId,
        configuredPerCall = insuranceValue(),
        configuredEmploymentPercent = settings.employmentInsurancePercent
    )

    private fun breakdown(input: Int): Payout.Breakdown {
        val rule = insuranceRule()
        return Payout.calculate(
            input = input,
            tip = tip(),
            feePercent = settings.effectiveFeePercent(platformId),
            tipFeePercent = settings.tipFeePercent,
            insurancePerCall = rule.perCall,
            employmentInsurancePercent = rule.employmentPercent,
            netShown = settings.platformNetShown(platformId)
        )
    }

    private fun updatePreview() {
        val input = inputAmount()
        val enteredTip = tip()
        val feePercent = settings.effectiveFeePercent(platformId)
        val tipPercent = settings.tipFeePercent
        val feeLabel = "플랫폼 수수료 ${String.format(java.util.Locale.KOREA, "%.0f", feePercent)}%"
        if (input <= 0 && enteredTip <= 0) {
            binding.previewTotal.text = Money.won(0)
            binding.previewFeeLabel.text = feeLabel
            binding.previewFee.text = "-0원"
            binding.previewInsurance.text = "-0원"
            binding.previewInsuranceNote.visibility = View.GONE
            binding.previewNet.text = "0원"
            binding.previewNet.setTextColor(getColor(R.color.text_secondary))
            binding.previewTipRow.visibility = View.GONE
            return
        }
        val result = breakdown(input)
        val tipFee = result.tipFee.coerceAtLeast(0)
        val tipPercentLabel = String.format(java.util.Locale.KOREA, "%.0f", tipPercent)

        binding.previewTotal.text = Money.won(result.total)
        // 수수료 줄에 팁·경유비 수수료율을 같이 보여 준다 — 팁을 적었을 때만
        binding.previewFeeLabel.text = if (result.tipGross > 0) "$feeLabel · 팁·경유비 $tipPercentLabel%" else feeLabel
        binding.previewFee.text = "-" + Money.won(result.fee)
        binding.previewInsurance.text = "-" + Money.won(result.insurance)
        // 보험&세금 내역 — 건당 보험료와 3.3% 세금을 나눠 보여 준다 (1,485원이 어디서 나왔는지)
        val rule = insuranceRule()
        val perCall = rule.perCall
        val empAmount = Payout.employmentOf(result.gross, rule.employmentPercent)
        val empLabel = String.format(java.util.Locale.KOREA, "%.1f", rule.employmentPercent)
        val insuranceParts = buildList {
            if (perCall > 0) add("건당 ${Money.won(perCall)}")
            if (empAmount > 0) add("$empLabel% ${Money.won(empAmount)}")
            if (isEmpty() && rule.exempt) add("보험료·세금 없음")
        }
        if (insuranceParts.isEmpty()) {
            binding.previewInsuranceNote.visibility = View.GONE
        } else {
            binding.previewInsuranceNote.visibility = View.VISIBLE
            binding.previewInsuranceNote.text = insuranceParts.joinToString(" + ")
        }
        binding.previewNet.text = Money.won(result.net)
        binding.previewNet.setTextColor(
            getColor(if (result.net < 0) R.color.loss else R.color.profit)
        )
        // 팁·경유비 역산 내역 — 역산 플랫폼이면 받은 금액과 총액이 같이 보이게
        if (result.tipGross > 0) {
            binding.previewTipRow.visibility = View.VISIBLE
            binding.previewTipLabel.text = if (tipFee > 0) {
                "팁 · 경유비 (받은 ${Money.won(enteredTip)} / ${tipPercentLabel}%)"
            } else {
                "팁 · 경유비 (받은 ${Money.won(enteredTip)} · 수수료 0%)"
            }
            binding.previewTip.text = "+" + Money.won(result.tipGross)
        } else {
            binding.previewTipRow.visibility = View.GONE
        }
    }

    // --------------------------------------------------------------- 저장

    private fun save(closeAfter: Boolean) {
        binding.root.hideKeyboard()
        val input = inputAmount()
        if (input <= 0) {
            toast("금액을 입력해 주세요")
            binding.amountInput.requestFocus()
            return
        }

        val cutoff = settings.cutoffHour
        val businessDay = BizTime.businessDayOf(timeMillis, cutoff)
        val result = breakdown(input)
        val current = existing
        val ride = Ride(
            id = current?.id ?: 0L,
            businessDay = businessDay,
            timeMillis = timeMillis,
            platformId = platformId,
            service = service,
            payment = payment,
            gross = result.gross,
            feePercent = settings.effectiveFeePercent(platformId),
            feeAmount = result.fee,
            // 팁·경유비도 역산 플랫폼이면 총액(수수료 떼기 전) 기준으로 저장한다.
            tip = result.tipGross,
            insuranceAmount = result.insurance,
            fromPlace = binding.fromInput.text.toString().trim(),
            viaPlace = binding.viaInput.text.toString().trim(),
            toPlace = binding.toInput.text.toString().trim(),
            memo = binding.memoInput.text.toString().trim(),
            // 콜밭 지역 입력은 화면에서 뺐다 — 예전 기록의 태그는 고칠 때도 그대로 남긴다
            spot = current?.spot.orEmpty(),
            settled = when {
                payment != PaymentType.UNPAID -> true
                current == null -> false
                current.payment == PaymentType.UNPAID -> current.settled
                else -> false
            },
            createdAt = current?.createdAt ?: 0L
        )
        repo.saveRide(ride)

        // 새 기록만 — 운행 시각의 현재 GPS 좌표를 익명 콜밭 집계에 보탈다 (v1.0.26).
        // 수정 저장에서는 보내지 않고, 몰아 올리기(밀린 기록·연속 입력)는 RideSpotGate 가 걸러 낸다.
        if (current == null) {
            RideSpotPing.onRideSaved(applicationContext, settings, ride.timeMillis, ride.fromPlace, gpsFix)
        }
        // 저장한 기록의 좌표는 여기서 끝 — `저장 후 계속` 의 다음 기록은 새 자리에서 잡아야 한다
        gpsFix = null

        settings.lastPlatformId = platformId
        settings.lastServiceType = service.id
        setResult(RESULT_OK)

        if (closeAfter) {
            toast("저장 완료 · 순수익 ${Money.won(ride.net)}")
            finish()
        } else {
            Snackbar.make(
                binding.root,
                "저장했어요 · 순수익 ${Money.won(ride.net)} · 다음 콜을 입력하세요",
                Snackbar.LENGTH_SHORT
            ).setBackgroundTint(getColor(R.color.surface_high))
                .setTextColor(getColor(R.color.text_primary))
                .show()
            binding.amountInput.setText("")
            binding.tipInput.setText("")
            binding.memoInput.setText("")
            binding.fromInput.setText("")
            binding.viaInput.setText("")
            binding.toInput.setText("")
            if (!insuranceTouched || !Platforms.chargesPerCallInsurance(platformId)) applyDefaultInsurance()
            timeMillis = System.currentTimeMillis()
            renderTime()
            updatePreview()
            binding.amountInput.requestFocus()
        }
    }
}
