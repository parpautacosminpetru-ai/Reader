# Reader — Parallel Worker Plan

Workers are separated by responsibility so several AI coding sessions can progress in parallel without editing the same subsystem blindly.

## Worker 0 — Foundation / Contracts

Owns the Android shell, build configuration, shared architectural rules, common identifiers/contracts and integration prerequisites.

**Current milestone:** app compiles, launches, shows the major workspaces, tests the module catalog and produces a debug APK in CI.

## Worker 1 — Local Vault / Storage

Owns local document library, metadata persistence, import/export, autosave foundations, file safety, large-file handles and migration strategy.

## Worker 2 — Office

Owns document/spreadsheet/presentation workspaces and Office-side ink canvas. Must use shared storage/anchors/converter contracts.

## Worker 3 — PDF Studio

Owns PDF rendering, navigation, standard annotation/editing workflows, PDF-native structures and OCR hooks. Does not own global lexical research logic.

## Worker 4 — Dynamic Marginalia

Owns synchronized margin layout, width control, canvas objects, bracket/range anchors, non-destructive persistence and reusable 2D/3D asset placement hooks.

## Worker 5 — Lexical Research Overlay

Owns deterministic lexical matching, axis profiles, color/marker overlays, punctuation matching, optional pulsing, proximity/combination rules, legends, density/distribution views and research history.

**Hard rule:** this worker does not infer semantic truth. It reports lexical matches and user-defined rule intersections only.

## Worker 6 — Converter

Owns shared conversion requests/results, format adapters, loss reporting and safe temporary/export file handling.

## Worker 7 — Language & Voice

Owns offline STT, offline TTS/read-aloud, locally installed voices/languages and later offline translation model adapters.

## Worker 8 — Universal Index

Owns user-built indexes, categories, alphabetical views, source anchors and cross-workspace indexing.

## Worker 9 — Memory Palace

Owns the static 2D/3D editor first: scene, camera/navigation, transformations, local assets/textures/models, text/images and links to Reader anchors. Physics/dynamic behavior is a later milestone.

## Integrator

The Integrator is not a general feature worker. It owns:

- shared-contract compatibility;
- dependency/version consistency;
- conflict resolution;
- complete test/build runs;
- data migration compatibility;
- final APK assembly and regression gates.

## Branch convention

Suggested worker branches:

```text
worker/storage
worker/office
worker/pdf
worker/marginalia
worker/lexical-research
worker/converter
worker/language-voice
worker/index
worker/memory-palace
integration
```

## Worker acceptance checklist

Before a worker hands work to the Integrator:

1. Its own tests pass.
2. The project still builds from a clean checkout.
3. It does not introduce a mandatory network dependency for core use.
4. It documents new public/shared contracts.
5. It does not silently alter another feature's persistent data.
6. It handles cancellation/failure without corrupting source files.
7. It identifies known limitations rather than hiding them.

Workers may prototype internally, but only contract-respecting code reaches integration.
