/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.extensions

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

inline fun <T1, T2, R> combine(
    ld1: LiveData<T1>,
    ld2: LiveData<T2>,
    crossinline transform: (T1?, T2?) -> R
): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(ld1) {
        result.value = transform(it, ld2.value)
    }
    result.addSource(ld2) {
        result.value = transform(ld1.value, it)
    }
    return result
}

inline fun <T1, T2, T3, R> combine(
    ld1: LiveData<T1>,
    ld2: LiveData<T2>,
    ld3: LiveData<T3>,
    crossinline transform: (T1?, T2?, T3?) -> R
): LiveData<R> {
    val result = MediatorLiveData<R>()
    result.addSource(ld1) {
        result.value = transform(it, ld2.value, ld3.value)
    }
    result.addSource(ld2) {
        result.value = transform(ld1.value, it, ld3.value)
    }
    result.addSource(ld3) {
        result.value = transform(ld1.value, ld2.value, it)
    }
    return result
}
