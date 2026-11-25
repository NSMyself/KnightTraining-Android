package com.knightindustries.knighttraining.share

import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.model.SharedCourse
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.KnightTrainingApplication
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.net.ResourceLinks
import com.knightindustries.knighttraining.core.store.saveSharedResources
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * "Share → Knight Training" from any app (iOS's Share Extension): no picker, no screen. The link
 * is enriched like a pasted one and saved; a failed lookup still saves it with a fallback title,
 * so a share is never lost (iOS AGENTS.md principle 6). A course already in the library is skipped.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val url = text?.let(::firstLink)
        if (url == null) {
            Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // The app scope outlives this activity, which finishes at once like the iOS extension.
        val container = AppContainer.from(application as KnightTrainingApplication)
        container.appScope.launch { import(container, url, intent.getStringExtra(Intent.EXTRA_SUBJECT)) }
        Toast.makeText(this, R.string.resource_added, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        /** The first http(s) link in shared text: apps often share "Title https://…". */
        fun firstLink(text: String): String? = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '"')

        suspend fun import(container: AppContainer, url: String, sharedTitle: String?) {
            val store = container.store
            val enriched = container.enricher.enrich(url)
            val now = Instant.now()
            val title = enriched.title ?: sharedTitle?.takeIf { it.isNotBlank() } ?: ResourceLinks.fallbackTitle(url)
            val resource = Resource(
                kind = store.resourceKind(enriched.kindCode)!!,
                title = title,
                author = enriched.author, url = url, imageUrl = enriched.imageUrl, description = enriched.description,
                // Shared from a store or a course page: something the user has, as iOS marks it.
                isOwned = true,
                publisher = enriched.publisher, publicationDate = enriched.publicationDate,
                createdAt = now, updatedAt = now, lastAccessedAt = null,
            )
            // A Chessable/ChessMood course remembers which course it is, so it's linked to its
            // catalog row now or after a later sync, and sharing it again is skipped (iOS). Any
            // other link is always saved. ChessMood's id is only in the cover image's path.
            val course = SharedCourse.Identity.from(url, enriched.imageUrl)
                ?.takeIf { enriched.kindCode == ResourceKind.COURSE }
                ?.let { SharedCourse(resourceId = resource.id, identity = it, url = url, title = title) }
            store.saveSharedResources(listOf(resource), listOfNotNull(course))
        }
    }
}
