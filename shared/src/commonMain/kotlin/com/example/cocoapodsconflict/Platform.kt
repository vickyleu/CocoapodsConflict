package com.example.cocoapodsconflict

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform