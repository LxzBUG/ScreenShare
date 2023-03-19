package org.loka.screensharekit.callback

import org.loka.screensharekit.ErrorInfo

fun interface ErrorCallBack {
    fun onError(error: ErrorInfo)
}