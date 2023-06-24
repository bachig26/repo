package com.fshare

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FshareProvider : MainAPI() {
    override var name = "Fshare"
    override var mainUrl = DOMAIN
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport: Boolean
        get() = true
    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
            TvType.TvSeries,
            TvType.Anime,
        )

    companion object {
        const val DOMAIN = "https://thuvienhd.com"
        const val URL_DETAIL = "$DOMAIN/?feed=fsharejson&id="
        const val URL_DETAIL_FILE_FSHARE = "https://www.fshare.vn/file/"
        const val URL_DETAIL_FSHARE =
            "https://www.fshare.vn/api/v3/files/folder?sort=type,name&page=0&per-page=99999&linkcode="
    }

    override val mainPage: List<MainPageData>
        get() = mainPageOf(
            "${mainUrl}/recent" to "Phim mới",
            "${mainUrl}/genre/phim-le" to "Phim Lẻ",
            "${mainUrl}/genre/series" to "Phim Bộ",
            "${mainUrl}/genre/tvb" to "Phim TVB",
            "${mainUrl}/genre/thuyet-minh-tieng-viet" to "Phim Thuyết Minh",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get("${request.data}/page/${page}").text
//        val listType: Type = object : TypeToken<ArrayList<HomeItem>>() {}.getType()
        val html = Jsoup.parse(response)
        val list = html.select(".items .item").map { itemHtml ->
            MovieSearchResponse(
                name = itemHtml.selectFirst("h3")?.text() ?: "",
                url = itemHtml.selectFirst("a")?.attr("href"),
                href = "$URL_DETAIL${itemHtml.attr("id").replace("post-","")}",
                apiName = name,
                type = TvType.TvSeries,
                posterUrl = itemHtml.selectFirst("img")?.attr("src")
            )
        }
        return newHomePageResponse(request.name, list ,true)
    }
    
    private fun Element.toSearchResult(): SearchResponse {
        val name = this.selectFirst("div.details div.title ")?.text()?.trim().toString()
        val url = this.selectFirst("div.details a")!!.attr("href")
        val posterUrl = this.selectFirst("div.image img")!!.attr("src")
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
        val poster = document.selectFirst("div.image img[itemprop=image]")?.attr("src")
        val tags = document.select("ul.entry-meta.block-film li:nth-child(4) a").map { it.text() }
        val year = document.select("ul.entry-meta.block-film li:nth-child(2) a").text().trim()
            .toIntOrNull()
        val description = document.select("div#film-content").text().trim()
        val trailer =
            document.select("div#trailer script").last()?.data()?.substringAfter("file: \"")
                ?.substringBefore("\",")
        val rating =
            document.select("ul.entry-meta.block-film li:nth-child(7) span").text().toRatingInt()
        val actors = document.select("ul.entry-meta.block-film li:last-child a").map { it.text() }
        val recommendations = document.select("ul#list-film-realted li.item").map {
            it.toSearchResult().apply {
                this.posterUrl = it.selectFirst("img")!!.attr("data-src").substringAfter("url=")
            }
        }
            return TvSeriesLoadResponse(
                name = title,
                url = url,
                apiName = name,
                type = TvType.TvSeries,
                posterUrl = poster,
                plot = description,
                tags = tags
            )        
    }
}
