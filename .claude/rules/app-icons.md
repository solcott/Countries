---
paths:
  - "desktop/icons/**"
  - "iosApp/**/AppIcon.appiconset/**"
---

# App icons on Apple

The PNGs in `iosApp/Countries/Assets.xcassets/AppIcon.appiconset` are **derived from
`desktop/icons/icon.icns`** (the only file with a 1024×1024 representation) and committed as plain
images. There is no generator; redo them by hand when the artwork changes.

| | macOS | iOS |
| --- | --- | --- |
| Shape | rounded rect inset in a transparent margin, as drawn | **full bleed**, square |
| Alpha | required | **must not have any** |
| Sizes | ten, 16pt–512pt @1x/2x | one 1024×1024 universal |

**macOS** — the source art unchanged. `icon_16x16.png` … `icon_512x512@2x.png` map one-for-one onto
the `mac-*` filenames:

```
iconutil -c iconset desktop/icons/icon.icns -o /tmp/icon.iconset
```

**iOS** — iOS applies its own superellipse mask, and App Store validation rejects any alpha. Build
from `icon_512x512@2x.png` in four steps:

1. **Crop to `(61, 61, 963, 963)`** — the alpha channel's bounding box. Re-measure if the art is
   redrawn.
2. **Scale the 902px crop to 1024×1024** — keeps the globe at 71.2% of the visible icon, as on macOS.
3. **Composite over an opaque vertical gradient, `#785F98` → `#584077`** (sampled at the rect's top
   and bottom edges) to fill the transparent corners seamlessly.
4. **Flatten to RGB.**

Check the result is `RGB`, not `RGBA`, and its four corner pixels equal the gradient endpoints.
`actool` adding an opaque alpha to the compiled output is expected.

**A missing image fails silently.** An `.appiconset` whose `Contents.json` lists sizes but no
`filename` keys builds clean and produces an app with no icon. Verify in the built bundle, never the
build log: `Countries.app/AppIcon60x60@2x.png` on iOS, `Contents/Resources/AppIcon.icns` on macOS.
