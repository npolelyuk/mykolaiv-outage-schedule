package com.example.lightmykolaiv

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers

data class GithubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    val assets: List<GithubAsset> = emptyList()
)

data class GithubAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

data class AppUpdateInfo(
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String
)

interface GithubApiService {
    @Headers("User-Agent: LightMykolaiv-App")
    @GET("repos/npolelyuk/mykolaiv-outage-schedule/releases/latest")
    suspend fun getLatestRelease(): GithubRelease
}

object UpdateChecker {
    private val api: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val release = api.getLatestRelease()
            Log.d("UpdateChecker", "Latest release: ${release.tagName}")
            val latestClean = cleanVersion(release.tagName)
            val currentClean = cleanVersion(currentVersion)

            if (isNewerVersion(latestClean, currentClean)) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                AppUpdateInfo(
                    latestVersion = release.tagName,
                    releaseTitle = release.name ?: release.tagName,
                    releaseNotes = release.body ?: "",
                    downloadUrl = apkAsset?.downloadUrl ?: release.htmlUrl,
                    releaseUrl = release.htmlUrl
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Failed to check update", e)
            null
        }
    }

    fun cleanVersion(v: String): String {
        return v.trim().removePrefix("v").removePrefix("V")
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
