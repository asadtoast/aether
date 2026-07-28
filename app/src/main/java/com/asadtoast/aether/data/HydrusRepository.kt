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
        var totalBytesRead = 0L
        val limit = 10 * 1024 * 1024L // 10 MB

        inputStream.use { stream ->
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                totalBytesRead += bytesRead
                if (totalBytesRead > limit) {
                    throw Exception("File exceeds 10MB limit for MD5 calculation.")
                }
                md.update(buffer, 0, bytesRead)
            }
        }

        val digest = md.digest()
        digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun searchFiles(
        tags: List<String>, sortType: Int? = null, sortAsc: Boolean? = null, service: String
    ) = safeApiCall {
        val tagsJson = org.json.JSONArray(tags).toString()
        getApi().searchFiles(getApiKey(), tagsJson, sortType, sortAsc, service)
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

    suspend fun addFile(file: RequestBody) = safeApiCall {
        getApi().addFile(getApiKey(), file)
    }

    suspend fun addTags(request: AddTagsRequest) = safeApiCall {
        getApi().addTags(getApiKey(), request)
    }

    suspend fun searchTags(search: String) = safeApiCall {
        val response = getApi().searchTags(getApiKey(), search)
        if (response.isSuccessful) response.body() else throw Exception("Tag search failed: ${response.code()}")
    }

    suspend fun getPotentialPairs() = safeApiCall {
        val response = getApi().getPotentialPairs(getApiKey())
        if (response.isSuccessful) response.body() else throw Exception("Pairs fetch failed: ${response.code()}")
    }

    suspend fun setFileRelationships(request: SetFileRelationshipsRequest) = safeApiCall {
        val response = getApi().setFileRelationships(getApiKey(), request)
        if (!response.isSuccessful) throw Exception("Relationship set failed: ${response.code()}")
    }

    // file actions (single and batch)
    private suspend fun performFileAction(fileId: Long?, hash: String?, action: suspend (HydrusApiService, HydrusApiService.FileActionRequest) -> Unit) = safeApiCall {
        val request = HydrusApiService.FileActionRequest(file_id = fileId, hash = hash)
        action(getApi(), request)
    }

    suspend fun trashFile(fileId: Long? = null, hash: String? = null) =
        performFileAction(fileId, hash) { api, req -> api.trashFile(getApiKey(), req) }

    suspend fun restoreFile(fileId: Long? = null, hash: String? = null) =
        performFileAction(fileId, hash) { api, req -> api.restoreFile(getApiKey(), req) }

    suspend fun archiveFile(fileId: Long? = null, hash: String? = null) =
        performFileAction(fileId, hash) { api, req -> api.archiveFile(getApiKey(), req) }

    suspend fun unarchiveFile(fileId: Long? = null, hash: String? = null) =
        performFileAction(fileId, hash) { api, req -> api.unarchiveFile(getApiKey(), req) }
}