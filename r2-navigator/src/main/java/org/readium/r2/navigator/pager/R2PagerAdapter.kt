/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator.pager

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication


class R2PagerAdapter(val fm: FragmentManager, l: Lifecycle, private val resources: List<Any>, private val title: String, private val type: Publication.TYPE, private val publicationPath: String = "") : FragmentStateAdapter(fm, l) {

//    private var currentFragment: Fragment? = null
//    private var previousFragment: Fragment? = null
//    private var nextFragment: Fragment? = null
//
//    fun getCurrentFragment(): Fragment? {
//        return currentFragment
//    }
//
//    fun getPreviousFragment(): Fragment? {
//        return previousFragment
//    }
//
//    fun getNextFragment(): Fragment? {
//        return nextFragment
//    }

    override fun createFragment(position: Int): Fragment =
            when (type) {
                Publication.TYPE.EPUB, Publication.TYPE.WEBPUB, Publication.TYPE.AUDIO -> {
                    val single = resources[position] as Pair<Int, String>
                    R2EpubPageFragment.newInstance(single.second)
                }
                Publication.TYPE.FXL -> {
                    if (resources[position] is Triple<*, *, *>) {
                        val double = resources[position] as Triple<Int, String, String>
                        R2FXLPageFragment.newInstance(double.second, double.third)
                    } else {
                        val single = resources[position] as Pair<Int, String>
                        R2FXLPageFragment.newInstance(single.second)
                    }
                }
                Publication.TYPE.CBZ ->
                    fm.fragmentFactory
                        .instantiate(ClassLoader.getSystemClassLoader(), R2CbzPageFragment::class.java.name)
                        .also {
                            it.arguments = Bundle().apply {
                                putParcelable("link", resources[position] as Link)
                        }
                    }
                Publication.TYPE.DiViNa -> TODO()
            }

    override fun getItemCount(): Int {
        return resources.size
    }

}
