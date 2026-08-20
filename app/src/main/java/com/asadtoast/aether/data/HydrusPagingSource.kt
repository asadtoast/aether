package com.asadtoast.aether.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

class HydrusPagingSource(
    private val hydrusRepository: HydrusRepository,
    private val fileIds: List<Long>
) : PagingSource<Int, FileMetadata>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FileMetadata> {
        val position = params.key ?: 0
        val limit = params.loadSize

        if (fileIds.isEmpty() || position >= fileIds.size) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        val endPosition = (position + limit).coerceAtMost(fileIds.size)
        val chunkIds = fileIds.subList(position, endPosition)

        return try {
            val response = hydrusRepository.getFileMetadata(chunkIds)

            val metadataList = response.getOrThrow().metadata.map { detail ->
                val extractedTags = detail.tags?.values
                    ?.find { it.name == "my tags" }
                    ?.display_tags?.get("0") ?: emptyList()

                FileMetadata(
                    id = detail.file_id,
                    thumbnailUrl = hydrusRepository.getFileUrl(detail.file_id, isThumbnail = true),
                    fileUrl = hydrusRepository.getFileUrl(detail.file_id, isThumbnail = false),
                    mime = detail.mime,
                    size = detail.size,
                    width = detail.width?.toInt() ?: 0,
                    height = detail.height?.toInt() ?: 0,
                    tags = extractedTags,
                    importedDate = detail.realImportedTime,
                    duration = detail.duration,
                    framerate = if (detail.num_frames != null && detail.duration != null && detail.duration > 0.0)
                        detail.num_frames / (detail.duration / 1000.0) else null,
                    hash = detail.hash,
                    is_inbox = detail.is_inbox,
                    is_trashed = detail.is_trashed,
                    knownUrls = detail.known_urls ?: emptyList(),
                    alternates = detail.alternates
                )
            }

            val nextKey = if (endPosition < fileIds.size) endPosition else null
            val prevKey = if (position > 0) maxOf(0, position - limit) else null

            LoadResult.Page(
                data = metadataList,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FileMetadata>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            maxOf(0, anchorPosition - (state.config.initialLoadSize / 2))
        }
    }
}