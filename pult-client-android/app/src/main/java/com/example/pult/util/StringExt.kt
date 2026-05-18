package com.example.pult.util

import java.util.Locale

fun String.extractHostname(): String {
    return this.removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
}
