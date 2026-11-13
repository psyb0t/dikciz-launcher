# Android source ownership

Treat the Fossify runtime source sets as read-only during normal work:

- `main/`
- `debug/`
- `foss/`
- `gplay/`

Do not add, edit, delete, rename, reformat, or generate files in those source
sets. Read them when needed to understand the inherited launcher behavior.

Put Dikciz implementation and resource overrides in `dikciz/` or in a
Dikciz-specific combined variant source set such as `dikcizDebug/`. Never fix a
Dikciz variant by changing a shared Fossify source set.

Replacing Fossify-owned source sets is allowed only as part of an explicitly
requested Fossify source refresh. Import them from the chosen upstream source
without hand-editing the imported result.
