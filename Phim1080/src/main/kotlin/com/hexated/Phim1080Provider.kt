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
import java.io.ByteArrayOutputStream

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
    
    private fun encodeString(e: String, t: Int): String {
        var a = ""
        for (i in 0 until e.length) {
            val r = e[i].toInt()
            val o = r xor t
            a += o.toChar()
        }
        return a
    }

    override val mainPage = mainPageOf(
        "$mainUrl/phim-de-cu?page=" to "Phim Đề Cử",
        "$mainUrl/the-loai/hoat-hinh?page=" to "Phim Hoạt Hình",
        "$mainUrl/phim-chieu-rap?page=" to "Phim Chiếu Rạp",
        "$mainUrl/phim-bo?page=" to "Phim Bộ",
        "$mainUrl/phim-le?page=" to "Phim Lẻ",
        "$mainUrl/bang-xep-hang?page=" to "Bảng Xếp Hạng",
        "$mainUrl/hom-nay-xem-gi?page=" to "Hôm Nay Xem Gì",
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
        return if (temp.contains("/")) {
//            val episode = Regex("((\\d+)\\()|((\\d+)\\s)").find(temp)?.groupValues?.map { num ->
//                num.replace(Regex("\\(|\\s"), "")
//            }?.distinct()?.firstOrNull()?.toIntOrNull()
            val episode = Regex("((\\d+)\\s)").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("span.tray-item-quality").text().replace("FHD", "HD").trim()
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
        val Id = document.select("div.container")?.attr("data-id")?.trim()
        val filmInfo =  app.get(
            "$mainUrl/api/v2/films/$Id",
            referer = url,
            headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text
        val title = document.selectFirst("h1.film-info-title")?.text()?.substringBefore("tập")?.trim().toString()
        val poster = filmInfo.substringAfter("poster\":\"").substringBefore("\",")
        val background = filmInfo.substringAfter("thumbnail\":\"").substringBefore("\",")
        val tags = document.select("div.film-content div.film-info-genre:nth-child(7) a").map { it.text() }
        val year = document.select("div.film-content div.film-info-genre:nth-child(2)")?.text()
            ?.substringAfter("Năm phát hành:")?.trim()?.toIntOrNull()
        val tvType = if (document.select("div.episode-group-tab").isNotEmpty()
                        ) TvType.TvSeries else TvType.Movie
//        val description = document.select("div.film-info-description").text().trim()
        val description =  app.get(
            "$mainUrl/api/v2/films/$Id",
            referer = url,
            headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text.toByteArray(Charsets.UTF_8)
        val trailerCode = filmInfo.substringAfter("id\":\"").substringBefore("\",")
        val trailer = "https://www.youtube.com/embed/$trailerCode"
        val recommendations = document.select("div.related-block div.related-item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.episode-list").map {
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
                this.backgroundPosterUrl = background
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
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        val Id = document.select("div.container")?.attr("data-id")?.trim()
        val epId = document.select("div.container")?.attr("data-episode-id")?.trim()
        val sources = app.get(
                "$mainUrl/api/v2/films/$Id/episodes/$epId",
                referer = data,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "cookie" to "xem1080=%3D",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).text.substringAfter("hls\":\"")
                    .substringBefore("\"},")
        val link = encodeString(sources as String, 69)
            safeApiCall {
                    callback.invoke(
                        ExtractorLink(
                            link,
                            "Phim1080",
                            link,
                            referer = "$mainUrl/",
                            quality = Qualities.Unknown.value,
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
