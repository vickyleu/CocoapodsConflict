package com.example.cocoapodsconflict

//import ImSDK_Plus.V2TIM_GET_CLOUD_OLDER_MSG
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.stringWithUTF8String
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    @OptIn(ExperimentalForeignApi::class)
    fun abc() {
        NSString.`class`()
//        V2TIM_GET_CLOUD_OLDER_MSG.dec()
    }
}

actual fun getPlatform(): Platform = IOSPlatform()