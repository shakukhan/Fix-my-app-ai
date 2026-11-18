package com.example.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppLanguage
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg

@Composable
fun AccountPricingScreen(
    language: AppLanguage,
    userEmail: String?,
    userName: String?,
    onToggleLanguage: () -> Unit,
    onSignOut: () -> Unit
) {
    val isHindi = language == AppLanguage.HI
    var billingNotice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 860.dp)
    ) {
        // Account Profile Card
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "User account", tint = BrandPrimary, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = userName?.ifBlank { "FixMyApp Creator" } ?: "FixMyApp Creator", style = MaterialTheme.typography.titleLarge)
                            Text(text = userEmail ?: "Google Account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(color = StatusSuccessBg, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                text = "FREE TIER",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusSuccess,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleLanguage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("account_language_toggle")
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = "Switch language", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (isHindi) "English" else "हिंदी में बदलें")
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (isHindi) "लॉग आउट" else "Sign Out")
                        }
                    }
                }
            }
        }

        // Transparent Pricing Plans
        item {
            Column {
                Text(
                    text = if (isHindi) "सरल और पारदर्शी प्राइसिंग" else "SaaS Plans & Pricing",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = if (isHindi) "मुफ़्त प्लान से शुरू करें। पेड प्लान तभी सक्रिय होंगे जब पेमेंट प्रोवाइडर जुड़ा हो।" else "Start free. Paid upgrades stay disabled until a payment provider (Google Play Billing / Stripe) is configured.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (billingNotice != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = billingNotice!!,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        item {
            PricingTierCard(
                name = "FREE",
                price = "$0 / month",
                features = listOf(
                    "Project uploads up to 25 MB",
                    "HTML, CSS, JS, asset & responsive inspection",
                    "English & Hindi explanations",
                    "Verified auto-repair & ZIP backup/export"
                ),
                ctaLabel = if (isHindi) "वर्तमान सक्रिय प्लान" else "Active Free Plan",
                highlighted = false,
                onSelect = {
                    billingNotice = "You are currently using the Free Tier with full inspection, Gemini AI, and repair tools enabled."
                }
            )
        }

        item {
            PricingTierCard(
                name = "PRO",
                price = "$12 / month",
                features = listOf(
                    "Higher monthly project inspection quota",
                    "Extended Gemini AI deep code repair",
                    "Cloud project version history",
                    "Guided Netlify deployment workflow"
                ),
                ctaLabel = if (isHindi) "Pro चुनें" else "Choose Pro",
                highlighted = true,
                onSelect = {
                    billingNotice = "PRO billing is not active yet because no payment provider (Google Play Billing or Stripe) is configured. No charge was made."
                }
            )
        }

        item {
            PricingTierCard(
                name = "BUSINESS",
                price = "$39 / month",
                features = listOf(
                    "Multi-project client agency workspace",
                    "Priority inspection & AI processing",
                    "Custom deployment checklists",
                    "Dedicated launch support"
                ),
                ctaLabel = if (isHindi) "Business चुनें" else "Choose Business",
                highlighted = false,
                onSelect = {
                    billingNotice = "BUSINESS billing is not active yet because no payment provider (Google Play Billing or Stripe) is configured. No charge was made."
                }
            )
        }
    }
}

@Composable
private fun PricingTierCard(
    name: String,
    price: String,
    features: List<String>,
    ctaLabel: String,
    highlighted: Boolean,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) BrandPrimary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, style = MaterialTheme.typography.labelLarge, color = BrandPrimary)
                Text(text = price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            features.forEach { f ->
                Text(text = "✓ $f", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            if (highlighted) {
                Button(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("plan_button_$name")
                ) {
                    Text(ctaLabel)
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("plan_button_$name")
                ) {
                    Text(ctaLabel)
                }
            }
        }
    }
}
