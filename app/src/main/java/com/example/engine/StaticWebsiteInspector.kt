package com.example.engine

import com.example.model.FileType
import com.example.model.IssueCategory
import com.example.model.IssueSeverity
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.ReadinessCheck

data class InspectionReport(
    val issues: List<ProjectIssue>,
    val readinessChecks: List<ReadinessCheck>,
    val criticalCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val autoFixableCount: Int
)

object StaticWebsiteInspector {

    fun inspect(
        files: List<ProjectFile>,
        useRepaired: Boolean,
        userId: String = "",
        projectId: String = ""
    ): InspectionReport {
        val allPaths = files.map { it.relativePath }.toSet()
        val lowerMap = files.associateBy({ it.relativePath.lowercase() }, { it.relativePath })
        val issues = mutableListOf<ProjectIssue>()

        val hasRootIndex = allPaths.contains("index.html")
        if (!hasRootIndex) {
            val nestedIndex = files.firstOrNull {
                it.relativePath.equals("index.htm", ignoreCase = true) ||
                    it.relativePath.endsWith("/index.html", ignoreCase = true) ||
                    it.relativePath.endsWith("/index.htm", ignoreCase = true)
            }
            issues.add(
                issue(
                    userId = userId,
                    projectId = projectId,
                    code = "DEPLOY_MISSING_INDEX",
                    category = IssueCategory.DEPLOYMENT,
                    severity = IssueSeverity.CRITICAL,
                    filePath = "Project Root",
                    lineNumber = 1L,
                    autoFixable = nestedIndex != null,
                    titleEn = "Missing root index.html entry page",
                    descEn = "Hosting services look for index.html in the main folder before opening your site.",
                    fixEn = if (nestedIndex != null) {
                        "Found ${nestedIndex.relativePath}. Auto-repair can promote it to root index.html."
                    } else {
                        "Rename or upload your main webpage as index.html."
                    },
                    titleHi = "मुख्य फ़ोल्डर में index.html फ़ाइल नहीं है",
                    descHi = "वेबसाइट खोलने के लिए मुख्य फ़ोल्डर में index.html होना ज़रूरी है।",
                    fixHi = if (nestedIndex != null) {
                        "${nestedIndex.relativePath} मिली है। ऑटो-रिपेयर इसे मुख्य index.html बना सकता है।"
                    } else {
                        "अपने मुख्य पेज का नाम index.html रखें।"
                    }
                )
            )
        }

        for (file in files) {
            val content = if (useRepaired) file.activeContent() else file.originalContent
            when (file.fileTypeEnum()) {
                FileType.HTML -> inspectHtml(file.relativePath, content, allPaths, lowerMap, issues, userId, projectId)
                FileType.CSS -> inspectCss(file.relativePath, content, allPaths, lowerMap, issues, userId, projectId)
                FileType.JS -> inspectJs(file.relativePath, content, issues, userId, projectId)
                else -> Unit
            }
        }

        val readiness = listOf(
            ReadinessCheck(
                key = "root_index",
                passed = hasRootIndex,
                labelEn = "Root index.html present",
                labelHi = "मुख्य index.html मौजूद है"
            ),
            ReadinessCheck(
                key = "no_syntax_crashes",
                passed = issues.none { it.code == "CSS_UNCLOSED_BRACE" || it.code == "JS_SYNTAX_BRACKET" || it.code == "JS_GETELEMENTBYID_TYPO" },
                labelEn = "No critical CSS/JS syntax crashes",
                labelHi = "CSS/JS में कोई बड़ी सिंटैक्स गलती नहीं"
            ),
            ReadinessCheck(
                key = "valid_paths",
                passed = issues.none { it.code == "PATH_CASE_MISMATCH" || it.code == "PATH_WINDOWS_BACKSLASH" },
                labelEn = "Clean web-safe file paths",
                labelHi = "वेब-सुरक्षित फ़ाइल पाथ सही हैं"
            ),
            ReadinessCheck(
                key = "mobile_viewport",
                passed = issues.none { it.code == "HTML_MISSING_VIEWPORT" },
                labelEn = "Mobile viewport configured",
                labelHi = "मोबाइल व्यूपोर्ट सेट है"
            )
        )

        return InspectionReport(
            issues = issues,
            readinessChecks = readiness,
            criticalCount = issues.count { it.severityEnum() == IssueSeverity.CRITICAL },
            warningCount = issues.count { it.severityEnum() == IssueSeverity.WARNING },
            infoCount = issues.count { it.severityEnum() == IssueSeverity.INFO },
            autoFixableCount = issues.count { it.autoFixable }
        )
    }

    private fun inspectHtml(
        filePath: String,
        content: String,
        allPaths: Set<String>,
        lowerMap: Map<String, String>,
        issues: MutableList<ProjectIssue>,
        userId: String,
        projectId: String
    ) {
        if (!Regex("<!doctype\\s+html", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            issues.add(
                issue(
                    userId, projectId, "HTML_MISSING_DOCTYPE", IssueCategory.HTML, IssueSeverity.WARNING,
                    filePath, 1L, true,
                    "Missing <!DOCTYPE html> declaration",
                    "Without <!DOCTYPE html> at line 1, browsers switch to quirks mode and break spacing.",
                    "Add <!DOCTYPE html> at the top of the file.",
                    "<!DOCTYPE html> शुरुआत में गायब है",
                    "इसके बिना ब्राउज़र में पेज की बनावट बिगड़ सकती है।",
                    "फ़ाइल की पहली लाइन में <!DOCTYPE html> जोड़ें।"
                )
            )
        }

        if (!Regex("<meta[^>]*name\\s*=\\s*[\"']viewport[\"']", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            issues.add(
                issue(
                    userId, projectId, "HTML_MISSING_VIEWPORT", IssueCategory.RESPONSIVE, IssueSeverity.CRITICAL,
                    filePath, findLine(content, "<head"), true,
                    "Not mobile-friendly (missing viewport tag)",
                    "Android phones will zoom out the desktop layout, making text tiny and hard to tap.",
                    "Add <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> inside <head>.",
                    "मोबाइल स्क्रीन टैग (viewport) गायब है",
                    "फ़ोन पर आपकी वेबसाइट बहुत छोटी और कटी हुई दिखेगी।",
                    "<head> के अंदर viewport meta टैग जोड़ें।"
                )
            )
        }

        if (!Regex("<meta[^>]*charset\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(content)) {
            issues.add(
                issue(
                    userId, projectId, "HTML_MISSING_CHARSET", IssueCategory.HTML, IssueSeverity.INFO,
                    filePath, findLine(content, "<head"), true,
                    "Missing UTF-8 character encoding",
                    "Hindi text, currency symbols, or emojis may display as garbled boxes.",
                    "Add <meta charset=\"UTF-8\"> inside <head>.",
                    "UTF-8 कैरेक्टर सेटिंग गायब है",
                    "इसके बिना हिंदी अक्षर या इमोजी गलत दिख सकते हैं।",
                    "<head> में <meta charset=\"UTF-8\"> जोड़ें।"
                )
            )
        }

        val titleMatch = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(content)
        if (titleMatch == null || titleMatch.groupValues[1].isBlank()) {
            issues.add(
                issue(
                    userId, projectId, "HTML_MISSING_TITLE", IssueCategory.HTML, IssueSeverity.WARNING,
                    filePath, findLine(content, "<head"), true,
                    "Missing or empty page <title>",
                    "Browser tabs and Google search results will show a raw URL instead of your brand name.",
                    "Add a descriptive <title> inside <head>.",
                    "पेज का <title> गायब या खाली है",
                    "ब्राउज़र टैब में आपकी वेबसाइट का नाम नहीं दिखेगा।",
                    "<head> में <title> जोड़ें।"
                )
            )
        }

        // Check <img> missing alt & src paths
        val imgRegex = Regex("<img\\b([^>]*)>", RegexOption.IGNORE_CASE)
        for (match in imgRegex.findAll(content)) {
            val attrs = match.groupValues[1]
            val line = content.substring(0, match.range.first).count { it == '\n' } + 1L
            if (!Regex("\\balt\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(attrs)) {
                issues.add(
                    issue(
                        userId, projectId, "HTML_IMG_MISSING_ALT", IssueCategory.HTML, IssueSeverity.INFO,
                        filePath, line, true,
                        "Image missing alt description",
                        "Screen readers and slow connections rely on alt text when an image cannot load.",
                        "Add an alt=\"...\" attribute to every <img> tag.",
                        "इमेज टैग में alt विवरण नहीं है",
                        "यदि फ़ोटो लोड न हो तो alt टेक्स्ट यूज़र की मदद करता है।",
                        "<img> टैग में alt=\"...\" जोड़ें।"
                    )
                )
            }
        }

        // Check href & src references
        val refRegex = Regex("(src|href)\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        for (match in refRegex.findAll(content)) {
            val attr = match.groupValues[1].lowercase()
            val ref = match.groupValues[2].trim()
            val line = content.substring(0, match.range.first).count { it == '\n' } + 1L

            if (ref.isEmpty() || ref == "#") {
                if (attr == "href") {
                    issues.add(
                        issue(
                            userId, projectId, "NAV_EMPTY_LINK", IssueCategory.NAVIGATION, IssueSeverity.WARNING,
                            filePath, line, false,
                            "Empty navigation link (href=\"${ref.ifEmpty { "empty" }}\")",
                            "Users tapping this link or button stay on the same spot with nothing happening.",
                            "Replace href=\"#\" with a real page or section ID like \"#contact\".",
                            "खाली नेविगेशन लिंक मिला",
                            "इस बटन या लिंक पर क्लिक करने से कोई पेज नहीं खुलेगा।",
                            "href में सही पेज या सेक्शन का नाम डालें।"
                        )
                    )
                }
                continue
            }

            if (isExternalOrSpecial(ref)) {
                if (ref.startsWith("http://", ignoreCase = true) && attr == "src") {
                    issues.add(
                        issue(
                            userId, projectId, "DEPLOY_INSECURE_HTTP", IssueCategory.DEPLOYMENT, IssueSeverity.WARNING,
                            filePath, line, true,
                            "Insecure http:// script or image ($ref)",
                            "Modern HTTPS websites block http:// scripts and stylesheets automatically.",
                            "Upgrade http:// to https://.",
                            "असुरक्षित http:// लिंक मिला",
                            "लाइव HTTPS वेबसाइट पर http:// फ़ाइलें ब्लॉक हो जाती हैं।",
                            "http:// को https:// में बदलें।"
                        )
                    )
                }
                continue
            }

            if (ref.contains('\\') || Regex("^[a-zA-Z]:[/\\\\]").containsMatchIn(ref) || ref.startsWith("file://")) {
                issues.add(
                    issue(
                        userId, projectId, "PATH_WINDOWS_BACKSLASH", IssueCategory.PATHS, IssueSeverity.CRITICAL,
                        filePath, line, true,
                        "Invalid local/Windows path: \"$ref\"",
                        "Backslashes (\\) or device folder paths (C:\\...) break completely on web servers.",
                        "Convert backslashes to forward slashes (/) and keep files inside the project.",
                        "गलत लोकल फ़ाइल पाथ: \"$ref\"",
                        "बैकस्लैश (\\) या कंप्यूटर का लोकल पाथ इंटरनेट पर काम नहीं करता।",
                        "पाथ में / का उपयोग करें और फ़ाइल को प्रोजेक्ट फ़ोल्डर में रखें।"
                    )
                )
                continue
            }

            val resolved = resolveRelPath(filePath, ref)
            if (!allPaths.contains(resolved)) {
                val actual = lowerMap[resolved.lowercase()]
                if (actual != null) {
                    issues.add(
                        issue(
                            userId, projectId, "PATH_CASE_MISMATCH", IssueCategory.PATHS, IssueSeverity.CRITICAL,
                            filePath, line, true,
                            "Letter-case mismatch: \"$ref\" vs \"$actual\"",
                            "Online Linux web servers treat uppercase and lowercase letters differently, breaking this file.",
                            "Update \"$ref\" to exact file name \"$actual\".",
                            "फ़ाइल नाम में बड़े/छोटे अक्षर की गलती: \"$ref\"",
                            "असली फ़ाइल \"$actual\" है। सर्वर पर नाम मेल न खाने से फ़ाइल नहीं खुलेगी।",
                            "\"$ref\" को \"$actual\" से बदलें।"
                        )
                    )
                } else {
                    val isImg = Regex("\\.(png|jpg|jpeg|gif|webp|svg|ico)$", RegexOption.IGNORE_CASE).containsMatchIn(resolved)
                    issues.add(
                        issue(
                            userId, projectId,
                            if (isImg) "ASSET_MISSING_IMG" else "BROKEN_FILE_REF",
                            if (isImg) IssueCategory.ASSETS else IssueCategory.PATHS,
                            IssueSeverity.CRITICAL,
                            filePath, line, false,
                            "Missing referenced file: \"$ref\"",
                            "The file \"$resolved\" was referenced in HTML but is missing from your uploaded project.",
                            "Upload \"$resolved\" or correct the file path.",
                            "फ़ाइल नहीं मिली: \"$ref\"",
                            "\"$resolved\" फ़ाइल आपके अपलोड किए गए प्रोजेक्ट में मौजूद नहीं है।",
                            "मिसिंग फ़ाइल अपलोड करें या सही पाथ लिखें।"
                        )
                    )
                }
            }
        }

        // Duplicate id="..." detection
        val seenIds = mutableSetOf<String>()
        val idRegex = Regex("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        for (match in idRegex.findAll(content)) {
            val idVal = match.groupValues[1]
            if (!seenIds.add(idVal)) {
                val line = content.substring(0, match.range.first).count { it == '\n' } + 1L
                issues.add(
                    issue(
                        userId, projectId, "HTML_DUPLICATE_ID", IssueCategory.HTML, IssueSeverity.WARNING,
                        filePath, line, false,
                        "Duplicate element id=\"$idVal\"",
                        "Two elements share id=\"$idVal\", causing JavaScript click handlers to target only the first one.",
                        "Give each element a unique id.",
                        "एक ही id=\"$idVal\" दो बार उपयोग हुई है",
                        "दो बटनों की एक ही ID होने से JavaScript सही काम नहीं करती।",
                        "हर एलिमेंट को अलग ID दें।"
                    )
                )
            }
        }
    }

    private fun inspectCss(
        filePath: String,
        content: String,
        allPaths: Set<String>,
        lowerMap: Map<String, String>,
        issues: MutableList<ProjectIssue>,
        userId: String,
        projectId: String
    ) {
        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces > closeBraces) {
            issues.add(
                issue(
                    userId, projectId, "CSS_UNCLOSED_BRACE", IssueCategory.CSS, IssueSeverity.CRITICAL,
                    filePath, content.lines().size.toLong().coerceAtLeast(1L), true,
                    "Unclosed CSS curly brace '{' (${openBraces - closeBraces} missing '}')",
                    "Missing closing '}' braces stop subsequent styles from loading cleanly.",
                    "Add the missing '}' brace at the end of the rule.",
                    "CSS में कोष्ठक '}' बंद करना छूट गया है",
                    "अधूरा ब्रैकेट आगे के सभी CSS डिज़ाइन को खराब कर सकता है।",
                    "नियम के अंत में '}' जोड़ें।"
                )
            )
        }

        val hasMediaQuery = Regex("@media\\b", RegexOption.IGNORE_CASE).containsMatchIn(content)
        val widthRegex = Regex("\\bwidth\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE)
        for (match in widthRegex.findAll(content)) {
            val px = match.groupValues[1].toIntOrNull() ?: 0
            if (px > 420 && !hasMediaQuery && !content.contains("FixMyApp Responsive Guard")) {
                val line = content.substring(0, match.range.first).count { it == '\n' } + 1L
                issues.add(
                    issue(
                        userId, projectId, "CSS_FIXED_WIDTH_OVERFLOW", IssueCategory.RESPONSIVE, IssueSeverity.WARNING,
                        filePath, line, true,
                        "Fixed width (${px}px) overflows mobile screens",
                        "Widths above 420px without responsive CSS rules cause sideways scrolling on Android phones.",
                        "Add responsive box-sizing, max-width: 100%, and mobile overflow protection.",
                        "फ़िक्स्ड चौड़ाई (${px}px) मोबाइल स्क्रीन से बाहर जा सकती है",
                        "फ़ोन की स्क्रीन पर पेज दाईं ओर कट सकता है।",
                        "max-width: 100% और रिस्पॉन्सिव नियम जोड़ें।"
                    )
                )
                break
            }
        }

        val urlRegex = Regex("url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)", RegexOption.IGNORE_CASE)
        for (match in urlRegex.findAll(content)) {
            val ref = match.groupValues[1].trim()
            if (isExternalOrSpecial(ref)) continue
            val line = content.substring(0, match.range.first).count { it == '\n' } + 1L
            if (ref.contains('\\')) {
                issues.add(
                    issue(
                        userId, projectId, "PATH_WINDOWS_BACKSLASH", IssueCategory.PATHS, IssueSeverity.CRITICAL,
                        filePath, line, true,
                        "CSS url() uses Windows backslash: \"$ref\"",
                        "Web browsers require forward slashes (/) in CSS url() paths.",
                        "Replace \\ with / inside CSS url().",
                        "CSS url() में गलत बैकस्लैश: \"$ref\"",
                        "वेब ब्राउज़र में हमेशा फॉरवर्ड स्लैश (/) काम करता है।",
                        "\\ को / से बदलें।"
                    )
                )
            } else {
                val resolved = resolveRelPath(filePath, ref)
                if (!allPaths.contains(resolved) && !lowerMap.containsKey(resolved.lowercase())) {
                    issues.add(
                        issue(
                            userId, projectId, "CSS_MISSING_URL_ASSET", IssueCategory.ASSETS, IssueSeverity.CRITICAL,
                            filePath, line, false,
                            "Missing CSS asset: \"$ref\"",
                            "The background image or font \"$ref\" was not found in the project.",
                            "Upload the missing asset or fix the relative path.",
                            "CSS में बताई गई फ़ाइल नहीं मिली: \"$ref\"",
                            "यह बैकग्राउंड इमेज या फ़ॉन्ट प्रोजेक्ट में नहीं मिला।",
                            "फ़ाइल अपलोड करें या सही पाथ डालें।"
                        )
                    )
                }
            }
        }
    }

    private fun inspectJs(
        filePath: String,
        content: String,
        issues: MutableList<ProjectIssue>,
        userId: String,
        projectId: String
    ) {
        // Common DOM typo check
        val typoMatch = Regex("document\\.getElementByID\\b").find(content)
        if (typoMatch != null) {
            val line = content.substring(0, typoMatch.range.first).count { it == '\n' } + 1L
            issues.add(
                issue(
                    userId, projectId, "JS_GETELEMENTBYID_TYPO", IssueCategory.JS, IssueSeverity.CRITICAL,
                    filePath, line, true,
                    "Typo: \"getElementByID\" must be \"getElementById\"",
                    "JavaScript is case-sensitive. Using uppercase \"ID\" crashes the script immediately.",
                    "Replace getElementByID with getElementById.",
                    "स्पेलिंग गलती: \"getElementByID\" की जगह \"getElementById\" लिखें",
                    "गलत बड़े अक्षरों के कारण आपका JavaScript बटन काम नहीं करेगा।",
                    "getElementByID को getElementById से बदलें।"
                )
            )
        }

        // Unsafe eval check
        val evalMatch = Regex("\\beval\\s*\\(").find(content)
        if (evalMatch != null) {
            val line = content.substring(0, evalMatch.range.first).count { it == '\n' } + 1L
            issues.add(
                issue(
                    userId, projectId, "JS_EVAL_WARNING", IssueCategory.JS, IssueSeverity.WARNING,
                    filePath, line, false,
                    "Unsafe eval() detected",
                    "eval() introduces security vulnerabilities and is blocked by strict Content Security Policies.",
                    "Replace eval() with JSON.parse() or regular functions.",
                    "असुरक्षित eval() मिला",
                    "eval() सुरक्षा के लिए जोखिम भरा है और कई होस्टिंग पर ब्लॉक रहता है।",
                    "eval() की जगह सुरक्षित फ़ंक्शन का उपयोग करें।"
                )
            )
        }

        // Lightweight bracket/paren/brace balance scan ignoring strings & single-line comments
        val bracketError = scanJsSyntaxBalance(content)
        if (bracketError != null) {
            issues.add(
                issue(
                    userId, projectId, "JS_SYNTAX_BRACKET", IssueCategory.JS, IssueSeverity.CRITICAL,
                    filePath, bracketError.first, false,
                    "JavaScript syntax mismatch: ${bracketError.second}",
                    "Unmatched brackets or braces stop the entire JavaScript file from running.",
                    "Check line ${bracketError.first} or use AI Smart Fix to repair the syntax.",
                    "लाइन ${bracketError.first} पर JavaScript ब्रैकेट गलती",
                    "अधूरे कोष्ठक के कारण पूरी JavaScript फ़ाइल चलना बंद हो जाएगी।",
                    "लाइन ${bracketError.first} जाँचें या AI स्मार्ट रिपेयर चलाएँ।"
                )
            )
        }
    }

    private fun scanJsSyntaxBalance(code: String): Pair<Long, String>? {
        val stack = ArrayDeque<Pair<Char, Long>>()
        var line = 1L
        var i = 0
        var inSingle = false
        var inDouble = false
        var inTemplate = false
        var inLineComment = false
        var inBlockComment = false

        while (i < code.length) {
            val c = code[i]
            val next = if (i + 1 < code.length) code[i + 1] else '\u0000'
            if (c == '\n') {
                line++
                inLineComment = false
                i++
                continue
            }
            if (inLineComment) { i++; continue }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i += 2; continue }
                i++; continue
            }
            if (inSingle) {
                if (c == '\\') { i += 2; continue }
                if (c == '\'') inSingle = false
                i++; continue
            }
            if (inDouble) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inDouble = false
                i++; continue
            }
            if (inTemplate) {
                if (c == '\\') { i += 2; continue }
                if (c == '`') inTemplate = false
                i++; continue
            }

            if (c == '/' && next == '/') { inLineComment = true; i += 2; continue }
            if (c == '/' && next == '*') { inBlockComment = true; i += 2; continue }
            if (c == '\'') { inSingle = true; i++; continue }
            if (c == '"') { inDouble = true; i++; continue }
            if (c == '`') { inTemplate = true; i++; continue }

            when (c) {
                '(', '{', '[' -> stack.addLast(c to line)
                ')', '}', ']' -> {
                    if (stack.isEmpty()) return line to "Unexpected closing '$c'"
                    val (open, openLine) = stack.removeLast()
                    val expected = when (open) {
                        '(' -> ')'
                        '{' -> '}'
                        else -> ']'
                    }
                    if (c != expected) return line to "Expected '$expected' (opened on line $openLine) but found '$c'"
                }
            }
            i++
        }
        if (stack.isNotEmpty()) {
            val (open, openLine) = stack.last()
            return openLine to "Unclosed '$open' opened on line $openLine"
        }
        return null
    }

    private fun isExternalOrSpecial(ref: String): Boolean =
        ref.startsWith("#") ||
            ref.startsWith("http://", ignoreCase = true) ||
            ref.startsWith("https://", ignoreCase = true) ||
            ref.startsWith("//") ||
            ref.startsWith("data:", ignoreCase = true) ||
            ref.startsWith("mailto:", ignoreCase = true) ||
            ref.startsWith("tel:", ignoreCase = true) ||
            ref.startsWith("javascript:", ignoreCase = true)

    private fun resolveRelPath(fromFile: String, rawRef: String): String {
        val clean = rawRef.substringBefore('#').substringBefore('?').trim().replace('\\', '/')
        if (clean.startsWith('/')) return clean.trimStart('/')
        val baseDir = fromFile.substringBeforeLast('/', "")
        val combined = if (baseDir.isEmpty()) clean else "$baseDir/$clean"
        val segments = mutableListOf<String>()
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun findLine(content: String, needle: String): Long {
        val idx = content.indexOf(needle, ignoreCase = true)
        if (idx < 0) return 1L
        return content.substring(0, idx).count { it == '\n' } + 1L
    }

    private fun issue(
        userId: String,
        projectId: String,
        code: String,
        category: IssueCategory,
        severity: IssueSeverity,
        filePath: String,
        lineNumber: Long,
        autoFixable: Boolean,
        titleEn: String,
        descEn: String,
        fixEn: String,
        titleHi: String,
        descHi: String,
        fixHi: String
    ) = ProjectIssue(
        userId = userId,
        projectId = projectId,
        code = code,
        category = category.name,
        severity = severity.name,
        filePath = filePath,
        lineNumber = lineNumber,
        autoFixable = autoFixable,
        fixed = false,
        titleEn = titleEn,
        descEn = descEn,
        fixEn = fixEn,
        titleHi = titleHi,
        descHi = descHi,
        fixHi = fixHi
    )
}
