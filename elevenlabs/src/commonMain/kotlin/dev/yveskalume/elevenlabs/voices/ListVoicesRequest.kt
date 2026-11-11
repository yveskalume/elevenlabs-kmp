package dev.yveskalume.elevenlabs.voices

/** Filters and pagination controls for [VoicesApi.list]. */
public class ListVoicesRequest private constructor(
    /** Maximum number of voices to return, from 1 through 100. */
    public val pageSize: Int,
    /** Opaque token returned by the previous [VoicePage]. */
    public val nextPageToken: String?,
    /** Free-text search. */
    public val search: String?,
    /** Voice-type filter accepted by ElevenLabs. */
    public val voiceType: String?,
    /** Voice-category filter accepted by ElevenLabs. */
    public val category: String?,
    /** Whether [VoicePage.totalCount] should be populated. */
    public val includeTotalCount: Boolean,
) {
    init {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.pageSize = pageSize
        it.nextPageToken = nextPageToken
        it.search = search
        it.voiceType = voiceType
        it.category = category
        it.includeTotalCount = includeTotalCount
    }

    /** Builds a [ListVoicesRequest]. Every value starts at its default. */
    public class Builder {
        /** Maximum number of voices to return, from 1 through 100. */
        public var pageSize: Int = 10
        /** Opaque token returned by the previous [VoicePage]. */
        public var nextPageToken: String? = null
        /** Free-text search. */
        public var search: String? = null
        /** Voice-type filter accepted by ElevenLabs. */
        public var voiceType: String? = null
        /** Voice-category filter accepted by ElevenLabs. */
        public var category: String? = null
        /** Whether [VoicePage.totalCount] should be populated. */
        public var includeTotalCount: Boolean = false

        /**
         * Creates the [ListVoicesRequest].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): ListVoicesRequest = ListVoicesRequest(
            pageSize,
            nextPageToken,
            search,
            voiceType,
            category,
            includeTotalCount,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is ListVoicesRequest &&
            pageSize == other.pageSize &&
            nextPageToken == other.nextPageToken &&
            search == other.search &&
            voiceType == other.voiceType &&
            category == other.category &&
            includeTotalCount == other.includeTotalCount

    override fun hashCode(): Int {
        var result = pageSize.hashCode()
        result = 31 * result + nextPageToken.hashCode()
        result = 31 * result + search.hashCode()
        result = 31 * result + voiceType.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + includeTotalCount.hashCode()
        return result
    }

    override fun toString(): String =
        "ListVoicesRequest(pageSize=$pageSize, nextPageToken=$nextPageToken, search=$search, " +
            "voiceType=$voiceType, category=$category, includeTotalCount=$includeTotalCount)"
}

/**
 * Creates a [ListVoicesRequest]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun ListVoicesRequest(
    configure: ListVoicesRequest.Builder.() -> Unit = {},
): ListVoicesRequest = ListVoicesRequest.Builder().apply(configure).build()
