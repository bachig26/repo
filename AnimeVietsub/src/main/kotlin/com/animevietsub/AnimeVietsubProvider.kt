package com.animevietsub

import android.util.Log
import android.util.Patterns
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.cloudstream3.*

import com.lagradost.cloudstream3.ui.search.SearchFragment
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.regex.Matcher
import java.util.regex.Pattern

class AnimeVietsubProvider : MainAPI() {
    override var mainUrl = "https://animevietsub.moe"
    override var name = "AnimeVietsub"
    override var lang = "vi"

    override val hasQuickSearch: Boolean
        get() = true

    override val hasMainPage: Boolean
        get() = true

    override val hasChromecastSupport: Boolean
        get() = true

    override val hasDownloadSupport: Boolean
        get() = true

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
            TvType.Anime,
        )
    override val vpnStatus: VPNStatus
        get() = VPNStatus.None

    companion object {
        const val HOST_STREAM = "so-trym.topphimmoi.org";
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val html = app.get(mainUrl).text
        val doc = Jsoup.parse(html)
        val listHomePageList = arrayListOf<HomePageList>()
        doc.select("section").forEach {
            val name = it.select("h1").text()
//            val urlMore = fixUrl(it.select(".viewall").attr("href"))
            val listMovie = it.select(".TPostMv").map {
                val title = it.selectFirst("a .Title")!!.text()
                val href = fixUrl(it.selectFirst("a")!!.attr("href"))
                val year = 0
                val image = it.selectFirst("img")!!.attr("src")
                MovieSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Movie,
                    image,
                    year,
                    posterHeaders = mapOf("referer" to mainUrl)
                )
            }
            if (listMovie.isNotEmpty())
                listHomePageList.add(HomePageList(name, listMovie))

        }

        return HomePageResponse(listHomePageList)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/tim-kiem/${query}/"//https://chillhay.net/search/boyhood
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("section .TPostMv").map {
            getItemMovie(it)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        try {
            val url =  "$mainUrl/tim-kiem/${query}/"//https://chillhay.net/search/boyhood
            val html = app.get(url).text
            val document = Jsoup.parse(html)

            return document.select("section .TPostMv").map {
                getItemMovie(it)
            }
        }catch (e : Exception){
            e.printStackTrace()
            return null
        }
    }

    private fun getItemMovie(it: Element): MovieSearchResponse {
        val title = it.selectFirst("a .Title")!!.text()
        val href = fixUrl(it.selectFirst("a")!!.attr("href"))
//        val year = 0
        val image = it.selectFirst("img")!!.attr("src")
        val temp = it.select("div.Image span").text()
        return if (temp.select("i").contains(Regex("\\d"))) {
            newMovieSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = image
                addSub(temp)?.toIntOrNull()
            }
        } else if (temp.contains("HD")){
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = image
                addQuality("HD")
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = image
            }
        }
    }

    fun findUrls(input: String): List<String> {
        val pattern =
            "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))"
        val regex = Regex(pattern)
        return regex.findAll(input).map { it.value }.toList()
    }

    data class ServerResponse(
        @JsonProperty("html") val html: String,
        @JsonProperty("success") val success: Int
    )

    data class FileResponse(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
    )

    data class LinkResponse(
        @JsonProperty("link") val link: List<FileResponse>,
        @JsonProperty("success") val success: Int
    )

    override suspend fun load(url: String): LoadResponse? {
        val html = app.get(url).text
        val doc: Document = Jsoup.parse(html)
        val realName = doc.select(".Title").first()!!.text()
        var year = 0
        try {
            val rawYear = doc.selectFirst(".Info .Date")?.text()?.trim()?.replace("(", "")?.replace(")", "")
            rawYear?.let {
                year = it.toInt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val rating = doc.select("strong#average_score").text().toRatingInt()
        val tags = doc.select("ul.InfoList li:nth-last-child(4) a").map { it.text() }
        val trailer = document.select("div#MvTb-Trailer").attr("src")
        val description = doc.select(".Description").text()
        val urlBackdoor = fixUrl(doc.select(".TPostBg img").attr("src"))
        val recommendations = doc.select("div.MovieListRelated .TPostMv").map {
                getItemMovie(it)
            }
        val urlWatch = doc.select(".watch_button_more").attr("href")
        val episodes = getDataEpisode(urlWatch)
        return TvSeriesLoadResponse(
            name = realName,
            url = url,
            apiName = this.name,
            type = TvType.TvSeries,
            posterUrl = urlBackdoor,
            year = year,
            rating = rating,
            tags = tags,
            plot = description,
            recommendations = recommendations,
            showStatus = null,
            episodes = episodes,
            comingSoon = episodes.isEmpty(),
            posterHeaders = mapOf("referer" to mainUrl)
        )
    }

    fun getDataEpisode(
        url: String,
    ): List<Episode> {
        if(!url.contains("http")){
            return emptyList()
        }
        val doc: Document = Jsoup.connect(url).timeout(60 * 1000).get()
        val listEpHtml = doc.select(".list-episode li")
        val list = arrayListOf<Episode>();
        listEpHtml.forEach {
//            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.text()
            val id = it.selectFirst("a")!!.attr("data-id")
//            val hash = it.selectFirst("a")!!.attr("data-hash")
            val episode = Episode(id, name, 0, null, null, null,id);
            list.add(episode);
        }
        return list
    }

    private fun extractUrl(input: String) =
        input
            .split(" ")
            .firstOrNull { Patterns.WEB_URL.matcher(it).find() }
            ?.replace("url(", "")
            ?.replace(")", "")

    /**
     * Returns a list with all links contained in the input
     */
    fun extractUrls(text: String): List<String>? {
        val containedUrls: MutableList<String> = ArrayList()
        val urlRegex =
            "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)"
        val pattern: Pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val urlMatcher: Matcher = pattern.matcher(text)
        while (urlMatcher.find()) {
            containedUrls.add(
                text.substring(
                    urlMatcher.start(0),
                    urlMatcher.end(0)
                )
            )
        }
        return containedUrls
    }

    /**
     * 1.  dùng idEp để lấy danh sách server
     * 2. dùng id server và mã hash của link để lấy link stream
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DuongKK", "data LoadLinks ---> $data ")
        val idEp = data

        try {
            //get server
            val urlRequest =
                "${this.mainUrl}/ajax/player?v=2019a" //'https://subnhanh.net/frontend/default/ajax-player'
            var response = app.post(
                urlRequest,
                data = mapOf("episodeId" to idEp, "backup" to "1")
                , headers = mapOf("Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8")
            ).text
            Log.d("BLUE","text = ${response}")
            val serverRes  =
                Gson().fromJson<ServerResponse>(response, ServerResponse::class.java)
            Log.d("BLUE","serverRes $serverRes")
            val doc: Document = Jsoup.parse(serverRes.html)
            val jsHtml = doc.html()
            Log.d("BLUE", "jsHtml $jsHtml")


            doc.select(".btn3dsv").amap {
                val linkHash = it.attr("data-href")
                val nameSv = it.text()
                val idSv = it.attr("data-id")
                val play = it.attr("data-play")
                if(play == "embed"){
                    return@amap
                }
                val responseLink = app.post(
                    urlRequest,
                    mapOf(),
                    data = mapOf("link" to linkHash,
                        "id" to idSv,
                        "play" to play,
                    )
                ).text
                val linkRes  =
                    Gson().fromJson<LinkResponse>(responseLink, LinkResponse::class.java)
                Log.d("BLUE", "linkRes $responseLink $linkRes")
                linkRes.link.forEach {
                    var link = it.file
                    if(link.startsWith("//")){
                        link = "https:$link"
                    }
                    callback.invoke(
                        ExtractorLink(
                            link,
                            nameSv,
                            link,
                            mainUrl,
                            getQualityFromName(it.label),
                            link.contains(".m3u8")
                        )
                    )
                }
            }

        } catch (error: Exception) {
            error.printStackTrace()
        }
        return true
    }
}
