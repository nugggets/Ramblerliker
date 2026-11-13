package com.nugggets.ramblerliker

import android.webkit.WebView

// Форматирование анкет для журнала и скрытие секретов в тексте

// В диагностике скрываем токены и ключи (access_token ВК и подобные), чтобы её можно было спокойно пересылать
fun maskSecrets(t: String): String {
    var r = t
    r = Regex("(access_token|logout_hash|crashToken|token|sign|secret|password|passwd)=[^&\\s\"]+").replace(r, "$1=***")
    r = Regex("\"(access_token|logout_hash|token|secret|sign|key)\"\\s*:\\s*\"[^\"]*\"").replace(r, "\"$1\":\"***\"")
    r = Regex("([?&]key=)[0-9a-f]{20,}").replace(r, "$1***")
    r = Regex("vk1\\.[a-zA-Z]\\.[A-Za-z0-9_\\-]{30,}").replace(r, "vk1.***")
    return r
}

class Prof(val name: String, val age: String, val from: String, val to: String, val city: String)

class Rep(val ts: Long, val login: String, val p: Prof)

fun yearsText(a: String): String {
    val n = a.toIntOrNull() ?: return "$a лет"
    val w = if (n % 100 in 11..14) {
        "лет"
    } else {
        when (n % 10) {
            1 -> "год"
            2, 3, 4 -> "года"
            else -> "лет"
        }
    }
    return "$n $w"
}

// «Katerina, 30 лет — ищет от 29 до 40 лет»
fun fmtProf(p: Prof): String {
    if (p.from == "tabor" || p.from == "ok" || p.from == "love" || p.from == "beboo") return "${p.name.ifEmpty { "имя ?" }}, ${if (p.age.isEmpty()) "? лет" else yearsText(p.age)}" + if (p.city.isNotEmpty()) ", ${p.city}" else ""
    if (p.age.isEmpty() && p.from.isEmpty() && p.to.isEmpty() && (p.name.startsWith("фото ") || p.name.startsWith("профиль "))) return p.name
    val seek = when {
        p.from.isNotEmpty() && p.to.isNotEmpty() -> "ищет от ${p.from} до ${p.to} лет"
        p.from.isNotEmpty() -> "ищет от ${p.from} лет"
        p.to.isNotEmpty() -> "ищет до ${p.to} лет"
        else -> "ищет: нет данных"
    }
    return "${p.name.ifEmpty { "имя ?" }}, ${if (p.age.isEmpty()) "? лет" else yearsText(p.age)} — $seek"
}

/**
 * Движок лайков. Живёт в процессе приложения как одиночка и работает внутри фоновой службы
 * (KeepAliveService) со своим скрытым WebView, поэтому окно приложения для работы не нужно:
 * если система закрыла процесс, служба перезапускается и движок продолжает сам.
 * Все вызовы только из главного потока.
 */
