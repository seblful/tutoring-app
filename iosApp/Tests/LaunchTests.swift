import XCTest

/// The linked app starts, draws the shared screens, and starts again over the database it left. What those
/// screens do is tested in Kotlin (`:ui`, `:shared` and `:domain` run their common tests on this simulator).
final class LaunchTests: XCTestCase {
    func testTheScreensComeUpAndComeBackAfterRelaunch() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30))
        XCTAssertTrue(app.staticTexts.firstMatch.waitForExistence(timeout: 30), "no text on screen")

        app.terminate()
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30))
        XCTAssertTrue(app.staticTexts.firstMatch.waitForExistence(timeout: 30), "no text after relaunch")
    }
}
