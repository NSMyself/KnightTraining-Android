package com.knightindustries.knighttraining.core.net

import java.time.Instant

/** What a pasted or shared link can tell the add-resource form. */
data class EnrichedLink(
    val kindCode: String,
    val title: String?,
    val author: String?,
    val description: String?,
    val imageUrl: String?,
    val publisher: String?,
    val publicationDate: Instant?,
)

/**
 * A link's page, then, when the page names a valid ISBN, the book's authoritative record, which
 * wins wherever both have a value: a shop's page is a poor stand-in (Amazon's `og:image` is its
 * logo). Never fails: with nothing found, it's just the kind guessed from the URL.
 */
class ResourceEnricher(
    private val metadata: LinkMetadataService = LinkMetadataService(),
    private val books: BookLookupService = BookLookupService(),
) {
    suspend fun enrich(url: String): EnrichedLink {
        val page = metadata.fetch(url)
        val isbn = page?.isbn?.let(ResourceLinks::validIsbn)
        val book = isbn?.let { books.lookup(it) }
        return EnrichedLink(
            kindCode = ResourceLinks.guessKindCode(url, isbn),
            title = book?.title ?: page?.title,
            author = book?.authors?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: page?.author,
            description = book?.description ?: page?.description,
            imageUrl = book?.imageUrl ?: page?.imageUrl,
            publisher = book?.publisher ?: page?.publisher,
            publicationDate = book?.publishedDate ?: page?.publishedDate,
        )
    }
}
