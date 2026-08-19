package com.asadtoast.aether.data

// response when retrieving pairs
data class PotentialPairsResponse(
    val potential_duplicate_pairs: List<List<String>>
)

// call to send decisions
data class SetFileRelationshipsRequest(
    val relationships: List<FileRelationship>
)

// duplicates processing
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

data class PotentialsCountResponse(
    val potential_duplicates_count: Int
)

// group alternates processing
data class PotentialGroupsResponse(
    val potential_duplicate_groups: List<List<String>>
)

data class DuplicateGroup(
    val hashes: List<String>,
    val metadata: List<FileMetadata>
)
