import SwiftUI
import UIKit
import UserNotifications
import Shared // Kotlin framework from :shared (baseName "Shared")

/// APNs registration + notification taps. Tokens and links go to the shared Kotlin code
/// (App/mobile/src/shared/iosMain/platform/IosHostBridge.kt): PushRegistrar → POST /me/push-tokens,
/// AppLinkBus → routeForLink (same mapping as Android).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Permission is asked in context (when the student turns reminders on); registering is silent.
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        IosHostBridgeKt.onApnsToken(hex: hex)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let link = response.notification.request.content.userInfo["link"] as? String {
            IosHostBridgeKt.openAppLink(url: link)
        }
        completionHandler()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                // Universal Links (https://shikkhasathi.com/...) incl. the payment gateway's return URL.
                .onOpenURL { url in IosHostBridgeKt.openAppLink(url: url.absoluteString) }
        }
    }
}
