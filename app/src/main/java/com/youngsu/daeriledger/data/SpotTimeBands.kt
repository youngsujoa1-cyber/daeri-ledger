package com.youngsu.daeriledger.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 콜밭이 **잘 뜨는 시간대(피크)** — 제보·운행 시각의 분포에서 제보가 가장 많이 모인 3시간 창을 찾는다.
 * 화면 위의 시간대 필터 칩 줄은 v1.0.29 에서 없어졌고, 시간대는 카드·지도 핀 정보의 피크 줄로만 쓴다.
 *
 * 서버(Firebase Firestore) 보안 규칙은 `spot_reports` 문서의 **8칸**
 * (`name`·`kind`·`lat`·`lng`·`address`·`note`·`deviceHash`·`createdAt`)만 받는다.
 * 그래서 시간대를 담을 새 칸을 만들 수 없어, 운행 기록에서 만든 익명 제보는
 * `note` 첫머리에 `#t:22` 같은 **시간대 표식**을 붙여 보내고 화면에 보일 때는 이 표식을 떼어 낸다.
 * (메모가 있는 제보는 `#t:22 메모` 처럼 표식 뒤에 한 칸 띄고 이어진다)
 *
 * Android 와 무관한 순수 로직이라 `tools/logic-check` 에서 그대로 검사한다.
 */
object SpotTimeBands {

    /** note 첫머리에 붙는 시간대 표식 */
    const val TOKEN = "#t:"
    /** 피크를 말할 때 쓰는 시간 창(시간) */
    const val WINDOW_HOURS = 3
    /** 한 시간대라고 말하려면 최소 이만큼의 제보가 그 창에 모여야 한다 */
    const val MIN_SAMPLES = 2

    /** 이 시각(ms)의 현지 시(hour) — 0~23 */
    fun hourOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        Instant.ofEpochMilli(millis).atZone(zone).hour

    /** 메모 + 시간대 → 제보 note. 예) ("", 22) → "#t:22", ("강남역 앞", 22) → "#t:22 강남역 앞" */
    fun encodeNote(memo: String, hour: Int): String {
        val token = TOKEN + hour.coerceIn(0, 23).toString().padStart(2, '0')
        val body = memo.trim()
        return if (body.isEmpty()) token else "$token $body"
    }

    /**
     * 제보 note → (메모, 시간대). 표식이 없으면 (원문, null).
     * 표식이 깨져 있으면(예: `#t:`) 원문을 그대로 메모로 본다 — 남의 메모를 잃지 않는다.
     */
    fun decodeNote(note: String): Pair<String, Int?> {
        val text = note.trim()
        if (!text.startsWith(TOKEN)) return text to null
        val rest = text.removePrefix(TOKEN)
        val hour = rest.substringBefore(' ').toIntOrNull()?.takeIf { it in 0..23 } ?: return text to null
        val memo = rest.substringAfter(' ', "").trim()
        return memo to hour
    }

    /** note 에서 읽은 시간대 — 없으면 null */
    fun bandOf(note: String): Int? = decodeNote(note).second

    /** 이 제보가 운행 기록(GPS)에서 만들어진 익명 제보인지 — note 에 시간대 표식이 있으면 true */
    fun isRideBand(note: String): Boolean = bandOf(note) != null

    /**
     * 24시간을 도는 [WINDOW_HOURS]시간 창 중 제보가 가장 많이 모인 곳의 시작 시각.
     * 같은 수면 샘플이 몰린 시(hour)에서 시작하는 창을 고르고(예: 23시 2건 → 23시 창),
     * 그래도 같으면 저녁부터 훑은 첫 창을 고른다(심야 콜밭다운 표기).
     * 창에 [MIN_SAMPLES]건이 못 미치면 null — 한두 건으로 시간대를 단정하지 않는다.
     */
    fun peakWindow(hours: List<Int>): Int? {
        if (hours.size < MIN_SAMPLES) return null
        val counts = IntArray(24)
        hours.forEach { counts[((it % 24) + 24) % 24]++ }
        var bestStart = -1
        var best = 0
        var bestFirst = 0
        for (start in EVENING_FIRST) {
            var sum = 0
            for (k in 0 until WINDOW_HOURS) sum += counts[(start + k) % 24]
            if (sum > best || (sum == best && counts[start] > bestFirst)) {
                best = sum
                bestStart = start
                bestFirst = counts[start]
            }
        }
        return if (best >= MIN_SAMPLES) bestStart else null
    }

    /** 창 시작 시각 → "22시~01시" */
    fun label(windowStart: Int): String {
        val start = ((windowStart % 24) + 24) % 24
        return String.format(Locale.KOREA, "%02d시~%02d시", start, (start + WINDOW_HOURS) % 24)
    }

    /** "22시~01시 활발" — 모인 제보가 적으면 빈 문자열 */
    fun peakLabel(hours: List<Int>): String = peakWindow(hours)?.let { label(it) + " 활발" } ?: ""

    /** 지금 시간대를 "지금"으로 보는 폭(시간) — 현재 시각 ±1시간 = 3시간 창 */
    const val NOW_RADIUS = 1

    /** 두 시(0~23)의 시계 방향 최단 거리 — 23시와 0시는 1 */
    fun hourDistance(a: Int, b: Int): Int {
        val x = ((a % 24) + 24) % 24
        val y = ((b % 24) + 24) % 24
        val d = Math.abs(x - y)
        return if (d > 12) 24 - d else d
    }

    /** [hour] 가 [windowStart] 에서 시작하는 [window]시간 창 안인지 */
    fun inWindow(hour: Int, windowStart: Int, window: Int = WINDOW_HOURS): Boolean {
        val h = ((hour % 24) + 24) % 24
        val start = ((windowStart % 24) + 24) % 24
        for (k in 0 until window) if ((start + k) % 24 == h) return true
        return false
    }

    /** [nowHour] 기준 ±[radius]시간(기본 ±1시간) 안에 모인 제보 수 */
    fun activeCount(hours: List<Int>, nowHour: Int, radius: Int = NOW_RADIUS): Int =
        hours.count { hourDistance(it, nowHour) <= radius }

    /**
     * 한 자리의 **지금 상태** — 시간대 필터 칩 대신 앱이 스스로 판단해 카드·지도 핀을 강조한다 (v1.0.29).
     *  - [activeCount]: 지금 시각(±[NOW_RADIUS]시간)에 모인 제보 수
     *  - [total]: 이 자리에 모인 제보 수(시간대를 아는 것)
     *  - [peakStart]: 가장 많이 모인 3시간 창의 시작 시각 (모자라면 null)
     */
    data class NowFit(
        val activeCount: Int,
        val total: Int,
        val peakStart: Int?
    ) {
        /** 지금 근처에서 콜이 있었는지 */
        val active: Boolean get() = activeCount > 0

        /** 목록·지도 정렬 점수 — 지금 활발할수록 크다 (같으면 제보가 많은 쪽) */
        val score: Int get() = activeCount * 100 + total

        /** 카드·핀 카드에 붙는 한 줄 — 예) `🟢 지금 활발 · 주로 22시~01시 · 3건` */
        val label: String get() {
            if (total <= 0) return ""
            val parts = ArrayList<String>()
            parts.add(if (active) "🟢 지금 활발" else "지금은 한산")
            peakStart?.let { parts.add("주로 " + SpotTimeBands.label(it)) }
            parts.add("${total}건")
            return parts.joinToString(" · ")
        }

        /** 배지·핀 카드에 쓰는 짧은 표기 — `🟢 지금 활발` / `지금은 한산` */
        val badge: String get() = when {
            total <= 0 -> ""
            active -> "🟢 지금 활발"
            else -> "지금은 한산"
        }
    }

    /** 제보 시간대 목록 [hours] 로 지금 상태를 계산한다 — 시간대를 모르는 자리는 total 0(빈 표기) */
    fun nowFit(hours: List<Int>, nowHour: Int, radius: Int = NOW_RADIUS): NowFit =
        NowFit(
            activeCount = activeCount(hours, nowHour, radius),
            total = hours.size,
            peakStart = peakWindow(hours)
        )

    /** 시간대를 훑는 순서 — 저녁 18시부터 자정, 그다음 새벽·낮 */
    private val EVENING_FIRST: List<Int> = (18..23).toList() + (0..17).toList()
}
