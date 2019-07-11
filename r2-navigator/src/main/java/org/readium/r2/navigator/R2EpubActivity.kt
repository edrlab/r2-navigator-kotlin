/*
 * Module: r2-navigator-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.navigator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.navigator.extensions.layoutDirectionIsRTL
import org.readium.r2.navigator.interfaces.R2EpubPageFragmentListener
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.shared.*
import java.net.URI
import kotlin.coroutines.CoroutineContext


open class R2EpubActivity : AppCompatActivity(), CoroutineScope, R2EpubPageFragmentListener {
    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override lateinit var preferences: SharedPreferences
    override lateinit var resourcePager: R2ViewPager
    override lateinit var publication: Publication
    override lateinit var publicationIdentifier: String
    override var allowToggleActionBar = true

    lateinit var resourcesSingle: ArrayList<Pair<Int, String>>
    lateinit var resourcesDouble: ArrayList<Triple<Int, String, String>>
    lateinit var publicationPath: String

    protected lateinit var epubName: String

    var pagerPosition = 0

    private var currentPagerPosition: Int = 0
    lateinit var adapter: R2PagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_r2_viewpager)

        preferences = getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)
        resourcePager = findViewById(R.id.resourcePager)
        resourcesSingle = ArrayList()
        resourcesDouble = ArrayList()

        publicationPath = intent.getStringExtra("publicationPath")
        publication = intent.getSerializableExtra("publication") as Publication
        epubName = intent.getStringExtra("epubName")
        publicationIdentifier = publication.metadata.identifier

        title = null

        val port = preferences.getString("$publicationIdentifier-publicationPort", 0.toString())?.toInt()

        // TODO needs work, currently showing two resources for fxl, needs to understand which two resources, left & right, or only right etc.
        var doublePageIndex = 0
        var doublePageLeft = ""
        var doublePageRight = ""
        var resourceIndexDouble = 0

        for ((resourceIndexSingle, spineItem) in publication.readingOrder.withIndex()) {
            val uri: String = if (URI(publicationPath).isAbsolute) {
                if (URI(spineItem.href).isAbsolute) {
                    spineItem.href!!
                } else {
                    publicationPath + spineItem.href
                }
            } else {

                //                uri = applicationContext.getExternalFilesDir(null).path + "/" + epubName + spineItem.href
                "$BASE_URL:$port" + "/" + epubName + spineItem.href
            }
            resourcesSingle.add(Pair(resourceIndexSingle, uri))

            // add first page to the right,
            if (resourceIndexDouble == 0) {
                doublePageLeft = ""
                doublePageRight = uri
                resourcesDouble.add(Triple(resourceIndexDouble, doublePageLeft, doublePageRight))
                resourceIndexDouble++
            } else {
                // add double pages, left & right
                if (doublePageIndex == 0) {
                    doublePageLeft = uri
                    doublePageIndex = 1
                } else {
                    doublePageRight = uri
                    doublePageIndex = 0
                    resourcesDouble.add(Triple(resourceIndexDouble, doublePageLeft, doublePageRight))
                    resourceIndexDouble++
                }
            }
        }

        // add last page if there is only a left page remaining
        if (doublePageIndex == 1) {
            doublePageIndex = 0
            resourcesDouble.add(Triple(resourceIndexDouble, doublePageLeft, ""))
        }

        if (publication.metadata.rendition.layout == RenditionLayout.Reflowable) {
            adapter = R2PagerAdapter(supportFragmentManager, resourcesSingle, publication.metadata.title, Publication.TYPE.EPUB, publicationPath, this)
        } else {
            adapter = when (preferences.getInt(COLUMN_COUNT_REF, 0)) {
                1 -> {
                    R2PagerAdapter(supportFragmentManager, resourcesSingle, publication.metadata.title, Publication.TYPE.FXL, publicationPath, this)
                }
                2 -> {
                    R2PagerAdapter(supportFragmentManager, resourcesDouble, publication.metadata.title, Publication.TYPE.FXL, publicationPath, this)
                }
                else -> {
                    // TODO based on device
                    // TODO decide if 1 page or 2 page
                    R2PagerAdapter(supportFragmentManager, resourcesSingle, publication.metadata.title, Publication.TYPE.FXL, publicationPath, this)
                }
            }
        }

        resourcePager.adapter = adapter
        resourcePager.direction = publication.metadata.direction

        if (publication.cssStyle == PageProgressionDirection.rtl.name) {
            resourcePager.direction = PageProgressionDirection.rtl.name
        }

        val index = preferences.getInt("$publicationIdentifier-document", 0)
        resourcePager.currentItem = index
        currentPagerPosition = index

        resourcePager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) {
                // Do nothing
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Do nothing
            }

            override fun onPageSelected(position: Int) {
                if (publication.metadata.rendition.layout == RenditionLayout.Reflowable) {
//                    resourcePager.disableTouchEvents = true
                }
                pagerPosition = 0
                val currentFragment = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment
                if (preferences.getBoolean(SCROLL_REF, false)) {
                    if (currentPagerPosition < position) {
                        // handle swipe LEFT
                        currentFragment?.webView?.scrollToStart()
                    } else if (currentPagerPosition > position) {
                        // handle swipe RIGHT
                        currentFragment?.webView?.scrollToEnd()
                    }
                } else {
                    if (currentPagerPosition < position) {
                        // handle swipe LEFT
                        currentFragment?.webView?.setCurrentItem(0, false)
                    } else if (currentPagerPosition > position) {
                        // handle swipe RIGHT
                        currentFragment?.webView?.setCurrentItem(currentFragment.webView.numPages - 1, false)
                    }
                }
                storeDocumentIndex()
                currentPagerPosition = position // Update current position
            }

        })

        storeDocumentIndex()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                pagerPosition = 0

                val locator = data.getSerializableExtra("locator") as Locator

                // Set the progression fetched
                storeProgression(locator.locations)

                // href is the link to the page in the toc
                var href = locator.href

                if (href.indexOf("#") > 0) {
                    href = href.substring(0, href.indexOf("#"))
                }

                fun setCurrent(resources: ArrayList<*>) {
                    for (resource in resources) {
                        if (resource is Pair<*, *>) {
                            resource as Pair<Int, String>
                            if (resource.second.endsWith(href)) {
                                if (resourcePager.currentItem == resource.first) {
                                    // reload webview if it has an anchor
                                    val currentFragent = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment
                                    locator.locations?.fragment?.let {
                                        var anchor = it
                                        if (anchor.startsWith("#")) {
                                        } else {
                                            anchor = "#$anchor"
                                        }
                                        val goto = resource.second + anchor
                                        currentFragent?.webView?.loadUrl(goto)
                                    } ?: run {
                                        currentFragent?.webView?.loadUrl(resource.second)
                                    }
                                } else {
                                    resourcePager.currentItem = resource.first
                                }
                                storeDocumentIndex()
                                break
                            }
                        } else {
                            resource as Triple<Int, String, String>
                            if (resource.second.endsWith(href) || resource.third.endsWith(href)) {
                                resourcePager.currentItem = resource.first
                                storeDocumentIndex()
                                break
                            }
                        }
                    }
                }

                resourcePager.adapter = adapter

                if (publication.metadata.rendition.layout == RenditionLayout.Reflowable) {
                    setCurrent(resourcesSingle)
                } else {
                    when (preferences.getInt(COLUMN_COUNT_REF, 0)) {
                        1 -> {
                            setCurrent(resourcesSingle)
                        }
                        2 -> {
                            setCurrent(resourcesDouble)
                        }
                        else -> {
                            // TODO based on device
                            // TODO decide if 1 page or 2 page
                            setCurrent(resourcesSingle)
                        }
                    }
                }

                if (supportActionBar!!.isShowing) {
                    resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            or View.SYSTEM_UI_FLAG_IMMERSIVE)
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * storeDocumentIndex() : save in the preference the last spine item
     */
    fun storeDocumentIndex() {
        val documentIndex = resourcePager.currentItem
        preferences.edit().putInt("$publicationIdentifier-document", documentIndex).apply()
    }

    /**
     * storeProgression() : save in the preference the last progression in the spine item
     */
    override fun storeProgression(locations: Locations?) {
        locations?.let {
            storeDocumentIndex()
            val publicationIdentifier = publication.metadata.identifier
            preferences.edit().putString("$publicationIdentifier-documentLocations", it.toJSON().toString()).apply()
        }
    }

    /**
     * onPageChanged() : when page changed inside webview
     */
    override fun onPageChanged(index: Int, numPages: Int, url: String?) {
        // optional
    }

    /**
     * onPageEnded() : when page ended inside webview
     */
    override fun onPageEnded(end: Boolean) {
        // optional
    }

    /**
     * toggleActionBar() : toggle actionbar when touch center
     */
    override fun toggleActionBar() {
        if (allowToggleActionBar) {
            launch {
                if (supportActionBar!!.isShowing) {
                    resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                            or View.SYSTEM_UI_FLAG_IMMERSIVE)
                } else {
                    resourcePager.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
            }
        }
    }

    /**
     * nextResource() : return next resource
     */
    override fun nextResource(smoothScroll: Boolean) {
        launch {
            pagerPosition = 0

            if (resourcePager.currentItem < resourcePager.adapter!!.count - 1) {
                resourcePager.setCurrentItem(resourcePager.currentItem + 1, smoothScroll)

                val currentFragent = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment

                if (layoutDirectionIsRTL() || publication.metadata.direction == PageProgressionDirection.rtl.name) {
                    // The view has RTL layout
                    currentFragent?.webView?.let {
                        currentFragent.webView.progression = 1.0
                        currentFragent.webView.setCurrentItem(currentFragent.webView.numPages - 1, false)
                    }
                } else {
                    // The view has LTR layout
                    currentFragent?.webView?.let {
                        currentFragent.webView.progression = 0.0
                        currentFragent.webView.setCurrentItem(0, false)
                    }
                }

                storeDocumentIndex()
            }
        }
    }

    /**
     * nextResource() : return previous resource
     */
    override fun previousResource(smoothScroll: Boolean) {
        launch {
            pagerPosition = 0

            if (resourcePager.currentItem > 0) {
                resourcePager.setCurrentItem(resourcePager.currentItem - 1, smoothScroll)

                val currentFragent = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment

                if (layoutDirectionIsRTL() || publication.metadata.direction == PageProgressionDirection.rtl.name) {
                    // The view has RTL layout
                    currentFragent?.webView?.let {
                        currentFragent.webView.progression = 0.0
                        currentFragent.webView.setCurrentItem(0, false)
                    }
                } else {
                    // The view has LTR layout
                    currentFragent?.webView?.let {
                        currentFragent.webView.progression = 1.0
                        currentFragent.webView.setCurrentItem(currentFragent.webView.numPages - 1, false)
                    }
                }

                storeDocumentIndex()
            }
        }
    }

}

