package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

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
            val r = e[i].code
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
        "$mainUrl/bo-suu-tap/disney-plus?page=" to "Disney+",
        "$mainUrl/bo-suu-tap/netflix-original?page=" to "Netflix",
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
        val title = this.selectFirst("div.tray-item-title")?.text()?.trim().toString()
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
    
    override suspend fun load( url: String ): LoadResponse {
        val document = app.get(
            url = url,
            referer = "$mainUrl/",
            headers = mapOf(
                "Sec-Ch-Ua-Mobile" to "?1",
                "Sec-Ch-Ua-Platform" to "\"Android\"",
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36 Edg/114.0.0.0",
            )
        ).document
        val fId = document.select("div.container").attr("data-id")
        val filmInfo =  app.get(
            "$mainUrl/api/v2/films/$fId",
            referer = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsedSafe<filmInfo>()
        val title = filmInfo?.name?.trim().toString()
        val poster = filmInfo?.thumbnail
        val background = filmInfo?.poster
        val slug = filmInfo?.slug
        val link = "$mainUrl/$slug"
        val tags = document.select("div.film-content div.film-info-genre:nth-child(7) a").map { it.text() }
        val year = filmInfo?.year
        val tvType = if (document.select("div.episode-group-tab").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description =  document.select("div.film-info-description").text().trim()
        val comingSoon = document.select("button.direction-trailer").isNotEmpty()
        val trailerCode = filmInfo?.trailer?.original?.id
        val trailer = "https://www.youtube.com/embed/$trailerCode"
        val recommendations = document.select("section.tray.index.related div.tray-content.carousel div.tray-item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val epsInfo =  app.get(
                "$mainUrl/api/v2/films/$fId/episodes?sort=name",
                referer = link,
                headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Requested-With" to "XMLHttpRequest",
                    )
                ).parsedSafe<MediaDetailEpisodes>()?.eps?.map { ep ->
                Episode(
                    data = fixUrl(ep.link.toString()),
                    name = ep.detailname,
                    episode = ep.episodeNumber,
                    )
            } ?: listOf()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, epsInfo) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.comingSoon = comingSoon
                addTrailer(trailer)
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.comingSoon = comingSoon
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
        val fId = document.select("div.container").attr("data-id")
        val epId = document.select("div.container").attr("data-episode-id")
        val doc = app.get(
            "$mainUrl/api/v2/films/$fId/episodes/$epId",
            referer = data,
            headers = mapOf(
                "Content-Type" to "application/json",
                "cookie" to "xem1080=%3D",
                "X-Requested-With" to "XMLHttpRequest"
            )
        )
        val optEncode = doc.text.substringAfter(",\"opt\":\"").substringBefore("\"},")
        val opt = encodeString(optEncode as String, 69)
        val hlsEncode = doc.text.substringAfter(":{\"hls\":\"").substringBefore("\"},")
        val hls = encodeString(hlsEncode as String, 69)
        val fb = doc.text.substringAfter("fb\":[{\"src\":\"").substringBefore("\",").replace("\\", "")
        
        listOf(
            if (hls.contains(".m3u8")) {Pair("$hls", "HS", true)} else {},
            if (fb.contains(".mp4")) {Pair("$fb", "FB", false)} else {},
            if (opt.contains(".m3u8")) {Pair("$opt", "OP", true)} else {},
        ).apmap { (link, source, isM3u8) ->
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        source,
                        source,
                        link,
                        referer = "$mainUrl/",
                        quality = Qualities.P1080.value,
                        isM3u8,
                    )
                )
            }
        }
//        when {
//            hls.contains(".m3u8") -> callback.invoke(
//                ExtractorLink(
//                    "HS",
//                    "HS",
//                    hls,
//                    referer = data,
//                    quality = Qualities.Unknown.value,
//                    isM3u8 = true,
//                )
//            )
//            fb.contains(".mp4") -> callback.invoke(
//                ExtractorLink(
//                    "FB",
//                    "FB",
//                    fb,
//                    referer = data,
//                    quality = Qualities.Unknown.value,
//                    isM3u8 = false,
//                )
//            )
//            opt.contains(".m3u8") -> callback.invoke(
//                ExtractorLink(
//                    "OP",
//                    "OP",
//                    opt,
//                    referer = data,
//                    quality = Qualities.Unknown.value,
//                    isM3u8 = true,
//                )
//            )
//        }
        val subId = doc.parsedSafe<Media>()?.subtitle?.vi
        val isSubIdEmpty = subId.isNullOrBlank()
        if (!isSubIdEmpty) {
            subtitleCallback.invoke(
                SubtitleFile(
                    "Vietnamese",
                    "$mainUrl/subtitle/$subId.vtt"
                )
            )
        }
        return true
    }

    data class filmInfo(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("trailer") val trailer: TrailerInfo? = null,
    )

    data class TrailerInfo(
        @JsonProperty("original") val original: TrailerKey? = null,
    )

    data class TrailerKey(
        @JsonProperty("id") val id: String? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("data") val eps: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Episodes(
        @JsonProperty("link") val link: String? = null,
        @JsonProperty("detail_name") val detailname: String? = null,
        @JsonProperty("name") val episodeNumber: Int? = null,
    )

    data class Media(
        @JsonProperty("subtitle") val subtitle: SubInfo? = null,
    )

    data class SubInfo(
        @JsonProperty("vi") val vi: String? = null,
    )

}
