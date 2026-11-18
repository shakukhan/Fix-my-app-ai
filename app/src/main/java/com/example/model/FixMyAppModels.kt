package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage {
    EN,
    HI;

    companion object {
        fun safeValueOf(raw: String?): AppLanguage =
            entries.firstOrNull { it.name == raw } ?: EN
    }
}

enum class ProjectStatus(val labelEn: String, val labelHi: String) {
    UPLOADED("Uploaded", "अपलोड हुआ"),
    INSPECTED("Inspected", "जाँच पूर्ण"),
    REPAIRED("Repaired", "सुधार पूर्ण"),
    DEPLOYED("Deployed", "लाइव हुआ");

    companion object {
        fun safeValueOf(raw: String?): ProjectStatus =
            entries.firstOrNull { it.name == raw } ?: UPLOADED
    }
}

enum class FileType {
    HTML,
    CSS,
    JS,
    ASSET,
    OTHER;

    companion object {
        fun safeValueOf(raw: String?): FileType =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class IssueCategory(val labelEn: String, val labelHi: String) {
    HTML("HTML", "HTML"),
    CSS("CSS", "CSS"),
    JS("JavaScript", "JavaScript"),
    PATHS("File Paths", "फ़ाइल पाथ"),
    ASSETS("Assets", "इमेज/फ़ाइलें"),
    RESPONSIVE("Mobile", "मोबाइल"),
    NAVIGATION("Navigation", "नेविगेशन"),
    DEPLOYMENT("Deploy", "पब्लिश");

    companion object {
        fun safeValueOf(raw: String?): IssueCategory =
            entries.firstOrNull { it.name == raw } ?: HTML
    }
}

enum class IssueSeverity(val labelEn: String, val labelHi: String) {
    CRITICAL("Critical", "गंभीर"),
    WARNING("Warning", "चेतावनी"),
    INFO("Tip", "सुझाव");

    companion object {
        fun safeValueOf(raw: String?): IssueSeverity =
            entries.firstOrNull { it.name == raw } ?: INFO
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredLanguage: String = AppLanguage.EN.name,
    val plan: String = "FREE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String, name: String, language: AppLanguage): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to name.take(80).ifBlank { "FixMyApp Creator" },
        "preferredLanguage" to language.name,
        "plan" to "FREE",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class WebsiteProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val status: String = ProjectStatus.UPLOADED.name,
    val fileCount: Long = 0L,
    val totalBytes: Long = 0L,
    val errorsDetected: Long = 0L,
    val errorsFixed: Long = 0L,
    val remainingErrors: Long = 0L,
    val deployUrl: String? = null,
    val aiSummaryEn: String? = null,
    val aiSummaryHi: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): ProjectStatus = ProjectStatus.safeValueOf(status)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.take(80).ifBlank { "Website Project" },
        "status" to statusEnum().name,
        "fileCount" to fileCount.coerceIn(0L, 500L),
        "totalBytes" to totalBytes.coerceIn(0L, 26_214_400L),
        "errorsDetected" to errorsDetected.coerceIn(0L, 10_000L),
        "errorsFixed" to errorsFixed.coerceIn(0L, 10_000L),
        "remainingErrors" to remainingErrors.coerceIn(0L, 10_000L),
        "deployUrl" to deployUrl?.take(500),
        "aiSummaryEn" to aiSummaryEn?.take(4000),
        "aiSummaryHi" to aiSummaryHi?.take(4000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ProjectFile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val relativePath: String = "",
    val fileType: String = FileType.OTHER.name,
    val sizeBytes: Long = 0L,
    val originalContent: String = "",
    val repairedContent: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun fileTypeEnum(): FileType = FileType.safeValueOf(fileType)

    fun activeContent(): String = repairedContent ?: originalContent

    fun hasChanges(): Boolean = repairedContent != null && repairedContent != originalContent

    fun toCreateMap(uid: String, parentProjectId: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to parentProjectId,
        "relativePath" to relativePath.take(200),
        "fileType" to fileTypeEnum().name,
        "sizeBytes" to sizeBytes.coerceIn(0L, 26_214_400L),
        "originalContent" to originalContent.take(300_000),
        "repairedContent" to repairedContent?.take(350_000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class ProjectIssue(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val code: String = "",
    val category: String = IssueCategory.HTML.name,
    val severity: String = IssueSeverity.INFO.name,
    val filePath: String = "",
    val lineNumber: Long = 1L,
    val autoFixable: Boolean = false,
    val fixed: Boolean = false,
    val titleEn: String = "",
    val descEn: String = "",
    val fixEn: String = "",
    val titleHi: String = "",
    val descHi: String = "",
    val fixHi: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): IssueCategory = IssueCategory.safeValueOf(category)
    fun severityEnum(): IssueSeverity = IssueSeverity.safeValueOf(severity)

    fun toCreateMap(uid: String, parentProjectId: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to parentProjectId,
        "code" to code.take(80).ifBlank { "GENERAL_ISSUE" },
        "category" to categoryEnum().name,
        "severity" to severityEnum().name,
        "filePath" to filePath.take(200).ifBlank { "Project Root" },
        "lineNumber" to lineNumber.coerceIn(1L, 100_000L),
        "autoFixable" to autoFixable,
        "fixed" to fixed,
        "titleEn" to titleEn.take(300).ifBlank { "Issue detected" },
        "descEn" to descEn.take(1500).ifBlank { "Review this code section." },
        "fixEn" to fixEn.take(1500).ifBlank { "Apply the recommended fix." },
        "titleHi" to titleHi.take(300).ifBlank { "समस्या मिली" },
        "descHi" to descHi.take(1500).ifBlank { "इस कोड हिस्से की जाँच करें।" },
        "fixHi" to fixHi.take(1500).ifBlank { "सुझाया गया सुधार लागू करें।" },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class DownloadLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val projectName: String = "",
    val version: String = "REPAIRED",
    val fileCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "projectId" to projectId,
        "projectName" to projectName.take(80).ifBlank { "Website Project" },
        "version" to if (version == "ORIGINAL") "ORIGINAL" else "REPAIRED",
        "fileCount" to fileCount.coerceIn(0L, 500L),
        "createdAt" to FieldValue.serverTimestamp()
    )
}

data class ReadinessCheck(
    val key: String,
    val passed: Boolean,
    val labelEn: String,
    val labelHi: String
)
