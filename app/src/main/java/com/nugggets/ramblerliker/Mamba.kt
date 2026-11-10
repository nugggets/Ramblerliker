package com.nugggets.ramblerliker

// Окно и служба Mamba: те же классы, но другой сервис и отдельный процесс (:mamba).
// Rambler и Mamba работают одновременно, у каждого свой движок, свои cookie и настройки
class MambaActivity : MainActivity() {
    override fun serviceId(): String = "mamba"
}

class MambaKeepAliveService : KeepAliveService() {
    override val svcId: String = "mamba"
}

// Окно и служба Табора: отдельный процесс (:tabor), работают одновременно с Rambler и Mamba
class TaborActivity : MainActivity() {
    override fun serviceId(): String = "tabor"
}

class TaborKeepAliveService : KeepAliveService() {
    override val svcId: String = "tabor"
}

// Окно и служба Love.ru: отдельный процесс (:love)
class LoveActivity : MainActivity() {
    override fun serviceId(): String = "love"
}

class LoveKeepAliveService : KeepAliveService() {
    override val svcId: String = "love"
}

// Окно и служба OK: отдельный процесс (:ok), работает одновременно с остальными сервисами
class OkActivity : MainActivity() {
    override fun serviceId(): String = "ok"
}

class OkKeepAliveService : KeepAliveService() {
    override val svcId: String = "ok"
}

// Окно и служба VK Знакомства: отдельный процесс (:vk)
class VkActivity : MainActivity() {
    override fun serviceId(): String = "vk"
}

class VkKeepAliveService : KeepAliveService() {
    override val svcId: String = "vk"
}

// Окно и служба Beboo: отдельный процесс (:beboo). Анкеты берутся из поиска с фильтрами, лайк ставится на странице самой анкеты
class BebooActivity : MainActivity() {
    override fun serviceId(): String = "beboo"
}

class BebooKeepAliveService : KeepAliveService() {
    override val svcId: String = "beboo"
}

// Love.Mail.Ru (страница оценок) и LovePlanet: лента на самой странице, жмём кнопку лайка по признакам
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
