package com.youngsu.daeriledger.ui.unpaid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.data.LedgerRepository
import com.youngsu.daeriledger.data.Ride
import com.youngsu.daeriledger.databinding.ActivityUnpaidBinding
import com.youngsu.daeriledger.databinding.ItemEmptyBinding
import com.youngsu.daeriledger.databinding.ItemUnpaidRideBinding
import com.youngsu.daeriledger.ui.entry.AddRideActivity
import com.youngsu.daeriledger.ui.widget.RideCardBinder
import com.youngsu.daeriledger.util.Money

/** 미수금 정리 — 후불·미수로 남긴 콜을 모아 한 번에 입금 처리 */
class UnpaidActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnpaidBinding
    private lateinit var repo: LedgerRepository
    private val adapter = UnpaidAdapter()
    private var rides: List<Ride> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnpaidBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        repo = LedgerRepository(this)
        binding.unpaidRecycler.layoutManager = LinearLayoutManager(this)
        binding.unpaidRecycler.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        load()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        rides = repo.unpaidRides()
        adapter.submit(rides)
        val total = rides.sumOf { it.net }
        binding.totalUnpaidText.text = Money.won(total)
        binding.unpaidCountText.text = "${rides.size}건"
    }

    private fun settle(ride: Ride) {
        repo.setRideSettled(ride.id, true)
        load()
        Snackbar.make(
            binding.root,
            "${Money.won(ride.net)} 입금 완료로 정리했어요",
            Snackbar.LENGTH_LONG
        ).setAction("되돌리기") {
            repo.setRideSettled(ride.id, false)
            load()
        }.setBackgroundTint(getColor(R.color.surface_high))
            .setTextColor(getColor(R.color.text_primary))
            .setActionTextColor(getColor(R.color.accent))
            .show()
    }

    private fun edit(ride: Ride) {
        val intent = Intent(this, AddRideActivity::class.java)
            .putExtra(AddRideActivity.EXTRA_RIDE_ID, ride.id)
        startActivity(intent)
    }

    private inner class UnpaidAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Ride> = emptyList()

        fun submit(list: List<Ride>) {
            items = list
            notifyDataSetChanged()
        }

        inner class Holder(val b: ItemUnpaidRideBinding) : RecyclerView.ViewHolder(b.root)
        inner class EmptyHolder(val b: ItemEmptyBinding) : RecyclerView.ViewHolder(b.root)

        override fun getItemViewType(position: Int): Int =
            if (items.isEmpty()) TYPE_EMPTY else TYPE_ITEM

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == TYPE_EMPTY) {
                EmptyHolder(ItemEmptyBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            } else {
                Holder(ItemUnpaidRideBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            }

        override fun getItemCount(): Int = if (items.isEmpty()) 1 else items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is EmptyHolder) {
                holder.b.emptyTitle.text = "미수금이 없어요"
                holder.b.emptyMessage.text = "미수금으로 기록한 콜이 여기에 모입니다"
                return
            }
            val ride = items[position]
            val b = (holder as Holder).b
            // 윗줄 = 경로, 아랫줄 = 플랫폼 · 구분 · 시각 (오늘·달력 카드와 같은 규칙)
            RideCardBinder.bindUnpaidLines(b, ride)
            b.settleButton.setOnClickListener { settle(ride) }
            b.root.setOnClickListener { edit(ride) }
        }

    }

    private companion object {
        const val TYPE_ITEM = 1
        const val TYPE_EMPTY = 2
    }
}
