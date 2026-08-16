package com.asadtoast.aether.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object HydrusNetwork {

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var currentApi: HydrusApiService? = null
    private var currentIp: String = ""
    private var currentPort: String = ""

    // centralized URL generator
    fun buildBaseUrl(address: String, port: String): String {
        val hasProtocol = address.startsWith("http://") || address.startsWith("https://")
        val protocol = if (hasProtocol) "" else "http://"
        val portSuffix = if (port.isBlank() || port == "80" || port == "443") "" else ":$port"

        var fullUrl = "$protocol$address$portSuffix"
        if (!fullUrl.endsWith("/")) fullUrl += "/"

        return fullUrl
    }

    fun getApi(ip: String, port: String): HydrusApiService {
        if (currentApi == null || currentIp != ip || currentPort != port) {
            currentIp = ip
            currentPort = port

            val dynamicBaseUrl = buildBaseUrl(ip, port)

            val retrofit = Retrofit.Builder()
                .baseUrl(dynamicBaseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            currentApi = retrofit.create(HydrusApiService::class.java)
        }
        return currentApi!!
    }

    // booru tag lookup
    val booruApi: BooruApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://danbooru.donmai.us/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BooruApiService::class.java)
    }
}