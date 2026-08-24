# Reader — Architecture

## Phase 0 architecture

The repository begins with a single Android application module so the foundation stays easy to build. Feature modules are split out only when their contracts are stable; premature Gradle-module proliferation would slow early iteration.

```text
Reader/
├── app/                         # Android shell and composition root
├── docs/                        # product/architecture/worker contracts
├── features/                    # introduced by feature workers
│   ├── office/
│   ├── pdf/
│   ├── marginalia/
│   ├── lexical-research/
│   ├── converter/
│   ├── language-voice/
│   ├── index/
│   └── memory-palace/
└── core/                        # introduced as contracts stabilize
    ├── document-model/
    ├── storage/
    ├── anchors/
    ├── rendering/
    ├── assets/
    └── shared-ui/
```

## Dependency rule

Feature implementations must not reach into each other's internal classes. Cross-feature work goes through shared contracts in `core/*`.

Expected shared contract concepts include:

- `DocumentId` / `DocumentRef`
- `DocumentRange` / `PageRange`
- `AnchorId` / `AnchorRef`
- `OverlayLayer`
- `AssetId` / `AssetRef`
- `ResearchAxis` / `ResearchProfile` / `ProximityRule`
- `ConversionRequest` / `ConversionResult`
- `IndexEntry`

These are concepts, not frozen APIs yet. Worker 0/Integrator owns final shared-contract shape.

## Storage model

Reader is local-first. Persistent data should be separated into four categories:

1. **Source files** — imported originals or user-created canonical documents.
2. **Reader metadata** — library metadata, favorites, recent state, settings, indexes, histories.
3. **Non-destructive overlays** — annotations, Marginalia, research profiles/results, anchors.
4. **Derived artifacts** — thumbnails, caches, converted exports, OCR text, 3D derivatives.

A database such as Room is intended for structured metadata once the storage worker begins. Large document binaries should remain files/streams rather than database blobs unless there is a specific reason otherwise.

## Non-destructive editing rule

A source document is never irreversibly changed merely because an overlay, research profile or Marginalia layer is displayed. Flattening/exporting is an explicit user action.

## Large-file rule

Rendering/search/conversion APIs must be designed for streaming, paging, indexing or chunking. No feature may assume that an entire large PDF/book fits comfortably in RAM.

## Offline rule

Workers must test their core feature with networking unavailable. If a future optional model/voice/asset downloader exists, downloading is separate from local execution.

## Integration rule

- Every feature has an API/contract boundary.
- Every feature has unit tests for deterministic logic.
- Shared data-format changes are versioned.
- Feature workers do not directly merge incompatible shared-contract changes.
- The Integrator resolves shared API changes and runs the complete regression suite.

## Initial Android baseline

- Kotlin
- Jetpack Compose
- Java 17 toolchain
- compileSdk 36 / targetSdk 36
- minSdk 26 for the initial prototype
- Android Gradle Plugin 8.13.2
- Gradle 8.13 in CI

The baseline can be upgraded deliberately after green builds; dependency versions must never be changed casually by individual feature workers.
