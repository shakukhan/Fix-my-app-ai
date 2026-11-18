package com.example.engine

import com.example.model.FileType
import com.example.model.ProjectFile
import com.example.model.ProjectIssue

data class RepairOutcome(
    val updatedFiles: List<ProjectFile>,
    val promotedRootIndex: ProjectFile?,
    val beforeReport: InspectionReport,
    val afterReport: InspectionReport,
    val resolvedIssues: List<ProjectIssue>,
    val remainingIssues: List<ProjectIssue>
)

object WebsiteAutoRepairer {

    fun repairAndValidate(
        files: List<ProjectFile>,
        userId: String,
        projectId: String,
        geminiOverrides: Map<String, String> = emptyMap()
    ): RepairOutcome {
        // 1. Inspect original code first to get exact baseline
        val beforeReport = StaticWebsiteInspector.inspect(files, useRepaired = false, userId, projectId)

        val allPaths = files.map { it.relativePath }.toMutableSet()
        val lowerMap = files.associateBy({ it.relativePath.lowercase() }, { it.relativePath }).toMutableMap()

        var promotedRootIndex: ProjectFile? = null
        if (!allPaths.contains("index.html")) {
            val candidate = files.firstOrNull {
                it.relativePath.equals("index.htm", ignoreCase = true) ||
                    it.relativePath.endsWith("/index.html", ignoreCase = true) ||
                    it.relativePath.endsWith("/index.htm", ignoreCase = true)
            }
            if (candidate != null) {
                val baseSource = geminiOverrides[candidate.relativePath]?.takeIf { it.isNotBlank() }
                    ?: candidate.originalContent
                val repairedHtml = repairHtmlContent(
                    baseSource,
                    "index.html",
                    allPaths,
                    lowerMap
                )
                promotedRootIndex = ProjectFile(
                    userId = userId,
                    projectId = projectId,
                    relativePath = "index.html",
                    fileType = FileType.HTML.name,
                    sizeBytes = repairedHtml.length.toLong(),
                    originalContent = candidate.originalContent,
                    repairedContent = repairedHtml
                )
                allPaths.add("index.html")
                lowerMap["index.html"] = "index.html"
            }
        }

        // 2. Repair every eligible file without ever touching originalContent
        val updatedFiles = files.map { file ->
            val overrideCode = geminiOverrides[file.relativePath]?.takeIf { it.isNotBlank() }
            val currentSource = overrideCode ?: file.originalContent
            val fixed = when (file.fileTypeEnum()) {
                FileType.HTML -> repairHtmlContent(currentSource, file.relativePath, allPaths, lowerMap)
                FileType.CSS -> repairCssContent(currentSource, file.relativePath, beforeReport)
                FileType.JS -> repairJsContent(currentSource)
                else -> currentSource
            }
            if (fixed != file.originalContent) {
                file.copy(repairedContent = fixed)
            } else {
                file.copy(repairedContent = file.repairedContent)
            }
        }

        val finalCandidateList = if (promotedRootIndex != null) {
            updatedFiles + promotedRootIndex
        } else {
            updatedFiles
        }

        // 3. CRITICAL: Re-inspect repaired code to verify what actually got fixed!
        val afterReport = StaticWebsiteInspector.inspect(finalCandidateList, useRepaired = true, userId, projectId)
        val remainingKeys = afterReport.issues.map { "${it.code}:${it.filePath}" }.toSet()

        val resolved = beforeReport.issues
            .filter { "${it.code}:${it.filePath}" !in remainingKeys }
            .map { it.copy(fixed = true) }

        return RepairOutcome(
            updatedFiles = updatedFiles,
            promotedRootIndex = promotedRootIndex,
            beforeReport = beforeReport,
            afterReport = afterReport,
            resolvedIssues = resolved,
            remainingIssues = afterReport.issues
        )
    }

    private fun repairHtmlContent(
        source: String,
        filePath: String,
        allPaths: Set<String>,
        lowerMap: Map<String, String>
    ): String {
        var html = source

        if (!Regex("<!doctype\\s+html", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            html = "<!DOCTYPE html>\n$html"
        }

        // Ensure <head> block additions cleanly
        val headAdditions = mutableListOf<String>()
        if (!Regex("<meta[^>]*charset\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            headAdditions.add("  <meta charset=\"UTF-8\">")
        }
        if (!Regex("<meta[^>]*name\\s*=\\s*[\"']viewport[\"']", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            headAdditions.add("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        }
        val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        if (titleMatch == null) {
            headAdditions.add("  <title>My Fixed Website</title>")
        } else if (titleMatch.groupValues[1].isBlank()) {
            html = html.replaceRange(titleMatch.range, "<title>My Fixed Website</title>")
        }

        if (headAdditions.isNotEmpty()) {
            val headTag = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
            if (headTag != null) {
                val insertAt = headTag.range.last + 1
                html = html.substring(0, insertAt) + "\n" + headAdditions.joinToString("\n") + html.substring(insertAt)
            } else {
                val htmlTag = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
                if (htmlTag != null) {
                    val insertAt = htmlTag.range.last + 1
                    html = html.substring(0, insertAt) + "\n<head>\n" + headAdditions.joinToString("\n") + "\n</head>" + html.substring(insertAt)
                }
            }
        }

        // Add missing alt attributes to <img> tags
        val imgRegex = Regex("<img\\b([^>]*?)(\\s*/?)>", RegexOption.IGNORE_CASE)
        html = imgRegex.replace(html) { match ->
            val attrs = match.groupValues[1]
            val closing = match.groupValues[2]
            if (Regex("\\balt\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(attrs)) {
                match.value
            } else {
                val srcVal = Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
                val altLabel = srcVal?.substringAfterLast('/')?.substringBeforeLast('.')?.replace('-', ' ')?.replace('_', ' ')
                    ?.takeIf { it.isNotBlank() } ?: "Website image"
                "<img$attrs alt=\"$altLabel\"$closing>"
            }
        }

        // Fix Windows backslashes, case-mismatches, and http:// scripts/links
        val refRegex = Regex("((?:src|href)\\s*=\\s*)([\"'])([^\"']*)\\2", RegexOption.IGNORE_CASE)
        html = refRegex.replace(html) { match ->
            val prefix = match.groupValues[1]
            val quote = match.groupValues[2]
            val rawRef = match.groupValues[3]
            val attrName = prefix.substringBefore('=').trim().lowercase()

            var fixedRef = rawRef
            if (attrName == "src" && fixedRef.startsWith("http://", ignoreCase = true)) {
                fixedRef = "https://" + fixedRef.substring(7)
            } else if (!fixedRef.startsWith("http", ignoreCase = true) && !fixedRef.startsWith("#") && !fixedRef.startsWith("data:")) {
                if (fixedRef.contains('\\')) {
                    fixedRef = if (Regex("^[a-zA-Z]:\\\\").containsMatchIn(fixedRef)) {
                        fixedRef.substringAfterLast('\\')
                    } else {
                        fixedRef.replace('\\', '/')
                    }
                }
                val baseDir = filePath.substringBeforeLast('/', "")
                val resolved = if (fixedRef.startsWith('/')) {
                    fixedRef.trimStart('/')
                } else if (baseDir.isEmpty()) {
                    fixedRef
                } else {
                    "$baseDir/$fixedRef"
                }
                if (!allPaths.contains(resolved)) {
                    val actual = lowerMap[resolved.lowercase()]
                    if (actual != null) {
                        fixedRef = if (baseDir.isEmpty()) actual else actual.removePrefix("$baseDir/")
                    }
                }
            }
            "$prefix$quote$fixedRef$quote"
        }

        // Repair common JS typos inside inline <script>...</script> blocks
        val inlineScriptRegex = Regex("(<script\\b[^>]*>)(.*?)(</script>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        html = inlineScriptRegex.replace(html) { match ->
            val openTag = match.groupValues[1]
            val body = match.groupValues[2]
            val closeTag = match.groupValues[3]
            if (body.isBlank()) match.value else "$openTag${repairJsContent(body)}$closeTag"
        }

        return html
    }

    private fun repairCssContent(
        source: String,
        filePath: String,
        beforeReport: InspectionReport
    ): String {
        var css = source

        val opens = css.count { it == '{' }
        val closes = css.count { it == '}' }
        if (opens > closes) {
            css = css.trimEnd() + "\n" + "}".repeat(opens - closes) + "\n"
        }

        val urlRegex = Regex("url\\(\\s*([\"']?)([^\"')]+)\\1\\s*\\)", RegexOption.IGNORE_CASE)
        css = urlRegex.replace(css) { match ->
            val quote = match.groupValues[1]
            val ref = match.groupValues[2]
            if (ref.contains('\\')) {
                "url($quote${ref.replace('\\', '/')}$quote)"
            } else {
                match.value
            }
        }

        val hasFixedWide = beforeReport.issues.any {
            it.filePath == filePath && it.code == "CSS_FIXED_WIDTH_OVERFLOW"
        }
        if (hasFixedWide && !css.contains("FixMyApp Responsive Guard")) {
            css = css.trimEnd() + "\n\n/* FixMyApp Responsive Guard */\n" +
                "*, *::before, *::after { box-sizing: border-box; }\n" +
                "img, video, canvas { max-width: 100%; height: auto; }\n" +
                "@media (max-width: 768px) {\n" +
                "  body { overflow-x: hidden; max-width: 100vw; }\n" +
                "  * { max-width: 100%; }\n" +
                "}\n"
        }
        return css
    }

    private fun repairJsContent(source: String): String {
        return source
            .replace(Regex("document\\.getElementByID\\b"), "document.getElementById")
            .replace(Regex("document\\.querySelectorall\\b"), "document.querySelectorAll")
            .replace(Regex("document\\.getElementsByclassName\\b"), "document.getElementsByClassName")
            .replace(Regex("\\.addEventListner\\s*\\("), ".addEventListener(")
    }
}
