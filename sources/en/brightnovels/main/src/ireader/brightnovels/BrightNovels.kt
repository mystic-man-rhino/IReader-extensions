package ireader.brightnovels

import io.ktor.client.request.get
import ireader.core.source.Dependencies
import ireader.core.source.SourceFactory
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.*
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.*
import tachiyomix.annotations.*

@Extension
@AutoSourceId(seed = "BrightNovels")
@GenerateFilters(title = true)
@GenerateCommands(detailFetch = true, contentFetch = true, chapterFetch = true)
@GenerateTests(unitTests = true, integrationTests = true, searchQuery = "forgotten field", minSearchResults = 0)
@TestFixture(
    "https://brightnovels.com/series/the-forgotten-field",
    chapterUrl = "https://brightnovels.com/series/the-forgotten-field/1",
    expectedAuthor = "",
    expectedTitle = "The Forgotten Field"
)
@TestExpectations()
@SourceMeta(description = "Read novels from Bright Novels", nsfw = false)
abstract class BrightNovels(deps: Dependencies) : SourceFactory(deps = deps) {

    override val lang: String get() = "en"
    override val baseUrl: String get() = "https://brightnovels.com"
    override val id: Long get() = BrightNovelsSourceId.ID
    override val name: String get() = "Bright Novels"

    override val exploreFetchers: List<BaseExploreFetcher>
        get() = listOf(
            BaseExploreFetcher(
                "Latest",
                endpoint = "/?latest_page={page}",
                selector = "data",
                nameSelector = "title",
                linkSelector = "series_slug",
                coverSelector = "coverImage",
                addBaseUrlToLink = false,
                maxPage = 373
            ),
            BaseExploreFetcher(
                "Search",
                endpoint = "/series?q={query}",
                selector = "data",
                nameSelector = "title",
                linkSelector = "slug",
                coverSelector = "cover",
                addBaseUrlToLink = false,
                type = SourceFactory.Type.Search
            )
        )

    override val detailFetcher: Detail
        get() = SourceFactory.Detail(
            nameSelector = "title",
            coverSelector = "coverImage",
            descriptionSelector = "description",
            categorySelector = "genres",
            addBaseurlToCoverLink = false,
            onStatus = { MangaInfo.UNKNOWN }
        )

    override val chapterFetcher: Chapters
        get() = SourceFactory.Chapters(
            selector = "chapters",
            nameSelector = "name",
            linkSelector = "slug",
            addBaseUrlToLink = false,
            reverseChapterList = true
        )

    override val contentFetcher: Content
        get() = SourceFactory.Content(
            pageTitleSelector = "title",
            pageContentSelector = "content"
        )

    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val url = "$baseUrl/?latest_page=$page"
        val document = client.get(requestBuilder(url)).asJsoup()
        return parseJsonListPage(document, "latestUpdates")
    }

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        titleFilter?.value?.takeIf { it.isNotBlank() }?.let { query ->
            // Site's search API is broken ("Query is too short" for any input)
            // Use homepage latest updates and filter client-side
            val url = "$baseUrl/?latest_page=$page"
            val document = client.get(requestBuilder(url)).asJsoup()
            val allNovels = parseJsonListPage(document, "latestUpdates")
            val filtered = allNovels.mangas.filter {
                it.title.contains(query, ignoreCase = true)
            }
            return MangasPageInfo(filtered, false)
        }
        return getMangaList(null, page)
    }

    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return parseDetailPage(document, manga)
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val document = client.get(requestBuilder(manga.key)).asJsoup()
        return parseChapterList(document)
    }

    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val document = client.get(requestBuilder(chapter.key)).asJsoup()
        return parsePageList(document)
    }

    private fun parseJsonListPage(document: Document, dataKey: String): MangasPageInfo {
        val json = parseDataPage(document) ?: return MangasPageInfo(emptyList(), false)
        val dataContainer = json["props"]?.jsonObject?.get(dataKey)?.jsonObject
            ?: return MangasPageInfo(emptyList(), false)

        val dataArray = dataContainer["data"]?.jsonArray ?: return MangasPageInfo(emptyList(), false)
        val meta = dataContainer["meta"]?.jsonObject
        val hasNext = meta?.get("has_more_pages")?.jsonPrimitive?.boolean ?: false

        val mangas = dataArray.mapNotNull { element ->
            val obj = element.jsonObject
            val slug = obj["series_slug"]?.jsonPrimitive?.content
                ?: obj["slug"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val coverObj = obj["cover"]
            val coverUrl = when {
                coverObj is JsonObject -> coverObj["url"]?.jsonPrimitive?.content ?: ""
                coverObj is JsonPrimitive -> coverObj.content
                else -> obj["coverImage"]?.jsonPrimitive?.content ?: ""
            }
            MangaInfo(
                key = "$baseUrl/series/$slug",
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                cover = if (coverUrl.startsWith("/")) "$baseUrl$coverUrl" else coverUrl,
            )
        }
        return MangasPageInfo(mangas, hasNext)
    }

    private fun parseDetailPage(document: Document, manga: MangaInfo): MangaInfo {
        val json = parseDataPage(document) ?: return manga
        val series = json["props"]?.jsonObject?.get("series")?.jsonObject ?: return manga

        val title = series["title"]?.jsonPrimitive?.content ?: manga.title
        val coverObj = series["cover"]
        val coverUrl = when (coverObj) {
            is JsonObject -> coverObj["url"]?.jsonPrimitive?.content ?: ""
            is JsonPrimitive -> coverObj.content
            else -> ""
        }
        val description = series["description"]?.jsonPrimitive?.content ?: ""
        val genres = try {
            series["genres"]?.jsonArray?.mapNotNull { 
                (it as? JsonPrimitive)?.content 
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }

        return manga.copy(
            title = title,
            cover = if (coverUrl.startsWith("/")) "$baseUrl$coverUrl" else coverUrl,
            description = description,
            genres = genres,
            status = MangaInfo.UNKNOWN
        )
    }

    private fun parseChapterList(document: Document): List<ChapterInfo> {
        val json = parseDataPage(document) ?: return emptyList()
        val series = json["props"]?.jsonObject?.get("series")?.jsonObject ?: return emptyList()
        val chaptersArray = series["chapters"]?.jsonArray ?: return emptyList()
        val seriesSlug = series["slug"]?.jsonPrimitive?.content ?: ""

        return chaptersArray.map { element ->
            val obj = element.jsonObject
            val slug = obj["slug"]?.jsonPrimitive?.content ?: ""
            val name = obj["name"]?.jsonPrimitive?.content ?: ""
            ChapterInfo(
                name = name,
                key = "$baseUrl/series/$seriesSlug/$slug"
            )
        }
    }

    private fun parsePageList(document: Document): List<Page> {
        val json = parseDataPage(document) ?: return emptyList()
        val chapter = json["props"]?.jsonObject?.get("chapter")?.jsonObject ?: return emptyList()
        val content = chapter["content"]?.jsonPrimitive?.content ?: ""
        val title = chapter["title"]?.jsonPrimitive?.content ?: ""

        val doc = Ksoup.parse(content)
        val paragraphs = doc.select("p").eachText()

        if (paragraphs.isEmpty()) {
            return listOf(Text("$title\n\n$content"))
        }

        return paragraphs.map { text -> Text(text) }
    }

    private fun htmlDecode(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun parseDataPage(document: Document): JsonObject? {
        val dataPage = document.select("div[data-page]").attr("data-page")
        if (dataPage.isBlank()) return null
        // Use lenient parsing - data-page contains Livewire JSON with
        // unescaped quotes in descriptions from double-encoded HTML entities
        return try {
            // First try normal parsing
            Json.parseToJsonElement(htmlDecode(dataPage)).jsonObject
        } catch (e: Exception) {
            // If that fails, fix unescaped quotes in JSON string values
            val decoded = htmlDecode(dataPage)
            val fixed = fixJsonQuotes(decoded)
            try {
                Json.parseToJsonElement(fixed).jsonObject
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun fixJsonQuotes(json: String): String {
        // Fix unescaped quotes inside JSON string values
        // Strategy: Parse character by character, tracking if we're inside a string
        val result = StringBuilder()
        var inString = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (c == '"' && (i == 0 || json[i - 1] != '\\')) {
                if (inString) {
                    // Check if this is a real string end (followed by , or } or ] or : or whitespace)
                    val nextNonSpace = json.substring(i + 1).firstOrNull { !it.isWhitespace() }
                    if (nextNonSpace != null && nextNonSpace in ",}]:") {
                        inString = false
                        result.append(c)
                    } else {
                        // This is an unescaped quote inside a string - escape it
                        result.append("\\\"")
                    }
                } else {
                    inString = true
                    result.append(c)
                }
            } else {
                result.append(c)
            }
            i++
        }
        return result.toString()
    }
}
