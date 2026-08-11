import UIKit
import XCTest

/// Navigation, which differs by device shape: `NavigationSplitView` collapses to push-and-pop on
/// iPhone and shows two columns on iPad. Each test skips on the shape it does not describe, so the
/// suite is meaningful on both destinations rather than passing vacuously on one.
final class NavigationUITests: XCTestCase {

  private var isPad: Bool { UIDevice.current.userInterfaceIdiom == .pad }

  override func setUp() {
    super.setUp()
    continueAfterFailure = false
  }

  /// The regression test for the iPad portrait bug: the app shipped launching to a full-width
  /// "No Country Selected" with the list nowhere, because `columnVisibility` was left `.automatic`
  /// and iPadOS 18 hides the sidebar in portrait. Both must be on screen at once.
  func testListAndDetailAreVisibleTogetherOnIPad() throws {
    try XCTSkipUnless(isPad, "Two columns only exist on iPad.")

    let app = XCUIApplication()
    app.launchAndAwaitList()

    XCTAssertTrue(
      app.element(withIdentifier: "no-country-selected").exists,
      "The detail placeholder is missing, so the split view is not showing two columns."
    )
    XCTAssertTrue(
      app.countryRow(UITest.sampleCode).exists,
      "The list is not visible alongside the detail — the sidebar is hidden."
    )
  }

  func testSelectingKeepsTheListVisibleOnIPad() throws {
    try XCTSkipUnless(isPad, "Two columns only exist on iPad.")

    let app = XCUIApplication()
    let row = app.launchAndAwaitList()
    row.tap()

    XCTAssertTrue(
      app.element(withIdentifier: "country-detail")
        .waitForExistence(timeout: UITest.interactionTimeout)
    )
    XCTAssertTrue(
      app.countryRow(UITest.sampleCode).exists,
      "Selecting a country hid the list; on iPad it should stay in its own column."
    )
  }

  /// On iPhone the same selection is a push, so there is a back button and it must return.
  func testBackReturnsToTheListOnIPhone() throws {
    try XCTSkipUnless(!isPad, "iPhone collapses the split view into a stack; iPad does not.")

    let app = XCUIApplication()
    let row = app.launchAndAwaitList()
    row.tap()

    XCTAssertTrue(
      app.element(withIdentifier: "country-detail")
        .waitForExistence(timeout: UITest.interactionTimeout)
    )

    app.navigationBars.buttons.element(boundBy: 0).tap()

    XCTAssertTrue(
      app.countryRow(UITest.sampleCode).waitForExistence(timeout: UITest.interactionTimeout),
      "Going back did not return to the list."
    )
  }
}
