# Desktop logic provenance

Ported from `Qrzzzz/lyrics-card-generator`, main commit
`4f2b411c1d9903727ab2680cd727247f751fd6ad` (6.3.5), `lib/`.
The repository license continues to apply.

Shared modules cover V2 lyric reconciliation, separator semantics, automatic width,
landscape plans, portrait geometry, artwork geometry, typography limits, palette
extraction, OKLab, spatial color fields, grids and content shadows. Imports using
`@/lib/` were changed to relative imports; DOM/native adapters live outside this folder.
`model/LyricDocumentV2.kt` is the native Kotlin counterpart of document reconciliation.

The pinned desktop production cards deliberately use content text shadows rather
than `createCardReadabilityPlan` / `LocalReadabilityLayer`. Android follows this
active implementation. Unused experimental masks are not a parity requirement.
Renderer identifier `android-alpha-renderer-1` and bridge protocol 1 remain stable;
persisted RenderSpec schema is now 2. The schema file retains its historical filename.
