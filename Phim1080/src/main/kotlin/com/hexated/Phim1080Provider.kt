package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.Jsoup

class Phim1080Provider : MainAPI() {
    override var mainUrl = "https://xem1080.com"
    override var name = "Phim1080"
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
        "$mainUrl/phim-de-cu?page=" to "Phim Đề Cử",
        "$mainUrl/tap-moi-nhat?page=" to "Mới Cập Nhật",
        "$mainUrl/the-loai/hoat-hinh?page=" to "Phim Hoạt Hình",
        "$mainUrl/phim-chieu-rap?page=" to "Phim Chiếu Rạp",
        "$mainUrl/phim-bo?page=" to "Phim Bộ",
        "$mainUrl/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/bang-xep-hang?page=" to "Bảng Xếp Hạng",
        "$mainUrl/phim-sap-chieu?page=" to "Phim Sắp Chiếu",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("div.tray-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title1 = this.selectFirst("div.tray-item-title")?.text()?.trim().toString()
        val title2 = this.selectFirst("div.related-item-title")?.text()?.trim().toString()
        val title = if (this.select("div.tray-item-title").isNotEmpty()
            ) title1 else title2
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")!!.attr("data-src")
        val temp = this.select("div.tray-film-likes").text()
        return if (temp.contains("tập")) {
            val episode = Regex("/\d+/").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("/"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.tray-item-quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/tim-kiem/$query"
        val document = app.get(link).document

        return document.select("div.tray-item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load( url: String ): LoadResponse {
        val document = app.get(url).document
        val Id = document.select("div.container")?.attr("data-id")?.trim()?.toIntOrNull()
        val title = document.selectFirst("h1.film-info-title")?.text()?.substringBefore("tập")?.trim().toString()
        val poster = document.selectFirst("div.film-thumbnail img")?.attr("src")
        val tags = document.select("div.film-content div.film-info-genre:nth-child(7) a").map { it.text() }
        val year = document.select("div.film-content div.film-info-genre:nth-child(2)")?.text()
            ?.substringAfter("Năm phát hành:")?.trim()?.toIntOrNull()
        val tvType = if (document.select("div.episode-group-tab").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.film-info-description").text().trim()
        val trailerCode = app.get(
            "$mainUrl/api/v2/films/$Id/trailer",
            referer = url
        ).document.select("id")
        
//        val trailer = app.post(
//            "$mainUrl/engine/ajax/gettrailervideo.php",
//            data = mapOf("id" to id),
//            referer = url
//        ).parsedSafe<Trailer>()?.code.let {
//            Jsoup.parse(it.toString()).select("iframe").attr("src")
//        }
        
        val trailer = "https://www.youtube.com/embed/$trailerCode"
        val recommendations = document.select("div.related-block div.related-item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.episode-list a").map {
                val href = it.select("a").attr("href")
                val episode = it.select("a episode-name")?.text()?.substringAfter("Tập")?.trim()?.toIntOrNull()
                val name = "$episode"
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
                addTrailer(trailer)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addTrailer(trailer)
                this.recommendations = recommendations
            }
        }
    }
    
    private fun encodeString(e: String, t: Int): String {
        var a = ""
        for (i in 0 until e.length) {
            val r = e[i].toInt()
            val o = r xor t
            a += o.toChar()
        }
        return a
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val Id = document.select("div.container")?.attr("data-id")?.trim()?.toIntOrNull()
        val epId = document.select("div.container")?.attr("data-episode-id")?.trim()?.toIntOrNull()
        val video = app.get(
                "$mainUrl/api/v2/films/$Id/episodes/$epId",
                referer = data,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).document.select("sources.hls")
        var link = encodeString(video as String, 69)
            safeApiCall {
                    callback.invoke(
                        ExtractorLink(
                            link,
                            "Phim1080",
                            link,
                            referer = "$mainUrl/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true,
                        )
                    )
            }
            return true
        }
}
// https://Phim1080.in/api/v2/films/21975/episodes/303806 - api link m3u8
// https://Phim1080.in/api/v2/films/21975/episodes?sort=name - api tập phim
// $mainUrl/api/v2/films/$Id/trailer - api trailer
// https://s198.imacdn.com/ff/2023/07/11/28055bb4c0e59e7c_d7a07589b2d87354_2662141689057850316068.jpg - api ảnh
