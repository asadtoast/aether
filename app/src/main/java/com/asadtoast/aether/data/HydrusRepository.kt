package com.asadtoast.aether.data

import com.asadtoast.aether.network.HydrusApiService
import com.asadtoast.aether.network.HydrusNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import java.security.MessageDigest

class HydrusRepository(private val settingsManager: SettingsManager) {

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(apiCall())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun getApi(): HydrusApiService {
        return HydrusNetwork.getApi(
            settingsManager.serverIpFlow.first(),
            settingsManager.serverPortFlow.first()
        )
    }

    private suspend fun getApiKey(): String {
        return settingsManager.apiKeyFlow.first()
    }

    suspend fun getFileUrl(fileId: Long, isThumbnail: Boolean = false): String {
        val ip = settingsManager.serverIpFlow.first()
        val port = settingsManager.serverPortFlow.first()
        val endpoint = if (isThumbnail) "get_files/thumbnail" else "get_files/file"
        return "http://$ip:$port/$endpoint?file_id=$fileId"
    }

    suspend fun getFileMd5(fileId: Long): Result<String> = safeApiCall {
        val response = getApi().getFile(getApiKey(), fileId)
        val inputStream = response.byteStream()
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        var bytesRead: Int

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }

        val digest = md.digest()
        digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun searchFiles(
        tags: List<String>, sortType: Int? = null, sortAsc: Boolean? = null, service: String? = null
    ) = safeApiCall {
        val tagsJson = org.json.JSONArray(tags).toString()
        val fileDomain = service ?: settingsManager.fileDomainFlow.first()
        val tagDomain = settingsManager.tagDomainFlow.first()
        getApi().searchFiles(getApiKey(), tagsJson, sortType, sortAsc, fileDomain, tagDomain)
    }
    suspend fun getFileMetadata(
        fileIds: List<Long>, hashType: String? = null
    ) = safeApiCall {
        val fileIdsJson = org.json.JSONArray(fileIds).toString()
        getApi().getFileMetadata(getApiKey(), fileIdsJson, hashType)
    }

    suspend fun getFileMetadataByHashes(
        hashes: List<String>
    ) = safeApiCall {
        val hashesJson = org.json.JSONArray(hashes).toString()
        getApi().getFileMetadataByHashes(getApiKey(), hashesJson)
    }

    suspend fun getFile(fileId: Long) = safeApiCall {
        getApi().getFile(getApiKey(), fileId)
    }

    suspend fun addFile(file: RequestBody, fileDomainOverride: String? = null) = safeApiCall {
        val fileServiceName = fileDomainOverride ?: settingsManager.fileDomainFlow.first()
        getApi().addFile(getApiKey(), fileServiceName, file)
    }

    suspend fun addTags(request: AddTagsRequest) = safeApiCall {
        val tagDomain = settingsManager.tagDomainFlow.first()

        val updatedTags = request.service_names_to_tags?.let {
            mapOf(tagDomain to it.values.flatten())
        }

        val updatedActions = request.service_names_to_actions_to_tags?.let { actionsMap ->
            val combinedActions = mutableMapOf<String, MutableList<String>>()
            actionsMap.values.forEach { actionToTags ->
                actionToTags.forEach { (action, tags) ->
                    combinedActions.getOrPut(action) { mutableListOf() }.addAll(tags)
                }
            }
            mapOf(tagDomain to combinedActions.mapValues { it.value.toList() })
        }

        val updatedRequest = request.copy(
            service_names_to_tags = updatedTags,
            service_names_to_actions_to_tags = updatedActions
        )
        getApi().addTags(getApiKey(), updatedRequest)
    }

    suspend fun searchTags(search: String, tagServiceName: String? = null) = safeApiCall {
        val domain = tagServiceName ?: settingsManager.tagDomainFlow.first()
        val response = getApi().searchTags(getApiKey(), search, domain)
        if (response.isSuccessful) response.body() else throw Exception("Tag search failed: ${response.code()}")
    }

    suspend fun getPotentialPairs(maxHammingDistance: Int? = null) = safeApiCall {
        val searchDomain = settingsManager.fileDomainFlow.first()
        val response = getApi().getPotentialPairs(getApiKey(), searchDomain = searchDomain, maxHammingDistance = maxHammingDistance)
        if (response.isSuccessful) response.body() else throw Exception("Pairs fetch failed: ${response.code()}")
    }

    suspend fun getPotentialPairsCount(maxHammingDistance: Int? = null) = safeApiCall {
        val searchDomain = settingsManager.fileDomainFlow.first()
        val response = getApi().getPotentialsCount(getApiKey(), searchDomain = searchDomain, maxHammingDistance = maxHammingDistance)
        if (response.isSuccessful) response.body() else throw Exception("Pairs count fetch failed: ${response.code()}")
    }

    // group alternate processing
    suspend fun setFileRelationships(request: SetFileRelationshipsRequest) = safeApiCall {
        val response = getApi().setFileRelationships(getApiKey(), request)
        if (!response.isSuccessful) throw Exception("Relationship set failed: ${response.code()}")
    }

    suspend fun getFileRelationships(hash: String) = safeApiCall {
        val response = getApi().getFileRelationships(getApiKey(), hash)
        if (response.isSuccessful) response.body() else throw Exception("Get relationships failed: ${response.code()}")
    }

    // file actions (single and batch)
    private suspend fun performFileAction(fileId: Long?, hash: String?, fileDomainOverride: String? = null, action: suspend (HydrusApiService, HydrusApiService.FileActionRequest) -> Unit) = safeApiCall {
        val fileDomain = fileDomainOverride ?: settingsManager.fileDomainFlow.first()
        val request = HydrusApiService.FileActionRequest(
            file_id = fileId,
            hash = hash,
            file_service_names = listOf(fileDomain)
        )
        action(getApi(), request)
    }

    suspend fun trashFile(fileId: Long? = null, hash: String? = null, fileDomainOverride: String? = null) =
        performFileAction(fileId, hash, fileDomainOverride) { api, req -> api.trashFile(getApiKey(), req) }

    suspend fun restoreFile(fileId: Long? = null, hash: String? = null, fileDomainOverride: String? = null) =
        performFileAction(fileId, hash, fileDomainOverride) { api, req -> api.restoreFile(getApiKey(), req) }

    suspend fun archiveFile(fileId: Long? = null, hash: String? = null, fileDomainOverride: String? = null) =
        performFileAction(fileId, hash, fileDomainOverride) { api, req -> api.archiveFile(getApiKey(), req) }

    suspend fun unarchiveFile(fileId: Long? = null, hash: String? = null, fileDomainOverride: String? = null) =
        performFileAction(fileId, hash, fileDomainOverride) { api, req -> api.unarchiveFile(getApiKey(), req) }

    suspend fun testConnection(ip: String, port: String, apiKey: String): Boolean {
        val result = safeApiCall {
            val tempApi = HydrusNetwork.getApi(ip, port)
            tempApi.verifyAccessKey(apiKey)
        }
        return result.isSuccess
    }

    suspend fun getServices() = safeApiCall {
        getApi().getServices(getApiKey())
    }
}