import SwiftUI
import WidgetKit

struct TodayEntry: TimelineEntry {
    let date: Date
    /// Nil until the app has written its first snapshot.
    let today: TodaySnapshot?
    let rows: [WidgetRow]
}

/// The app's pages for the days ahead, so «On now» moves and the day turns while the app is closed. The app
/// asks for a reload whenever they change, so the timeline never asks on its own.
struct TodayProvider: TimelineProvider {
    func placeholder(in context: Context) -> TodayEntry {
        TodayEntry(date: Date(), today: nil, rows: [])
    }

    func getSnapshot(in context: Context, completion: @escaping (TodayEntry) -> Void) {
        completion(entries(at: Date()).first ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TodayEntry>) -> Void) {
        completion(Timeline(entries: entries(at: Date()), policy: .never))
    }

    private func entries(at now: Date) -> [TodayEntry] {
        guard let today = WidgetSnapshot.load()?.today else {
            return [TodayEntry(date: now, today: nil, rows: [])]
        }
        let timeline = today.timeline(from: now)
        if timeline.isEmpty {
            return [TodayEntry(date: now, today: today, rows: [])]
        }
        return timeline.map { TodayEntry(date: $0.date, today: today, rows: $0.page.rows) }
    }
}

/// The day's lessons: who, when and what, «On now» while one is taught, and a check once it is. A row opens
/// the lesson's sheet in the app, where it is marked taught; the plus books a lesson.
struct TodayView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TodayEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            if entry.rows.isEmpty {
                Spacer(minLength: 0)
                Text(entry.today?.empty ?? "")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Spacer(minLength: 0)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(entry.rows.prefix(rowsThatFit)), id: \.self) { row in
                        LessonRow(row: row, onNow: entry.today?.onNow ?? "")
                    }
                }
                .privacySensitive(entry.today?.privateWhenLocked ?? true)
                Spacer(minLength: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(entry.today.flatMap { URL(string: $0.openLink) })
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var header: some View {
        HStack(spacing: 4) {
            Text(entry.today?.title ?? "Klas")
                .font(.headline)
                .lineLimit(1)
            Spacer(minLength: 0)
            if let today = entry.today, let book = URL(string: today.bookLink) {
                Link(destination: book) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                        .accessibilityLabel(today.bookLesson)
                }
            }
        }
    }

    private var rowsThatFit: Int {
        switch family {
        case .systemSmall: return 2
        case .systemMedium: return 3
        default: return 7
        }
    }
}

private struct LessonRow: View {
    let row: WidgetRow
    let onNow: String

    var body: some View {
        if let url = URL(string: row.link) {
            Link(destination: url) { content }
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: 8) {
            Image(systemName: row.taught ? "checkmark.circle.fill" : "circle")
                .foregroundStyle(row.taught ? Color.green : Color.secondary)
                .accessibilityLabel(row.statusLabel)
            VStack(alignment: .leading, spacing: 0) {
                Text(row.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(row.onNow ? "\(onNow) · \(row.detail)" : row.detail)
                    .font(.caption)
                    .foregroundStyle(row.onNow ? Color.accentColor : Color.secondary)
                    .lineLimit(1)
            }
        }
    }
}
