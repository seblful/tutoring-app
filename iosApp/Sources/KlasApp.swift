import KlasApp
import SwiftUI
import UIKit

/// The iOS app: the shared Compose screens in one window. Everything the app does lives in Kotlin
/// (`ui/src/iosMain/.../KlasIos.kt`); this file only hands the window and the links over.
@main
struct KlasIosApp: App {
    init() {
        KlasIos.shared.start()
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
