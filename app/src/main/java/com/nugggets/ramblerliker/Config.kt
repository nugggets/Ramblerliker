package com.nugggets.ramblerliker

// Общие константы: адреса по умолчанию, темп, окно, размеры журнала, блокируемые домены, запросы Mamba/OK

const val SITE = "https://m.dating.rambler.ru"

const val DEFAULT_LIKE_URL = "/api/a-likes?likes=1&login={login}&num=1"

const val DEFAULT_SEARCH_URL = "/api/a-search?foto=1&offset={offset}&save=1&fotopref1=200x200&fotopref2=BP_FILE"

const val DEFAULT_SBODY = "pol=1&bage=60&tage=99&purp=2&geo=3159%2C4312%2C4400&online=0"

const val DEFAULT_RATE_MIN = 60f

const val DEFAULT_RATE_MAX = 80f

const val SITE_LIMIT_MS = 24L * 3600000L // лимит лайков у сайта: ждём сутки до сброса

const val DEFAULT_WIN_FROM = 9

const val DEFAULT_WIN_TO = 23

const val LOG_MAX = 500

const val REP_MAX = 3000

// Счётчики и аналитика: в скрытом окне службы их не грузим (меньше трафика, процессора и батареи)
val BLOCK_HOSTS = listOf(
    "mc.yandex.ru", "googletagmanager.com", "google-analytics.com", "doubleclick.net",
    "kraken.rambler.ru", "cnt.rambler.ru", "top100.rambler.ru", "counter.yadro.ru"
)

val COOKIE_URLS = listOf(
    "https://m.dating.rambler.ru/",
    "https://dating.rambler.ru/",
    "https://auth.rambler.ru/",
    "https://id.rambler.ru/",
    "https://rambler.ru/"
)

// Для скрытой страницы Mamba: запоминаем заголовки, которые шлёт сам сайт (авторизация и т.п.), чтобы слать их же
// прозрачный gif 1x1 для Love.ru
val PIXEL_GIF: ByteArray = android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT)

const val OK_API = "https://okdating-app.ru/api/"

const val MAMBA_VARS = "{\"ageFrom\":18,\"ageTo\":80,\"gender\":\"F\",\"heightFrom\":150,\"heightTo\":220,\"location\":\"3159_4312_4400_0\",\"limit\":15}"

// Запрос ленты «Рейтинг» урезан до id фото: остальные поля сайта нам не нужны
const val MAMBA_QUERY = "query Rating(\$ageFrom: Int!, \$ageTo: Int!, \$gender: String!, \$heightFrom: Int, \$heightTo: Int, " +
    "\$offsetPhotoId: Int, \$location: String, \$coordinates: GeoPointArg, \$limit: Int!) { photoRating { queue(ageFrom: \$ageFrom, " +
    "ageTo: \$ageTo, gender: \$gender, heightFrom: \$heightFrom, heightTo: \$heightTo, location: \$location, coordinates: \$coordinates, " +
    "offsetPhotoId: \$offsetPhotoId, limit: \$limit) { photos { photo { photoId: id } } } } }"
