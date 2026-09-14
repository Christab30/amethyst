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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.ui.components.TranslationConfig
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.lang.ResultOrError
import com.vitorpamplona.amethyst.service.lang.TranslationsCache
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.MaxWidthPaddingTop5dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TranslatableRichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier = Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    id: String,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TranslatableRichTextViewer(
        content = content,
        id = id,
        accountViewModel = accountViewModel,
    ) {
        ExpandableRichTextViewer(
            it,
            canPreview,
            quotesLeft,
            modifier,
            tags,
            backgroundColor,
            id,
            callbackUri,
            authorPubKey,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun TranslatableRichTextViewer(
    content: String,
    id: String,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()
    val languagePreferences by languages.languagePreferences.collectAsStateWithLifecycle()

    val translatedTextState =
        remember(id, content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom) {
        try {
            translatedTextState.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(content, translateTo, dontTranslateFrom)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // On-device service/model unavailable or a transient platform failure: keep original.
        }
    }

    RenderTextWithTranslateOptions(
        translatedTextState = translatedTextState.value,
        content = content,
        languagePreferences = languagePreferences,
        translationMessageModifier = translationMessageModifier,
        accountViewModel = accountViewModel,
        displayText = displayText,
    )
}

@Composable
fun rememberTranslation(
    content: String,
    accountViewModel: AccountViewModel,
): String {
    val languages = accountViewModel.account.settings.syncedSettings.languages
    val translateTo by languages.translateTo.collectAsStateWithLifecycle()
    val dontTranslateFrom by languages.dontTranslateFrom.collectAsStateWithLifecycle()

    val state =
        remember(content, translateTo, dontTranslateFrom) {
            mutableStateOf(
                TranslationsCache.get(content, translateTo, dontTranslateFrom)
                    ?: TranslationConfig(content, null, null),
            )
        }

    LaunchedEffect(content, translateTo, dontTranslateFrom) {
        try {
            state.value =
                withContext(Dispatchers.IO) {
                    translateAndCache(content, translateTo, dontTranslateFrom)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep original text when on-device translation is unavailable.
        }
    }

    val config = state.value
    val translated = config.sourceLang != null && config.targetLang != null && config.sourceLang != config.targetLang
    return if (translated) config.result else content
}

@Composable
private fun RenderTextWithTranslateOptions(
    translatedTextState: TranslationConfig,
    content: String,
    languagePreferences: Map<String, String>,
    translationMessageModifier: Modifier = MaxWidthPaddingTop5dp,
    accountViewModel: AccountViewModel,
    displayText: @Composable (String) -> Unit,
) {
    val source = translatedTextState.sourceLang
    val target = translatedTextState.targetLang
    val translationOccurred = source != null && target != null && source != target

    val storedPreference = if (translationOccurred) languagePreferences["$source,$target"] else null
    var showOriginal by
        remember(translatedTextState, storedPreference) {
            mutableStateOf(storedPreference == source)
        }

    val toBeViewed = if (showOriginal || !translationOccurred) content else translatedTextState.result

    Column {
        displayText(toBeViewed)

        if (translationOccurred) {
            TranslationStatusBar(
                source = source,
                target = target,
                modifier = translationMessageModifier,
                accountViewModel = accountViewModel,
            ) { showOriginal = it }
        }
    }
}

private suspend fun translateAndCache(
    content: String,
    translateTo: String,
    dontTranslateFrom: Set<String>,
): TranslationConfig {
    TranslationsCache.get(content, translateTo, dontTranslateFrom)?.let { return it }

    val noOp = TranslationConfig(content, null, null)
    val raw =
        try {
            LanguageTranslatorService.autoTranslate(content, dontTranslateFrom, translateTo)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Do not cache service/model failures: installing a local model should allow a retry.
            return noOp
        }

    val config = raw.toTranslationConfig(content) ?: noOp
    TranslationsCache.set(content, translateTo, dontTranslateFrom, config)
    return config
}

private fun ResultOrError.toTranslationConfig(content: String): TranslationConfig? {
    val translated = result ?: return null
    val source = sourceLang ?: return null
    val target = targetLang ?: return null
    if (languageTagsEquivalent(source, target) || translated == content) return null
    return TranslationConfig(translated, source, target)
}

private fun languageTagsEquivalent(
    first: String,
    second: String,
): Boolean =
    first.replace('_', '-').substringBefore('-').equals(
        second.replace('_', '-').substringBefore('-'),
        ignoreCase = true,
    )
