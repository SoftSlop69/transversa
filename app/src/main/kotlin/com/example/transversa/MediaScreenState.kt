// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.net.Uri
import com.transversa.core.media.MediaDescriptor

sealed interface MediaScreenState {
    data object Empty : MediaScreenState
    data class Inspecting(val uri: Uri) : MediaScreenState
    data class Ready(val media: MediaDescriptor) : MediaScreenState
    data class Failed(val uri: Uri, val permissionRevoked: Boolean) : MediaScreenState
}
