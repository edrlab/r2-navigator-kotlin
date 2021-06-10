/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.media

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Process
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.readium.r2.navigator.extensions.let
import org.readium.r2.navigator.media.extensions.publicationId
import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.shared.extensions.splitAt
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.publication.services.cover
import timber.log.Timber
import kotlin.reflect.KMutableProperty0

@AudiobookNavigator
@OptIn(ExperimentalCoroutinesApi::class)
open class MediaService : MediaBrowserServiceCompat(), CoroutineScope by MainScope() {

    /**
     * @param packageName The package name of the application which is requesting access.
     * @param uid The UID of the application which is requesting access.
     */
    open fun isClientAuthorized(packageName: String, uid: Int): Boolean =
        (uid == Process.myUid())

    open val navigatorActivityIntent: PendingIntent? = null

    open fun onCreatePlayer(mediaSession: MediaSessionCompat, media: PendingMedia): MediaPlayer =
        ExoMediaPlayer(this, mediaSession, media)

    open fun onCurrentLocatorChanged(publication: Publication, publicationId: PublicationId, locator: Locator) {}

    open suspend fun coverOfPublication(publication: Publication, publicationId: PublicationId): Bitmap? =
        publication.cover()

    // We don't support any custom commands by default.
    open fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?): Boolean = false

    open fun onPlayerStopped() {}

    /**
     * Called when a resource failed to be loaded, for example because the Internet connection
     * is offline and the resource is streamed.
     *
     * You should present the exception to the user.
     */
    open fun onResourceLoadFailed(link: Link, error: Resource.Exception) {}

    protected val mediaSession: MediaSessionCompat get() = getMediaSession(this, javaClass)

    private var player: MediaPlayer? = null
        set(value) {
            field?.apply {
                onDestroy()
                listener = null
            }

            field = value?.apply {
                listener = mediaPlayerListener
            }

            navigator.value?.player = value
        }


    private var notificationId: Int? = null
    private var notification: Notification? = null

    private val mediaPlayerListener = object : MediaPlayer.Listener {

        override fun locatorFromMediaId(mediaId: String, extras: Bundle?): Locator? {
            val navigator = navigator.value ?: return null
            val (publicationId, href) = mediaId.splitAt("#")

            if (navigator.publicationId != publicationId) {
                return null
            }

            val locator = (extras?.getParcelable(EXTRA_LOCATOR) as? Locator)
                ?: href?.let { navigator.publication.linkWithHref(it)?.toLocator() }

            if (locator != null && href != null && locator.href != href) {
                Timber.e("Ambiguous playback location provided. HREF `$href` doesn't match locator $locator.")
            }

            return locator
        }

        override suspend fun coverOfPublication(publication: Publication, publicationId: PublicationId): Bitmap? =
            this@MediaService.coverOfPublication(publication, publicationId)

        override fun onNotificationPosted(notificationId: Int, notification: Notification) {
            this@MediaService.notificationId = notificationId
            this@MediaService.notification = notification
            startForeground(notificationId, notification)
        }

        override fun onNotificationCancelled(notificationId: Int) {
            this@MediaService.notificationId = null
            this@MediaService.notification = null
            stopForeground(true)

            if (navigator.value?.isPlaying == false) {
                onPlayerStopped()
            }
        }

        override fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?): Boolean =
            this@MediaService.onCommand(command, args, cb)

        override fun onPlayerStopped() {
            mediaSession.publicationId = null
            player = null
            navigator.value = null
            this@MediaService.onPlayerStopped()
        }

        override fun onResourceLoadFailed(link: Link, error: Resource.Exception) {
            this@MediaService.onResourceLoadFailed(link, error)
        }

    }

    // Service

    override fun onCreate() {
        super.onCreate()

        sessionToken = mediaSession.sessionToken

        mediaSession.run {
            isActive = true
            navigatorActivityIntent?.let(::setSessionActivity)
        }

        launch {
            pendingNavigator.receiveAsFlow().collect {
                player = onCreatePlayer(mediaSession, it.media)
                mediaSession.publicationId = it.media.publicationId
                navigator.value = it.navigator
            }
        }

        launch {
            navigator.collect { it?.player = player }
        }

        launch {
            navigator
                .flatMapLatest { navigator ->
                    navigator ?: return@flatMapLatest emptyFlow<Pair<MediaSessionNavigator, Locator?>>()
                    navigator.currentLocator.map { Pair(navigator, it) }
                }
                .collect { (navigator, locator) ->
                    if (locator != null) {
                        onCurrentLocatorChanged(navigator.publication, navigator.publicationId, locator)
                    }
                }
        }

        launch {
            navigator
                .flatMapLatest { navigator ->
                    navigator?.playback?.map { it.state }
                        ?: flowOf(MediaPlayback.State.Idle)
                }
                .collect {
                    if (it.isPlaying) {
                        let(notificationId, notification) { id, note ->
                            startForeground(id, note)
                        }
                    } else {
                        stopForeground(false)
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        releaseMediaSession()
        navigator.value = null
        player = null
    }

    // MediaBrowserServiceCompat

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (!isClientAuthorized(packageName = clientPackageName, uid = clientUid)) {
            return null
        }

        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    companion object {

        const val EVENT_PUBLICATION_CHANGED = "org.readium.r2.navigator.EVENT_PUBLICATION_CHANGED"
        const val EXTRA_PUBLICATION_ID = "org.readium.r2.navigator.EXTRA_PUBLICATION_ID"

        @Volatile private var connection: Connection? = null
        @Volatile private var mediaSession: MediaSessionCompat? = null

        private val navigator = MutableStateFlow<MediaSessionNavigator?>(null)
        private val pendingNavigator = Channel<PendingNavigator>(Channel.CONFLATED)

        fun connect(context: Context, serviceClass: Class<*> = MediaService::class.java): Connection =
            createIfNull(this::connection, this) {
                Connection(context, serviceClass)
            }

        private fun getMediaSession(context: Context, serviceClass: Class<*>): MediaSessionCompat =
            createIfNull(this::mediaSession, this) {
                MediaSessionCompat(context, /* log tag */ serviceClass.simpleName)
            }

        private fun releaseMediaSession() {
            mediaSession?.apply {
                isActive = false
                release()
            }
            mediaSession = null
        }

    }

    class Connection internal constructor(private val context: Context, private val serviceClass: Class<*>) {

        val currentNavigator: StateFlow<MediaSessionNavigator?> get() = navigator

        fun getNavigator(publication: Publication, publicationId: PublicationId, initialLocator: Locator?): MediaSessionNavigator {
            context.startService(Intent(context, serviceClass))

            navigator.value
                ?.takeIf { it.publicationId == publicationId }
                ?.let { return it }

            val navigator = MediaSessionNavigator(publication, publicationId, getMediaSession(context, serviceClass).controller)
            pendingNavigator.offer(PendingNavigator(
                navigator = navigator,
                media = PendingMedia(publication, publicationId, locator = initialLocator ?: publication.readingOrder.first().toLocator())
            ))

            return navigator
        }

    }

    private class PendingNavigator(val navigator: MediaSessionNavigator, val media: PendingMedia)

}

// FIXME: Move to r2-shared
internal fun <T> createIfNull(property: KMutableProperty0<T?>, owner: Any, factory: () -> T): T =
    property.get() ?: synchronized(owner) {
        property.get() ?: factory().also {
            property.set(it)
        }
    }

private const val ROOT_ID = "/"
private const val EXTRA_LOCATOR = "locator"
