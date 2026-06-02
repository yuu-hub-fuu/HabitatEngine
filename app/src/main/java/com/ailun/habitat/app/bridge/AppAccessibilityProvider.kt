package com.ailun.habitat.app.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.ailun.habitat.api.IAccessibilityProvider
import java.lang.ref.WeakReference

/** [IAccessibilityProvider] 实现，通过 WeakReference 持有无障碍服务实例。 */
object AppAccessibilityProvider : IAccessibilityProvider {

    @Volatile
    private var serviceRef: WeakReference<AccessibilityService>? = null

    @Volatile
    private var cachedPackageName: String? = null

    @Volatile
    private var cachedActivityName: String? = null

    /** 由宿主无障碍服务在 onServiceConnected 中调用。 */
    fun onServiceConnected(service: AccessibilityService) {
        serviceRef = WeakReference(service)
    }

    /** 由宿主无障碍服务在 onDestroy 中调用。 */
    fun onServiceDestroyed() {
        serviceRef = null
    }

    /** 由宿主在窗口变化时调用。 */
    fun onWindowChanged(packageName: String?, className: String?) {
        cachedPackageName = packageName
        cachedActivityName = className
    }

    override fun getService(): AccessibilityService? = serviceRef?.get()

    override val foregroundPackage: String?
        get() {
            // Try live service first
            val service = serviceRef?.get()
            if (service != null) {
                try {
                    val root = service.rootInActiveWindow
                    if (root != null) {
                        return root.packageName?.toString()
                    }
                } catch (_: Exception) {}
            }
            return cachedPackageName
        }

    override val foregroundActivity: String?
        get() = cachedActivityName
}
