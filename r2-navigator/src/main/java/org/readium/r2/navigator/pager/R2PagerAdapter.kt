/**
Copyright 2018 Readium Foundation. All rights reserved.
Use of this source code is governed by a BSD-style license which is detailed in the LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.pager

import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager


class R2PagerAdapter internal constructor(fm: FragmentManager, private val mItems: List<String>, val title: String) : R2FragmentPagerAdapter(fm) {

    private val TAG = this::class.java.simpleName

    override fun getItem(position: Int): Fragment {
        return R2PageFragment.newInstance(mItems[position], title)
    }

    override fun getCount(): Int {
        return mItems.size
    }

}