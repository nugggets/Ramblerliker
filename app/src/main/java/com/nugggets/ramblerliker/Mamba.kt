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

// Окно и служба OK: отдельный процесс (:ok), работает одновременно с остальными сервисами
class OkActivity : MainActivity() {
    override fun serviceId(): String = "ok"
}

class OkKeepAliveService : KeepAliveService() {
    override val svcId: String = "ok"
}
