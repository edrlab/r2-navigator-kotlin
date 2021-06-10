/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator

import android.graphics.PointF
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.media.MediaPlayback
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.ReadingProgression
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Base interface for a navigator rendering a publication.
 *
 * A few points to keep in mind when implementing this interface:
 *
 * - **The navigator should have a minimal UX** and be focused only on browsing and interacting with
 *   the document. However, it offers a rich API to build a user interface around it.
 * - **The last read page (progression) should not be persisted and restored by the navigator.**
 *   Instead, the reading app will save the [Locator] reported by the navigator in [currentLocator],
 *   and provide the initial location when creating the navigator.
 * - **User accessibility settings should override the behavior when needed** (eg. disabling
 *   animated transition, even when requested by the caller).
 * - **The navigator is the single source of truth for the current location.** So for example, the
 *   TTS should observe the position from the navigator instead of having the reading app move
 *   manually both the navigator and the TTS reader when the user skips forward.
 * - **The navigator should only provide a minimal gestures/interactions set.** For example,
 *   scrolling through a web view or zooming a fixed image is expected from the user. But additional
 *   interactions such as tapping/clicking the edge of the page to skip to the next one should be
 *   implemented by the reading app, and not the navigator.
 */
interface Navigator {

    /**
     * Publication rendered by this navigator.
     */
    val publication: Publication

    /**
     * Current location in the publication.
     *
     * Reading apps can save the last read position by observing the [currentLocator].
     */
    val currentLocator: StateFlow<Locator>

    /**
     * Jumps to a [locator], for example from a position or annotation locator.
     */
    fun go(locator: Locator, animated: Boolean = false, completion: () -> Unit = {}): Boolean

    /**
     * Jumps to a [link], for example from the reading order or the table of contents.
     */
    fun go(link: Link, animated: Boolean = false, completion: () -> Unit = {}): Boolean

    /**
     * Moves forward by one step (eg. a page or n seconds) in the reading progression direction.
     */
    fun goForward(animated: Boolean = false, completion: () -> Unit = {}): Boolean

    /**
     * Moves backward by one step (eg. a page or n seconds) in the reading progression direction.
     */
    fun goBackward(animated: Boolean = false, completion: () -> Unit = {}): Boolean

    interface Listener {
    }

    @Deprecated("Use [currentLocator] instead", ReplaceWith("currentLocator.value"))
    val currentLocation: Locator? get() = currentLocator.value
    @Deprecated("Use [VisualNavigator.Listener] instead", ReplaceWith("VisualNavigator.Listener"))
    interface VisualListener : VisualNavigator.Listener

}

interface NavigatorDelegate {
    @Deprecated("Observe [currentLocator] instead")
    fun locationDidChange(navigator: Navigator? = null, locator: Locator) {}
}


/**
 * A navigator rendering the publication visually on the screen, and allowing user gestures.
 */
interface VisualNavigator : Navigator {

    /**
     * The current reading progression calculated from the one described in the [Publication]
     * metadata, the current language, etc.
     */
    val readingProgression: ReadingProgression

    /**
     * Moves by one step (eg. a page) to the left. It can be forward or backward, depending on the
     * reading progression direction.
     *
     * This should be used to implement left edge taps by the reading app.
     */
    fun goLeft(animated: Boolean = false, completion: () -> Unit = {}): Boolean {
        return when (readingProgression) {
            ReadingProgression.LTR, ReadingProgression.TTB, ReadingProgression.AUTO ->
                goBackward(animated = animated, completion = completion)

            ReadingProgression.RTL, ReadingProgression.BTT ->
                goForward(animated = animated, completion = completion)
        }
    }

    /**
     * Moves by one step (eg. a page) to the right. It can be forward or backward, depending on the
     * reading progression direction.
     *
     * This should be used to implement right edge taps by the reading app.
     */
    fun goRight(animated: Boolean = false, completion: () -> Unit = {}): Boolean {
        return when (readingProgression) {
            ReadingProgression.LTR, ReadingProgression.TTB, ReadingProgression.AUTO ->
                goForward(animated = animated, completion = completion)

            ReadingProgression.RTL, ReadingProgression.BTT ->
                goBackward(animated = animated, completion = completion)
        }
    }

    interface Listener : Navigator.Listener {

        /**
         * Called when the user tapped the content, but nothing handled the event internally (eg.
         * by following an internal link).
         *
         * Can be used in the reading app to toggle the navigation bars, or switch to the
         * previous/next page if the tapped occurred on the edges.
         */
        fun onTap(point: PointF): Boolean = false

    }
}


/**
 * A navigator rendering an audio or video publication.
 */
@OptIn(ExperimentalTime::class)
interface MediaNavigator : Navigator {

    /**
     * Current playback information.
     */
    val playback: Flow<MediaPlayback>

    /**
     * Indicates whether the navigator is currently playing.
     */
    val isPlaying: Boolean

    /**
     * Sets the speed of the media playback.
     *
     * Normal speed is 1.0 and 0.0 is incorrect.
     */
    fun setPlaybackRate(rate: Double)

    /**
     * Resumes or start the playback at the current location.
     */
    fun play()

    /**
     * Pauses the playback.
     */
    fun pause()

    /**
     * Toggles the playback.
     * Can be useful as a handler for play/pause button.
     */
    fun playPause()

    /**
     * Stops the playback.
     *
     * Compared to [pause], the navigator may clear its state in whatever way is appropriate. For
     * example, recovering a player's resources.
     */
    fun stop()

    /**
     * Seeks to the given time in the current resource.
     */
    fun seekTo(position: Duration)

    /**
     * Seeks relatively from the current position in the current resource.
     */
    fun seekRelative(offset: Duration)

}
