---
paths:
  - "**/composeResources/**"
---

# Compose Multiplatform resources

Strings and drawables live in `src/commonMain/composeResources/` and are reached through the
generated `Res`, never AGP's `R`:

```kotlin
import io.github.solcott.countries.ui.resources.Res
import io.github.solcott.countries.ui.resources.capital
import org.jetbrains.compose.resources.stringResource

stringResource(Res.string.capital, country.capital)
painterResource(Res.drawable.globe_24px)
```

- `strings.xml` keeps the ordinary Android format, `%1$s` placeholders included.
- **A vector drawable must contain no `?attr/…` and no `@android:…`.** CMP's parser resolves
  neither, and **both fail at runtime, not at build time.** Use literal colours (`#FFFFFFFF`) and
  let `Icon` tint from `LocalContentColor`. The existing drawables are the right shape — copy one.
- **A module with `composeResources` needs `android { androidResources { enable = true } }`** inside
  `kotlin { }` (see `ui/build.gradle.kts`). Without it the APK has no `assets/composeResources/` and
  the app dies on first use with `MissingResourceException` — nothing warns at build time.

Neither failure shows up in a Gradle task. Look at the running app.
