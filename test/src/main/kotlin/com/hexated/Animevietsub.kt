package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class AnimevietsubProvider : MainAPI() {
    override var mainUrl = "https://animevietsub.moe"
    override var name = "Animevietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
    )

    override val mainPage = mainPageOf(
        "$mainUrl/danh-sach/list-dang-chieu/////trang-" to "Anime Đang Chiếu",
        "$mainUrl/anime-bo/trang-" to "Anime Bộ",
        "$mainUrl/anime-le/trang-" to "Anime Lẻ",
        "$mainUrl/hoat-hinh-trung-quoc/trang-" to "Hoạt Hình Trung Quốc",
        "$mainUrl/anime-sap-chieu/trang-" to "Anime Sắp Chiếu",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select(".TPostMv").mapNotNull {
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
        val title = this.selectFirst("a .Title")?.text()?.trim().toString()
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = this.selectFirst("img")!!.attr("src")
        val temp = this.select("span.Time.AAIco-access_time").text()
        return if (temp.contains("/")) {
            val episode = Regex("((\\d+)\\s)").find(temp)?.groupValues?.map { num ->
                num.replace(Regex("\\s"), "")
            }?.distinct()?.firstOrNull()?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("span.Qlty").text().replace("FHD", "HD").trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = "$mainUrl/tim-kiem/$query"
        val document = app.get(link).document

        return document.select("section .TPostMv").map {
            it.toSearchResult()
        }
    }
    
    override suspend fun load( url: String ): LoadResponse {
        val doc = app.get(url).document
        
        val title = doc.selectFirst(".Title")?.text()?.trim().toString()
        val poster = fixUrl(doc.select("header figure.Objf img").attr("src"))
        val background = fixUrl(doc.select("img.TPostBg").attr("src"))
        val link = doc.select(".watch_button_more").attr("href")
        val rating = doc.select("strong#average_score").text().toRatingInt()
        val tags = doc.select("ul.InfoList li:nth-last-child(4) a").map { it.text() }
        val year = doc.selectFirst(".Info .Date")?.text()?.trim()?.replace("(", "")?.replace(")", "")?.toIntOrNull()
        val tvType = if (tags.contains("Anime bộ")) TvType.TvSeries else TvType.Movie
        val description =  doc.select(".Description").text()
        val comingSoon = tags.contains("Anime sắp chiếu")
        val trailer = doc.select("div#MvTb-Trailer").attr("src").toString()
        val recommendations = doc.select("div.MovieListRelated .TPostMv").map {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val docEpisodes = app.get(link).document
            val episodes = docEpisodes.select(".list-episode li").map {
                val id = it.selectFirst("a")!!.attr("data-id")
                val name = it.selectFirst("a")!!.text()
                Episode(id, name, 0, null, null, null,id)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.rating = rating
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
                this.rating = rating
                this.tags = tags
                this.comingSoon = comingSoon
                addTrailer(trailer)
                this.recommendations = recommendations
            }
        }
    }

}
