package com.youngsu.daeriledger.data

import android.content.Context
import com.youngsu.daeriledger.R

/**
 * 앱이 보여 주는 전체 콜밭.
 *  - [Hotspots.CURATED] : 직접 정리한 기본 콜밭(피크 시간대·특징 메모 포함)
 *  - res/raw/callspots.tsv : 기사님이 쓰시는 카카오맵 공개 즐겨찾기 3개 그룹
 *    (`대리 콜 포인트1` · `대리 콜 포인트2` · `탁송 발주`)에서 중복을 정리해 옮긴 목록
 *
 * 파일은 처음 한 번만 읽고 이후에는 같은 목록을 돌려준다. (1,500곳 남짓, 수 ms)
 */
object CallSpots {

    @Volatile
    private var cache: List<Hotspot>? = null

    fun all(context: Context): List<Hotspot> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = Hotspots.CURATED + loadRaw(context)
            cache = loaded
            return loaded
        }
    }

    /** callspots.tsv 한 줄: id · 출처 · 이름 · 시도 · 지역 · 위도 · 경도 · 주소 */
    private fun loadRaw(context: Context): List<Hotspot> {
        val out = ArrayList<Hotspot>(1400)
        runCatching {
            context.applicationContext.resources.openRawResource(R.raw.callspots)
                .bufferedReader()
                .useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val p = line.split('\t')
                        if (p.size < 8) continue
                        val lat = p[5].toDoubleOrNull() ?: continue
                        val lng = p[6].toDoubleOrNull() ?: continue
                        out.add(
                            Hotspot(
                                id = p[0],
                                name = p[2],
                                area = p[4],
                                city = p[3],
                                lat = lat,
                                lng = lng,
                                peak = "",
                                tip = p[7],
                                source = p[1]
                            )
                        )
                    }
                }
        }
        return out
    }
}
