package com.fshare

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
//import com.phimhd.*
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
        const val POST_PER_PAGE = 6
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
                url = "$URL_DETAIL${itemHtml.attr("id").replace("post-","")}",
                apiName = name,
                type = TvType.TvSeries,
                posterUrl = itemHtml.selectFirst("img")?.attr("src")
            )
        }
        return newHomePageResponse(request.name, list ,true)
    }
}
