package com.shikkhasathi.shared.platform

import com.shikkhasathi.features.notifications.data.PushRegistrar
import com.shikkhasathi.shared.navigation.AppLinkBus
import org.koin.mp.KoinPlatformTools

/*
 * Functions the Swift host calls (exported as `IosHostBridgeKt.*`). Koin is started by `MainViewController()`.
 *
 *   .onOpenURL { url in IosHostBridgeKt.openAppLink(url: url.absoluteString) }           // Universal Links
 *   didRegisterForRemoteNotificationsWithDeviceToken → IosHostBridgeKt.onApnsToken(hex: …) // APNs token
 *   userNotificationCenter(_:didReceive:) → IosHostBridgeKt.openAppLink(url: userInfo["link"])
 */

fun openAppLink(url: String) {
    KoinPlatformTools
        .defaultContext()
        .getOrNull()
        ?.get<AppLinkBus>()
        ?.dispatch(url)
}

fun onApnsToken(hex: String) {
    KoinPlatformTools
        .defaultContext()
        .getOrNull()
        ?.get<PushRegistrar>()
        ?.onNewToken(hex)
}
