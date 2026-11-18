package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.FileType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedWebFile(
    val relativePath: String,
    val fileType: FileType,
    val sizeBytes: Long,
    val textContent: String,
    val rawBytes: ByteArray? = null
)

data class ImportedProjectBundle(
    val suggestedName: String,
    val files: List<ImportedWebFile>,
    val totalBytes: Long
)

object ZipProjectImporter {
    const val MAX_UPLOAD_BYTES = 25L * 1024L * 1024L // 25 MB
    private const val MAX_UNZIPPED_BYTES = 60L * 1024L * 1024L
    private const val MAX_FILES = 500
    private const val MAX_TEXT_CHARS = 280_000

    private val ALLOWED_EXTENSIONS = setOf(
        "html", "htm", "css", "js", "mjs", "json", "txt", "md", "xml",
        "svg", "png", "jpg", "jpeg", "gif", "webp", "ico", "avif",
        "woff", "woff2", "ttf", "eot", "otf", "mp4", "webm", "mp3", "pdf"
    )

    private val TEXT_EXTENSIONS = setOf(
        "html", "htm", "css", "js", "mjs", "json", "txt", "md", "xml", "svg"
    )

    fun importFromUris(context: Context, uris: List<Uri>): Result<ImportedProjectBundle> {
        if (uris.isEmpty()) {
            return Result.failure(IllegalArgumentException("No files selected."))
        }
        return try {
            val resolver = context.contentResolver
            if (uris.size == 1) {
                val uri = uris.first()
                val displayName = resolveDisplayName(context, uri)
                if (displayName.endsWith(".zip", ignoreCase = true)) {
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return Result.failure(IllegalStateException("Could not read selected ZIP file."))
                    if (bytes.size > MAX_UPLOAD_BYTES) {
                        return Result.failure(IllegalArgumentException("ZIP exceeds the 25 MB maximum upload limit."))
                    }
                    return parseZipBytes(displayName.removeSuffix(".zip"), bytes)
                }
            }

            var totalBytes = 0L
            val collected = mutableListOf<ImportedWebFile>()
            var firstName = "Website Project"

            for ((index, uri) in uris.withIndex()) {
                val name = sanitizeRelPath(resolveDisplayName(context, uri))
                    ?: return Result.failure(IllegalArgumentException("Unsafe or invalid file name."))
                if (name.endsWith(".zip", ignoreCase = true)) {
                    return Result.failure(IllegalArgumentException("Upload either 1 ZIP file or individual HTML/CSS/JS files."))
                }
                if (!isAllowedExtension(name)) {
                    return Result.failure(IllegalArgumentException("Unsupported file type: $name"))
                }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.failure(IllegalStateException("Could not read file: $name"))
                totalBytes += bytes.size
                if (totalBytes > MAX_UPLOAD_BYTES) {
                    return Result.failure(IllegalArgumentException("Total upload exceeds 25 MB limit."))
                }
                if (index == 0) {
                    firstName = name.substringBeforeLast('.').ifBlank { "Website Project" }
                }
                val fType = detectFileType(name)
                val text = if (isTextFile(name)) {
                    bytes.toString(Charsets.UTF_8).take(MAX_TEXT_CHARS)
                } else {
                    ""
                }
                collected.add(
                    ImportedWebFile(
                        relativePath = name,
                        fileType = fType,
                        sizeBytes = bytes.size.toLong(),
                        textContent = text,
                        rawBytes = if (isTextFile(name)) null else bytes
                    )
                )
            }

            Result.success(
                ImportedProjectBundle(
                    suggestedName = firstName.take(80),
                    files = collected,
                    totalBytes = totalBytes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseZipBytes(baseName: String, zipBytes: ByteArray): Result<ImportedProjectBundle> {
        val rawEntries = mutableListOf<Pair<String, ByteArray>>()
        var uncompressedTotal = 0L

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val clean = sanitizeRelPath(entry.name)
                    if (clean != null && isAllowedExtension(clean)) {
                        if (rawEntries.size >= MAX_FILES) {
                            return Result.failure(IllegalArgumentException("ZIP contains more than $MAX_FILES files."))
                        }
                        val buffer = ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var read = zis.read(chunk)
                        while (read > 0) {
                            uncompressedTotal += read
                            if (uncompressedTotal > MAX_UNZIPPED_BYTES) {
                                return Result.failure(IllegalArgumentException("Extracted ZIP exceeds safe 60 MB limit."))
                            }
                            buffer.write(chunk, 0, read)
                            read = zis.read(chunk)
                        }
                        rawEntries.add(clean to buffer.toByteArray())
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (rawEntries.isEmpty()) {
            return Result.failure(IllegalArgumentException("No supported web files (.html, .css, .js, images) found in ZIP."))
        }

        // Unwrap single top-level directory (very common when zipping a folder on phone/PC)
        val topDirs = rawEntries.map { it.first.substringBefore('/') }.toSet()
        val allNested = rawEntries.all { it.first.contains('/') }
        val stripRoot = topDirs.size == 1 && allNested

        val imported = rawEntries.map { (path, bytes) ->
            val rel = if (stripRoot) path.substringAfter('/') else path
            val isText = isTextFile(rel)
            ImportedWebFile(
                relativePath = rel,
                fileType = detectFileType(rel),
                sizeBytes = bytes.size.toLong(),
                textContent = if (isText) bytes.toString(Charsets.UTF_8).take(MAX_TEXT_CHARS) else "",
                rawBytes = if (isText) null else bytes
            )
        }

        return Result.success(
            ImportedProjectBundle(
                suggestedName = baseName.ifBlank { "Website Project" }.take(80),
                files = imported,
                totalBytes = zipBytes.size.toLong()
            )
        )
    }

    fun exportProjectZipBytes(
        files: List<com.example.model.ProjectFile>,
        useRepaired: Boolean,
        binaryAssets: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            val seen = HashSet<String>()
            for (file in files) {
                val path = file.relativePath.trimStart('/')
                if (path.isBlank() || !seen.add(path)) continue
                val content = if (useRepaired) file.activeContent() else file.originalContent
                val raw = binaryAssets[path] ?: binaryAssets[file.relativePath]
                val entry = ZipEntry(path)
                zos.putNextEntry(entry)
                if (raw != null) {
                    zos.write(raw)
                } else if (content.isNotEmpty()) {
                    zos.write(content.toByteArray(Charsets.UTF_8))
                } else {
                    zos.write("<!-- Binary asset placeholder from cloud metadata: $path -->".toByteArray())
                }
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun writeZipToUri(context: Context, uri: Uri, zipBytes: ByteArray): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(zipBytes)
                stream.flush()
            } ?: return Result.failure(IllegalStateException("Unable to open destination file."))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createSampleBrokenProjectFiles(): ImportedProjectBundle {
        val html = """
<html>
<head>
  <link rel="stylesheet" href="Style.css">
</head>
<body>
  <header class="hero-card">
    <img src="images\bakery-logo.svg">
    <h1>Welcome to Sweet Bakery</h1>
    <p>Artisan cakes, warm pastries, and fresh sourdough baked daily.</p>
    <nav>
      <a href="#">Order Online</a>
    </nav>
  </header>
  <main class="menu-card">
    <h2>Today's Specials</h2>
    <p id="orderStatus">Tap a treat below to add it to your basket.</p>
    <div class="actions">
      <button id="buyBtn" onclick="handleOrder()">Buy Chocolate Cake</button>
      <button id="buyBtn" onclick="handleOrder()">Special Pastry Box</button>
    </div>
  </main>
  <script src="app.js"></script>
</body>
</html>
        """.trimIndent()

        val css = """
body {
  font-family: Arial, sans-serif;
  width: 960px;
  background: #fff7ed;
  color: #291810;
  margin: 0;
  padding: 20px;
}
.hero-card {
  background: linear-gradient(135deg, #fb923c, #ea580c);
  color: #ffffff;
  padding: 24px;
  border-radius: 16px;
  margin-bottom: 16px;
}
.hero-card a {
  display: inline-block;
  margin-top: 8px;
  background: #ffffff;
  color: #c2410c;
  padding: 10px 16px;
  border-radius: 10px;
  text-decoration: none;
  font-weight: bold;
}
.menu-card {
  background: #ffffff;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(41, 24, 16, 0.08);
}
button {
  background: #ea580c;
  color: #ffffff;
  border: none;
  padding: 12px 18px;
  border-radius: 10px;
  font-weight: bold;
  margin-right: 8px;
  margin-top: 8px;
        """.trimIndent()

        val js = """
function handleOrder() {
  const btn = document.getElementByID('buyBtn');
  const status = document.getElementById('orderStatus');
  if (btn) {
    btn.textContent = 'Added to Cart!';
  }
  if (status) {
    status.textContent = 'Sweet Bakery item added to your order!';
  }
}
        """.trimIndent()

        val logoSvg = """
<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="16" fill="#fffbeb"/>
  <path d="M16 34h32v14a4 4 0 0 1-4 4H20a4 4 0 0 1-4-4V34z" fill="#ea580c"/>
  <path d="M14 30c0-6 8-10 18-10s18 4 18 10v4H14v-4z" fill="#fdba74"/>
  <circle cx="32" cy="16" r="4" fill="#dc2626"/>
</svg>
        """.trimIndent()

        val files = listOf(
            ImportedWebFile("index.html", FileType.HTML, html.length.toLong(), html),
            ImportedWebFile("style.css", FileType.CSS, css.length.toLong(), css),
            ImportedWebFile("app.js", FileType.JS, js.length.toLong(), js),
            ImportedWebFile("images/bakery-logo.svg", FileType.ASSET, logoSvg.length.toLong(), logoSvg)
        )
        return ImportedProjectBundle(
            suggestedName = "Sweet Bakery Demo",
            files = files,
            totalBytes = files.sumOf { it.sizeBytes }
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "upload.txt"
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "upload.txt"
    }

    private fun sanitizeRelPath(raw: String): String? {
        val norm = raw.replace('\\', '/').trimStart('/')
        if (norm.isBlank() || norm.contains('\u0000')) return null
        val segments = norm.split('/').filter { it.isNotBlank() && it != "." }
        if (segments.isEmpty() || segments.any { it == ".." }) return null
        if (segments.first() == "__MACOSX" || segments.last() == ".DS_Store") return null
        return segments.joinToString("/")
    }

    private fun isAllowedExtension(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in ALLOWED_EXTENSIONS
    }

    private fun isTextFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS
    }

    fun detectFileType(path: String): FileType {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> FileType.HTML
            "css" -> FileType.CSS
            "js", "mjs" -> FileType.JS
            "png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "woff", "woff2", "ttf" -> FileType.ASSET
            else -> FileType.OTHER
        }
    }
}
