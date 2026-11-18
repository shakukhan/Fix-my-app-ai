package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.DownloadLog
import com.example.model.ProjectStatus
import com.example.model.WebsiteProject
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusSuccess
import java.text.DateFormat
import java.util.Date

@Composable
fun DashboardScreen(
    language: AppLanguage,
    projectsState: StreamState<List<WebsiteProject>>,
    downloadsState: StreamState<List<DownloadLog>>,
    onOpenProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    val isHindi = language == AppLanguage.HI

    when (projectsState) {
        is StreamState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.size(12.dp))
                Text(if (isHindi) "डैशबोर्ड लोड हो रहा है..." else "Loading live dashboard...")
            }
        }
        is StreamState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = projectsState.message,
                    color = StatusCritical,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        is StreamState.Success -> {
            val projects = projectsState.data
            val totalUploaded = projects.size
            val inspectedCount = projects.count {
                it.statusEnum() in listOf(ProjectStatus.INSPECTED, ProjectStatus.REPAIRED, ProjectStatus.DEPLOYED)
            }
            val totalDetected = projects.sumOf { it.errorsDetected }
            val totalFixed = projects.sumOf { it.errorsFixed }
            val downloads = (downloadsState as? StreamState.Success)?.data.orEmpty()

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = if (isHindi) "लाइव प्रोजेक्ट डैशबोर्ड" else "Live Project Dashboard",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = if (isHindi) "आपका वास्तविक क्लाउड डेटा (Firestore द्वारा सिंक किया गया)" else "Real project statistics synced securely to your Firebase account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetricCard(
                            title = if (isHindi) "कुल प्रोजेक्ट" else "Uploaded",
                            value = totalUploaded.toString(),
                            accent = BrandPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetricCard(
                            title = if (isHindi) "जाँचे गए" else "Inspected",
                            value = inspectedCount.toString(),
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetricCard(
                            title = if (isHindi) "मिली गलतियाँ" else "Errors Detected",
                            value = totalDetected.toString(),
                            accent = StatusCritical,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetricCard(
                            title = if (isHindi) "सुधारी गईं" else "Errors Fixed",
                            value = totalFixed.toString(),
                            accent = StatusSuccess,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(
                        text = if (isHindi) "हाल के प्रोजेक्ट" else "Recent Project History",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (projects.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isHindi) "अभी तक कोई प्रोजेक्ट अपलोड नहीं हुआ। Workspace में जाकर ZIP या सैंपल साइट अपलोड करें।" else "No projects uploaded yet. Head to Fix Project to upload a ZIP or try the sample site.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(projects, key = { it.id }) { proj ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = proj.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "${proj.fileCount} files • Fixed ${proj.errorsFixed}/${proj.errorsDetected}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = if (isHindi) proj.statusEnum().labelHi else proj.statusEnum().labelEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (!proj.deployUrl.isNullOrBlank()) {
                                    Text(
                                        text = "Live URL: ${proj.deployUrl}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StatusSuccess
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenProject(proj.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("open_project_${proj.id}")
                                    ) {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = "Open project", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(if (isHindi) "खोलें" else "Open")
                                    }
                                    Spacer(Modifier.size(8.dp))
                                    IconButton(
                                        onClick = { onDeleteProject(proj.id) },
                                        modifier = Modifier.testTag("delete_project_${proj.id}")
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete project",
                                            tint = StatusCritical
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (isHindi) "डाउनलोड इतिहास" else "Download History",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (downloads.isEmpty()) {
                    item {
                        Text(
                            text = if (isHindi) "अभी तक कोई ZIP डाउनलोड नहीं की गई है।" else "No ZIP exports recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(downloads, key = { it.id }) { dl ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = StatusSuccess)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = dl.projectName, style = MaterialTheme.typography.titleSmall)
                                    val dateText = dl.createdAt?.toDate()?.let {
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(it)
                                    } ?: DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
                                    Text(
                                        text = "${dl.version} ZIP • ${dl.fileCount} files • $dateText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
