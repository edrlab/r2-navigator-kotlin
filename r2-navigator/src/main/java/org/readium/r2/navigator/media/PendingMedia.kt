/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.media

import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId

@AudiobookNavigator
data class PendingMedia(
    val publication: Publication,
    val publicationId: PublicationId,
    val locator: Locator
)
