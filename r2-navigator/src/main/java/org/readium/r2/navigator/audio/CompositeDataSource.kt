/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.audio

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener

// FIXME: Not used anymore, to delete after validating the new PublicationDataSource
internal class CompositeDataSource private constructor(private val dataSources: List<Pair<(Uri) -> Boolean, DataSource>>) : DataSource {

    class Factory(dataSourceFactories: Map<(Uri) -> Boolean, DataSource.Factory> = emptyMap()) : DataSource.Factory {

        private var dataSourceFactories = mutableListOf<Pair<(Uri) -> Boolean, DataSource.Factory>>()

        fun bind(dataSourceFactory: DataSource.Factory, accepts: (Uri) -> Boolean = { true }): Factory {
            dataSourceFactories.add(Pair(accepts, dataSourceFactory))
            return this
        }

        override fun createDataSource(): DataSource =
            CompositeDataSource(dataSourceFactories.map { (accepts, factory) -> Pair(accepts, factory.createDataSource()) })

    }

    /** Currently opened data source. */
    private var openedDataSource: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        for ((_, dataSource) in dataSources) {
            dataSource.addTransferListener(transferListener)
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        dataSpec ?: throw IllegalArgumentException("[dataSpec] is required")
        close()

        for ((accept, dataSource) in dataSources) {
            if (accept(dataSpec.uri)) {
                openedDataSource = dataSource
                return dataSource.open(dataSpec)
            }
        }

        throw IllegalArgumentException("Can't find a matching [DataSource] for ${dataSpec.uri}.")
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(openedDataSource).read(target, offset, length)

    override fun getUri(): Uri? =
        requireNotNull(openedDataSource).uri

    override fun close() {
        openedDataSource?.close()
        openedDataSource = null
    }

}
