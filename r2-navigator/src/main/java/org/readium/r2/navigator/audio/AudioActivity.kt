/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.audio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.readium.r2.navigator.MediaNavigator
import org.readium.r2.navigator.R
import org.readium.r2.navigator.media.*
import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.shared.extensions.getPublicationOrNull
import org.readium.r2.shared.extensions.putPublication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId

@AudiobookNavigator
open class AudioActivity : AppCompatActivity() {

    protected lateinit var publication: Publication
    protected lateinit var publicationId: PublicationId

    protected val mediaServiceClass: Class<MediaService> get() = MediaService::class.java

    private val mediaService by lazy { MediaService.connect(this, mediaServiceClass) }

    val navigator: MediaNavigator get() =
        supportFragmentManager.findFragmentById(R.id.audio_navigator) as MediaNavigator

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val intentPublication = intent.getPublicationOrNull(this)
        val intentPublicationId = intent.getStringExtra(EXTRA_LOCATOR)
        val initialLocator = intent.getParcelableExtra("locator") as? Locator

        val mediaNavigator =
            if (intentPublication != null && intentPublicationId != null) mediaService.getNavigator(intentPublication, intentPublicationId, initialLocator)
            else mediaService.currentNavigator.value
                ?: run {
                    finish()
                    return
                }

        publication = mediaNavigator.publication
        publicationId = mediaNavigator.publicationId
        mediaNavigator.play()

        supportFragmentManager.fragmentFactory = AudioNavigatorFragment.createFactory(mediaNavigator)

        super.onCreate(savedInstanceState)

        setContentView(R.layout.r2_audio_activity)
    }

    override fun finish() {
        setResult(Activity.RESULT_OK, intent)
        super.finish()
    }

    companion object {

        private const val EXTRA_PUBLICATION_ID = "publicationId"
        private const val EXTRA_LOCATOR = "locator"

        fun createIntent(context: Context, publication: Publication, publicationId: PublicationId, initialLocator: Locator?): Intent = Intent(context, AudioActivity::class.java).apply {
            putPublication(publication)
            putExtra(EXTRA_PUBLICATION_ID, publicationId)
            putExtra(EXTRA_LOCATOR, initialLocator)
        }

    }

}
