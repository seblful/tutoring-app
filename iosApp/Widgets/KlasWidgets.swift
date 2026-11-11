import SwiftUI
import WidgetKit

/// The app's home-screen widgets, as on Android: today's lessons, and shortcuts into the app. Each draws the
/// snapshot the app writes (`WidgetSnapshot`) and nothing else; WidgetKit redraws them when the app asks.
@main
struct KlasWidgets: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        QuickActionsWidget()
    }
}

/// The app's words when it has written any, so the gallery speaks the app's language; else the app's name.
private let snapshot = WidgetSnapshot.load()

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "Today", provider: TodayProvider()) { entry in
            TodayView(entry: entry)
        }
        .configurationDisplayName(snapshot?.today.title ?? "Klas")
        .description(snapshot?.today.empty ?? "")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

struct QuickActionsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "QuickActions", provider: QuickActionsProvider()) { entry in
            QuickActionsView(entry: entry)
        }
        .configurationDisplayName(snapshot?.quickActions.appName ?? "Klas")
        .description(snapshot?.quickActions.header.label ?? "")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
