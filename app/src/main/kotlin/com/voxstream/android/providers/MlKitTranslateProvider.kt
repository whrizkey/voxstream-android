package com.voxstream.android.providers

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.voxstream.core.providers.TranslateProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device, private, zero-cost translation provider backed by Google ML Kit.
 * Dynamically downloads offline translation models when requested.
 */
class MlKitTranslateProvider : TranslateProvider {

    override val name: String = "Google ML Kit (On-Device)"
    override val isOffline: Boolean = true

    private val translators = ConcurrentHashMap<String, Translator>()

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        if (text.isBlank()) return ""

        val sourceTag = TranslateLanguage.fromLanguageTag(sourceLanguage) ?: TranslateLanguage.ENGLISH
        val targetTag = TranslateLanguage.fromLanguageTag(targetLanguage) ?: TranslateLanguage.SPANISH

        val key = "$sourceTag->$targetTag"
        val translator = translators.computeIfAbsent(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build()
            Translation.getClient(options)
        }

        ensureModelDownloaded(translator)

        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    private suspend fun ensureModelDownloaded(translator: Translator) {
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        suspendCancellableCoroutine<Unit> { continuation ->
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    continuation.resume(Unit)
                }
                .addOnFailureListener {
                    // Try without requiring wifi if download failed
                    translator.downloadModelIfNeeded()
                        .addOnSuccessListener { continuation.resume(Unit) }
                        .addOnFailureListener { err -> continuation.resumeWithException(err) }
                }
        }
    }

    fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
