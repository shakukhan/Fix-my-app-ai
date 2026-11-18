package com.example.engine

import com.example.model.FileType
import com.example.model.ProjectFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class PreviewAsset(
    val relativePath: String,
    val mimeType: String,
    val content: String,
    val rawBytes: ByteArray? = null
)

data class PreparedPreview(
    val entryPath: String,
    val entryUrl: String,
    val entryHtmlWithMonitor: String,
    val isEntryBlank: Boolean,
    val assetsByPath: Map<String, PreviewAsset>,
    val contentSignature: Int,
    val hasRepairedFiles: Boolean
)

object PreviewBundleBuilder {

    const val PREVIEW_HOST = "fixmyapp.local"
    const val PREVIEW_ORIGIN = "https://$PREVIEW_HOST/"

    private const val ERROR_MONITOR_SCRIPT = """
<script data-fixmyapp-monitor="true">
(function() {
  if (window.__fixMyAppMonitorInstalled) return;
  window.__fixMyAppMonitorInstalled = true;
  window.addEventListener('unhandledrejection', function(e) {
    var reason = (e && e.reason) ? String(e.reason) : 'Unhandled Promise rejection';
    console.error('[JS Promise Error] ' + reason);
  });
})();
</script>
"""

    fun build(
        files: List<ProjectFile>,
        useRepaired: Boolean,
        binaryAssets: Map<String, ByteArray> = emptyMap()
    ): PreparedPreview? {
        if (files.isEmpty()) return null

        val entryFile = selectEntryHtmlFile(files) ?: return null
        val assets = linkedMapOf<String, PreviewAsset>()
        var sig = if (useRepaired) 31 else 17

        for (file in files.sortedBy { it.relativePath }) {
            val cleanPath = normalizeRelPath(file.relativePath)
            if (cleanPath.isBlank()) continue
            val raw = binaryAssets[cleanPath] ?: binaryAssets[file.relativePath]
            val content = if (useRepaired) file.activeContent() else file.originalContent
            sig = 31 * sig + cleanPath.hashCode()
            sig = 31 * sig + content.hashCode()
            if (raw != null) {
                sig = 31 * sig + raw.size
            }
            assets[cleanPath] = PreviewAsset(
                relativePath = cleanPath,
                mimeType = mimeTypeForPath(cleanPath),
                content = content,
                rawBytes = raw
            )
        }

        for ((path, bytes) in binaryAssets.entries.sortedBy { it.key }) {
            val cleanPath = normalizeRelPath(path)
            if (cleanPath.isNotBlank() && !assets.containsKey(cleanPath)) {
                sig = 31 * sig + cleanPath.hashCode() + bytes.size
                assets[cleanPath] = PreviewAsset(
                    relativePath = cleanPath,
                    mimeType = mimeTypeForPath(cleanPath),
                    content = "",
                    rawBytes = bytes
                )
            }
        }

        val entryPath = normalizeRelPath(entryFile.relativePath)
        val rawEntryHtml = if (useRepaired) entryFile.activeContent() else entryFile.originalContent
        val wrappedHtml = if (rawEntryHtml.isBlank()) "" else injectRuntimeMonitor(rawEntryHtml)

        return PreparedPreview(
            entryPath = entryPath,
            entryUrl = "$PREVIEW_ORIGIN$entryPath",
            entryHtmlWithMonitor = wrappedHtml,
            isEntryBlank = rawEntryHtml.isBlank(),
            assetsByPath = assets,
            contentSignature = sig,
            hasRepairedFiles = files.any { it.repairedContent != null }
        )
    }

    fun selectEntryHtmlFile(files: List<ProjectFile>): ProjectFile? {
        return files.firstOrNull { normalizeRelPath(it.relativePath).equals("index.html", ignoreCase = true) }
            ?: files.firstOrNull { normalizeRelPath(it.relativePath).equals("index.htm", ignoreCase = true) }
            ?: files.firstOrNull { normalizeRelPath(it.relativePath).endsWith("/index.html", ignoreCase = true) }
            ?: files.firstOrNull { it.fileTypeEnum() == FileType.HTML || it.relativePath.endsWith(".html", ignoreCase = true) || it.relativePath.endsWith(".htm", ignoreCase = true) }
    }

    fun resolveAssetForUrl(prepared: PreparedPreview, requestUrl: String?): PreviewAsset? {
        if (requestUrl.isNullOrBlank()) return null
        if (!requestUrl.startsWith(PREVIEW_ORIGIN, ignoreCase = true)) return null
        val rawRel = requestUrl
            .substring(PREVIEW_ORIGIN.length)
            .substringBefore('#')
            .substringBefore('?')
        val rel = normalizeRelPath(rawRel)
        if (rel.isEmpty()) {
            return prepared.assetsByPath[prepared.entryPath]
        }
        return prepared.assetsByPath[rel]
    }

    fun openAssetStream(asset: PreviewAsset): InputStream {
        val bytes = asset.rawBytes ?: asset.content.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayInputStream(bytes)
    }

    fun mimeTypeForPath(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "txt", "md" -> "text/plain"
            "xml" -> "application/xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            else -> "text/plain"
        }
    }

    internal fun normalizeRelPath(raw: String): String {
        val cleaned = raw.replace('\\', '/').trimStart('/')
        if (cleaned.isBlank()) return ""
        val parts = mutableListOf<String>()
        for (segment in cleaned.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    internal fun injectRuntimeMonitor(html: String): String {
        val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val headMatch = headRegex.find(html)
        if (headMatch != null) {
            val insertAt = headMatch.range.last + 1
            return html.substring(0, insertAt) + "\n" + ERROR_MONITOR_SCRIPT + html.substring(insertAt)
        }
        val bodyRegex = Regex("<body[^>]*>", RegexOption.IGNORE_CASE)
        val bodyMatch = bodyRegex.find(html)
        if (bodyMatch != null) {
            val insertAt = bodyMatch.range.first
            return html.substring(0, insertAt) + ERROR_MONITOR_SCRIPT + html.substring(insertAt)
        }
        return ERROR_MONITOR_SCRIPT + "\n" + html
    }
}
