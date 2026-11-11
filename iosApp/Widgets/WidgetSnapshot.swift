import Foundation

/// What the app wrote for its widgets. `WidgetSnapshot.kt` in `:ui` decides every word and every page from
/// the same rows Android's widgets draw; this only reads it. Kotlin and Swift are both tested against
/// `Tests/Fixtures/widget-snapshot.json`.
struct WidgetSnapshot: Codable, Equatable {
    let today: TodaySnapshot
    let quickActions: QuickActionsSnapshot

    /// Shared with the app; `project.yml` grants it to both targets.
    static let appGroup = "group.com.seblful.klas"
    static let fileName = "widget-snapshot.json"

    /// The app's latest, or nil before the app has first run.
    static func load() -> WidgetSnapshot? {
        guard
            let folder = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup),
            let data = try? Data(contentsOf: folder.appendingPathComponent(fileName))
        else { return nil }
        return try? decode(data)
    }

    static func decode(_ data: Data) throws -> WidgetSnapshot {
        try JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }
}

struct TodaySnapshot: Codable, Equatable {
    let title: String
    let empty: String
    let onNow: String
    let bookLesson: String
    let bookLink: String
    let openLink: String
    let privateWhenLocked: Bool
    let pages: [TodayPage]

    /// The page showing at `now` (dated `now`), then each later one at its own moment: a widget timeline.
    func timeline(from now: Date) -> [(date: Date, page: TodayPage)] {
        guard let current = pages.lastIndex(where: { $0.start <= now }) ?? pages.indices.first else { return [] }
        return pages[current...].enumerated().map { offset, page in
            (date: offset == 0 ? now : page.start, page: page)
        }
    }
}

struct TodayPage: Codable, Equatable {
    /// Seconds since the epoch.
    let from: Int64
    let rows: [WidgetRow]

    var start: Date { Date(timeIntervalSince1970: TimeInterval(from)) }
}

struct WidgetRow: Codable, Equatable, Hashable {
    let title: String
    let detail: String
    let taught: Bool
    let onNow: Bool
    let statusLabel: String
    let link: String
}

struct QuickActionsSnapshot: Codable, Equatable {
    let appName: String
    let header: WidgetButton
    let buttons: [WidgetButton]
}

struct WidgetButton: Codable, Equatable, Hashable {
    let label: String
    let link: String
    /// An SF Symbol.
    let icon: String
}
