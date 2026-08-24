# Research visual overlay — Reader 0.6

Reader 0.6 adds a non-destructive visual overlay for deterministic lexical research on PDF pages.

## Contract

- The source PDF bytes are never changed.
- Saved research axes provide stable visual colors.
- Lexical matching remains deterministic (`EXACT`, `PREFIX`, `CONTAINS`).
- On Android 15 / API 35+, lexical hit character offsets are converted to PDF text-selection bounds and drawn over the rendered bitmap.
- Overlay rendering is page-local and capped to a safe number of hit regions per render to avoid pathological UI stalls.
- Scanned/image-only PDFs continue to require the later OCR path.

## Coordinate rule

`PdfPageTextContent` and selection bounds use PDF points (1/72 inch). The renderer scales them by the same width-fit scale used for the page bitmap, so the overlay stays aligned at every zoom level.
