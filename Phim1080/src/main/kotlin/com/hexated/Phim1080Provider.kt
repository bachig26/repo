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
    
    data class filmInfo(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("upcoming") val description: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("time") val time: Int? = null,
        @JsonProperty("trailer") val trailer: TrailerInfo? = null,
    )

    data class TrailerInfo(
        @JsonProperty("original") val original: TrailerKey? = null,
    )
    
    data class TrailerKey(
        @JsonProperty("id") val id: String? = null,
    )    
    
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
            ).parsedSafe<filmInfo>()
        val title = filmInfo?.name?.trim().toString()
        val poster = filmInfo?.thumbnail
        val background = filmInfo?.poster
        val tags = document.select("div.film-content div.film-info-genre:nth-child(7) a").map { it.text() }
        val year = filmInfo?.year
        val tvType = if (document.select("div.episode-group-tab").isNotEmpty()
                        ) TvType.TvSeries else TvType.Movie
        val description =  document.select("div.film-info-description").text().trim()
        val trailerCode = filmInfo?.trailer?.original?.id
        val trailer = "https://www.youtube.com/embed/$trailerCode"
        val recommendations = document.select("div.related-block div.related-item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val epInfo =  app.get(
                "$mainUrl/api/v2/films/$Id/episodes?sort=name",
                referer = url,
                headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Requested-With" to "XMLHttpRequest",
                    )
                ).parsedSafe<Season>()
            val listEp = arrayListOf<com.lagradost.cloudstream3.Episode>()
            epInfo?.data?.forEachIndexed { index, episode ->
                    com.lagradost.cloudstream3.Episode(
                        data = episode?.link,
                        name = episode?.name,
                    )
                }
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, listEp) {
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
                this.backgroundPosterUrl = background
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
        val doc = app.get(
                "$mainUrl/api/v2/films/$Id/episodes/$epId",
                referer = data,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "cookie" to "xem1080=%3D",
                    "X-Requested-With" to "XMLHttpRequest"
                )
            ).parsedSafe<Video>()
        val link = encodeString(doc?.sources?.hls as String, 69)
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
    
    data class Season(
        @JsonProperty("data") val data: List<Episode>,
    )
    
    data class Episode(
        @JsonProperty("detail_name") val name: String? = null,
        @JsonProperty("link") val link: String? = null,
    )    
    
    data class Video(
        @JsonProperty("sources") val sources: Server? = null,
        @JsonProperty("subtitle") val subtitle: SubInfo? = null,
    )
    
    data class Server(
        @JsonProperty("hls") val hls: List<String>? = null,
    )    
    
    data class SubInfo(
        @JsonProperty("vi") val vi: String? = null,
        @JsonProperty("en") val en: String? = null,
    )    
    
}
