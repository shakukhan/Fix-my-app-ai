package com.example.data

import android.content.Context
import com.example.R
import com.example.engine.ImportedProjectBundle
import com.example.engine.RepairOutcome
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AppLanguage
import com.example.model.DownloadLog
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.ProjectStatus
import com.example.model.UserProfile
import com.example.model.WebsiteProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProjectRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun projectsCol(uid: String) = userDoc(uid).collection("projects")
    private fun filesCol(uid: String, projectId: String) = projectsCol(uid).document(projectId).collection("files")
    private fun issuesCol(uid: String, projectId: String) = projectsCol(uid).document(projectId).collection("issues")
    private fun downloadsCol(uid: String) = userDoc(uid).collection("downloads")

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjects(userId: String): Flow<List<WebsiteProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            projectsCol(userId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap -> snap.toObjects(WebsiteProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjectFiles(userId: String, projectId: String): Flow<List<ProjectFile>> = flow {
        val path = "users/$userId/projects/$projectId/files"
        emitAll(
            filesCol(userId, projectId)
                .snapshots()
                .map { snap -> snap.toObjects(ProjectFile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjectIssues(userId: String, projectId: String): Flow<List<ProjectIssue>> = flow {
        val path = "users/$userId/projects/$projectId/issues"
        emitAll(
            issuesCol(userId, projectId)
                .snapshots()
                .map { snap -> snap.toObjects(ProjectIssue::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDownloads(userId: String): Flow<List<DownloadLog>> = flow {
        val path = "users/$userId/downloads"
        emitAll(
            downloadsCol(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snap -> snap.toObjects(DownloadLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val ref = userDoc(uid)
        val snap = ref.get().await()
        if (!snap.exists()) {
            val profile = UserProfile(userId = uid, displayName = displayName)
            ref.set(profile.toCreateMap(uid, displayName, AppLanguage.EN)).await()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId")
        Result.failure(e)
    }

    suspend fun updatePreferredLanguage(userId: String, language: AppLanguage): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        userDoc(uid).update(
            mapOf(
                "preferredLanguage" to language.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$userId")
        Result.failure(e)
    }

    suspend fun createProjectWithFiles(
        userId: String,
        projectName: String,
        bundle: ImportedProjectBundle
    ): Result<String> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }

        val projectRef = projectsCol(uid).document()
        val projectId = projectRef.id
        val project = WebsiteProject(
            userId = uid,
            name = projectName.trim().ifBlank { bundle.suggestedName }.take(80),
            status = ProjectStatus.UPLOADED.name,
            fileCount = bundle.files.size.toLong(),
            totalBytes = bundle.totalBytes
        )

        val batch = db.batch()
        batch.set(projectRef, project.toCreateMap(uid))

        // Respect Firestore 500-write batch limit cleanly
        for (imported in bundle.files.take(400)) {
            val fileRef = filesCol(uid, projectId).document()
            val model = ProjectFile(
                userId = uid,
                projectId = projectId,
                relativePath = imported.relativePath,
                fileType = imported.fileType.name,
                sizeBytes = imported.sizeBytes,
                originalContent = imported.textContent
            )
            batch.set(fileRef, model.toCreateMap(uid, projectId))
        }
        batch.commit().await()
        Result.success(projectId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "users/$userId/projects")
        Result.failure(e)
    }

    suspend fun saveInspectionResults(
        userId: String,
        projectId: String,
        issues: List<ProjectIssue>,
        aiSummaryEn: String?,
        aiSummaryHi: String?
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        require(projectId.isNotBlank()) { "Project ID cannot be blank." }

        // Clear old issues before writing fresh report
        val existingIssues = issuesCol(uid, projectId).get().await()
        val deleteBatch = db.batch()
        for (doc in existingIssues.documents.take(400)) {
            deleteBatch.delete(doc.reference)
        }
        deleteBatch.commit().await()

        val writeBatch = db.batch()
        for (issue in issues.take(350)) {
            val docRef = issuesCol(uid, projectId).document()
            writeBatch.set(docRef, issue.toCreateMap(uid, projectId))
        }

        val projectUpdates = mutableMapOf<String, Any>(
            "status" to ProjectStatus.INSPECTED.name,
            "errorsDetected" to issues.size.toLong(),
            "remainingErrors" to issues.size.toLong(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!aiSummaryEn.isNullOrBlank()) projectUpdates["aiSummaryEn"] = aiSummaryEn.take(4000)
        if (!aiSummaryHi.isNullOrBlank()) projectUpdates["aiSummaryHi"] = aiSummaryHi.take(4000)

        writeBatch.update(projectsCol(uid).document(projectId), projectUpdates)
        writeBatch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/projects/$projectId")
        Result.failure(e)
    }

    suspend fun saveRepairOutcome(
        userId: String,
        projectId: String,
        outcome: RepairOutcome
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }

        // Replace issue docs with validated resolved + remaining issues
        val existingIssues = issuesCol(uid, projectId).get().await()
        val cleanupBatch = db.batch()
        for (doc in existingIssues.documents.take(400)) {
            cleanupBatch.delete(doc.reference)
        }
        cleanupBatch.commit().await()

        val missingIdMap = if (outcome.updatedFiles.any { it.id.isBlank() && it.repairedContent != null }) {
            filesCol(uid, projectId).get().await().documents
                .mapNotNull { doc ->
                    val path = doc.getString("relativePath")
                    if (path != null) path to doc.id else null
                }
                .toMap()
        } else {
            emptyMap()
        }

        val batch = db.batch()
        for (file in outcome.updatedFiles) {
            val docId = file.id.ifBlank { missingIdMap[file.relativePath].orEmpty() }
            if (docId.isNotBlank() && file.repairedContent != null) {
                batch.update(
                    filesCol(uid, projectId).document(docId),
                    mapOf(
                        "repairedContent" to file.repairedContent.take(350_000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        outcome.promotedRootIndex?.let { promoted ->
            val newFileRef = filesCol(uid, projectId).document()
            batch.set(newFileRef, promoted.toCreateMap(uid, projectId))
        }

        val combinedIssues = outcome.resolvedIssues + outcome.remainingIssues
        for (issue in combinedIssues.take(350)) {
            batch.set(issuesCol(uid, projectId).document(), issue.toCreateMap(uid, projectId))
        }

        batch.update(
            projectsCol(uid).document(projectId),
            mapOf(
                "status" to ProjectStatus.REPAIRED.name,
                "errorsDetected" to outcome.beforeReport.issues.size.toLong(),
                "errorsFixed" to outcome.resolvedIssues.size.toLong(),
                "remainingErrors" to outcome.remainingIssues.size.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/projects/$projectId")
        Result.failure(e)
    }

    suspend fun undoProjectRepairs(
        userId: String,
        projectId: String,
        files: List<ProjectFile>,
        restoredIssues: List<ProjectIssue>
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }

        val existingIssues = issuesCol(uid, projectId).get().await()
        val cleanupBatch = db.batch()
        for (doc in existingIssues.documents.take(400)) {
            cleanupBatch.delete(doc.reference)
        }
        cleanupBatch.commit().await()

        val docIdByPath = if (files.any { it.id.isBlank() }) {
            filesCol(uid, projectId).get().await().documents
                .mapNotNull { doc ->
                    val path = doc.getString("relativePath")
                    if (path != null) path to doc.id else null
                }
                .toMap()
        } else {
            emptyMap()
        }

        val batch = db.batch()
        for (file in files) {
            val docId = file.id.ifBlank { docIdByPath[file.relativePath].orEmpty() }
            if (docId.isNotBlank() && file.repairedContent != null) {
                batch.update(
                    filesCol(uid, projectId).document(docId),
                    mapOf(
                        "repairedContent" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        for (issue in restoredIssues.take(350)) {
            batch.set(issuesCol(uid, projectId).document(), issue.toCreateMap(uid, projectId))
        }

        batch.update(
            projectsCol(uid).document(projectId),
            mapOf(
                "status" to ProjectStatus.INSPECTED.name,
                "errorsDetected" to restoredIssues.size.toLong(),
                "errorsFixed" to 0L,
                "remainingErrors" to restoredIssues.size.toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/projects/$projectId")
        Result.failure(e)
    }

    suspend fun updateSingleFileRepairedContent(
        userId: String,
        projectId: String,
        fileId: String,
        repairedCode: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        filesCol(uid, projectId).document(fileId).update(
            mapOf(
                "repairedContent" to repairedCode.take(350_000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$userId/projects/$projectId/files/$fileId")
        Result.failure(e)
    }

    suspend fun saveDeploymentUrl(
        userId: String,
        projectId: String,
        deployUrl: String
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        projectsCol(uid).document(projectId).update(
            mapOf(
                "status" to ProjectStatus.DEPLOYED.name,
                "deployUrl" to deployUrl.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "users/$userId/projects/$projectId")
        Result.failure(e)
    }

    suspend fun logDownload(
        userId: String,
        projectId: String,
        projectName: String,
        version: String,
        fileCount: Long
    ): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val log = DownloadLog(
            userId = uid,
            projectId = projectId,
            projectName = projectName,
            version = version,
            fileCount = fileCount
        )
        downloadsCol(uid).document().set(log.toCreateMap(uid)).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "users/$userId/downloads")
        Result.failure(e)
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> = try {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val files = filesCol(uid, projectId).get().await()
        val issues = issuesCol(uid, projectId).get().await()
        val batch = db.batch()
        for (doc in files.documents.take(200)) batch.delete(doc.reference)
        for (doc in issues.documents.take(200)) batch.delete(doc.reference)
        batch.delete(projectsCol(uid).document(projectId))
        batch.commit().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/$userId/projects/$projectId")
        Result.failure(e)
    }
}
