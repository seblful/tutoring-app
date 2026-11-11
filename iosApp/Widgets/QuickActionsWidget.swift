import SwiftUI
import WidgetKit

struct QuickActionsEntry: TimelineEntry {
    let date: Date
    /// Nil until the app has written its first snapshot.
    let actions: QuickActionsSnapshot?
}

/// Only the words change, with the app's language, and the app asks for a reload when they do.
struct QuickActionsProvider: TimelineProvider {
    func placeholder(in context: Context) -> QuickActionsEntry {
        QuickActionsEntry(date: Date(), actions: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (QuickActionsEntry) -> Void) {
        completion(QuickActionsEntry(date: Date(), actions: WidgetSnapshot.load()?.quickActions))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<QuickActionsEntry>) -> Void) {
        let entry = QuickActionsEntry(date: Date(), actions: WidgetSnapshot.load()?.quickActions)
        completion(Timeline(entries: [entry], policy: .never))
    }
}

/// Shortcuts into the app, as Android's widget: booking prominent, then today, people, homework and debts.
struct QuickActionsView: View {
    @Environment(\.widgetFamily) private var family
    let entry: QuickActionsEntry

    var body: some View {
        content.containerBackground(.fill.tertiary, for: .widget)
    }

    @ViewBuilder private var content: some View {
        if let actions = entry.actions {
            if family == .systemSmall {
                VStack(spacing: 8) {
                    ActionButton(button: actions.header, prominent: true, labelled: true)
                    Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                        ForEach(Array(stride(from: 0, to: actions.buttons.count, by: 2)), id: \.self) { first in
                            GridRow {
                                ForEach(actions.buttons[first..<min(first + 2, actions.buttons.count)], id: \.self) {
                                    ActionButton(button: $0, prominent: false, labelled: false)
                                }
                            }
                        }
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(actions.appName).font(.headline).lineLimit(1)
                        Spacer(minLength: 8)
                        ActionButton(button: actions.header, prominent: true, labelled: true)
                            .fixedSize()
                    }
                    HStack(spacing: 8) {
                        ForEach(actions.buttons, id: \.self) {
                            ActionButton(button: $0, prominent: false, labelled: true)
                        }
                    }
                }
            }
        } else {
            Text("Klas").font(.headline)
        }
    }
}

private struct ActionButton: View {
    let button: WidgetButton
    let prominent: Bool
    let labelled: Bool

    var body: some View {
        if let url = URL(string: button.link) {
            Link(destination: url) { face }
                .accessibilityLabel(button.label)
        }
    }

    private var face: some View {
        VStack(spacing: 2) {
            if prominent {
                Label(button.label, systemImage: button.icon)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: labelled ? .infinity : nil)
                    .foregroundStyle(Color.white)
                    .background(Color.accentColor, in: Capsule())
            } else {
                Image(systemName: button.icon)
                    .font(.title3)
                    .frame(maxWidth: .infinity, minHeight: 32)
                    .padding(.vertical, 4)
                    .background(.fill.secondary, in: RoundedRectangle(cornerRadius: 12))
                if labelled {
                    Text(button.label)
                        .font(.caption2)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}
