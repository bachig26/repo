package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder

class VuigheProvider : MainAPI() {
    override var mainUrl = "https://mehoathinh2.com"
    override var name = "Vuighe"
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
        "$mainUrl/hot-trong-ngay/trang-" to "Bảng Xếp Hạng",
        "$mainUrl/phim-bo/trang-" to "Phim Bộ",
        "$mainUrl/phim-le/trang-" to "Phim Lẻ",
        "$mainUrl/tap-moi-nhat/trang-" to "Mới Cập Nhật",
        "$mainUrl/phim-chieu-rap/trang-" to "Phim Chiếu Rạp",
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

    private fun decode(input: String): String? = URLDecoder.decode(input, "utf-8")

    private fun Element.toSearchResult(): SearchResponse {
//        val title = this.selectFirst("div.tray-item-title")?.text()?.trim().toString().orEmpty()
//            .ifEmpty { this.selectFirst("div.related-item-title")?.text()?.trim().toString() }
        val title = if (this.selectFirst("div.tray-item-title").isNotEmpty())
            this.selectFirst("div.tray-item-title")?.text()?.trim().toString()
            else this.selectFirst("div.related-item-title")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")!!.attr("data-src")
        val temp = this.select("div.tray-item-quality").text()
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

        return document.select("div.tray-item").map {
            it.toSearchResult()
        }
    }

    override suspend fun load( url: String ): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.film-info-title").text().substringBefore("Tập")?.trim().toString()
        val link = document.select("ul.list-button li:last-child a").attr("href")
        val poster = document.selectFirst("div.film-thumbnail img")?.attr("src")
        val tags = document.select("div.film-content div.film-info-genre:nth-child(2) a").map { it.text() }
        val year = document.selectFirst("div.film-thumbnail img")?.attr("src").text()
            .substringAfter("ff/").substringBefore("/").trim().toIntOrNull()
        val tvType = if (document.select("a.episode-item").isNotEmpty()
        ) TvType.TvSeries else TvType.Movie
        val description = document.select("div.film-info-description").text().trim()
        val recommendations = document.select("div.related-block div.related-item").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.film-episode a").map {
                val href = it.select("a").attr("href")
                val episode =
                    it.select("a").text().trim().toIntOrNull()
//                val name = "Episode $episode"
                Episode(
                    data = href,
//                    name = name,
                    episode = episode,
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
            }
        }
    }
}
// https://Vuighe.in/api/v2/films/21975/episodes/303806 - api link m3u8
// https://Vuighe.in/api/v2/films/21975/episodes?sort=name - api tập phim
// https://s198.imacdn.com/ff/2023/07/11/28055bb4c0e59e7c_d7a07589b2d87354_2662141689057850316068.jpg - api ảnh
