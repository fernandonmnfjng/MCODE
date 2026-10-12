# MCODE 2.4.0 — Phase 7: model and project resources

- Added static Wavefront OBJ import with `mc.import_model(path, x, y, z, scale, axis, ...)`.
- Blender Z-up coordinates can be converted to Minecraft Y-up with `axis="blender"`; use `axis="xyz"` for already-converted OBJ assets.
- Added a cross-platform Resources screen for importing OBJ files and importing/exporting `.mcp` project archives.
- Project packages include MCODE sources and passive resources; paths are checked against traversal, packages are size-limited, and executable binaries are not imported.
- Added in-game API help and function descriptions in completion hints.
- Added a draggable tab scrollbar, mouse-wheel tab navigation, left/right scrolling, plus Ctrl+Tab, Ctrl+Shift+Tab, Ctrl+PageUp, and Ctrl+PageDown.
- Tab labels now prefer the active file name or the leaf name of its `SetPATH`.
- Typing or pasting `true` / `false` normalizes to `True` / `False`; the lexer also accepts any casing for these literals.

- Added `math.radians`, `math.degrees` and `math.tau`, and updated API help/highlighting for drawing, file, frame, transform and math APIs.
- Added an importable example project at `examples/MCODE_demo_project.mcp`.
