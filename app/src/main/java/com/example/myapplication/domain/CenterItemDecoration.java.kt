package com.example.myapplication.domain

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration


class CenterItemDecoration : ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect, view: View,
        parent: RecyclerView, state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val itemCount = state.itemCount
        if (itemCount == 0) return

        val position = parent.getChildAdapterPosition(view)
        val totalWidth = parent.width
        val childWidth = view.width


        // 计算总内容宽度（包括所有卡片宽度）
        val contentWidth = childWidth * itemCount


        // 计算左右边距（使内容居中）
        val sidePadding = (totalWidth - contentWidth) / 2


        // 第一个元素：左边距 = sidePadding，右边距 = 0
        if (position == 0) {
            outRect.left = sidePadding
            outRect.right = 0
        } else if (position == itemCount - 1) {
            outRect.left = 0
            outRect.right = sidePadding
        } else {
            outRect.left = 0
            outRect.right = 0
        }
    }
}