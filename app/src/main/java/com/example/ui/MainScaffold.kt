package com.example.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.AiRepairRepository
import com.example.data.ProjectRepository
import com.example.firebase.signOut
import com.example.model.AppLanguage
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandViolet
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_WORKSPACE = "workspace"
const val ROUTE_PREVIEW = "preview"
const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_DEPLOY = "deploy"
const val ROUTE_ACCOUNT = "account"

private data class NavTab(
    val route: String,
    val labelEn: String,
    val labelHi: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    currentUserId: String,
    currentUserEmail: String?,
    currentUserName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: FixMyAppViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                FixMyAppViewModel(
                    projectRepo = ProjectRepository(db, Firebase.auth),
                    aiRepo = AiRepairRepository(),
                    currentUserId = currentUserId,
                    currentUserName = currentUserName ?: "FixMyApp Creator"
                )
            }
        }
    )

    val language by viewModel.language.collectAsStateWithLifecycle()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val activeFiles by viewModel.activeFiles.collectAsStateWithLifecycle()
    val activeBinaryAssets by viewModel.activeBinaryAssets.collectAsStateWithLifecycle()
    val activeIssues by viewModel.activeIssues.collectAsStateWithLifecycle()
    val previewUseRepaired by viewModel.previewUseRepaired.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_WORKSPACE
    val isHindi = language == AppLanguage.HI

    val tabs = remember {
        listOf(
            NavTab(ROUTE_WORKSPACE, "Fix", "सुधारें", Icons.Filled.Build, Icons.Outlined.Build),
            NavTab(ROUTE_PREVIEW, "Preview", "प्रीव्यू", Icons.Filled.Visibility, Icons.Outlined.Visibility),
            NavTab(ROUTE_DASHBOARD, "History", "डैशबोर्ड", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
            NavTab(ROUTE_DEPLOY, "Deploy", "पब्लिश", Icons.Filled.RocketLaunch, Icons.Outlined.RocketLaunch),
            NavTab(ROUTE_ACCOUNT, "Account", "अकाउंट", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.linearGradient(listOf(BrandSecondary, BrandViolet))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚡", color = Color.White)
                            }
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier.testTag("top_language_toggle")
                        ) {
                            Text(
                                text = if (isHindi) "🌐 English" else "🌐 हिंदी",
                                color = BrandPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.labelEn
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isHindi) tab.labelHi else tab.labelEn,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        Spacer(Modifier.size(8.dp))
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.labelEn
                                    )
                                },
                                label = { Text(if (isHindi) tab.labelHi else tab.labelEn) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_WORKSPACE
                    ) {
                        composable(ROUTE_WORKSPACE) {
                            WorkspaceScreen(
                                language = language,
                                activeProject = activeProject,
                                files = activeFiles,
                                issues = activeIssues,
                                feedback = feedback,
                                onUploadUris = { uris, name -> viewModel.uploadFromUris(context, uris, name) },
                                onLoadSample = { viewModel.loadSampleBrokenProject() },
                                onInspect = { proj -> viewModel.inspectActiveProject(proj) },
                                onRepair = { proj -> viewModel.repairActiveProject(proj) },
                                onUndo = { proj -> viewModel.undoChanges(proj) },
                                onAiDeepFix = { proj, file -> viewModel.runAiDeepFixForFile(proj, file) },
                                onExportZip = { proj, uri, repaired -> viewModel.exportZipToUri(context, proj, uri, repaired) },
                                onSelectCategory = { cat -> viewModel.setCategoryFilter(cat) },
                                onSelectDiffFile = { path -> viewModel.selectDiffFile(path) },
                                onNavigateToPreview = { navigateTab(ROUTE_PREVIEW) },
                                onNavigateToDeploy = { navigateTab(ROUTE_DEPLOY) },
                                onDismissBanner = { viewModel.clearBanner() }
                            )
                        }
                        composable(ROUTE_PREVIEW) {
                            PreviewScreen(
                                language = language,
                                activeProject = activeProject,
                                files = activeFiles,
                                binaryAssets = activeBinaryAssets,
                                issues = activeIssues,
                                useRepaired = previewUseRepaired,
                                feedback = feedback,
                                onTogglePreviewMode = { useRepaired -> viewModel.setPreviewUseRepaired(useRepaired) },
                                onInspect = { proj -> viewModel.inspectActiveProject(proj) },
                                onRepairAll = { proj -> viewModel.repairActiveProject(proj) },
                                onUndo = { proj -> viewModel.undoChanges(proj) },
                                onLoadSample = { viewModel.loadSampleBrokenProject() },
                                onGoToWorkspace = { navigateTab(ROUTE_WORKSPACE) }
                            )
                        }
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                language = language,
                                projectsState = projectsState,
                                downloadsState = downloadsState,
                                onOpenProject = { id ->
                                    viewModel.selectProject(id)
                                    navigateTab(ROUTE_WORKSPACE)
                                },
                                onDeleteProject = { id -> viewModel.deleteProject(id) }
                            )
                        }
                        composable(ROUTE_DEPLOY) {
                            DeployScreen(
                                language = language,
                                activeProject = activeProject,
                                readinessChecks = viewModel.computeReadiness(activeFiles),
                                feedback = feedback,
                                onDeployNetlify = { proj, token -> viewModel.deployToNetlify(proj, token) }
                            )
                        }
                        composable(ROUTE_ACCOUNT) {
                            AccountPricingScreen(
                                language = language,
                                userEmail = currentUserEmail,
                                userName = currentUserName,
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onSignOut = {
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
