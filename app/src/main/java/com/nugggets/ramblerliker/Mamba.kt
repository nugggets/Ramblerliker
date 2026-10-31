package com.nugggets.ramblerliker

// Окно и служба Mamba: те же классы, но другой сервис и отдельный процесс (:mamba).
// Rambler и Mamba работают одновременно, у каждого свой движок, свои cookie и настройки
class MambaActivity : MainActivity() {
    override fun serviceId(): String = "mamba"
}

class MambaKeepAliveService : KeepAliveService() {
    override val svcId: String = "mamba"
}
