import XCTest

/// These are **XCTest**, not Swift Testing, unlike `CountriesTests/PresentationTests.swift`.
/// Swift Testing has no UI-testing support — `XCUIApplication` assertions need `XCTestCase` — so
/// the inconsistency is forced, not an oversight.
///
/// The tests drive the real app against the live GraphQL API. The first row on a cold simulator can
/// take a while, hence ``firstLoadTimeout``; reruns are warm because the Apollo SQLite cache
/// persists in the simulator container. Assertions are on presence rather than counts or ordering,
/// so a change in the API's data does not break them.
enum UITest {

  /// Generous: a cold simulator has to reach the network before the first row appears.
  static let firstLoadTimeout: TimeInterval = 45

  /// Short. Used after the list is already up, where the only wait is the presenter's 300ms search
  /// debounce plus a round trip.
  static let interactionTimeout: TimeInterval = 20

  /// **Both fixtures must be at the very top of the list.** `List` only realises visible rows, so a
  /// country further down does not exist as an element until it is scrolled to — the first version
  /// of these tests used Canada and every one of them failed on a row that was simply off screen.
  ///
  /// The API returns countries in code order, so Andorra and the UAE are rows one and two.
  static let sampleCode = "AD"
  static let sampleName = "Andorra"

  /// Andorra's calling code. The detail assertion uses this rather than the capital because the
  /// capital also appears in the list row's subtitle — and on iPad the list stays on screen beside
  /// the detail, so matching it would prove nothing. The calling code appears only on the detail
  /// screen, and only for this country.
  static let samplePhone = "376"

  /// A second visible row, on a different continent, for assertions about things disappearing.
  static let otherCode = "AE"
}

extension XCUIApplication {

  /// A country row, by code.
  func countryRow(_ code: String) -> XCUIElement {
    descendants(matching: .any)["country-\(code)"]
  }

  func element(withIdentifier identifier: String) -> XCUIElement {
    descendants(matching: .any)[identifier]
  }

  /// Launches and waits for the list to have loaded at least the sample country.
  @discardableResult
  func launchAndAwaitList(file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
    launch()
    let row = countryRow(UITest.sampleCode)
    if !row.waitForExistence(timeout: UITest.firstLoadTimeout) {
      // Dumping the tree turns "the row wasn't there" into something diagnosable: whether the app
      // failed to load, or the element is present under a different identifier or hierarchy.
      XCTFail(
        """
        The country list never loaded row 'country-\(UITest.sampleCode)'.
        Either the simulator has no network, or the accessibility identifiers have moved.
        Accessibility tree follows:
        \(debugDescription)
        """,
        file: file,
        line: line
      )
    }
    return row
  }
}
