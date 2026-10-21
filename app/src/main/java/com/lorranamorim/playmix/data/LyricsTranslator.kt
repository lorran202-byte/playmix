package com.lorranamorim.playmix.data

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Traduz letras para português do Brasil com o tradutor do próprio aparelho (ML Kit).
 * Depois de baixar o pacote do idioma uma vez (~30 MB), funciona sem internet.
 * Letras que já estão em português não são traduzidas.
 */
class LyricsTranslator(context: Context) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /** Uma tradução por linha (mesma ordem), ou null se já está em português / não deu para traduzir. */
    suspend fun translate(trackId: Long, lines: List<String>): List<String>? = withContext(Dispatchers.IO) {
        val cache = File(dir, "$trackId.tr.json")
        if (cache.exists()) {
            runCatching {
                val o = JSONObject(cache.readText())
                if (o.optBoolean("none")) return@withContext null
                val arr = o.getJSONArray("lines")
                if (arr.length() == lines.size) return@withContext (0 until arr.length()).map { arr.optString(it) }
            }
        }

        val text = lines.filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank()) return@withContext null

        val lang = runCatching { LanguageIdentification.getClient().identifyLanguage(text).await() }.getOrNull()
            ?: return@withContext null
        if (lang == "und" || lang.startsWith("pt")) {
            runCatching { cache.writeText(JSONObject().put("none", true).toString()) }
            return@withContext null
        }
        val source = TranslateLanguage.fromLanguageTag(lang) ?: return@withContext null

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                .build(),
        )
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val memo = HashMap<String, String>()
            val out = lines.map { line ->
                if (line.isBlank()) "" else memo.getOrPut(line) { translator.translate(line).await() }
            }
            runCatching { cache.writeText(JSONObject().put("lines", JSONArray(out)).toString()) }
            out
        } catch (_: Exception) {
            null
        } finally {
            translator.close()
        }
    }
}
