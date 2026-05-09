package com.ailun.habitat.api

import android.accessibilityservice.AccessibilityService

/**
 * 无障碍服务提供者接口，解耦引擎与宿主平台的无障碍服务实现。
 */
interface IAccessibilityProvider {
    /** 获取当前运行的无障碍服务实例。 */
    fun getService(): AccessibilityService?

    /** 获取前台应用的包名。 */
    val foregroundPackage: String?

    /** 获取前台应用的 Activity 类名。 */
    val foregroundActivity: String?
}
