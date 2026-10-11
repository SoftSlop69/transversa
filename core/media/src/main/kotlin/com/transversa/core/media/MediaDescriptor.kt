// SPDX-License-Identifier: GPL-3.0-or-later
package com.transversa.core.media

import android.net.Uri
import com.transversa.core.model.MediaKind

data class MediaDescriptor(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val kind: MediaKind,
    val sizeBytes: Long?,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val hasAudio: Boolean = false,
    val rotationDegrees: Int = 0,
)
