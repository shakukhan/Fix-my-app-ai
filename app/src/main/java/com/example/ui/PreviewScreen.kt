package com.example.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.PreparedPreview
import com.example.engine.PreviewBundleBuilder
import com.example.model.AppLanguage
import com.example.model.IssueSeverity
import com.example.model.ProjectFile
import com.example.model.ProjectIssue
import com.example.model.WebsiteProject
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusCriticalBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

private class PreviewWebViewHolder {
    @Volatile
    var prepared: PreparedPreview? = null
    var lastLoadedKey: String? = null
    var webView: WebView? = null
}

@Composable
fun PreviewScreen(
    language: AppLanguage,
    activeProject: WebsiteProject?,
    files: List<ProjectFile>,
    binaryAssets: Map<String, ByteArray> = emptyMap(),
    issues: List<ProjectIssue>,
    useRepaired: Boolean,
    feedback: WorkspaceUiFeedback,
    onTogglePreviewMode: (Boolean) -> Unit,
    onInspect: (WebsiteProject) -> Unit,
    onRepairAll: (WebsiteProject) -> Unit,
    onUndo: (WebsiteProject) -> Unit,
    onLoadSample: () -> Unit,
    onGoToWorkspace: () -> Unit
) {
    val isHindi = language == AppLanguage.HI
    val hasRepairedFiles = remember(files) { files.any { it.repairedContent != null } }
    val effectiveUseRepaired = useRepaired && hasRepairedFiles

    val prepared = remember(files, binaryAssets, effectiveUseRepaired) {
        PreviewBundleBuilder.build(files, useRepaired = effectiveUseRepaired, binaryAssets = binaryAssets)
    }

    var reloadNonce by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(false) }
    var mainFrameError by remember { mutableStateOf<String?>(null) }
    val runtimeDiagnostics = remember { mutableStateListOf<String>() }
    val scrollState = rememberScrollState()

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        if (activeProject == null || files.isEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = "Preview Empty State",
                        tint = BrandPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (isHindi) "कोई वेबसाइट प्रोजेक्ट चुना नहीं गया" else "No Website Loaded in Preview",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isHindi) {
                            "सुधार से पहले और बाद में अपनी वेबसाइट का लाइव प्रीव्यू देखने के लिए कोई प्रोजेक्ट अपलोड करें या Sweet Bakery डेमो लोड करें।"
                        } else {
                            "Upload a ZIP/HTML project or try the Sweet Bakery demo to inspect errors and preview your website before and after repair."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onLoadSample,
                        enabled = !feedback.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("preview_load_sample_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isHindi) "Sweet Bakery डेमो चलाएँ" else "Load Sweet Bakery Demo")
                    }
                    OutlinedButton(
                        onClick = onGoToWorkspace,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("preview_go_workspace_button")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isHindi) "फ़ाइल अपलोड पर जाएँ" else "Go to Upload & Fix")
                    }
                }
            }
            return@Column
        }

        // Preview Control Header Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeProject.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (effectiveUseRepaired) {
                                if (isHindi) "सुधरा हुआ लाइव प्रीव्यू (Auto Fix के बाद)" else "Showing Repaired Website (After Auto Fix)"
                            } else {
                                if (isHindi) "असली वेबसाइट प्रीव्यू (सुधार से पहले)" else "Showing Original Website (Before Fix)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (effectiveUseRepaired) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            mainFrameError = null
                            reloadNonce++
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("preview_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reload Preview",
                            tint = BrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(if (isHindi) "रीलोड प्रीव्यू" else "Reload Preview")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !effectiveUseRepaired,
                        onClick = { onTogglePreviewMode(false) },
                        label = {
                            Text(if (isHindi) "असली (सुधार पूर्व)" else "Original (Before)")
                        },
                        modifier = Modifier.testTag("preview_mode_original")
                    )
                    FilterChip(
                        selected = effectiveUseRepaired,
                        enabled = hasRepairedFiles,
                        onClick = { onTogglePreviewMode(true) },
                        label = {
                            Text(
                                if (hasRepairedFiles) {
                                    if (isHindi) "सुधरी वेबसाइट प्रीव्यू" else "Preview Fixed Website"
                                } else {
                                    if (isHindi) "सुधरा हुआ (लॉक)" else "Preview Fixed (Run Fix First)"
                                }
                            )
                        },
                        modifier = Modifier.testTag("preview_mode_repaired")
                    )
                }

                AnimatedVisibility(visible = feedback.isBusy) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = feedback.busyMessage ?: "Processing...",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandPrimary
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                            .testTag("preview_repair_telemetry_banner")
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
            }
        }

        // Live WebView Frame
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (effectiveUseRepaired) StatusSuccess.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "Preview URL",
                        tint = if (effectiveUseRepaired) StatusSuccess else BrandPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = prepared?.entryUrl ?: "${PreviewBundleBuilder.PREVIEW_ORIGIN}index.html",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = when {
                            isPageLoading -> MaterialTheme.colorScheme.primaryContainer
                            mainFrameError != null || runtimeDiagnostics.isNotEmpty() -> StatusCriticalBg
                            else -> StatusSuccessBg
                        },
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = when {
                                isPageLoading -> if (isHindi) "लोडिंग..." else "Loading…"
                                mainFrameError != null -> if (isHindi) "लोड त्रुटि" else "Load Error"
                                runtimeDiagnostics.isNotEmpty() -> "${runtimeDiagnostics.size} Console"
                                else -> if (isHindi) "लाइव" else "Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isPageLoading -> BrandPrimary
                                mainFrameError != null || runtimeDiagnostics.isNotEmpty() -> StatusCritical
                                else -> StatusSuccess
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isPageLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    prepared == null -> {
                        PreviewLoadErrorPanel(
                            message = if (isHindi) {
                                "प्रीव्यू लोड नहीं हो सका: इस प्रोजेक्ट में कोई HTML पेज (.html) नहीं मिला।"
                            } else {
                                "Preview could not load: No HTML entry file (.html) was found in this project."
                            },
                            retryLabel = if (isHindi) "फ़ाइलें जांचें" else "Back to Upload",
                            onRetry = onGoToWorkspace
                        )
                    }

                    prepared.isEntryBlank -> {
                        PreviewLoadErrorPanel(
                            message = if (isHindi) {
                                "प्रीव्यू लोड नहीं हो सका: ${prepared.entryPath} फ़ाइल खाली है।"
                            } else {
                                "Preview failed to render: ${prepared.entryPath} is empty (0 characters)."
                            },
                            retryLabel = if (isHindi) "रीलोड प्रीव्यू" else "Reload Preview",
                            onRetry = { reloadNonce++ }
                        )
                    }

                    mainFrameError != null -> {
                        PreviewLoadErrorPanel(
                            message = mainFrameError.orEmpty(),
                            retryLabel = if (isHindi) "पुनः प्रयास करें" else "Reload Preview",
                            onRetry = {
                                mainFrameError = null
                                reloadNonce++
                            }
                        )
                    }

                    else -> {
                        WebsitePreviewWebView(
                            projectId = activeProject.id,
                            prepared = prepared,
                            useRepaired = effectiveUseRepaired,
                            reloadNonce = reloadNonce,
                            onLoadingChanged = { loading -> isPageLoading = loading },
                            onMainFrameError = { msg -> mainFrameError = msg },
                            onResetDiagnostics = { runtimeDiagnostics.clear() },
                            onAddDiagnostic = { msg ->
                                if (msg !in runtimeDiagnostics && runtimeDiagnostics.size < 25) {
                                    runtimeDiagnostics.add(msg)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(390.dp)
                                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                        )
                    }
                }
            }
        }

        // JavaScript & Runtime Asset Console Diagnostics
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (runtimeDiagnostics.isEmpty()) StatusSuccessBg.copy(alpha = 0.7f) else StatusCriticalBg
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("preview_runtime_console_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (runtimeDiagnostics.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.BugReport,
                        contentDescription = "Runtime Console",
                        tint = if (runtimeDiagnostics.isEmpty()) StatusSuccess else StatusCritical,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (runtimeDiagnostics.isEmpty()) {
                            if (isHindi) "0 लाइव JavaScript / एसेट त्रुटियाँ" else "0 Live JavaScript / Asset Console Errors"
                        } else {
                            if (isHindi) {
                                "लाइव प्रीव्यू में मिली त्रुटियाँ (${runtimeDiagnostics.size})"
                            } else {
                                "Live Preview Console (${runtimeDiagnostics.size})"
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (runtimeDiagnostics.isEmpty()) StatusSuccess else StatusCritical,
                        fontWeight = FontWeight.Bold
                    )
                }
                runtimeDiagnostics.forEach { diag ->
                    Text(
                        text = "• $diag",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = StatusCritical
                    )
                }
            }
        }

        // Auto Fix / Repair All & Re-Inspect Actions
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
                val unresolvedCount = issues.count { !it.fixed }
                val fixedCount = issues.count { it.fixed }
                Text(
                    text = if (issues.isEmpty()) {
                        if (isHindi) "वेबसाइट निरीक्षण परिणाम" else "Website Repair & Validation"
                    } else if (isHindi) {
                        "कुल समस्याएँ: ${issues.size} • ठीक हुईं: $fixedCount • शेष: $unresolvedCount"
                    } else {
                        "Detected: ${issues.size} • Confirmed Fixed: $fixedCount • Remaining: $unresolvedCount"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

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
                            .testTag("preview_inspect_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Inspect", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isHindi) "फिर जाँचें" else "Inspect")
                    }

                    Button(
                        onClick = { onRepairAll(activeProject) },
                        enabled = !feedback.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandSecondary),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("preview_repair_all_button")
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Fix Automatically", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isHindi) "अपने आप ठीक करें" else "Fix Automatically")
                    }
                }

                OutlinedButton(
                    onClick = { onUndo(activeProject) },
                    enabled = !feedback.isBusy && hasRepairedFiles,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("preview_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Changes",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (isHindi) "बदलाव वापस लें (मूल फ़ाइलें लाएँ)" else "Undo Changes (Restore Original)")
                }
            }
        }

        // Visible Detected Errors List
        if (issues.isNotEmpty()) {
            Text(
                text = if (isHindi) "मिली गई सभी त्रुटियाँ (${issues.size})" else "Detected Errors (${issues.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("preview_errors_header")
            )

            issues.forEach { issue ->
                PreviewIssueRow(issue = issue, isHindi = isHindi)
            }
        }
    }
}

@Composable
private fun PreviewLoadErrorPanel(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .testTag("preview_error_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = "Preview Load Error",
            tint = StatusCritical,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = StatusCritical
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.height(48.dp)
        ) {
            Text(retryLabel)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun WebsitePreviewWebView(
    projectId: String,
    prepared: PreparedPreview,
    useRepaired: Boolean,
    reloadNonce: Int,
    onLoadingChanged: (Boolean) -> Unit,
    onMainFrameError: (String?) -> Unit,
    onResetDiagnostics: () -> Unit,
    onAddDiagnostic: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember { PreviewWebViewHolder() }
    holder.prepared = prepared

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val latestOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val latestOnMainFrameError by rememberUpdatedState(onMainFrameError)
    val latestOnResetDiagnostics by rememberUpdatedState(onResetDiagnostics)
    val latestOnAddDiagnostic by rememberUpdatedState(onAddDiagnostic)

    val loadKey = "$projectId:${prepared.contentSignature}:$useRepaired:$reloadNonce"

    AndroidView(
        modifier = modifier.testTag("preview_webview"),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    defaultTextEncodingName = "utf-8"
                }

                setOnTouchListener { view, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        post {
                            latestOnLoadingChanged(true)
                            latestOnMainFrameError(null)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        post {
                            latestOnLoadingChanged(false)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val urlStr = request?.url?.toString().orEmpty()
                        if (urlStr.startsWith(PreviewBundleBuilder.PREVIEW_ORIGIN, ignoreCase = true) ||
                            urlStr.startsWith("#") ||
                            urlStr == "about:blank"
                        ) {
                            return false
                        }
                        post {
                            latestOnAddDiagnostic("Blocked external link in preview: $urlStr")
                        }
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val currentPrepared = holder.prepared ?: return null
                        val urlStr = request?.url?.toString() ?: return null
                        if (!urlStr.startsWith(PreviewBundleBuilder.PREVIEW_ORIGIN, ignoreCase = true)) {
                            return null
                        }
                        val asset = PreviewBundleBuilder.resolveAssetForUrl(currentPrepared, urlStr)
                        if (asset != null) {
                            return WebResourceResponse(
                                asset.mimeType,
                                "UTF-8",
                                200,
                                "OK",
                                mapOf(
                                    "Cache-Control" to "no-store",
                                    "Access-Control-Allow-Origin" to "*"
                                ),
                                PreviewBundleBuilder.openAssetStream(asset)
                            )
                        }
                        val missingPath = urlStr.removePrefix(PreviewBundleBuilder.PREVIEW_ORIGIN)
                            .substringBefore('?')
                            .substringBefore('#')
                        if (!missingPath.equals("favicon.ico", ignoreCase = true)) {
                            view?.post {
                                latestOnAddDiagnostic("Missing file (404): $missingPath")
                            }
                        }
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            404,
                            "Not Found",
                            mapOf(
                                "Cache-Control" to "no-store",
                                "Access-Control-Allow-Origin" to "*"
                            ),
                            ByteArrayInputStream("Not Found".toByteArray(StandardCharsets.UTF_8))
                        )
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        val desc = error?.description?.toString() ?: "Unknown rendering error"
                        if (request?.isForMainFrame == true) {
                            post {
                                latestOnLoadingChanged(false)
                                latestOnMainFrameError("Preview loading failed: $desc")
                            }
                        } else {
                            val failingUrl = request?.url?.toString()?.removePrefix(PreviewBundleBuilder.PREVIEW_ORIGIN) ?: "resource"
                            if (!failingUrl.equals("favicon.ico", ignoreCase = true)) {
                                post {
                                    latestOnAddDiagnostic("Resource error ($failingUrl): $desc")
                                }
                            }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        if (consoleMessage != null && consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            val msg = consoleMessage.message().orEmpty().trim()
                            if (msg.isNotBlank()) {
                                val lineInfo = if (consoleMessage.lineNumber() > 0) " (line ${consoleMessage.lineNumber()})" else ""
                                post {
                                    latestOnAddDiagnostic("$msg$lineInfo")
                                }
                            }
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }
                }

                holder.webView = this
                webViewInstance = this
            }
        },
        update = { webView ->
            holder.webView = webView
            if (webViewInstance !== webView) {
                webViewInstance = webView
            }
        }
    )

    LaunchedEffect(webViewInstance, loadKey) {
        val wv = webViewInstance ?: return@LaunchedEffect
        if (holder.lastLoadedKey != loadKey) {
            holder.lastLoadedKey = loadKey
            latestOnResetDiagnostics()
            latestOnMainFrameError(null)
            latestOnLoadingChanged(true)
            wv.loadDataWithBaseURL(
                prepared.entryUrl,
                prepared.entryHtmlWithMonitor,
                "text/html",
                "UTF-8",
                prepared.entryUrl
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnLoadingChanged(false)
            holder.webView?.apply {
                stopLoading()
                webChromeClient = null
                destroy()
            }
            holder.webView = null
            webViewInstance = null
        }
    }
}

@Composable
private fun PreviewIssueRow(
    issue: ProjectIssue,
    isHindi: Boolean
) {
    val sev = issue.severityEnum()
    val accent = when {
        issue.fixed -> StatusSuccess
        sev == IssueSeverity.CRITICAL -> StatusCritical
        sev == IssueSeverity.WARNING -> StatusWarning
        else -> BrandSecondary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${issue.filePath} • Line ${issue.lineNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (issue.fixed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = if (issue.fixed) {
                                if (isHindi) "ठीक हुआ" else "FIXED"
                            } else {
                                if (isHindi) sev.labelHi else sev.labelEn
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "💡 ${if (isHindi) issue.fixHi else issue.fixEn}",
                style = MaterialTheme.typography.bodySmall,
                color = BrandPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
