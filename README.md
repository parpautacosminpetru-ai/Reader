# Reader

Reader is an offline-first Android workspace for reading, writing, research, annotation, conversion, indexing, and spatial memory workflows.

## Foundation goal

The first milestone is deliberately small: produce a stable Android APK shell that opens offline, exposes the major workspaces, and gives future feature workers clear module boundaries.

## Product pillars

- Local Document Hub / Vault
- Office workspace (documents, spreadsheets, presentations, ink input)
- PDF Studio (reader, editor, annotations, forms, signatures, export/conversion)
- Dynamic Marginalia canvas synchronized with source content
- Lexical Research Overlay with user-defined axes, colors, markers, combinations, proximity rules, and persistent search history
- Offline speech-to-text, text-to-speech, translation and language packs
- Universal format conversion
- Universal practical indexing
- Memory Palace Studio in 2D/3D, with reusable/imported assets and document anchors

The project targets independently implemented functionality. It must not copy proprietary source code, branding, or protected assets from third-party products.

## Engineering principles

1. Offline-first by default.
2. Local user data is the source of truth.
3. Modules communicate through explicit contracts, not implementation details.
4. Original documents remain untouched unless the user explicitly saves/export changes.
5. Overlays, marginalia, anchors, indexes and research profiles are stored separately from source files when possible.
6. Every worker owns a bounded module and tests it before integration.
7. No critical known regressions are accepted into the integration branch.

See `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, and `docs/WORKERS.md` as the project evolves.
