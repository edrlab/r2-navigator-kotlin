/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.media

import android.app.Notification
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ResultReceiver
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId

interface MediaPlayer {

    interface Listener {

        fun locatorFromMediaId(mediaId: String, extras: Bundle?): Locator?

        suspend fun coverOfPublication(publication: Publication, publicationId: PublicationId): Bitmap?

        fun onNotificationPosted(notificationId: Int, notification: Notification)
        fun onNotificationCancelled(notificationId: Int)
        fun onCommand(command: String, args: Bundle?, cb: ResultReceiver?): Boolean

        fun onPlayerStopped()

        /**
         * Called when a resource failed to be loaded, for example because the Internet connection
         * is offline and the resource is streamed.
         */
        fun onResourceLoadFailed(link: Link, error: Resource.Exception)

    }

    // FIXME: ExoPlayer's media session connector doesn't handle the playback speed yet, so I used a custom solution until we create our own connector
    var playbackRate: Double

    var listener: Listener?

    fun onDestroy()

}