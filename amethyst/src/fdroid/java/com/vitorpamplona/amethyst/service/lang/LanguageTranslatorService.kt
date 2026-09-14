/*
 * Copyright (c) 2025 Vitor Pamplona
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
import android.os.Build
import android.os.CancellationSignal
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.Amethyst
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Immutable
data class ResultOrError(
    val result: String?,
    val sourceLang: String?,
    val targetLang: String?,
)

private class OnDeviceTranslationUnavailableException : Exception()

object LanguageTranslatorService {
    private const val MIN_LANGUAGE_CONFIDENCE = 0.6f

    private val executorService: ExecutorService =
        Executors.newFixedThreadPool(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))

    fun clear() {
        TranslationsCache.clear()
    }

    suspend fun autoTranslate(
        text: String,
        dontTranslateFrom: Set<String>,
        translateTo: String,
    ): ResultOrError {
        if (!TranslationDictionary.isWorthTranslating(text)) {
            return ResultOrError(null, null, null)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            throw OnDeviceTranslationUnavailableException()
        }

        val detected = identifyLanguage(text) ?: return ResultOrError(null, null, null)
        if (languageTagsMatch(detected, translateTo)) {
            return ResultOrError(null, detected, translateTo)
        }
        if (dontTranslateFrom.any { languageTagsMatch(detected, it) }) {
            return ResultOrError(null, detected, translateTo)
        }

        val translated = translate(text, detected, translateTo)
        return ResultOrError(translated, detected, translateTo)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun identifyLanguage(text: String): String? {
        val manager =
            Amethyst.instance.appContext.getSystemService(TextClassificationManager::class.java)
                ?: return null
        val result = manager.textClassifier.detectLanguage(TextLanguage.Request.Builder(text).build())
        if (result.localeHypothesisCount == 0) return null

        val bestLocale = result.getLocale(0)
        if (result.getConfidenceScore(bestLocale) < MIN_LANGUAGE_CONFIDENCE) return null
        return bestLocale.toLanguageTag()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun translate(
        text: String,
        source: String,
        target: String,
    ): String {
        val manager =
            Amethyst.instance.appContext.getSystemService(TranslationManager::class.java)
                ?: throw OnDeviceTranslationUnavailableException()

        val capability =
            manager
                .getOnDeviceTranslationCapabilities(
                    TranslationSpec.DATA_FORMAT_TEXT,
                    TranslationSpec.DATA_FORMAT_TEXT,
                ).firstOrNull {
                    canUseOnDeviceCapability(
                        requestedSource = source,
                        requestedTarget = target,
                        availableSource = it.sourceSpec.locale.toLanguageTag(),
                        availableTarget = it.targetSpec.locale.toLanguageTag(),
                        isOnDevice = it.state == TranslationCapability.STATE_ON_DEVICE,
                    )
                } ?: throw OnDeviceTranslationUnavailableException()

        val context = TranslationContext.Builder(capability.sourceSpec, capability.targetSpec).build()
        val translator = createTranslator(manager, context) ?: throw OnDeviceTranslationUnavailableException()

        return try {
            val dictionary = TranslationDictionary.build(text)
            val encoded = TranslationDictionary.encode(text, dictionary)
            val translated = translateText(translator, encoded) ?: throw OnDeviceTranslationUnavailableException()
            TranslationDictionary.decode(translated, dictionary) ?: throw OnDeviceTranslationUnavailableException()
        } finally {
            translator.destroy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun createTranslator(
        manager: TranslationManager,
        context: TranslationContext,
    ): Translator? =
        suspendCancellableCoroutine { continuation ->
            manager.createOnDeviceTranslator(context, executorService) { translator ->
                if (!continuation.isActive) {
                    translator?.destroy()
                } else {
                    continuation.resume(translator)
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun translateText(
        translator: Translator,
        text: String,
    ): String? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            val request =
                TranslationRequest
                    .Builder()
                    .setTranslationRequestValues(
                        mutableListOf(TranslationRequestValue.forText(text)),
                    ).build()

            translator.translate(request, cancellationSignal, executorService) { response ->
                if (!continuation.isActive) return@translate
                if (response.translationStatus != TranslationResponse.TRANSLATION_STATUS_SUCCESS) {
                    continuation.resume(null)
                    return@translate
                }

                val value = response.translationResponseValues.get(0)
                if (value == null || value.statusCode != TranslationResponseValue.STATUS_SUCCESS) {
                    continuation.resume(null)
                    return@translate
                }

                continuation.resume(value.text?.toString())
            }
        }
}
