package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.model.ReadinessCheck
import com.example.model.WebsiteProject
import com.example.ui.components.FixMyAppOutlinedTextField
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.StatusCritical
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg

@Composable
fun DeployScreen(
    language: AppLanguage,
    activeProject: WebsiteProject?,
    readinessChecks: List<ReadinessCheck>,
    feedback: WorkspaceUiFeedback,
    onDeployNetlify: (WebsiteProject, String) -> Unit
) {
    val context = LocalContext.current
    val isHindi = language == AppLanguage.HI
    val netlifyTokenState = rememberTextFieldState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isHindi) "डिप्लॉयमेंट असिस्टेंट" else "Deployment Assistant",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (isHindi) "बिना Git या टर्मिनल के अपनी सुधरी हुई वेबसाइट लाइव करें।" else "Verify readiness and publish your fixed website online without Git or terminal commands.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Readiness Checks
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isHindi) "1. डिप्लॉयमेंट तैयारी जाँच" else "1. Deployment Readiness Checks",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (activeProject == null || readinessChecks.isEmpty()) {
                        Text(
                            text = if (isHindi) "तैयारी जाँचने के लिए पहले Fix Project टैब में कोई वेबसाइट अपलोड करें।" else "Upload a project in the Fix Project tab to run live readiness checks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${activeProject.name} • ${readinessChecks.count { it.passed }}/${readinessChecks.size} passed",
                            style = MaterialTheme.typography.labelLarge,
                            color = BrandPrimary
                        )
                        readinessChecks.forEach { check ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (check.passed) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                    contentDescription = null,
                                    tint = if (check.passed) StatusSuccess else StatusCritical,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isHindi) check.labelHi else check.labelEn,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Netlify Direct Integration
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isHindi) "2. एक-क्लिक Netlify पब्लिश" else "2. Direct Netlify Publishing",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isHindi) {
                            "सीधे Netlify पर लाइव करने के लिए अपना Netlify Personal Access Token डालें (यह केवल इस अनुरोध के लिए उपयोग होता है)।"
                        } else {
                            "Paste your Netlify Personal Access Token to create a real site and deploy your ZIP directly (never stored on disk)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FixMyAppOutlinedTextField(
                        state = netlifyTokenState,
                        labelText = "Netlify Personal Access Token",
                        isPassword = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        testTag = "netlify_token_input",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (activeProject != null) onDeployNetlify(activeProject, netlifyTokenState.text.toString())
                        },
                        enabled = activeProject != null && !feedback.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("deploy_netlify_button")
                    ) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = "Deploy to Netlify", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (isHindi) "Netlify पर लाइव करें" else "Deploy to Netlify")
                    }

                    if (!activeProject?.deployUrl.isNullOrBlank()) {
                        Surface(
                            color = StatusSuccessBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isHindi) "आपकी वेबसाइट लाइव है!" else "Live Public Website URL:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusSuccess
                                )
                                Text(
                                    text = activeProject!!.deployUrl!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeProject.deployUrl))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open live URL", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(if (isHindi) "लाइव वेबसाइट खोलें" else "Open Live Website")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Beginner Zero-Token Free Method
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isHindi) "3. बिना टोकन वाला मुफ़्त तरीका (Netlify Drop)" else "3. Free Zero-Token Publish (Netlify Drop)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isHindi) {
                            "1. Fix Project में जाकर 'सुधरा ZIP डाउनलोड' पर टैप करें।\n2. नीचे दिए बटन से app.netlify.com/drop खोलें।\n3. अपनी डाउनलोड की गई ZIP चुनें और तुरंत मुफ़्त HTTPS लिंक पाएँ।"
                        } else {
                            "1. Tap 'Download Fixed' in the Fix Project tab.\n2. Tap the button below to open Netlify Drop in Chrome.\n3. Upload your fixed ZIP to get an instant free HTTPS link."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.netlify.com/drop")))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_netlify_drop_button")
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open Netlify Drop", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Open app.netlify.com/drop")
                    }
                }
            }
        }
    }
}
