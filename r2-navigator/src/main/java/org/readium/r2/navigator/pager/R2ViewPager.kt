package org.readium.r2.navigator.pager

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.duolingo.open.rtlviewpager.RtlViewPager

class R2ViewPager : RtlViewPager {

    private val TAG = this::class.java.simpleName

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    override fun setCurrentItem(item: Int, smoothScroll: Boolean) {
        super.setCurrentItem(item, false)
    }

    override fun setCurrentItem(item: Int) {
        super.setCurrentItem(item, false)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        // Never allow swiping to switch between pages
        return false
    }

}