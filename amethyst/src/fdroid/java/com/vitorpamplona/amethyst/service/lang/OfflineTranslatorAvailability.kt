/*
 * Copyright (c) 2026 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.lang

import android.content.Context
import android.content.Intent

/** Discovery only. The Binder/AIDL client is intentionally not vendored until its IPC contract is
 * available under a license compatible with Amethyst's MIT license.
 */
object OfflineTranslatorAvailability {
    const val PACKAGE_NAME = "dev.davidv.translator"
    const val SERVICE_ACTION = "dev.davidv.translator.ITranslationService"

    @Suppress("DEPRECATION")
    fun isServiceAvailable(context: Context): Boolean {
        val intent = Intent(SERVICE_ACTION).setPackage(PACKAGE_NAME)
        return context.packageManager.resolveService(intent, 0) != null
    }
}
