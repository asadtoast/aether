package com.asadtoast.aether.network

import com.asadtoast.aether.data.AddTagsRequest
import com.asadtoast.aether.data.FileMetadataResponse
import com.asadtoast.aether.data.PotentialPairsResponse
import com.asadtoast.aether.data.SearchFilesResponse
import com.asadtoast.aether.data.SearchTagsResponse
import com.asadtoast.aether.data.SetFileRelationshipsRequest
import com.asadtoast.aether.data.VerifyAccessResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface HydrusApiService {

    @GET("verify_access_key")
    suspend fun verifyAccessKey(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String
    ): VerifyAccessResponse

    @GET("get_files/search_files")
    suspend fun searchFiles(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("tags") tags: String,
        @Query("file_sort_type") sortType: Int? = null,
        @Query("file_sort_asc") sortAsc: Boolean? = null,
        @Query("file_service_name") fileServiceName: String,
        @Query("tag_service_name") tagServiceName: String? = null,
        @Query("return_file_ids") returnFileIds: Boolean = true
    ): SearchFilesResponse

    @GET("get_files/file_metadata")
    suspend fun getFileMetadata(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("file_ids") fileIdsJson: String,
        @Query("hash_type") hashType: String? = null
    ): FileMetadataResponse

    @GET("get_files/file_metadata")
    suspend fun getFileMetadataByHashes(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("hashes") hashesJson: String
    ): FileMetadataResponse

    @retrofit2.http.Streaming
    @GET("get_files/file")
    suspend fun getFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("file_id") fileId: Long
    ): okhttp3.ResponseBody

    @POST("/add_tags/add_tags")
    suspend fun addTags(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: AddTagsRequest
    )

    @POST("add_files/add_file")
    suspend fun addFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("file_service_name") fileServiceName: String? = null,
        @Body file: okhttp3.RequestBody
    ): com.asadtoast.aether.data.AddFileResponse

    data class FileActionRequest(
        val file_id: Long? = null,
        val hash: String? = null,
        val file_service_names: List<String>? = null
    )

    @POST("add_files/archive_files")
    suspend fun archiveFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: FileActionRequest
    )

    @POST("add_files/unarchive_files")
    suspend fun unarchiveFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: FileActionRequest
    )

    @POST("add_files/delete_files")
    suspend fun trashFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: FileActionRequest
    )

    @POST("add_files/undelete_files")
    suspend fun restoreFile(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: FileActionRequest
    )

    @GET("add_tags/search_tags")
    suspend fun searchTags(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("search") search: String,
        @Query("tag_service_name") tagServiceName: String? = null
    ): Response<SearchTagsResponse>

    @GET("manage_file_relationships/get_potential_pairs")
    suspend fun getPotentialPairs(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("search_domain") searchDomain: String? = null,
        @Query("max_hamming_distance") maxHammingDistance: Int? = null,
        @Query("max_num_pairs") maxNumPairs: Int = 20 // 20 at a time
    ): Response<PotentialPairsResponse>

    @GET("manage_file_relationships/get_potentials_count")
    suspend fun getPotentialsCount(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("search_domain") searchDomain: String? = null,
        @Query("max_hamming_distance") maxHammingDistance: Int? = null
    ): Response<com.asadtoast.aether.data.PotentialsCountResponse>

    @POST("manage_file_relationships/set_file_relationships")
    suspend fun setFileRelationships(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Body request: SetFileRelationshipsRequest
    ): Response<Unit>

    @GET("manage_file_relationships/get_file_relationships")
    suspend fun getFileRelationships(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String,
        @Query("hash") hash: String
    ): Response<FileRelationshipsResponse>

    @GET("get_services")
    suspend fun getServices(
        @Header("Hydrus-Client-API-Access-Key") apiKey: String
    ): Response<GetServicesResponse>
}

data class FileRelationshipsResponse(
    val file_relationships: Map<String, Any>
)
data class GetServicesResponse(
    val services_v2: List<HydrusService>
)

data class HydrusService(
    val name: String,
    val type: Int,
    val type_pretty: String
)