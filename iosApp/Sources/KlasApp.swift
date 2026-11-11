import KlasApp
import SwiftUI
import UIKit
import WidgetKit

/// The iOS app: the shared Compose screens in one window. Everything the app does lives in Kotlin
/// (`ui/src/iosMain/.../KlasIos.kt`); this file only hands the window, the links and the quick actions over.
@main
struct KlasIosApp: App {
    @UIApplicationDelegateAdaptor(QuickActionLaunch.self) private var launch

    init() {
        KlasIos.shared.start(widgets: WidgetKitReloads())
    }

    var body: some Scene {
        WindowGroup {
            ComposeScreens()
                // Compose draws behind the bars and the keyboard and pads itself with the insets.
                .ignoresSafeArea()
                .onOpenURL { url in KlasIos.shared.openLink(link: url.absoluteString) }
        }
    }
}

private struct ComposeScreens: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        KlasIosKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

private final class WidgetKitReloads: NSObject, WidgetReloads {
    func reloadAll() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}

/// A quick action on the app's icon is a `klas://` link (`IosHomeScreen` publishes them). One that launches
/// the app arrives with the scene's connection; one chosen while it runs, at the scene's delegate.
final class QuickActionLaunch: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting session: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let item = options.shortcutItem {
            KlasIos.shared.openLink(link: item.type)
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = QuickActionScene.self
        return configuration
    }
}

final class QuickActionScene: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        KlasIos.shared.openLink(link: shortcutItem.type)
        completionHandler(true)
    }
}
