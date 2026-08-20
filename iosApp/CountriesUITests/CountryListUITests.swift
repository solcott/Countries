import XCTest

/// The list screen's touch paths. None of this was exercised before — the presenters are covered in
/// `:presenter` and `:apple`, but nothing had ever tapped the SwiftUI on top of them.
final class CountryListUITests: XCTestCase {

  override func setUp() {
    super.setUp()
    continueAfterFailure = false
  }

  func testShowsTheCountryListOnLaunch() {
    let app = XCUIApplication()
    app.launchAndAwaitList()
  }

  func testSelectingACountryShowsItsDetail() {
    let app = XCUIApplication()
    let row = app.launchAndAwaitList()

    row.tap()

    XCTAssertTrue(
      app.element(withIdentifier: "country-detail")
        .waitForExistence(timeout: UITest.interactionTimeout),
      "Selecting a country did not show the detail screen."
    )
    // Matched by predicate rather than as an exact static text: `LabeledContent` merges its label
    // and value into a single accessibility element, so the value is not addressable on its own.
    let phone = app.descendants(matching: .any)
      .matching(NSPredicate(format: "label CONTAINS %@", UITest.samplePhone))
      .firstMatch
    XCTAssertTrue(
      phone.waitForExistence(timeout: UITest.interactionTimeout),
      "The detail screen appeared but never showed \(UITest.sampleName)'s data."
    )
  }

  func testSearchNarrowsTheList() {
    let app = XCUIApplication()
    app.launchAndAwaitList()

    let other = app.countryRow(UITest.otherCode)
    XCTAssertTrue(other.exists, "Expected a second row to be on screen before searching.")

    let search = app.searchFields.firstMatch
    XCTAssertTrue(search.waitForExistence(timeout: UITest.interactionTimeout))
    search.tap()
    search.typeText(UITest.sampleName)

    XCTAssertTrue(
      app.countryRow(UITest.sampleCode).waitForExistence(timeout: UITest.interactionTimeout),
      "Searching for \(UITest.sampleName) dropped it from the list."
    )
    // The presenter debounces 300ms and re-queries the server, so the drop is not instant.
    expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: other)
    waitForExpectations(timeout: UITest.interactionTimeout)
  }

  func testContinentFilterNarrowsTheList() {
    let app = XCUIApplication()
    app.launchAndAwaitList()

    let filter = app.element(withIdentifier: "continent-filter")
    XCTAssertTrue(filter.waitForExistence(timeout: UITest.interactionTimeout))
    filter.tap()

    // Andorra is in Europe, so filtering to Africa must drop it.
    let africa = app.buttons["Africa"]
    XCTAssertTrue(
      africa.waitForExistence(timeout: UITest.interactionTimeout),
      "The continent menu never listed Africa."
    )
    africa.tap()

    expectation(
      for: NSPredicate(format: "exists == false"),
      evaluatedWith: app.countryRow(UITest.sampleCode)
    )
    waitForExpectations(timeout: UITest.interactionTimeout)
  }

  /// The chip row is what says *which* filters are on without opening the menu, and tapping one is
  /// the shortest way back out. Both halves are asserted here because a chip that appears but does
  /// not clear is worse than no chip at all.
  func testActiveFilterChipShowsTheFilterAndClearsIt() {
    let app = XCUIApplication()
    app.launchAndAwaitList()

    let filter = app.element(withIdentifier: "continent-filter")
    XCTAssertTrue(filter.waitForExistence(timeout: UITest.interactionTimeout))
    filter.tap()

    let africa = app.buttons["Africa"]
    XCTAssertTrue(
      africa.waitForExistence(timeout: UITest.interactionTimeout),
      "The continent menu never listed Africa."
    )
    africa.tap()

    // "AF" is Africa's continent code. It collides with Afghanistan's country code, which is why
    // the chips carry their own identifier prefix rather than sharing the row namespace.
    let chip = app.element(withIdentifier: "filter-chip-AF")
    XCTAssertTrue(
      chip.waitForExistence(timeout: UITest.interactionTimeout),
      "Selecting Africa did not put a chip on screen for it."
    )

    // Andorra is in Europe, so the filter has to have dropped it before clearing means anything.
    expectation(
      for: NSPredicate(format: "exists == false"),
      evaluatedWith: app.countryRow(UITest.sampleCode)
    )
    waitForExpectations(timeout: UITest.interactionTimeout)

    chip.tap()

    XCTAssertTrue(
      app.countryRow(UITest.sampleCode).waitForExistence(timeout: UITest.interactionTimeout),
      "Tapping the chip did not clear the filter and restore the full list."
    )
  }
}
