package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiRepairRepository
import com.example.data.ProjectRepository
import com.example.engine.ImportedProjectBundle
import com.example.engine.NetlifyDeployer
import com.example.engine.StaticWebsiteInspector
import com.example.engine.WebsiteAutoRepairer
import com.example.engine.ZipProjectImporter
import com.example.model.AppLanguage
import com.example.model.DownloadLog
import com.example.model.IssueCategory
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.ReadinessCheck
import com.example.model.UserProfile
import com.example.model.WebsiteProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StreamState<out T> {
    data object Loading : StreamState<Nothing>
    data class Success<T>(val data: T) : StreamState<T>
    data class Error(val message: String) : StreamState<Nothing>
}

data class WorkspaceUiFeedback(
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    val selectedCategory: IssueCategory? = null,
    val selectedDiffFilePath: String? = null,
    val aiDeepFixNoteEn: String? = null,
    val aiDeepFixNoteHi: String? = null,
    val repairTelemetry: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FixMyAppViewModel(
    private val projectRepo: ProjectRepository,
    private val aiRepo: AiRepairRepository,
    private val currentUserId: String,
    private val currentUserName: String
) : ViewModel() {

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _previewUseRepaired = MutableStateFlow(false)
    val previewUseRepaired: StateFlow<Boolean> = _previewUseRepaired.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.EN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _feedback = MutableStateFlow(WorkspaceUiFeedback())
    val feedback: StateFlow<WorkspaceUiFeedback> = _feedback.asStateFlow()

    private val _cachedProjectsById = MutableStateFlow<Map<String, WebsiteProject>>(emptyMap())
    private val _cachedFilesByProject = MutableStateFlow<Map<String, List<ProjectFile>>>(emptyMap())
    private val _cachedIssuesByProject = MutableStateFlow<Map<String, List<ProjectIssue>>>(emptyMap())
    private val _binaryAssetsByProject = MutableStateFlow<Map<String, Map<String, ByteArray>>>(emptyMap())

    val userProfile: StateFlow<UserProfile?> = projectRepo.observeUserProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Profile observe warning", error)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val projectsState: StateFlow<StreamState<List<WebsiteProject>>> =
        projectRepo.observeProjects(currentUserId)
            .map<List<WebsiteProject>, StreamState<List<WebsiteProject>>> { list ->
                if (list.isNotEmpty()) {
                    _cachedProjectsById.update { existing ->
                        existing + list.associateBy { it.id }
                    }
                    if (_selectedProjectId.value == null) {
                        _selectedProjectId.value = list.first().id
                    }
                }
                StreamState.Success(list)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing projects", root)
                emit(StreamState.Error(root.message ?: "Failed to load projects"))
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, StreamState.Loading)

    val activeProject: StateFlow<WebsiteProject?> = combine(
        _selectedProjectId,
        projectsState,
        _cachedProjectsById
    ) { selectedId, state, cachedMap ->
        val remoteList = (state as? StreamState.Success)?.data.orEmpty()
        when {
            !selectedId.isNullOrBlank() ->
                remoteList.firstOrNull { it.id == selectedId } ?: cachedMap[selectedId]
            remoteList.isNotEmpty() -> remoteList.first()
            else -> cachedMap.values.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val activeBinaryAssets: StateFlow<Map<String, ByteArray>> = combine(
        _selectedProjectId,
        _binaryAssetsByProject
    ) { pid, map ->
        if (pid.isNullOrBlank()) emptyMap() else map[pid].orEmpty()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val downloadsState: StateFlow<StreamState<List<DownloadLog>>> =
        projectRepo.observeDownloads(currentUserId)
            .map<List<DownloadLog>, StreamState<List<DownloadLog>>> { StreamState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(StreamState.Error((fsErr ?: error).message ?: "Failed to load downloads"))
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, StreamState.Loading)

    val activeFiles: StateFlow<List<ProjectFile>> = _selectedProjectId
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) flowOf(emptyList())
            else projectRepo.observeProjectFiles(currentUserId, pid).map { remoteFiles ->
                if (remoteFiles.isNotEmpty()) {
                    _cachedFilesByProject.update { it + (pid to remoteFiles) }
                    remoteFiles
                } else {
                    _cachedFilesByProject.value[pid].orEmpty()
                }
            }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing files", error)
            val pid = _selectedProjectId.value
            if (!pid.isNullOrBlank()) {
                emit(_cachedFilesByProject.value[pid].orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeIssues: StateFlow<List<ProjectIssue>> = _selectedProjectId
        .flatMapLatest { pid ->
            if (pid.isNullOrBlank()) flowOf(emptyList())
            else projectRepo.observeProjectIssues(currentUserId, pid).map { remoteIssues ->
                if (remoteIssues.isNotEmpty()) {
                    _cachedIssuesByProject.update { it + (pid to remoteIssues) }
                    remoteIssues
                } else {
                    _cachedIssuesByProject.value[pid].orEmpty()
                }
            }
        }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing issues", error)
            val pid = _selectedProjectId.value
            if (!pid.isNullOrBlank()) {
                emit(_cachedIssuesByProject.value[pid].orEmpty())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            projectRepo.ensureUserProfile(currentUserId, currentUserName)
        }
        viewModelScope.launch {
            userProfile.collect { prof ->
                if (prof != null) {
                    _language.value = AppLanguage.safeValueOf(prof.preferredLanguage)
                }
            }
        }
    }

    fun selectProject(projectId: String) {
        if (_selectedProjectId.value != projectId) {
            _previewUseRepaired.value = false
        }
        _selectedProjectId.value = projectId
        _feedback.update {
            it.copy(
                selectedCategory = null,
                selectedDiffFilePath = null,
                bannerMessage = null,
                repairTelemetry = null
            )
        }
    }

    fun setPreviewUseRepaired(useRepaired: Boolean) {
        _previewUseRepaired.value = useRepaired
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.EN) AppLanguage.HI else AppLanguage.EN
        _language.value = next
        viewModelScope.launch {
            projectRepo.updatePreferredLanguage(currentUserId, next)
        }
    }

    fun setCategoryFilter(category: IssueCategory?) {
        _feedback.update { it.copy(selectedCategory = category) }
    }

    fun selectDiffFile(relativePath: String) {
        _feedback.update { it.copy(selectedDiffFilePath = relativePath) }
    }

    fun clearBanner() {
        _feedback.update { it.copy(bannerMessage = null, isBannerError = false) }
    }

    fun showInfoBanner(message: String, isError: Boolean = false) {
        _feedback.update { it.copy(bannerMessage = message, isBannerError = isError) }
    }

    fun uploadFromUris(context: Context, uris: List<Uri>, customName: String) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            setBusy(true, if (_language.value == AppLanguage.HI) "फ़ाइलें सुरक्षित रूप से जाँची जा रही हैं..." else "Validating & uploading project...")
            val bundleResult = ZipProjectImporter.importFromUris(context, uris)
            bundleResult.onFailure { err ->
                setBusy(false)
                showInfoBanner(err.message ?: "Upload failed.", isError = true)
            }.onSuccess { bundle ->
                saveAndInspectBundle(bundle, customName)
            }
        }
    }

    fun loadSampleBrokenProject() {
        viewModelScope.launch {
            setBusy(true, if (_language.value == AppLanguage.HI) "सैंपल प्रोजेक्ट बनाया जा रहा है..." else "Creating sample broken website...")
            val bundle = ZipProjectImporter.createSampleBrokenProjectFiles()
            saveAndInspectBundle(bundle, bundle.suggestedName)
        }
    }

    private suspend fun saveAndInspectBundle(bundle: ImportedProjectBundle, customName: String) {
        val name = customName.trim().ifBlank { bundle.suggestedName }
        val createRes = projectRepo.createProjectWithFiles(currentUserId, name, bundle)
        createRes.onFailure { err ->
            setBusy(false)
            showInfoBanner("Could not save project: ${err.localizedMessage}", isError = true)
        }.onSuccess { projectId ->
            _previewUseRepaired.value = false
            val tempProject = WebsiteProject(
                id = projectId,
                userId = currentUserId,
                name = name.take(80),
                fileCount = bundle.files.size.toLong(),
                totalBytes = bundle.totalBytes
            )
            _cachedProjectsById.update { it + (projectId to tempProject) }
            val binaries = bundle.files
                .mapNotNull { file -> file.rawBytes?.let { file.relativePath to it } }
                .toMap()
            if (binaries.isNotEmpty()) {
                _binaryAssetsByProject.update { it + (projectId to binaries) }
            }
            val tempFiles = bundle.files.map {
                ProjectFile(
                    userId = currentUserId,
                    projectId = projectId,
                    relativePath = it.relativePath,
                    fileType = it.fileType.name,
                    sizeBytes = it.sizeBytes,
                    originalContent = it.textContent
                )
            }
            _cachedFilesByProject.update { it + (projectId to tempFiles) }
            _selectedProjectId.value = projectId
            runInspectionInternal(projectId, name, tempFiles)
        }
    }

    fun inspectActiveProject(project: WebsiteProject) {
        val files = activeFiles.value
        if (files.isEmpty()) {
            showInfoBanner("No project files loaded yet.", isError = true)
            return
        }
        viewModelScope.launch {
            runInspectionInternal(project.id, project.name, files)
        }
    }

    private suspend fun runInspectionInternal(
        projectId: String,
        projectName: String,
        files: List<ProjectFile>
    ) {
        setBusy(true, if (_language.value == AppLanguage.HI) "HTML, CSS, JS और AI जाँच जारी है..." else "Inspecting HTML, CSS, JS & AI insights...")
        val useRepaired = files.any { it.repairedContent != null }
        val staticReport = StaticWebsiteInspector.inspect(files, useRepaired = useRepaired, currentUserId, projectId)
        _cachedIssuesByProject.update { it + (projectId to staticReport.issues) }
        _cachedProjectsById.update { map ->
            val current = map[projectId] ?: WebsiteProject(
                id = projectId,
                userId = currentUserId,
                name = projectName,
                fileCount = files.size.toLong()
            )
            map + (projectId to current.copy(
                status = com.example.model.ProjectStatus.INSPECTED.name,
                errorsDetected = staticReport.issues.size.toLong(),
                remainingErrors = staticReport.issues.count { !it.fixed }.toLong()
            ))
        }

        var aiEn: String? = null
        var aiHi: String? = null
        val aiRes = aiRepo.generateBilingualSummary(projectName, files.size, staticReport.issues)
        aiRes.onSuccess { summary ->
            aiEn = summary.summaryEn
            aiHi = summary.summaryHi
        }

        val saveRes = projectRepo.saveInspectionResults(
            userId = currentUserId,
            projectId = projectId,
            issues = staticReport.issues,
            aiSummaryEn = aiEn,
            aiSummaryHi = aiHi
        )
        setBusy(false)

        saveRes.onFailure { err ->
            showInfoBanner("Failed to save inspection: ${err.localizedMessage}", isError = true)
        }.onSuccess {
            val aiNote = aiRes.exceptionOrNull()?.let { " (AI note unavailable: ${it.localizedMessage})" }.orEmpty()
            val msg = if (_language.value == AppLanguage.HI) {
                "जाँच पूरी हुई: ${staticReport.issues.size} समस्याएँ मिलीं।$aiNote"
            } else {
                "Inspection complete: ${staticReport.issues.size} issue(s) detected.$aiNote"
            }
            showInfoBanner(msg, isError = false)
        }
    }

    fun repairActiveProject(project: WebsiteProject) {
        val files = activeFiles.value
        if (files.isEmpty()) {
            showInfoBanner("No files found to repair.", isError = true)
            return
        }
        viewModelScope.launch {
            val baselineReport = StaticWebsiteInspector.inspect(files, useRepaired = false, currentUserId, project.id)
            setBusy(
                true,
                if (_language.value == AppLanguage.HI) {
                    "Gemini AI को ${files.size} फ़ाइलें और ${baselineReport.issues.size} त्रुटियाँ भेजी जा रही हैं..."
                } else {
                    "Sending ${files.size} project files & ${baselineReport.issues.size} errors to Gemini AI..."
                }
            )

            val geminiRes = aiRepo.repairProjectWithGemini(project.name, files, baselineReport.issues)
            val geminiOverrides = geminiRes.getOrNull()?.repairedFiles
                ?.associate { it.relativePath to it.repairedCode }
                .orEmpty()

            val outcome = WebsiteAutoRepairer.repairAndValidate(
                files = files,
                userId = currentUserId,
                projectId = project.id,
                geminiOverrides = geminiOverrides
            )
            val repairedList = if (outcome.promotedRootIndex != null) {
                outcome.updatedFiles + outcome.promotedRootIndex
            } else {
                outcome.updatedFiles
            }
            _cachedFilesByProject.update { it + (project.id to repairedList) }
            _cachedIssuesByProject.update { it + (project.id to (outcome.resolvedIssues + outcome.remainingIssues)) }
            _cachedProjectsById.update { map ->
                val current = map[project.id] ?: project
                map + (project.id to current.copy(
                    status = com.example.model.ProjectStatus.REPAIRED.name,
                    errorsDetected = outcome.beforeReport.issues.size.toLong(),
                    errorsFixed = outcome.resolvedIssues.size.toLong(),
                    remainingErrors = outcome.remainingIssues.size.toLong()
                ))
            }

            val modifiedNames = outcome.updatedFiles.filter { it.hasChanges() }.map { it.relativePath }
            val telemetry = geminiRes.fold(
                onSuccess = { batch ->
                    "Gemini AI received ${batch.filesSentCount} code files & ${batch.issuesSentCount} errors • Modified: ${modifiedNames.joinToString(", ").ifBlank { "none" }} • Validated & saved."
                },
                onFailure = { err ->
                    "Verified local repair saved (${modifiedNames.joinToString(", ").ifBlank { "no changes" }}). Gemini fallback reason: ${err.localizedMessage}"
                }
            )

            val saveRes = projectRepo.saveRepairOutcome(currentUserId, project.id, outcome)
            setBusy(false)
            saveRes.onFailure { err ->
                showInfoBanner("Repair save failed: ${err.localizedMessage}", isError = true)
            }.onSuccess {
                _previewUseRepaired.value = true
                val firstDiff = modifiedNames.firstOrNull()
                _feedback.update {
                    it.copy(
                        selectedDiffFilePath = firstDiff,
                        repairTelemetry = telemetry
                    )
                }
                val msg = if (_language.value == AppLanguage.HI) {
                    "सुधार सहेजा गया! ${modifiedNames.size} फ़ाइलें बदली गईं (${outcome.resolvedIssues.size} समस्याएँ ठीक, ${outcome.remainingIssues.size} शेष)।"
                } else {
                    "Repaired & saved! ${modifiedNames.size} file(s) modified (${outcome.resolvedIssues.size} fixed, ${outcome.remainingIssues.size} remaining)."
                }
                showInfoBanner(msg, isError = false)
            }
        }
    }

    fun undoChanges(project: WebsiteProject) {
        val files = activeFiles.value
        if (files.isEmpty()) return
        viewModelScope.launch {
            setBusy(
                true,
                if (_language.value == AppLanguage.HI) "मूल फ़ाइलें वापस लाई जा रही हैं..." else "Restoring original uploaded files..."
            )
            val restoredFiles = files.map { it.copy(repairedContent = null) }
            val baselineReport = StaticWebsiteInspector.inspect(restoredFiles, useRepaired = false, currentUserId, project.id)

            _cachedFilesByProject.update { it + (project.id to restoredFiles) }
            _cachedIssuesByProject.update { it + (project.id to baselineReport.issues) }
            _cachedProjectsById.update { map ->
                val current = map[project.id] ?: project
                map + (project.id to current.copy(
                    status = com.example.model.ProjectStatus.INSPECTED.name,
                    errorsDetected = baselineReport.issues.size.toLong(),
                    errorsFixed = 0L,
                    remainingErrors = baselineReport.issues.size.toLong()
                ))
            }
            _previewUseRepaired.value = false

            val undoRes = projectRepo.undoProjectRepairs(
                userId = currentUserId,
                projectId = project.id,
                files = files,
                restoredIssues = baselineReport.issues
            )
            setBusy(false)
            undoRes.onFailure { err ->
                showInfoBanner("Undo failed: ${err.localizedMessage}", isError = true)
            }.onSuccess {
                _feedback.update {
                    it.copy(
                        selectedDiffFilePath = null,
                        repairTelemetry = null
                    )
                }
                val msg = if (_language.value == AppLanguage.HI) {
                    "मूल फ़ाइलें सफलतापूर्वक वापस आ गई हैं (${baselineReport.issues.size} त्रुटियाँ पुनः सक्रिय)।"
                } else {
                    "Undo complete: original files restored (${baselineReport.issues.size} issues active)."
                }
                showInfoBanner(msg, isError = false)
            }
        }
    }

    fun runAiDeepFixForFile(project: WebsiteProject, file: ProjectFile) {
        val allFiles = activeFiles.value
        val fileIssues = activeIssues.value.filter { it.filePath == file.relativePath && !it.fixed }
        viewModelScope.launch {
            setBusy(true, if (_language.value == AppLanguage.HI) "Gemini AI से स्मार्ट सुधार हो रहा है..." else "Running Gemini AI Deep Fix & validation...")
            val aiRes = aiRepo.aiRepairAndValidateFile(file, fileIssues, allFiles)
            aiRes.onFailure { err ->
                setBusy(false)
                showInfoBanner("AI Deep Fix failed: ${err.localizedMessage}", isError = true)
            }.onSuccess { fix ->
                if (!fix.validated) {
                    setBusy(false)
                    showInfoBanner(
                        "AI generated a candidate fix, but post-repair validation detected remaining syntax risks. Change was not auto-applied.",
                        isError = true
                    )
                    return@onSuccess
                }
                val updatedList = allFiles.map {
                    if (it.id == file.id) it.copy(repairedContent = fix.repairedCode) else it
                }
                _cachedFilesByProject.update { it + (project.id to updatedList) }
                val reInspect = StaticWebsiteInspector.inspect(updatedList, useRepaired = true, currentUserId, project.id)
                _cachedIssuesByProject.update { it + (project.id to reInspect.issues) }
                projectRepo.updateSingleFileRepairedContent(currentUserId, project.id, file.id, fix.repairedCode)
                projectRepo.saveInspectionResults(
                    userId = currentUserId,
                    projectId = project.id,
                    issues = reInspect.issues,
                    aiSummaryEn = project.aiSummaryEn,
                    aiSummaryHi = project.aiSummaryHi
                )
                _previewUseRepaired.value = true
                setBusy(false)
                _feedback.update {
                    it.copy(
                        selectedDiffFilePath = file.relativePath,
                        aiDeepFixNoteEn = fix.explanationEn,
                        aiDeepFixNoteHi = fix.explanationHi,
                        bannerMessage = if (_language.value == AppLanguage.HI) fix.explanationHi else fix.explanationEn,
                        isBannerError = false
                    )
                }
            }
        }
    }

    fun exportZipToUri(context: Context, project: WebsiteProject, uri: Uri, repairedVersion: Boolean) {
        val files = activeFiles.value
        if (files.isEmpty()) return
        viewModelScope.launch {
            setBusy(true, "Creating ZIP package...")
            val zipBytes = ZipProjectImporter.exportProjectZipBytes(
                files = files,
                useRepaired = repairedVersion,
                binaryAssets = activeBinaryAssets.value
            )
            val writeRes = ZipProjectImporter.writeZipToUri(context, uri, zipBytes)
            writeRes.onFailure { err ->
                setBusy(false)
                showInfoBanner("Export failed: ${err.localizedMessage}", isError = true)
            }.onSuccess {
                projectRepo.logDownload(
                    userId = currentUserId,
                    projectId = project.id,
                    projectName = project.name,
                    version = if (repairedVersion) "REPAIRED" else "ORIGINAL",
                    fileCount = files.size.toLong()
                )
                setBusy(false)
                val msg = if (_language.value == AppLanguage.HI) {
                    "ZIP फ़ाइल सफलतापूर्वक सेव हो गई!"
                } else {
                    "ZIP saved successfully and logged in Download History!"
                }
                showInfoBanner(msg, isError = false)
            }
        }
    }

    fun deployToNetlify(project: WebsiteProject, token: String) {
        val files = activeFiles.value
        if (files.isEmpty()) {
            showInfoBanner("Upload and inspect a project first.", isError = true)
            return
        }
        viewModelScope.launch {
            setBusy(true, if (_language.value == AppLanguage.HI) "Netlify पर वेबसाइट पब्लिश हो रही है..." else "Deploying verified bundle to Netlify...")
            val useRepaired = files.any { it.repairedContent != null }
            val result = NetlifyDeployer.deployToNetlify(token, project.name, files, useRepaired)
            result.onFailure { err ->
                setBusy(false)
                showInfoBanner(err.localizedMessage ?: "Netlify deployment failed.", isError = true)
            }.onSuccess { liveUrl ->
                projectRepo.saveDeploymentUrl(currentUserId, project.id, liveUrl)
                setBusy(false)
                showInfoBanner("Live website published: $liveUrl", isError = false)
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = projectRepo.deleteProject(currentUserId, projectId)
            res.onFailure { err ->
                showInfoBanner("Delete failed: ${err.localizedMessage}", isError = true)
            }.onSuccess {
                if (_selectedProjectId.value == projectId) {
                    _selectedProjectId.value = null
                }
                showInfoBanner("Project deleted.", isError = false)
            }
        }
    }

    fun computeReadiness(files: List<ProjectFile>): List<ReadinessCheck> {
        if (files.isEmpty()) return emptyList()
        val useRepaired = files.any { it.repairedContent != null }
        return StaticWebsiteInspector.inspect(files, useRepaired).readinessChecks
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        _feedback.update { it.copy(isBusy = busy, busyMessage = message) }
    }

    private companion object {
        const val TAG = "FixMyAppVM"
    }
}
