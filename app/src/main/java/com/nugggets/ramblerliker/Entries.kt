package com.nugggets.ramblerliker

// Окно и служба каждого сервиса: общие классы MainActivity и KeepAliveService, у каждого сервиса свой процесс (:id),
// свои cookie и настройки. Rambler работает в основном процессе (классы MainActivity и KeepAliveService из манифеста)

class MambaActivity : MainActivity() {
    override fun serviceId(): String = "mamba"
}

class MambaKeepAliveService : KeepAliveService() {
    override val svcId: String = "mamba"
}

class TaborActivity : MainActivity() {
    override fun serviceId(): String = "tabor"
}

class TaborKeepAliveService : KeepAliveService() {
    override val svcId: String = "tabor"
}

class LoveActivity : MainActivity() {
    override fun serviceId(): String = "love"
}

class LoveKeepAliveService : KeepAliveService() {
    override val svcId: String = "love"
}

class OkActivity : MainActivity() {
    override fun serviceId(): String = "ok"
}

class OkKeepAliveService : KeepAliveService() {
    override val svcId: String = "ok"
}

class VkActivity : MainActivity() {
    override fun serviceId(): String = "vk"
}

class VkKeepAliveService : KeepAliveService() {
    override val svcId: String = "vk"
}

class BebooActivity : MainActivity() {
    override fun serviceId(): String = "beboo"
}

class BebooKeepAliveService : KeepAliveService() {
    override val svcId: String = "beboo"
}

class LoveMailActivity : MainActivity() {
    override fun serviceId(): String = "lmail"
}

class LoveMailKeepAliveService : KeepAliveService() {
    override val svcId: String = "lmail"
}

class LovePlanetActivity : MainActivity() {
    override fun serviceId(): String = "lplanet"
}

class LovePlanetKeepAliveService : KeepAliveService() {
    override val svcId: String = "lplanet"
}
