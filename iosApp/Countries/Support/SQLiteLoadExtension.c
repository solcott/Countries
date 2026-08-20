// Definitions for the two sqlite3 symbols Apple does not ship.
//
// Apple builds libsqlite3 with extension loading omitted, so neither
// sqlite3_enable_load_extension nor sqlite3_load_extension is exported by any Apple SDK — macosx,
// iphoneos and iphonesimulator all export zero of them, and sqlite3.h does not even declare them.
//
// SQLiter reaches SQLite through a cinterop binding generated from a full sqlite3.h, which binds
// every function in the header whether or not anything calls it. libCountriesKit.a therefore
// references all 255 sqlite functions, these two included. Nothing ever calls them: SQLiter's
// Kotlin never mentions either.
//
// Defining them here is what keeps them from being *undefined*. OTHER_LDFLAGS used to carry
// -Wl,-U for both instead, which only tells the static linker not to complain — the symbols stayed
// unresolved, and dyld killed the app at launch with "symbol not found in flat namespace". iOS got
// away with it for one reason only: it builds with DEAD_CODE_STRIPPING = YES, so the unused
// cinterop wrappers were discarded along with their references. macOS defaults that to NO and kept
// all 255, so macOS is where it surfaced.
//
// Squatting on these names is safe precisely because no Apple SDK exports them — there is nothing
// to collide with. The prototypes below are declared rather than inherited from sqlite3.h for the
// same reason: the header does not declare these, and without them the file trips
// -Wmissing-prototypes.
#include <sqlite3.h>

int sqlite3_enable_load_extension(sqlite3 *db, int onoff);
int sqlite3_load_extension(sqlite3 *db, const char *zFile, const char *zProc, char **pzErrMsg);

int sqlite3_enable_load_extension(sqlite3 *db, int onoff) {
  (void)db;
  (void)onoff;
  return SQLITE_ERROR;
}

int sqlite3_load_extension(sqlite3 *db, const char *zFile, const char *zProc, char **pzErrMsg) {
  (void)db;
  (void)zFile;
  (void)zProc;
  (void)pzErrMsg;
  return SQLITE_ERROR;
}
