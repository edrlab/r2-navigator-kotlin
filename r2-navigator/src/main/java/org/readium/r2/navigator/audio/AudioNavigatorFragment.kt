/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.audio

import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.MediaNavigator
import org.readium.r2.navigator.R
import org.readium.r2.navigator.extensions.formatElapsedTime
import org.readium.r2.navigator.extensions.let
import org.readium.r2.navigator.extensions.viewById
import org.readium.r2.navigator.util.createFragmentFactory
import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.shared.publication.services.cover
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@AudiobookNavigator
@OptIn(ExperimentalTime::class)
class AudioNavigatorFragment(
    private val mediaNavigator: MediaNavigator,
    @LayoutRes private val layoutId: Int = R.layout.r2_audio_fragment
) : Fragment(layoutId), MediaNavigator by mediaNavigator {

    private val coverView: ImageView? by viewById(R.id.r2_coverView)
    private val timelineBar: SeekBar? by viewById(R.id.r2_timelineBar)
    private val positionTextView: TextView? by viewById(R.id.r2_timelinePosition)
    private val durationTextView: TextView? by viewById(R.id.r2_timelineDuration)
    private val playPauseButton: View? by viewById(R.id.r2_playPause)
    private val playButton: View? by viewById(R.id.r2_play)
    private val pauseButton: View? by viewById(R.id.r2_pause)
    private val skipForwardButton: View? by viewById(R.id.r2_skipForward)
    private val skipBackwardButton: View? by viewById(R.id.r2_skipBackward)

    private var isSeeking = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            let(coverView, publication.cover()) {
                view, cover -> view.setImageBitmap(cover)
            }
        }

        mediaNavigator.playback.asLiveData().observe(viewLifecycleOwner, Observer { playback ->
            playPauseButton?.isSelected = playback.isPlaying
            
            with(playback.timeline) {
                if (!isSeeking) {
                    timelineBar?.max = duration?.inSeconds?.toInt() ?: 0
                    timelineBar?.progress = position.inSeconds.toInt()
                    buffered?.let { timelineBar?.secondaryProgress = it.inSeconds.toInt() }
                }
                positionTextView?.text = position.formatElapsedTime()
                durationTextView?.text = duration?.formatElapsedTime() ?: ""
            }
        })

        timelineBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                mediaNavigator.seekTo(progress.seconds)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                isSeeking = false
            }

        })

        playButton?.setOnClickListener { play() }
        pauseButton?.setOnClickListener { pause() }
        playPauseButton?.setOnClickListener { playPause() }
        skipForwardButton?.setOnClickListener { goForward() }
        skipBackwardButton?.setOnClickListener { goBackward() }

//            next_chapter!!.setOnClickListener {
//                goForward(false) {}
//            }
//
//            prev_chapter!!.setOnClickListener {
//                goBackward(false) {}
//            }
    }

    override fun onResume() {
        super.onResume()
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
    }

    companion object {

        /**
         * Creates a factory for a [AudioNavigatorFragment].
         *
         * @param mediaNavigator The underlying chromeless navigator handling media playback.
         * @param layoutId ID of the layout to use for the interface.
         */
        fun createFactory(mediaNavigator: MediaNavigator, @LayoutRes layoutId: Int = R.layout.r2_audio_fragment): FragmentFactory =
            createFragmentFactory { AudioNavigatorFragment(mediaNavigator, layoutId) }

    }

}