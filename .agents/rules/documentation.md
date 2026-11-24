# Dikciz documentation website

When changing public behavior, update its focused guide in `docs/` and regenerate the packaged offline search corpus. Tutorials, recipes and API references publish from that directory as a Docusaurus site built in `website/`.

- Preserve page front matter, unique titles and specific descriptions, stable permalinks, existing anchors and grouped navigation. Follow `docs/documentation-site.md` for site conventions.
- Keep relative `.md` links between documentation pages. Use complete default-branch GitHub URLs for source outside `docs/`, and full published site URLs for README documentation links.
- Write plain CommonMark; the build refuses Liquid left over from the former Jekyll site. In raw HTML, use `images/...` and `page.md` paths. Keep generated command and token metadata in their generators. Generate website recipes from their packaged source with `make docs-recipes`.
- Run `make docs-check`, inspect actual desktop and mobile rendering, and exercise search after its index loads. Check image loading, long references and code examples. Documentation-only work does not need an Android emulator.
