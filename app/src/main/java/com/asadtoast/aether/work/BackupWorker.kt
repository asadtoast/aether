package com.asadtoast.aether.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asadtoast.aether.data.AddTagsRequest
import com.asadtoast.aether.data.BackupManager
import com.asadtoast.aether.data.SettingsManager
import com.asadtoast.aether.network.HydrusApiService
import com.asadtoast.aether.network.HydrusNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BackupWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settingsManager = SettingsManager(applicationContext)
        val apiKey = settingsManager.apiKeyFlow.first()
        val serverIp = settingsManager.serverIpFlow.first()
        val port = settingsManager.serverPortFlow.first()
        val backupDestinationDomain = settingsManager.backupDestinationDomainFlow.first()
        val tagDomain = settingsManager.tagDomainFlow.first()

        if (apiKey.isEmpty() || serverIp.isEmpty()) return@withContext Result.failure()

        val api = HydrusNetwork.getApi(serverIp, port)
        val backupTag = "app_backup:tag_wiki"

        try {
            val searchResponse = api.searchFiles(
                apiKey = apiKey,
                tags = "[\"$backupTag\"]",
                fileServiceName = backupDestinationDomain
            )
            searchResponse.file_ids.forEach { id ->
                api.trashFile(apiKey, HydrusApiService.FileActionRequest(file_id = id, file_service_names = listOf(backupDestinationDomain)))
            }

            // reusing isolated handler for context manipulation
            val backupManager = BackupManager(applicationContext)
            val requestBody = backupManager.createBackupZip() ?: return@withContext Result.success()

            val uploadResponse = api.addFile(apiKey, backupDestinationDomain, requestBody)
            api.restoreFile(apiKey, HydrusApiService.FileActionRequest(hash = uploadResponse.hash, file_service_names = listOf(backupDestinationDomain)))

            val tagRequest = AddTagsRequest(
                hash = uploadResponse.hash,
                service_names_to_tags = mapOf(tagDomain to listOf(backupTag))
            )
            api.addTags(apiKey, tagRequest)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}