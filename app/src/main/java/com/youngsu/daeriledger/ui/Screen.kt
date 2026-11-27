package com.youngsu.daeriledger.ui

import android.view.View

/** 하단 탭 하나에 대응하는 화면 */
interface Screen {
    val view: View
    fun refresh()

    /**
     * 화면이 가려졌을 때(다른 탭으로 이동·앱이 뒤로·화면 꺼짐) 불린다.
     * GPS 처럼 계속 켜 두면 배터리를 쓰는 것은 여기서 멈춘다 (v1.0.31 콜밭 위치 갱신).
     */
    fun onPause() {}
}
