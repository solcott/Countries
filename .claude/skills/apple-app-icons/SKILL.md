---
name: apple-app-icons
description: How the Apple app icon is derived from desktop/icons/icon.icns, and the different artwork macOS and iOS each require. Use when changing the app icon, editing iosApp/Countries/Assets.xcassets/AppIcon.appiconset, or touching desktop/icons/. An empty appiconset builds clean and silently produces an app with no icon, so read this before assuming the catalog is wired up.
---

# App icons on Apple

The PNGs in `iosApp/Countries/Assets.xcassets/AppIcon.appiconset` are **derived from
`desktop/icons/icon.icns`**, the only file there carrying a 1024×1024 representation. They are
committed as plain images — Xcode has no build step that would produce them, and there is no
generator to run. Redo them by hand if the artwork changes; the recipe is below.

macOS and iOS need materially different images out of that one source, which is the part to get
right:

| | macOS | iOS |
| --- | --- | --- |
| Shape | rounded rect inset in a transparent margin, as drawn | **full bleed**, square |
| Alpha | required | **must not have any** |
| Sizes | ten, 16pt–512pt @1x/2x | one 1024×1024 universal |

The ten macOS images are the source art unchanged, and come straight out of the icns:

```
iconutil -c iconset desktop/icons/icon.icns -o /tmp/icon.iconset
```

`icon_16x16.png` … `icon_512x512@2x.png` map onto the `mac-*` filenames one for one.

**The iOS image is the one that needs work**, because iOS applies its own superellipse mask: handing
it the macOS art shows a rounded rect *inside* iOS's rounding with the corners going black, and App
Store validation rejects an icon carrying an alpha channel at all. Build it from
`icon_512x512@2x.png` (1024×1024) in four steps:

1. **Crop to `(61, 61, 963, 963)`** — the opaque bounds of the rounded rect, i.e. the transparent
   margin removed. Re-measure this if the artwork is redrawn; it is the alpha channel's bounding box.
2. **Scale that 902px crop to 1024×1024.** Not arbitrary: it leaves the globe at 729px, the same
   71.2% of the visible icon it occupies on macOS.
3. **Composite over an opaque vertical gradient**, `#785F98` at the top to `#584077` at the bottom —
   the colours sampled at the rect's own top and bottom edges. The rect's rounded corners are still
   transparent inside that crop, and this is what fills them seamlessly.
4. **Flatten to RGB**, so the file has no alpha channel at all.

Verify the result is `RGB` and not `RGBA`, and that its four corner pixels equal the gradient
endpoints. `actool` will add a fully-opaque alpha channel to the compiled output, which is expected
and fine — validation looks at the source.

**A missing image here fails silently.** An `.appiconset` whose `Contents.json` lists sizes but no
`filename` keys — Xcode's default placeholder, and what this was before — builds clean, emits no
warning, and simply produces an app with no icon. Verify by checking the built bundle rather than
the build log: `Countries.app/AppIcon60x60@2x.png` on iOS, `Contents/Resources/AppIcon.icns` on
macOS. Neither exists when the catalog is empty.

