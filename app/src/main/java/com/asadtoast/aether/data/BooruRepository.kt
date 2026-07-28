package com.asadtoast.aether.data

import com.asadtoast.aether.network.HydrusNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BooruRepository {
    private val api = HydrusNetwork.booruApi

    suspend fun getTagsForMd5(hash: String): Result<List<ParsedTag>> = withContext(Dispatchers.IO) {
        fetchAndParseTags("md5:$hash")
    }

    suspend fun getTagsForPostId(postId: String): Result<List<ParsedTag>> = withContext(Dispatchers.IO) {
        fetchAndParseTags("id:$postId")
    }

    private suspend fun fetchAndParseTags(query: String): Result<List<ParsedTag>> {
        return try {
            val posts = api.getPostByMd5(query)
            if (posts.isNullOrEmpty()) {
                return Result.failure(Exception("Unable to find file on danbooru"))
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

            addTags(post.tagStringArtist, "creator")
            addTags(post.tagStringCharacter, "character")
            addTags(post.tagStringCopyright, "series")
            addTags(post.tagStringMeta, "meta")
            addTags(post.tagStringGeneral, "")

            Result.success(parsedTags)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}