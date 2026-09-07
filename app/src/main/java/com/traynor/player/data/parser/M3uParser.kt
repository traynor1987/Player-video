package com.traynor.player.data.parser

import com.traynor.player.core.model.ImportProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null
)

class M3uParser {
    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(input: InputStream, onBatch: suspend (List<M3uEntry>) -> Unit): Flow<ImportProgress> = channelFlow {
        withContext(Dispatchers.IO) {
            BufferedReader(InputStreamReader(input), 128 * 1024).use { reader ->
                val batch = ArrayList<M3uEntry>(500)
                var metadata: String? = null
                var processed = 0
                reader.lineSequence().forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.startsWith("#EXTINF", ignoreCase = true) -> metadata = line
                        line.isNotBlank() && !line.startsWith("#") && metadata != null -> {
                            parseEntry(requireNotNull(metadata), line)?.let(batch::add)
                            metadata = null
                            if (batch.size >= 500) {
                                onBatch(batch.toList()); processed += batch.size; batch.clear()
                                trySend(ImportProgress(processed, "Imported $processed channels"))
                            }
                        }
                    }
                }
                if (batch.isNotEmpty()) { onBatch(batch); processed += batch.size }
                trySend(ImportProgress(processed, "Imported $processed channels", complete = true))
            }
        }
    }

    internal fun parseEntry(info: String, url: String): M3uEntry? {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return null
        val attrs = attribute.findAll(info).associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        val name = info.substringAfterLast(',', attrs["tvg-name"].orEmpty()).trim().ifBlank { attrs["tvg-name"] ?: return null }
        return M3uEntry(name, url, attrs["tvg-id"], attrs["tvg-name"], attrs["tvg-logo"], attrs["group-title"])
    }
}
