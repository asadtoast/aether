package com.asadtoast.aether.data

// response when retrieving pairs
data class PotentialPairsResponse(
    val potential_duplicate_pairs: List<List<String>>
)

// call to send decisions
data class SetFileRelationshipsRequest(
    val relationships: List<FileRelationship>
)

data class FileRelationship(
    val hash_a: String,
    val hash_b: String,
    val relationship: Int,
    val do_default_content_merge: Boolean = true,
    val delete_a: Boolean = false,
    val delete_b: Boolean = false
)

data class DuplicatePair(
    val hash_a: String,
    val hash_b: String,
    val meta_a: FileMetadata,
    val meta_b: FileMetadata
)
