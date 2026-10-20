import XCTest

final class PersistenceTests: XCTestCase {
    func testStudentAndBookingSurviveRelaunch() {
        let app = XCUIApplication()
        app.launchArguments = ["--database", "test-\(UUID().uuidString).db"]
        app.launch()

        let name = app.textFields["student-name"]
        XCTAssertTrue(name.waitForExistence(timeout: 30))
        name.tap()
        name.typeText("Persistence Student")
        let add = app.buttons["add-student"]
        waitUntilEnabled(add)
        add.tap()

        let student = app.staticTexts["student-1"]
        XCTAssertTrue(student.waitForExistence(timeout: 15))
        XCTAssertEqual(student.label, "Persistence Student")
        let book = app.buttons["book-lesson"]
        waitUntilEnabled(book)
        book.tap()

        let lesson = app.descendants(matching: .any).matching(identifier: "lesson-1").firstMatch
        reveal(lesson, in: app)
        XCTAssertTrue(lesson.waitForExistence(timeout: 15))
        XCTAssertTrue(lesson.label.contains("Persistence Student"))

        app.terminate()
        app.launch()

        XCTAssertTrue(student.waitForExistence(timeout: 30))
        XCTAssertEqual(student.label, "Persistence Student")
        reveal(lesson, in: app)
        XCTAssertTrue(lesson.waitForExistence(timeout: 15))
        XCTAssertTrue(lesson.label.contains("Persistence Student"))
    }

    private func waitUntilEnabled(_ button: XCUIElement) {
        let ready = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "enabled == true"),
            object: button
        )
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 15), .completed)
    }

    private func reveal(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<3 {
            if element.waitForExistence(timeout: 2) { return }
            app.swipeUp()
        }
    }
}
