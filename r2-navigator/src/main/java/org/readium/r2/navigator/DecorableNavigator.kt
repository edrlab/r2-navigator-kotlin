/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.ColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.readium.r2.shared.JSONable
import org.readium.r2.shared.publication.Locator
import kotlin.reflect.KClass

/**
 * A navigator able to render arbitrary decorations over a publication.
 */
interface DecorableNavigator {

    /**
     * Declares the current state of the decorations in the given decoration [group].
     *
     * The Navigator will decide when to actually render each decoration efficiently. Your only
     * responsibility is to submit the updated list of decorations when there are changes.
     * Name each decoration group as you see fit. A good practice is to use the name of the feature
     * requiring decorations, e.g. annotation, search, tts, etc.
     */
    suspend fun applyDecorations(decorations: List<Decoration>, group: String)

    /**
     * Indicates whether the Navigator supports the given decoration [style] class.
     *
     * You should check whether the Navigator supports drawing the decoration styles required by a
     * particular feature before enabling it. For example, underlining an audiobook does not make
     * sense, so an Audiobook Navigator would not support the `underline` decoration style.
     */
    fun <T: Decoration.Style> supportsDecorationStyle(style: KClass<T>): Boolean
}

/**
 * A decoration is a user interface element drawn on top of a publication. It associates a [style]
 * to be rendered with a discrete [locator] in the publication.
 *
 * For example, decorations can be used to draw highlights, images or buttons.
 *
 * @param id An identifier for this decoration. It must be unique in the group the decoration is applied to.
 * @param locator Location in the publication where the decoration will be rendered.
 * @param style Declares the look and feel of the decoration.
 * @param extras Additional context data specific to a reading app. Readium does not use it.
 */
@Parcelize
data class Decoration(
    val id: DecorationId,
    val locator: Locator,
    val style: Style,
    val extras: Bundle = Bundle(),
) : JSONable, Parcelable {

    /**
     * The Decoration Style determines the look and feel of a decoration once rendered by a
     * Navigator.
     *
     * It is media type agnostic, meaning that each Navigator will translate the style into a set of
     * rendering instructions which makes sense for the resource type.
     */
    interface Style : Parcelable {
        @Parcelize
        class Highlight(@ColorInt val tint: Int? = null) : Style
        @Parcelize
        class Underline(@ColorInt val tint: Int? = null) : Style
    }

    override fun toJSON() = JSONObject().apply {
        put("id", id)
        put("locator", locator.toJSON())
        putOpt("style", style::class.qualifiedName)
    }
}

/** Unique identifier for a decoration. */
typealias DecorationId = String

/** Represents an atomic change in a list of [Decoration] objects. */
sealed class DecorationChange {
    data class Added(val decoration: Decoration) : DecorationChange()
    data class Updated(val decoration: Decoration) : DecorationChange()
    data class Moved(val id: DecorationId, val fromPosition: Int, val toPosition: Int) : DecorationChange()
    data class Removed(val id: DecorationId) : DecorationChange()
}

/**
 * Lists the atomic changes between the receiver list and the [target] list of [Decoration] objects.
 *
 * The changes need to be applied in the same order, one by one.
 */
suspend fun List<Decoration>.changesByHref(target: List<Decoration>): Map<String, List<DecorationChange>> = withContext(Dispatchers.IO) {
    val source = this@changesByHref
    val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
        override fun getOldListSize(): Int = source.size
        override fun getNewListSize(): Int = target.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            source[oldItemPosition].id == target[newItemPosition].id

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            source[oldItemPosition] == target[newItemPosition]
    })

    val changes = mutableMapOf<String, List<DecorationChange>>()

    fun registerChange(change: DecorationChange, locator: Locator) {
        val resourceChanges = changes[locator.href] ?: emptyList()
        changes[locator.href] = resourceChanges + change
    }

    result.dispatchUpdatesTo(object : ListUpdateCallback {
        override fun onInserted(position: Int, count: Int) {
            val decoration = target[position]
            registerChange(DecorationChange.Added(decoration), decoration.locator)
        }

        override fun onRemoved(position: Int, count: Int) {
            val decoration = source[position]
            registerChange(DecorationChange.Removed(decoration.id), decoration.locator)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            val decoration = target[toPosition]
            registerChange(DecorationChange.Moved(decoration.id, fromPosition = fromPosition, toPosition = toPosition), decoration.locator)
        }

        override fun onChanged(position: Int, count: Int, payload: Any?) {
            val decoration = target[position]
            registerChange(DecorationChange.Updated(decoration), decoration.locator)
        }
    })

    changes
}