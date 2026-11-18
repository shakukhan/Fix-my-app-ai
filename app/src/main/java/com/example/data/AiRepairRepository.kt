package com.example.data

import android.util.Log
import com.example.engine.StaticWebsiteInspector
import com.example.model.FileType
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiBilingualSummary(
    val summaryEn: String,
    val summaryHi: String
)

data class AiCodeFixResult(
    val repairedCode: String,
    val explanationEn: String,
    val explanationHi: String,
    val validated: Boolean
)

data class AiBatchRepairFile(
    val relativePath: String,
    val repairedCode: String
)

data class AiBatchRepairResult(
    val repairedFiles: List<AiBatchRepairFile>,
    val summaryEn: String,
    val summaryHi: String,
    val filesSentCount: Int,
    val issuesSentCount: Int
)

class AiRepairRepository {

    private val summarySchema = Schema.obj(
        properties = mapOf(
            "summaryEn" to Schema.string(
                description = "2-3 beginner-friendly sentences in plain English explaining website health and next step."
            ),
            "summaryHi" to Schema.string(
                description = "2-3 beginner-friendly sentences in simple Hindi (Devanagari) explaining website health and next step."
            )
        )
    )

    private val codeFixSchema = Schema.obj(
        properties = mapOf(
            "repairedCode" to Schema.string(
                description = "Complete repaired source code for the file with syntax and bugs fixed."
            ),
            "explanationEn" to Schema.string(
                description = "Short plain English explanation of what was fixed."
            ),
            "explanationHi" to Schema.string(
                description = "Short simple Hindi (Devanagari) explanation of what was fixed."
            )
        )
    )

    private val batchRepairSchema = Schema.obj(
        properties = mapOf(
            "repairedFiles" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "relativePath" to Schema.string(
                            description = "Exact relative path of the modified file (e.g. index.html, style.css, app.js)."
                        ),
                        "repairedCode" to Schema.string(
                            description = "Complete repaired source code for this file."
                        )
                    )
                ),
                description = "List of every repaired HTML, CSS, and JS file with complete valid source code."
            ),
            "summaryEn" to Schema.string(
                description = "Concise plain English summary of all repairs made across HTML, CSS, and JS."
            ),
            "summaryHi" to Schema.string(
                description = "Concise simple Hindi (Devanagari) summary of all repairs made across HTML, CSS, and JS."
            )
        )
    )

    private val summaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = summarySchema
            }
        )
    }

    private val repairModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = codeFixSchema
            }
        )
    }

    private val batchRepairModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = batchRepairSchema
            }
        )
    }

    suspend fun generateBilingualSummary(
        projectName: String,
        fileCount: Int,
        issues: List<ProjectIssue>
    ): Result<AiBilingualSummary> {
        return try {
            val topIssues = issues.take(12).joinToString("\n") {
                "- [${it.severity}] ${it.filePath} (line ${it.lineNumber}): ${it.titleEn}"
            }.ifBlank { "No errors found!" }

            val prompt = """
                You are FixMyApp AI helping a non-technical beginner on an Android phone.
                Project: "$projectName" ($fileCount files).
                Detected issues:
                $topIssues

                Give an encouraging, crystal-clear summary in plain English AND simple Hindi explaining what is wrong and what happens when they tap "Fix Automatically".
            """.trimIndent()

            val response = withTimeout(60_000L) {
                summaryModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty summary.")
            val json = JSONObject(raw)
            Result.success(
                AiBilingualSummary(
                    summaryEn = json.getString("summaryEn"),
                    summaryHi = json.getString("summaryHi")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI summary timed out", e)
            Result.failure(IllegalStateException("AI summary timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI summary failed", e)
            Result.failure(e)
        }
    }

    suspend fun repairProjectWithGemini(
        projectName: String,
        files: List<ProjectFile>,
        detectedIssues: List<ProjectIssue>
    ): Result<AiBatchRepairResult> {
        return try {
            val codeFiles = files.filter {
                val t = it.fileTypeEnum()
                (t == FileType.HTML || t == FileType.CSS || t == FileType.JS) && it.originalContent.isNotBlank()
            }
            if (codeFiles.isEmpty()) {
                return Result.failure(IllegalStateException("No editable HTML, CSS, or JavaScript files found."))
            }

            val filesSection = buildProjectPromptPayload(codeFiles, detectedIssues)
            Log.d(TAG, "Sending ${codeFiles.size} code files and ${detectedIssues.size} detected issues to Gemini for project=$projectName")

            val prompt = """
                You are FixMyApp AI. Repair all HTML, CSS, and JavaScript bugs in the website project below while preserving the site's original brand, text, and working features.
                Project: "$projectName"
                All project files available for asset/path matching: ${files.joinToString(", ") { it.relativePath }}

                $filesSection

                Instructions:
                1. Fix every detected issue across HTML, CSS, and JavaScript (DOCTYPE, charset, viewport, title, alt text, case-sensitive file paths, Windows backslashes, unclosed CSS braces, mobile responsiveness, duplicate IDs if safe, and JavaScript DOM/syntax typos such as getElementByID -> getElementById).
                2. Return the complete repaired file contents for every modified file using the exact relativePath.
                3. Do not omit existing markup or break working elements.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                batchRepairModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Gemini returned an empty repair payload.")
            val json = JSONObject(raw)
            val arr = json.optJSONArray("repairedFiles")
                ?: throw IllegalStateException("Gemini response omitted repairedFiles.")

            val knownPaths = codeFiles.map { it.relativePath }.toSet()
            val repairedList = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val rel = obj.optString("relativePath").trim()
                    val code = obj.optString("repairedCode")
                    if (rel in knownPaths && code.isNotBlank()) {
                        add(AiBatchRepairFile(relativePath = rel, repairedCode = code))
                    }
                }
            }

            Result.success(
                AiBatchRepairResult(
                    repairedFiles = repairedList,
                    summaryEn = json.optString("summaryEn").ifBlank { "Gemini AI repaired ${repairedList.size} file(s)." },
                    summaryHi = json.optString("summaryHi").ifBlank { "Gemini AI ने ${repairedList.size} फ़ाइलें ठीक कीं।" },
                    filesSentCount = codeFiles.size,
                    issuesSentCount = detectedIssues.size
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini batch repair timed out", e)
            Result.failure(IllegalStateException("Gemini repair timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini batch repair failed", e)
            Result.failure(e)
        }
    }

    internal fun buildProjectPromptPayload(
        codeFiles: List<ProjectFile>,
        detectedIssues: List<ProjectIssue>
    ): String {
        val issuesText = if (detectedIssues.isEmpty()) {
            "No issues listed."
        } else {
            detectedIssues.joinToString("\n") { issue ->
                "- [${issue.severity}] ${issue.code} in ${issue.filePath} (line ${issue.lineNumber}): ${issue.titleEn}. Fix: ${issue.fixEn}"
            }
        }
        val filesText = codeFiles.joinToString("\n\n") { file ->
            """
            === FILE: ${file.relativePath} (${file.fileType}) ===
            ${file.originalContent.take(30_000)}
            """.trimIndent()
        }
        return "DETECTED ERRORS (${detectedIssues.size}):\n$issuesText\n\nPROJECT FILES (${codeFiles.size}):\n$filesText"
    }

    suspend fun aiRepairAndValidateFile(
        file: ProjectFile,
        issuesForFile: List<ProjectIssue>,
        allFiles: List<ProjectFile>
    ): Result<AiCodeFixResult> {
        return try {
            val currentCode = file.activeContent()
            val issueBullets = issuesForFile.joinToString("\n") {
                "- Line ${it.lineNumber}: ${it.titleEn} (${it.fixEn})"
            }
            val prompt = """
                You are FixMyApp AI. Repair the following ${file.fileType} file ("${file.relativePath}") safely without breaking existing working functionality or inventing fake features.
                Issues to resolve:
                $issueBullets

                Return the complete repaired file content in "repairedCode" plus brief beginner explanations in English ("explanationEn") and Hindi ("explanationHi").

                CODE:
                $currentCode
            """.trimIndent()

            val response = withTimeout(60_000L) {
                repairModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned empty repair code.")
            val json = JSONObject(raw)
            val candidateCode = json.getString("repairedCode")

            // Validate the AI-repaired code before accepting it!
            val simulatedFiles = allFiles.map {
                if (it.relativePath == file.relativePath) it.copy(repairedContent = candidateCode) else it
            }
            val before = StaticWebsiteInspector.inspect(allFiles, useRepaired = true)
            val after = StaticWebsiteInspector.inspect(simulatedFiles, useRepaired = true)

            val beforeForFile = before.issues.count { it.filePath == file.relativePath }
            val afterForFile = after.issues.count { it.filePath == file.relativePath }
            val validated = afterForFile <= beforeForFile && candidateCode.isNotBlank() &&
                (file.fileTypeEnum() != FileType.JS || after.issues.none { it.filePath == file.relativePath && it.code == "JS_SYNTAX_BRACKET" })

            Result.success(
                AiCodeFixResult(
                    repairedCode = candidateCode,
                    explanationEn = json.getString("explanationEn"),
                    explanationHi = json.getString("explanationHi"),
                    validated = validated
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI code repair timed out", e)
            Result.failure(IllegalStateException("AI repair timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI code repair failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiRepairRepository"
    }
}
