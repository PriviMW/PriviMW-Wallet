package com.privimemobile.dapp

import android.content.Intent
import android.net.Uri

/** URIs from a file-chooser activity result, or null when empty/cancelled. */
fun Intent?.selectedUrisFromChooserResult(): Array<Uri>? {
    if (this == null) return null
    clipData?.let { clip ->
        if (clip.itemCount == 0) return null
        return Array(clip.itemCount) { clip.getItemAt(it).uri }
    }
    return data?.let { arrayOf(it) }
}
