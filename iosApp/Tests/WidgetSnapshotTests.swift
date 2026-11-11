import XCTest

/// Swift's half of the snapshot contract: `WidgetSnapshotContractTest` in `:ui` holds the fixture to
/// Kotlin's exact shape, and this reads the same file as the widget extension does.
final class WidgetSnapshotTests: XCTestCase {

    private func fixture() throws -> WidgetSnapshot {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "widget-snapshot", withExtension: "json"))
        return try WidgetSnapshot.decode(Data(contentsOf: url))
    }

    func testTheAppsSnapshotReadsAsWritten() throws {
        let snapshot = try fixture()

        XCTAssertEqual(snapshot.today.bookLink, "klas://book")
        XCTAssertTrue(snapshot.today.privateWhenLocked)
        XCTAssertEqual(snapshot.today.pages.map(\.rows.count), [1, 1, 0])
        XCTAssertEqual(snapshot.today.pages[1].rows.first?.onNow, true)
        XCTAssertEqual(snapshot.today.pages[0].rows.first?.link, "klas://lesson/7")
        XCTAssertEqual(snapshot.quickActions.header.icon, "plus")
        XCTAssertEqual(snapshot.quickActions.buttons.map(\.link), [
            "klas://today", "klas://people", "klas://homework", "klas://debts",
        ])
    }

    func testTheTimelineStartsAtThePageShowingNow() throws {
        let today = try fixture().today
        let second = today.pages[1]
        let now = second.start.addingTimeInterval(60)

        let timeline = today.timeline(from: now)

        XCTAssertEqual(timeline.map(\.page), Array(today.pages[1...]))
        XCTAssertEqual(timeline.first?.date, now)
        XCTAssertEqual(timeline.last?.date, today.pages[2].start)
    }

    func testATimelineBeforeTheFirstPageShowsIt() throws {
        let today = try fixture().today
        let early = today.pages[0].start.addingTimeInterval(-3600)

        XCTAssertEqual(today.timeline(from: early).map(\.page), today.pages)
    }
}
