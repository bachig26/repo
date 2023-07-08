package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder

class PhimmoichillProvider : MainAPI() {
    override var mainUrl = "https://phimmoichilld.net"
    override var name = "Phimmoichill"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/genre/phim-chieu-rap/page-" to "Phim Chiếu Rạp",
        "$mainUrl/list/phim-le/page-" to "Phim Lẻ",
        "$mainUrl/list/phim-bo/page-" to "Phim Bộ",
        "$mainUrl/genre/phim-hoat-hinh/page-" to "Phim Hoạt Hình",
        "$mainUrl/country/phim-han-quoc/page-" to "Phim Hàn Quốc",
        "$mainUrl/country/phim-trung-quoc/page-" to "Phim Trung Quốc",
        "$mainUrl/country/phim-thai-lan/page-" to "Phim Thái Lan",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("li.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("p,h3")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = decode(this.selectFirst("img")!!.attr("src").substringAfter("url="))
        val temp = this.select("span.label").text()
        return if (temp.contains(Regex("\\d"))) {
            val episode = Regex("(\\((\\d+))|(\\s(\\d+))").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\(|\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality =
                temp.replace(Regex("(-.*)|(\\|.*)|(?i)(VietSub.*)|(?i)(Thuyết.*)"), "").trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/tim-kiem/$query"
        val document = app.get(link).document

        return document.select("ul.list-film li").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1[itemprop=name]")?.text()?.trim().toString()
        val link = document.select("ul.list-button li:last-child a").attr("href")
        val poster = document.selectFirst("div.image img[itemprop=image]")?.attr("src")
        val tags = document.select("ul.entry-meta.block-film li:nth-child(4) a").map { it.text() }!!.substringAfter("Phim").trim()
        val year = document.select("ul.entry-meta.block-film li:nth-child(2) a").text().trim()
            .toIntOrNull()
        val tvType = if (document.select("div.latest-episode").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div#film-content")!!.substringBefore("@phimmoi").text().trim()
        val trailer =
            document.select("div#trailer script").last()?.data()?.substringAfter("file: \"")
                ?.substringBefore("\",")
        val rating =
            document.select("ul.entry-meta.block-film li:nth-child(7) span").text().toRatingInt()
        val actors = document.select("ul.entry-meta.block-film li:last-child a").map { it.text() }
        val recommendations = document.select("ul#list-film-realted li.item").map {
            it.toSearchResult().apply {
                this.posterUrl = decode(it.selectFirst("img")!!.attr("data-src").substringAfter("url="))
            }
        }

        return if (tvType == TvType.TvSeries) {
            val docEpisodes = app.get(link).document
            val episodes = docEpisodes.select("ul#list_episodes > li").map {
                val href = it.select("a").attr("href")
                val episode =
                    it.select("a").text().replace(Regex("[^0-9]"), "").trim().toIntOrNull()
                val name = "Episode $episode"
                Episode(
                    data = href,
                    name = name,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }
    
    fun getDataEpisode(
        url: String,
    ): List<Episode> {
        val doc: Document = Jsoup.connect(url).timeout(60 * 1000).get()
        var idEpisode = ""
        var idMovie = ""
        var token = ""
        val listEpHtml = doc.select("#list_episodes li")
        val list = arrayListOf<Episode>();
        listEpHtml.forEach {
            val url = it.selectFirst("a")!!.attr("href")
            val name = it.selectFirst("a")!!.text()
            val id = it.selectFirst("a")!!.attr("data-id")
            val episode = Episode(url,name, 0, null, null, null, id);
            list.add(episode);
        }
        return list
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DuongKK", "data LoadLinks ---> $data ")
        val listEp = getDataEpisode(data)
        val idEp = listEp.find { data.contains(it.description!!) }?.description ?: data.substring(data.indexOf("-pm")+3)
        Log.d("DuongKK", "data LoadLinks ---> $data  --> $idEp")
        try {
            val urlRequest =
                "${this.mainUrl}/chillsplayer.php" //'https://subnhanh.net/frontend/default/ajax-player'
            val response = app.post(urlRequest, mapOf(), data = mapOf("qcao" to idEp)).okhttpResponse
            if (!response.isSuccessful || response.body == null) {
//                Log.e("DuongKK ${response.message}")
                return false
            }
            val doc: Document = Jsoup.parse(response.body?.string())
            val jsHtml = doc.html()
            if (doc.selectFirst("iframe") != null) {
                // link embed
                val linkIframe =
                    "http://ophimx.app/player.html?src=${doc.selectFirst("iframe")!!.attr("src")}"
                return false
            } else {
                // get url stream
                var keyStart = "iniPlayers(\""
                var keyEnd = "\""
                if (!jsHtml.contains(keyStart)) {
                    keyStart = "initPlayer(\""
                }
                var tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
                var tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
                val urlPlaylist = if (tempEnd.contains("https://")) {
                    tempEnd
                } else {
                    "https://${HOST_STREAM}/raw/${tempEnd}/index.m3u8"
                }
                callback.invoke(
                    ExtractorLink(
                        urlPlaylist,
                        this.name,
                        urlPlaylist,
                        mainUrl,
                        getQualityFromName("720"),
                        true
                    )
                )

                //get url subtitle
                keyStart = "tracks:"
                if (jsHtml.contains(keyStart)) {
                    keyEnd = "]"
                }
                tempStart = jsHtml.substring(jsHtml.indexOf(keyStart) + keyStart.length)
                tempEnd = tempStart.substring(0, tempStart.indexOf(keyEnd))
                val urls = extractUrls(tempEnd)
                urls?.forEach {
                    subtitleCallback.invoke(SubtitleFile("vi", it))
                }
            }
        } catch (error: Exception) {
        }
        return true
    }

}
