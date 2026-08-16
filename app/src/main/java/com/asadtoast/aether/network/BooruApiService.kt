package com.asadtoast.aether.network

import com.asadtoast.aether.data.DanbooruPost
import com.asadtoast.aether.data.PawchivePost
import retrofit2.http.GET

interface BooruApiService {
    @GET
    suspend fun getPostByMd5(
        @retrofit2.http.Url url: String
    ): List<DanbooruPost>

    @GET
    suspend fun getPawchivePostByHash(
        @retrofit2.http.Url url: String
    ): List<PawchivePost>
}