package com.youngsu.daeriledger.ui.entry

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import com.youngsu.daeriledger.data.Platforms
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.data.ServiceType
import com.youngsu.daeriledger.databinding.ActivityAddRideBinding
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

/** 3초 입력 화면 — 플랫폼·금액만 찍고 저장하면 순수익이 바로 계산된다. */
class AddRideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RIDE_ID = "ride_id"
        const val EXTRA_DATE = "date"
    }

    private lateinit var binding: ActivityAddRideBinding
    private lateinit var repo: LedgerRepository
    private lateinit var settings: AppSettings

    private var rideId = 0L
    private var timeMillis = System.currentTimeMillis()
    private var platformId = "kakao"
    private var service = ServiceType.DAERI
    private var payment = PaymentType.CASH
    private var existing: Ride? = null
    private var insuranceTouched = false
    private var applyingDefaultInsurance = false

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
        platformId = settings.lastPlatformId
        service = ServiceType.from(settings.lastServiceType)

        setupInsetsAndBar()
        setupChips()
        setupInputs()

        if (rideId != 0L) {
            existing = repo.rideById(rideId)
            existing?.let { prefill(it) } ?: run { finish(); return }
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

        binding.platformChips.setChips(Platforms.ALL.map { it.id to it.label }, platformId) { id ->
            platformId = id
            renderPlatformNote()
            if (!insuranceTouched) applyDefaultInsurance()
            updatePreview()
        }

        binding.serviceChips.setChips(
            listOf(ServiceType.DAERI.id to "대리", ServiceType.TAKSONG.id to "탁송"),
            service.id
        ) { id ->
            service = ServiceType.from(id)
            if (!insuranceTouched) applyDefaultInsurance()
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
    }

    private fun simpleWatcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = action()
    }

    private fun toggleAdvanced(forceOpen: Boolean? = null) {
        val open = forceOpen ?: (binding.advancedPanel.visibility != View.VISIBLE)
        binding.advancedPanel.visibility = if (open) View.VISIBLE else View.GONE
        binding.advancedToggle.text = if (open) "▴ 상세 입력 접기" else "▾ 상세 입력 (보험료)"
    }

    // ------------------------------------------------------------ 칩/표시

    private fun renderChips() {
        binding.platformChips.selectChip(platformId)
        binding.serviceChips.selectChip(service.id)
        binding.paymentChips.selectChip(payment.id)
        binding.insuranceHintText.text = "건당 보험료 ${Money.won(settings.insuranceFor(service))} 자동"
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
        if (settings.platformNetShown(platformId)) {
            binding.amountSectionLabel.text = "금액 (수수료 떼고 받은 금액)"
            binding.platformFeeNote.text = (if (pct > 0) {
                "${platform.label}은(는) 수수료 ${pctLabel}%를 떼고 금액이 보여요. " +
                    "받은 금액을 넣으면 총매출을 역산해서 잡아요. " +
                    "(받은 ${Money.won(20_000)} → 총매출 ${Money.won(Payout.grossFromNet(20_000, pct))})"
            } else {
                "${platform.label}은(는) 앱에 수수료가 빠진 금액이 보여요. " +
                    "수수료율을 넣으면 총매출을 역산해서 잡아요."
            }) + "\n" + tipNote
        } else {
            binding.amountSectionLabel.text = "금액 (총매출)"
            binding.platformFeeNote.text = (if (pct <= 0) {
                "${platform.label} 수수료 0% — 설정에서 실제 수수료율을 넣으면 순수익이 더 정확해져요."
            } else {
                "${platform.label}은(는) 금액의 ${pct.toInt()}%를 수수료로 뺍니다. 설정에서 바꿀 수 있어요."
            }) + "\n" + tipNote
        }
    }

    private fun renderTime() {
        val cutoff = settings.cutoffHour
        val businessDay = BizTime.businessDayOf(timeMillis, cutoff)
        val date = BizTime.dateOf(timeMillis)
        binding.timeText.text = "${BizTime.dayTitle(date)} ${BizTime.clockLabel(timeMillis)}"
        binding.businessDayChip.text = "영업일 ${businessDay.monthValue}/${businessDay.dayOfMonth}"
    }

    private fun applyDefaultInsurance() {
        applyingDefaultInsurance = true
        binding.insuranceInput.setText(settings.insuranceFor(service).toString())
        applyingDefaultInsurance = false
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
        binding.insuranceInput.setText(ride.insuranceAmount.toString())
        insuranceTouched = true
        // 출발·도착·특이사항은 항상 보이므로, 보험료가 기본값과 다를 때만 상세를 편다.
        if (ride.insuranceAmount != settings.insuranceFor(ride.service)) {
            toggleAdvanced(forceOpen = true)
        }
        renderChips()
        renderPlatformNote()
        renderTime()
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

    private fun breakdown(input: Int) = Payout.calculate(
        input = input,
        tip = tip(),
        feePercent = settings.effectiveFeePercent(platformId),
        tipFeePercent = settings.tipFeePercent,
        insurancePerCall = insuranceValue(),
        employmentInsurancePercent = settings.employmentInsurancePercent,
        netShown = settings.platformNetShown(platformId)
    )

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
            if (!insuranceTouched) applyDefaultInsurance()
            timeMillis = System.currentTimeMillis()
            renderTime()
            updatePreview()
            binding.amountInput.requestFocus()
        }
    }
}
