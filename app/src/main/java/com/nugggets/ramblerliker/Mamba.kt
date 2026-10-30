package com.nugggets.ramblerliker

// Окно и служба Mamba: те же классы, но другой сервис и отдельный процесс (:mamba),
// поэтому Rambler и Mamba работают одновременно и не мешают друг другу
class MambaActivity : MainActivity() {
    override fun serviceId(): String = "mamba"
}

class MambaKeepAliveService : KeepAliveService() {
    override val notifId: Int = 11
    override val alertId: Int = 12
    override val target: Class<*> get() = MambaActivity::class.java
    override val lockTag: String = "liker_mamba"
}
