// use an integer for version numbers
version = 12


cloudstream {
    // All of these properties are optional, you can safely remove them


    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified
    language = "vi"
    // All of these properties are optional, you can safely remove them

    description = "Chuyên Anime Vietsub 1080P"
    authors = listOf("TuaSan")

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
            "Anime",
    )
    iconUrl = "https://cdn.animevietsub.moe/data/logo/logoz.png"
}
