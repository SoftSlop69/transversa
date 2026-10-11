// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.transversa

import android.net.Uri

data class MediaOutputState(
    val isBusy: Boolean = false,
    val progressPercent: Int = 0,
    val message: String? = null,
    val savedFileName: String? = null,
    val savedFileUri: Uri? = null,
    val hasError: Boolean = false,
)
