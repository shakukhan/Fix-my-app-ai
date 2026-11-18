package com.example.engine

import com.example.model.ProjectFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NetlifyDeployer {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun deployToNetlify(
        token: String,
        projectName: String,
        files: List<ProjectFile>,
        useRepaired: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a Netlify Personal Access Token or use the free Netlify Drop ZIP option below.")
            )
        }

        val hasRootIndex = files.any { it.relativePath == "index.html" }
        if (!hasRootIndex) {
            return@withContext Result.failure(
                IllegalStateException("Deployment blocked: root index.html is missing. Run Fix My Project first.")
            )
        }

        try {
            val zipBytes = ZipProjectImporter.exportProjectZipBytes(files, useRepaired)

            // 1. Create a Netlify site
            val safeSlug = projectName.lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(18)
                .ifBlank { "site" }
            val uniqueName = "fixmyapp-$safeSlug-${System.currentTimeMillis() % 100000}"

            val createBody = JSONObject().put("name", uniqueName).toString()
                .toRequestBody("application/json".toMediaType())

            val createReq = Request.Builder()
                .url("https://api.netlify.com/api/v1/sites")
                .header("Authorization", "Bearer $cleanToken")
                .post(createBody)
                .build()

            val siteId: String
            val fallbackSiteUrl: String
            client.newCall(createReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Netlify site creation failed (${resp.code}). Verify your Personal Access Token.")
                    )
                }
                val json = JSONObject(bodyStr)
                siteId = json.getString("id")
                fallbackSiteUrl = json.optString("ssl_url").ifBlank { json.optString("url") }
            }

            // 2. Upload ZIP bundle to deploys endpoint
            val deployReq = Request.Builder()
                .url("https://api.netlify.com/api/v1/sites/$siteId/deploys")
                .header("Authorization", "Bearer $cleanToken")
                .post(zipBytes.toRequestBody("application/zip".toMediaType()))
                .build()

            client.newCall(deployReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Netlify upload failed (${resp.code}): ${bodyStr.take(160)}")
                    )
                }
                val json = JSONObject(bodyStr)
                val liveUrl = json.optString("ssl_url")
                    .ifBlank { json.optString("url") }
                    .ifBlank { fallbackSiteUrl }

                if (liveUrl.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Netlify did not return a public URL.")
                    )
                }
                Result.success(liveUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
