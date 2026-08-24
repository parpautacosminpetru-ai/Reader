# Reader — Product Specification

This document is the shared product target for independent workers. It captures the intended capabilities without prescribing copied third-party implementations.

## 1. Product invariant

Reader is an **offline-first, local-first Android application**. Internet access is not required for normal document work. Optional assets may be downloaded elsewhere and imported by the user, but core workspaces must remain usable offline.

Third-party applications may be used only as public feature references. Reader must independently implement equivalent workflows and must not copy proprietary source code, private APIs, branding, trademarks, layouts, icons, protected assets, or other non-public material.

## 2. Local Document Hub / Vault

- Local library for supported documents and Reader-native projects.
- Import, export, duplicate, rename, move, delete, recent items and favorites.
- Original source files remain recoverable.
- Persistent metadata, annotations, anchors, indexes, research profiles and history.
- Designed for very large documents; memory use must be bounded through paging/streaming where applicable.
- No mandatory cloud dependency.

## 3. Office workspace

Independent office-style suite with modern document, spreadsheet and presentation functionality.

### Documents
- Rich text editing, layout, styles, lists, tables, images, links, headers/footers, page setup, find/replace, comments, review-oriented metadata and common document export/import.
- Ink canvas available while editing.
- Stylus handwriting may remain ink or be converted to editable typed text.
- Preserve the original ink when requested.

### Spreadsheets
- Grid editing, formulas/functions, formatting, sorting/filtering, charts, sheets, common import/export and offline calculation.

### Presentations
- Slides, text, shapes, media, layout, transitions/animations where technically appropriate, speaker-oriented metadata and common import/export.

## 4. PDF Studio

One unified Reader module combining professional PDF workflows with study/annotation workflows, implemented independently.

- PDF reading at arbitrary zoom with mouse, touch and stylus navigation.
- Large-document paging/rendering.
- Highlight, underline, strikeout, freehand ink, shapes, text boxes, stamps/assets and comments.
- Page management: insert, delete, rotate, reorder, extract and merge where supported.
- Forms, signatures, links, bookmarks, attachments and metadata where supported.
- Text/image editing where technically possible.
- OCR for image-only/scanned sources as a document-processing capability.
- Export and conversion through the shared Converter.

## 5. Dynamic Marginalia

A Reader-owned layer that behaves like a resizable extension of the source page/document rather than a fixed side panel.

- Normally displayed on the right side and width-adjustable by the user.
- Scrolls/synchronizes with the underlying source so annotations remain aligned with their anchor positions.
- Stored separately from the original file unless explicitly flattened/exported.
- Full canvas: pen, highlighter, shapes, typed text, handwriting, images, emoji, reusable assets and supported 2D/3D objects.
- Brackets, regions and anchors can bind a marginal note/object to exact source ranges.
- Objects can contain links that navigate back to their source anchor.
- Reusable asset library prevents recreating the same mnemonic/drawing repeatedly.

## 6. Lexical Research Overlay

This is a global Reader capability available over Office documents, PDFs and other supported textual sources.

It is intentionally **lexical/rule-based, not semantic AI interpretation**. The user defines meaning; the engine performs deterministic detection and spatial/proximity calculations.

### Search axes
- User-defined named lists/categories (for example causes, places, dates, names, punctuation, a topic, or any personal research category).
- Unlimited practical number of lists, subject to device resources.
- Each axis can have an independent color, marker, optional emoji/icon and visual behavior.
- Profiles persist across documents and sessions and remain editable.

### Matching modes
- Exact match.
- Starts with / prefix.
- Contains substring.
- Ends with / suffix.
- Case and diacritic options.
- Literal punctuation and arbitrary character patterns.
- Advanced deterministic patterns may later include regular expressions.

### Parallel visualization
- Any subset of axes can be active simultaneously.
- Hits are overlaid directly on the current document without modifying the source.
- Optional pulse/blink emphasizes hits; animation can be disabled and its intensity/speed adjusted.
- A live legend shows axis name, color/marker and the exact textual form that matched.
- The user can inspect distribution at whole-document, page, paragraph, sentence or smaller selected ranges.

### Combination/proximity rules
The user can define intersections such as `causes + topic X + place Y` and choose the allowed scope:

- same sentence;
- same paragraph;
- same page;
- within N characters/words/lines/pages;
- within a selected range;
- across a whole source.

The engine reports whether the required axes coexist in that scope and can highlight only qualifying regions. It does **not** claim that the detected relationship is semantically true.

### Research persistence
- Search history stores profiles, activated axes, combination rules and source references.
- A previous query can be reopened and re-rendered.
- Selected relevant regions can be anchored directly into Marginalia.

## 7. Language & Voice Pack

Shared across Office and PDF.

- Offline speech-to-text dictation.
- Offline text-to-speech / read-aloud.
- Romanian is a priority language.
- Installable voice/language packs so different high-quality voices can be added locally.
- Offline translation is a future shared capability; architecture must leave room for locally installed models.

## 8. Universal Converter

A shared conversion service used by every workspace rather than separate conversion logic in each feature.

Examples include PDF ↔ editable document formats when technically recoverable, office formats ↔ PDF, image extraction/import and later supported 2D → 3D asset-generation workflows.

Conversions must report losses/unsupported features rather than silently corrupting content.

## 9. Universal Index

Practical, readable indexes rather than mandatory graph/constellation visualizations.

- User-created indexes for books/documents that do not already contain useful indexes.
- Alphabetical and user-defined category indexes.
- Index entries link to precise source locations.
- Indexing also applies to Memory Palace projects and Reader collections.

## 10. Memory Palace Studio

Separate but interoperable workspace for mnemonic spatial construction.

### Initial static phase
- 2D plan/editor and 3D scene/editor views.
- Create rooms, structures and spatial layouts manually or from imported assets.
- Import reusable models, textures, images, emoji and other supported assets into folders/libraries.
- Change object material/texture, position, scale, rotation and composition.
- Place text and images on surfaces.
- Navigate around the 3D space.
- Any object can represent the user's own mnemonic meaning; Reader does not impose semantics.
- Objects can link to document anchors, PDF passages, Office content or Marginalia items.

### Later dynamic phase
- Optional physics for dynamic objects (gravity, collisions, bouncing, etc.).
- Kept separate from the first static milestone to control complexity.

### 2D → 3D
Architecture should allow later offline/import-based 2D-to-3D workflows. This is not required for the first static scene milestone and model quality must be reported honestly.

## 11. Global interaction requirements

- Mouse, touch and stylus where the device supports them.
- Fast zoom/pan.
- Consistent color palettes and annotation tools across applicable modules.
- Shared anchors so distant pieces of a source can be connected through notes/assets/index entries.
- Reusable local asset folders for drawings, geometric shapes, emoji, images, models and textures.
- Autosave and crash-safe project state.
- No core feature may require an always-on internet connection.

## 12. Quality target

No software of this scale can honestly guarantee zero defects. The engineering target is:

- no known critical data-loss/crash bugs at release gates;
- automated tests for shared contracts and critical workflows;
- explicit migration/versioning for persistent data;
- failure-safe save/export behavior;
- large-file and airplane-mode testing;
- worker changes integrated only after automated checks pass.
