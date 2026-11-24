---
title: Documentation site
description: Maintain the Dikciz documentation site, build the same Docusaurus site locally and check navigation, screenshots, search and generated recipes.
permalink: /documentation-site/
nav_order: 2
parent: Source development
---

# Documentation site

[The documentation site](https://psyb0t.github.io/dikciz-launcher/) is built with Docusaurus from the Markdown in the default branch's [`docs/`](https://github.com/psyb0t/dikciz-launcher/tree/main/docs/) directory. The [site configuration](https://github.com/psyb0t/dikciz-launcher/tree/main/website/) and its theme match the other psyb0t project sites. Public tutorials, phone API references, recipes and screenshots belong here.

## Write a page

Keep one top-level heading. Add front matter with a unique title, a specific description between 50 and 170 characters, a stable permalink and a navigation order. Use an existing guide's title as the parent when the subject belongs under it; `grand_parent` is accepted for third-level pages. Preserve existing URLs and anchors when editing.

```yaml
---
title: Example guide
description: Explain the concrete Dikciz feature, its setup requirements, accepted inputs and observable results for readers following this guide.
permalink: /example-guide/
parent: Developer manual
nav_order: 20
---
```

Link to other guides with their relative `.md` paths. Link files outside the documentation tree with complete GitHub URLs on the default branch. README links use complete published site URLs. In raw HTML, write image paths as `images/...` and page links as `page.md`; the site build rewrites both to the published paths. Check screenshots in the built site, not only on GitHub.

Write plain Markdown. Pages are read as CommonMark, not MDX, so double braces and angle brackets in examples render literally. The build refuses Liquid tags and filters left over from the former Jekyll site.

## Build and check

```bash
make docs-recipes
make docs-check
make docs-preview
```

[`scripts/prepare-docs-site.py`](https://github.com/psyb0t/dikciz-launcher/blob/main/scripts/prepare-docs-site.py) copies `docs/` into the ignored `website/.content` directory. It turns each permalink into a Docusaurus slug, builds the sidebar from `parent` and `nav_order`, copies images and generates `llms.txt`. Docusaurus then builds in a digest-pinned Node container without network access, from the exact versions in the committed pnpm lockfile. The build fails on a broken page link, anchor or Markdown link.

`docs-check` also checks repository paths, headings, descriptions, canonical URLs and structured metadata. `docs-preview` serves `http://127.0.0.1:61327/dikciz-launcher/`; press Ctrl+C to stop it. It binds only to loopback.

Open the home page, a long API reference and the illustrated tutorials. Check desktop and narrow screens, the mobile menu, wide tables and code blocks. Wait for the search index to load, then search for a specific command and follow a result. Inspect image loading and browser errors. The website checks also run through `make lint` in CI.

## Dependencies

[`website/package.json`](https://github.com/psyb0t/dikciz-launcher/blob/main/website/package.json) pins every package to an exact version, and the lockfile records each package's checksum. pnpm refuses package versions published less than seven days ago and runs no install scripts. After changing a version, run `make docs-lock` to refresh the lockfile and `make docs-audit` to check it against known advisories.

## Generated content

`make docs-recipes` renders the short packaged MCP recipes as website pages and translates resource links into browser links. Their editable source remains the [packaged recipe directory](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/mcp/recipes). `make docs-recipes-check` rejects stale copies. Regenerate the [offline search corpus](search.md) after documentation changes with `make dikciz-search-docs`.

The generated [command](command-reference.md) and [token](token-permissions.md) references retain their site metadata when regenerated from installed discovery. The [developer guide](source-development.md#regenerate-references) describes those inputs. Generated pages have no "Edit this page" link; change their generator instead.

## Publication

GitHub Actions builds the site with `make docs-build` and deploys `website/build` to GitHub Pages. Verify the deployed commit and the public home, a deep guide, `sitemap.xml` and `llms.txt` before treating publication as complete.

Docusaurus writes canonical URLs, page descriptions, structured metadata and the sitemap. `llms.txt` lists guides with short descriptions for clients that use that convention. Search engine indexing is separate from publishing: the site owner can add the URL to Google Search Console and submit `sitemap.xml` using their own Google account.
