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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationDictionaryTest {
    @Test
    fun shortAndLetterlessTextIsSkipped() {
        assertFalse(TranslationDictionary.isWorthTranslating("abc"))
        assertFalse(TranslationDictionary.isWorthTranslating("😊😊😊"))
        assertTrue(TranslationDictionary.isWorthTranslating("hello"))
    }

    @Test
    fun urlRoundTripPreservesOriginal() {
        val text = "Read https://example.com/path before translating this note"
        val dictionary = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dictionary)

        assertFalse(encoded.contains("https://example.com/path"))
        assertEquals(text, TranslationDictionary.decode(encoded, dictionary))
    }

    @Test
    fun nostrAndLightningTokensAreProtected() {
        val invoice = "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364s"
        val nostr = "nostr:note1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
        val text = "Pay $invoice and inspect $nostr"
        val dictionary = TranslationDictionary.build(text)

        assertTrue(dictionary.values.any { it == invoice })
        assertTrue(dictionary.values.any { it.startsWith("nostr:note1") })
        assertEquals(text, TranslationDictionary.decode(TranslationDictionary.encode(text, dictionary), dictionary))
    }

    @Test
    fun userPlaceholdersAreNotReused() {
        val text = "Keep {0} and {3}; translate https://example.com"
        val dictionary = TranslationDictionary.build(text)

        assertFalse("{0}" in dictionary.keys)
        assertFalse("{3}" in dictionary.keys)
        assertEquals(text, TranslationDictionary.decode(TranslationDictionary.encode(text, dictionary), dictionary))
    }
}
