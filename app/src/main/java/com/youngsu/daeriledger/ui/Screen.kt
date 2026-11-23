package com.youngsu.daeriledger.ui

import android.view.View

/** 하단 탭 하나에 대응하는 화면 */
interface Screen {
    val view: View
    fun refresh()
}
