package dev.yveskalume.elevenlabs.voices

data class ListVoicesRequest(
    val pageSize: Int = 10,
    val nextPageToken: String? = null,
    val search: String? = null,
    val voiceType: String? = null,
    val category: String? = null,
    val includeTotalCount: Boolean = false,
) {
    init {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100." }
    }
}