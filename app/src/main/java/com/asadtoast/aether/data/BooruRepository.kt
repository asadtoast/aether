package com.asadtoast.aether.data

import com.asadtoast.aether.network.HydrusNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BooruRepository {
    private val api = HydrusNetwork.booruApi

    suspend fun getTagsForHash(md5: String?, sha256: String?, source: ScraperSource): Result<List<ParsedTag>> = withContext(Dispatchers.IO) {
        try {
            when (source) {
                ScraperSource.DANBOORU -> {
                    if (md5 == null) return@withContext Result.failure(Exception("MD5 required for Danbooru"))
                    fetchDanbooruTags(md5)
                }
                ScraperSource.PAWCHIVE -> {
                    if (sha256 == null) return@withContext Result.failure(Exception("SHA256 required for Pawchive"))
                    fetchPawchiveTags(sha256)
                }
                else -> Result.failure(Exception("Unsupported source: $source"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchDanbooruTags(md5: String): Result<List<ParsedTag>> {
        val url = "https://danbooru.donmai.us/posts.json?tags=md5:$md5"
        val posts = api.getPostByMd5(url)
        if (posts.isNullOrEmpty()) {
            return Result.failure(Exception("Could not find a match on Danbooru"))
        }

        val post = posts.first()
        val parsedTags = mutableListOf<ParsedTag>()

        fun addTags(tagString: String?, namespace: String) {
            if (tagString.isNullOrBlank()) return
            tagString.split(" ").forEach { tag ->
                if (tag.isNotBlank()) {
                    val cleanTag = tag.replace("_", " ")
                    parsedTags.add(ParsedTag(cleanTag, namespace))
                }
            }
        }

        addTags(post.tagStringArtist, "artist")
        addTags(post.tagStringCharacter, "character")
        addTags(post.tagStringCopyright, "series")
        addTags(post.tagStringMeta, "meta")
        addTags(post.tagStringGeneral, "")

        return Result.success(parsedTags)
    }

    private suspend fun fetchPawchiveTags(sha256: String): Result<List<ParsedTag>> {
        return try {
            val url = "https://pawchive.pw/api/v1/search_hash/$sha256"

            val response = api.getPawchivePostByHash(url)
            val post = response.posts?.firstOrNull()
                ?: run {
                    return Result.failure(Exception("Could not find a match on Pawchive"))
                }

            val parsedTags = mutableListOf<ParsedTag>()
            post.user?.let { parsedTags.add(ParsedTag(it, "artist")) }

            Result.success(parsedTags)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) {
                Result.failure(Exception("Could not find a match on Pawchive"))
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}