package com.nugggets.ramblerliker

// Описание сервисов (адреса, окна, службы) и таблица SERVICES

// Профиль сервиса: адрес сайта, куки и значения по умолчанию. У каждого сервиса свой процесс, свои настройки и файлы
class Svc(
    val id: String,
    val title: String,
    val site: String,
    val hosts: List<String>,
    val hostSuffix: String,
    val prefsName: String,
    val suffix: String,
    val cookieUrls: List<String>,
    val cookieDomain: String,
    val defLikeUrl: String,
    val defLikeMethod: String,
    val defLikeBody: String,
    val defSearchUrl: String,
    val defSearchMethod: String,
    val defSbody: String,
    val nidOff: Int,
    val activityCls: Class<*>,
    val keepCls: Class<*>,
    // сколько длится окно лимита лайков у сайта (если сайт сам не назвал время сброса)
    val limitWindowMs: Long = SITE_LIMIT_MS,
    // известный лимит лайков за окно (0 = пока не известен, узнаём по ответам сайта)
    val siteLimit: Int = 0,
    // учиться длине окна по ответам сайта (у сайтов, где окно известно точно, выключено)
    val learnWin: Boolean = true,
    // лимит сбрасывается в полночь по Москве (календарные сутки), а не через N часов после первого лайка
    val dailyReset: Boolean = false
)

val SERVICES = listOf(
    Svc(
        "rambler", "Rambler", SITE, listOf(SITE), "rambler.ru", "liker", "",
        COOKIE_URLS, ".rambler.ru",
        DEFAULT_LIKE_URL, "GET", "", DEFAULT_SEARCH_URL, "POST", DEFAULT_SBODY,
        0, MainActivity::class.java, KeepAliveService::class.java,
        // Rambler: по диагностике сайт отдаёт likesfree=2000 на окно 24 ч, а время сброса в секундах в likestimeout
        siteLimit = 2000, learnWin = false
    ),
    // Mamba: лайк ставится на фото в «Рейтинге», лента листается по id последнего фото
    Svc(
        "mamba", "Mamba", "https://www.mamba.ru",
        listOf("https://www.mamba.ru", "https://mamba.ru", "https://m.mamba.ru"), "mamba.ru", "liker_mamba", "_mamba",
        listOf("https://www.mamba.ru/", "https://mamba.ru/", "https://m.mamba.ru/"), ".mamba.ru",
        "/api/ratings/v2/voting/photos/{login}/like?_loc%5Blocale%5D=ru", "POST", "{\"voteSource\":1}",
        "/api/graphql/?_loc[locale]=ru", "POST", "",
        10, MambaActivity::class.java, MambaKeepAliveService::class.java,
        // Mamba: по диагностике сайт отказывает (limit_exceeded) ровно после 250 лайков за окно
        siteLimit = 250
    ),
    // Табор: голосование «Симпатии». Первые анкеты берём со страницы, дальше каждая оценка возвращает следующую
    Svc(
        "tabor", "Tabor", "https://m.tabor.ru/sympathy/vote.php",
        listOf("https://m.tabor.ru", "https://tabor.ru", "https://www.tabor.ru"), "tabor.ru", "liker_tabor", "_tabor",
        listOf("https://m.tabor.ru/", "https://tabor.ru/", "https://www.tabor.ru/"), ".tabor.ru",
        // оценка возвращает в ответе следующую анкету; в запросе перечисляются остальные уже показанные анкеты
        "/api/sympathy-vote.php?method=vote", "POST", "user_id={login}&type=like&ad_user={ad}{exclude}",
        "/sympathy/vote.php", "GET", "",
        20, TaborActivity::class.java, TaborKeepAliveService::class.java,
        // Tabor: сайт пишет на странице «Вы сможете проголосовать через 15 часов, 20 минут и 4 секунды»: берём время оттуда
        learnWin = false
    ),
    // OK: «ОК Знакомства» это мини-приложение внутри ok.ru (окно другого домена). Лента: dating.getRecommendedUsers,
    // лайк: dating.like (user_id + meta из ленты). Токен и прочие параметры берутся у самого приложения (см. OK_JS)
    Svc(
        "ok", "OK", "https://ok.ru/app/dating",
        listOf("https://ok.ru", "https://www.ok.ru", "https://m.ok.ru"), "ok.ru", "liker_ok", "_ok",
        listOf("https://ok.ru/", "https://www.ok.ru/", "https://m.ok.ru/"), ".ok.ru",
        OK_API + "dating.like", "POST", "user_id={login}&meta={meta}",
        OK_API + "dating.getRecommendedUsers", "POST", "count=20",
        30, OkActivity::class.java, OkKeepAliveService::class.java,
        12L * 3600000L, // OK: по диагностике окно 12 часов (сброс = первый лайк окна + 12 ч)
        learnWin = false
    ),
    // Love.ru: пока только вход и запись из «Браузера». Адреса ленты и лайка неизвестны, их даст диагностика с сайта
    Svc(
        "love", "Love.ru", "https://love.ru/znakomstva/female/18-33/",
        listOf("https://love.ru", "https://www.love.ru", "https://m.love.ru"), "love.ru", "liker_love", "_love",
        listOf("https://love.ru/", "https://www.love.ru/", "https://m.love.ru/"), ".love.ru",
        // адреса для Love.ru только для проверки настроек: сами запросы шлёт страница сайта, мы жмём её кнопку «Симпатия»
        "/search/?action=sympathy-send&login_to={login}", "POST", "", "/search/?action=load-more-users", "GET", "",
        40, LoveActivity::class.java, LoveKeepAliveService::class.java,
        // Love.ru: по диагностике отказ «Превышен ежедневный лимит на отправку симпатий» пришёл после ~10000 лайков, после полуночи лайки пошли снова
        siteLimit = 10000, learnWin = false, dailyReset = true
    ),
    // VK Знакомства: мини-приложение m.vk.ru/dating. Тот же движок, что у ОК: лента dating.getRecommendedUsers и лайк dating.like
    // на vkdating-app.ru, токен и параметры берутся у самого мини-приложения (см. OK_JS)
    Svc(
        "vk", "VK Знакомства", "https://m.vk.ru/dating",
        listOf("https://m.vk.ru", "https://vk.ru", "https://www.vk.ru", "https://m.vk.com", "https://vk.com"), "vk.ru", "liker_vk", "_vk",
        listOf("https://m.vk.ru/", "https://vk.ru/", "https://id.vk.ru/", "https://m.vk.com/", "https://vk.com/"), ".vk.ru",
        "https://vkdating-app.ru/api/dating.like", "POST", "user_id={login}&meta={meta}",
        "https://vkdating-app.ru/api/dating.getRecommendedUsers", "POST", "count=20&geo={\"latitude\":55.7558,\"longitude\":37.6173}",
        50, VkActivity::class.java, VkKeepAliveService::class.java
    ),
    // Beboo: в поиске с фильтрами открываем каждую анкету и нажимаем на ней лайк (см. BEBOO_JS). Адрес поиска с фильтрами меняется в «Адресах и подсказках»
    Svc(
        "beboo", "Beboo",
        "https://m.beboo.ru/search?status=recently&lookFor=1&f=1&countryId=104&regionId=445&townId=4465&lookFor=1&startAge=40&endAge=100&reason=0&f=0",
        listOf("https://m.beboo.ru", "https://beboo.ru", "https://www.beboo.ru"), "beboo.ru", "liker_beboo", "_beboo",
        listOf("https://m.beboo.ru/", "https://beboo.ru/", "https://www.beboo.ru/"), ".beboo.ru",
        // адреса лайка у Beboo нет: лайк ставится нажатием на кнопку в анкете. Значение нужно только для проверки настроек
        "/{login}", "GET", "",
        "https://m.beboo.ru/search?status=recently&lookFor=1&f=1&countryId=104&regionId=445&townId=4465&lookFor=1&startAge=40&endAge=100&reason=0&f=0", "GET", "",
        60, BebooActivity::class.java, BebooKeepAliveService::class.java
    ),
    // Love.Mail.Ru построен на той же платформе, что и Mamba: тот же лайк фото в «Рейтинге» и та же GraphQL-лента. Работает как Mamba, но на домене love.mail.ru
    Svc(
        "lmail", "Love.Mail.Ru",
        "https://love.mail.ru/ru/rating",
        listOf("https://love.mail.ru", "https://mail.ru", "https://auth.mail.ru", "https://account.mail.ru"), "mail.ru", "liker_lmail", "_lmail",
        listOf("https://love.mail.ru/", "https://mail.ru/", "https://auth.mail.ru/", "https://account.mail.ru/"), ".mail.ru",
        "/api/ratings/v2/voting/photos/{login}/like?_loc%5Blocale%5D=ru", "POST", "{\"voteSource\":1}",
        "/api/graphql/?_loc[locale]=ru", "POST", "",
        70, LoveMailActivity::class.java, LoveMailKeepAliveService::class.java,
        siteLimit = 250
    ),
    // LovePlanet: та же платформа, что у Rambler Знакомств: лайк /api/a-likes, лента /api/a-search, ответ с likesfree/likesleft. Работает как Rambler, но на домене m.loveplanet.ru
    Svc(
        "lplanet", "LovePlanet",
        "https://m.loveplanet.ru/index/meet",
        listOf("https://m.loveplanet.ru", "https://loveplanet.ru", "https://www.loveplanet.ru"), "loveplanet.ru", "liker_lplanet", "_lplanet",
        listOf("https://m.loveplanet.ru/", "https://loveplanet.ru/", "https://www.loveplanet.ru/"), ".loveplanet.ru",
        DEFAULT_LIKE_URL, "GET", "", DEFAULT_SEARCH_URL, "POST", DEFAULT_SBODY,
        80, LovePlanetActivity::class.java, LovePlanetKeepAliveService::class.java,
        // по записи сайт отдаёт likesfree=2000, как у Rambler
        siteLimit = 2000, learnWin = false
    )
)
