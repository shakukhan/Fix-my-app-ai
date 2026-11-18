package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLanguage
import com.example.model.IssueCategory
import com.example.model.IssueSeverity
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.WebsiteProject
import com.example.ui.components.FixMyAppOutlinedTextField
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusCriticalBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg

@Composable
fun WorkspaceScreen(
    language: AppLanguage,
    activeProject: WebsiteProject?,
    files: List<ProjectFile>,
    issues: List<ProjectIssue>,
    feedback: WorkspaceUiFeedback,
    onUploadUris: (List<Uri>, String) -> Unit,
    onLoadSample: () -> Unit,
    onInspect: (WebsiteProject) -> Unit,
    onRepair: (WebsiteProject) -> Unit,
    onUndo: (WebsiteProject) -> Unit,
    onAiDeepFix: (WebsiteProject, ProjectFile) -> Unit,
    onExportZip: (WebsiteProject, Uri, Boolean) -> Unit,
    onSelectCategory: (IssueCategory?) -> Unit,
    onSelectDiffFile: (String) -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToDeploy: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val context = LocalContext.current
    val customProjectNameState = rememberTextFieldState()
    var pendingExportRepaired by remember { mutableStateOf(true) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadUris(listOf(uri), customProjectNameState.text.toString())
        }
    }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onUploadUris(uris, customProjectNameState.text.toString())
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null && activeProject != null) {
            onExportZip(activeProject, destUri, pendingExportRepaired)
        }
    }

    val isHindi = language == AppLanguage.HI
    val filteredIssues = remember(issues, feedback.selectedCategory) {
        if (feedback.selectedCategory == null) issues
        else issues.filter { it.categoryEnum() == feedback.selectedCategory }
    }
    val repairedFiles = remember(files) { files.filter { it.hasChanges() } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
    ) {
        // Hero Banner Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(BrandPrimary, BrandViolet)))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_repair_1794934584349),
                                contentDescription = "Website Repair Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHindi) "बिना कोडिंग के अपनी वेबसाइट ठीक करें।" else "Fix Your Website Without Coding.",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isHindi) "अपलोड • जाँच • सुधार • पब्लिश (अधिकतम 25 MB)" else "Upload. Check. Fix. Deploy. • Max 25 MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFDDE3FF)
                                )
                            }
                        }
                        Text(
                            text = if (isHindi) {
                                "अपनी वेबसाइट की ZIP या HTML, CSS, JavaScript फ़ाइलें चुनें। हम सुरक्षित जांच, बैकअप और सत्यापित सुधार करते हैं।"
                            } else {
                                "Select a website ZIP or HTML/CSS/JS files from your phone. We preserve backups, explain bugs simply, and verify every repair."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFEEF2FF)
                        )
                    }
                }
            }
        }

        // Busy indicator / Status Banner
        item {
            AnimatedVisibility(visible = feedback.isBusy) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = feedback.busyMessage ?: "Processing...",
                            style = MaterialTheme.typography.labelLarge
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            AnimatedVisibility(visible = feedback.bannerMessage != null && !feedback.isBusy) {
                Surface(
                    color = if (feedback.isBannerError) StatusCriticalBg else StatusSuccessBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = feedback.bannerMessage.orEmpty(),
                            color = if (feedback.isBannerError) StatusCritical else StatusSuccess,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissBanner) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // Upload Project Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isHindi) "प्रोजेक्ट अपलोड करें" else "Upload Website Project",
                        style = MaterialTheme.typography.titleLarge
                    )

                    FixMyAppOutlinedTextField(
                        state = customProjectNameState,
                        labelText = if (isHindi) "प्रोजेक्ट का नाम (वैकल्पिक)" else "Optional Project Name (e.g., My Bakery)",
                        maxLength = 80,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        testTag = "project_name_input",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                            },
                            enabled = !feedback.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("upload_zip_button")
                        ) {
                            Icon(Icons.Filled.FolderZip, contentDescription = "Upload ZIP", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (isHindi) "ZIP अपलोड" else "Upload ZIP")
                        }

                        OutlinedButton(
                            onClick = {
                                multiFilePicker.launch(
                                    arrayOf("text/html", "text/css", "application/javascript", "text/javascript", "text/plain", "*/*")
                                )
                            },
                            enabled = !feedback.isBusy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("upload_files_button")
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = "Upload HTML/CSS/JS", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("HTML/CSS/JS")
                        }
                    }

                    OutlinedButton(
                        onClick = onLoadSample,
                        enabled = !feedback.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("try_sample_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Try Sweet Bakery Demo", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (isHindi) "Sweet Bakery डेमो आज़माएँ (1-टैप टेस्ट)" else "Try Sweet Bakery Demo (1-Tap Test)"
                        )
                    }
                }
            }
        }

        // Active Project Workspace
        if (activeProject != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeProject.name,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "${activeProject.fileCount} files • ${formatBytes(activeProject.totalBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = if (isHindi) activeProject.statusEnum().labelHi else activeProject.statusEnum().labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Summary Metric Tiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiniStatTile(
                                label = if (isHindi) "मिली गलतियाँ" else "Detected",
                                value = activeProject.errorsDetected.toString(),
                                color = StatusCritical,
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatTile(
                                label = if (isHindi) "सुधारी गईं" else "Fixed",
                                value = activeProject.errorsFixed.toString(),
                                color = StatusSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatTile(
                                label = if (isHindi) "शेष" else "Remaining",
                                value = activeProject.remainingErrors.toString(),
                                color = StatusWarning,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onInspect(activeProject) },
                                enabled = !feedback.isBusy,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("inspect_project_button")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Inspect Project", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isHindi) "जाँचें" else "Inspect")
                            }

                            Button(
                                onClick = { onRepair(activeProject) },
                                enabled = !feedback.isBusy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandSecondary),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("fix_project_button")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "Fix Automatically", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isHindi) "अपने आप ठीक करें" else "Fix Automatically")
                            }
                        }

                        val hasRepaired = files.any { it.hasChanges() || it.repairedContent != null }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    pendingExportRepaired = true
                                    val slug = activeProject.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                                    exportZipLauncher.launch("${slug.ifBlank { "website" }}-fixed.zip")
                                },
                                enabled = !feedback.isBusy && hasRepaired,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_fixed_button")
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Download Fixed ZIP", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isHindi) "सुधरा ZIP डाउनलोड" else "Download Fixed ZIP")
                            }

                            OutlinedButton(
                                onClick = { onUndo(activeProject) },
                                enabled = !feedback.isBusy && hasRepaired,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("undo_changes_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo Changes", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isHindi) "बदलाव वापस लें" else "Undo Changes")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingExportRepaired = false
                                    val slug = activeProject.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                                    exportZipLauncher.launch("${slug.ifBlank { "website" }}-backup.zip")
                                },
                                enabled = !feedback.isBusy && files.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("download_backup_button")
                            ) {
                                Text(if (isHindi) "मूल बैकअप ZIP" else "Backup Original ZIP")
                            }
                        }

                        val telemetry = feedback.repairTelemetry
                        if (!telemetry.isNullOrBlank()) {
                            val isFallback = telemetry.contains("fallback", ignoreCase = true)
                            Surface(
                                color = if (isFallback) StatusWarningBg else StatusSuccessBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("repair_telemetry_banner")
                            ) {
                                Text(
                                    text = telemetry,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFallback) StatusWarning else StatusSuccess,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onNavigateToPreview,
                                modifier = Modifier.testTag("go_to_preview_button")
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = "Preview Website", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    when {
                                        hasRepaired && isHindi -> "सुधरी वेबसाइट प्रीव्यू करें →"
                                        hasRepaired -> "Preview Fixed Website →"
                                        isHindi -> "लाइव प्रीव्यू खोलें →"
                                        else -> "Open Live Preview →"
                                    }
                                )
                            }

                            TextButton(
                                onClick = onNavigateToDeploy,
                                modifier = Modifier.testTag("go_to_deploy_button")
                            ) {
                                Icon(Icons.Filled.RocketLaunch, contentDescription = "Deployment Assistant", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(if (isHindi) "डिप्लॉय असिस्टेंट →" else "Deploy →")
                            }
                        }
                    }
                }
            }

            // Gemini AI Bilingual Summary Card
            val aiText = if (isHindi) activeProject.aiSummaryHi else activeProject.aiSummaryEn
            if (!aiText.isNullOrBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Summary", tint = BrandPrimary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (isHindi) "FixMyApp AI सारांश" else "FixMyApp AI Summary",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = aiText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Category Filter Chips + Inspector Report Header
            if (issues.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isHindi) "वेबसाइट इंस्पेक्टर रिपोर्ट (${filteredIssues.size})" else "Website Inspector Report (${filteredIssues.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = feedback.selectedCategory == null,
                                onClick = { onSelectCategory(null) },
                                label = { Text(if (isHindi) "सभी" else "All") }
                            )
                            IssueCategory.entries.forEach { cat ->
                                val count = issues.count { it.categoryEnum() == cat }
                                if (count > 0) {
                                    FilterChip(
                                        selected = feedback.selectedCategory == cat,
                                        onClick = { onSelectCategory(cat) },
                                        label = {
                                            Text("${if (isHindi) cat.labelHi else cat.labelEn} ($count)")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredIssues, key = { it.id.ifBlank { "${it.code}_${it.filePath}_${it.lineNumber}_${it.titleEn}" } }) { issue ->
                    val targetFile = files.firstOrNull { it.relativePath == issue.filePath }
                    IssueReportCard(
                        issue = issue,
                        isHindi = isHindi,
                        canAiDeepFix = !issue.fixed && targetFile != null && !feedback.isBusy,
                        onAiDeepFix = {
                            if (targetFile != null) onAiDeepFix(activeProject, targetFile)
                        }
                    )
                }
            }

            // Original vs Repaired Code Diff Viewer
            if (repairedFiles.isNotEmpty()) {
                item {
                    val selectedPath = feedback.selectedDiffFilePath
                        ?.takeIf { path -> repairedFiles.any { it.relativePath == path } }
                        ?: repairedFiles.first().relativePath
                    val activeDiffFile = repairedFiles.first { it.relativePath == selectedPath }

                    CodeDiffReviewCard(
                        repairedFiles = repairedFiles,
                        activeDiffFile = activeDiffFile,
                        isHindi = isHindi,
                        onSelectFile = onSelectDiffFile
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStatTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IssueReportCard(
    issue: ProjectIssue,
    isHindi: Boolean,
    canAiDeepFix: Boolean,
    onAiDeepFix: () -> Unit
) {
    val sev = issue.severityEnum()
    val accentColor = when {
        issue.fixed -> StatusSuccess
        sev == IssueSeverity.CRITICAL -> StatusCritical
        sev == IssueSeverity.WARNING -> StatusWarning
        else -> BrandSecondary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${issue.filePath} • Line ${issue.lineNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (issue.fixed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = if (issue.fixed) {
                                if (isHindi) "सत्यापित सुधार" else "VERIFIED FIXED"
                            } else {
                                if (isHindi) sev.labelHi else sev.labelEn
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                    }
                }
            }

            Text(
                text = if (isHindi) issue.titleHi else issue.titleEn,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (isHindi) issue.descHi else issue.descEn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "💡 ${if (isHindi) issue.fixHi else issue.fixEn}",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandPrimary,
                fontWeight = FontWeight.Medium
            )

            if (canAiDeepFix && !issue.autoFixable) {
                TextButton(
                    onClick = onAiDeepFix,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("ai_deep_fix_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Deep Fix", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (isHindi) "AI से इस फ़ाइल को सुधारें" else "Try Gemini AI Fix on this file")
                }
            }
        }
    }
}

@Composable
private fun CodeDiffReviewCard(
    repairedFiles: List<ProjectFile>,
    activeDiffFile: ProjectFile,
    isHindi: Boolean,
    onSelectFile: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Code, contentDescription = "Code diff", tint = BrandPrimary)
                Text(
                    text = if (isHindi) "कोड बदलाव की समीक्षा करें" else "Review Code Changes (Original vs Repaired)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repairedFiles.forEach { f ->
                    FilterChip(
                        selected = f.relativePath == activeDiffFile.relativePath,
                        onClick = { onSelectFile(f.relativePath) },
                        label = { Text(f.relativePath) }
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (isHindi) "असली बैकअप" else "Original Backup") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (isHindi) "सुधरा हुआ कोड" else "Repaired (Validated)") }
                )
            }

            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val codeText = if (selectedTab == 0) activeDiffFile.originalContent else activeDiffFile.activeContent()
                Text(
                    text = codeText.take(6000),
                    fontFamily = JetBrainsMonoFamily,
                    color = Color(0xFFE2E8F0),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    if (bytes < 1024L * 1024L) return "${bytes / 1024L} KB"
    return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
}
