---
title: Documentation site
description: Maintain the Dikciz GitHub Pages site, preview the same Jekyll build locally and check navigation, screenshots, search and generated recipes.
permalink: /documentation-site/
nav_order: 2
parent: Source development
---

# Documentation site

[The documentation site](https://psyb0t.github.io/dikciz-launcher/) publishes Markdown from the default branch's [`docs/`](https://github.com/psyb0t/dikciz-launcher/tree/main/docs/) directory using GitHub Pages. The [site configuration](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/_config.yml) pins Just the Docs and uses the bundled Catppuccin Mocha color scheme. Public tutorials, phone API references, recipes and screenshots belong here.

## Write a page

Keep one top-level heading. Add front matter with a unique title, a specific description between 50 and 170 characters, a stable permalink and a navigation order. Use an existing guide as the parent when the subject belongs under it. Preserve existing URLs and anchors when editing.

```yaml
---
title: Example guide
description: Explain the concrete Dikciz feature, its setup requirements, accepted inputs and observable results for readers following this guide.
permalink: /example-guide/
parent: Developer manual
nav_order: 20
---
```

Link to other guides with their relative `.md` paths. Link files outside the documentation tree with complete GitHub URLs on the default branch. README links use complete published site URLs. Image galleries use the configured base path; check screenshots in the built site, not only on GitHub.

Pages evaluates Liquid before Markdown. Protect examples containing double braces or Liquid delimiters with raw blocks in HTML comments. Keep HTML, JavaScript and Lua examples literal in the rendered page. The [configuration](dikciz-configuration.md) and [Lua reference](lua-api.md) contain examples of this protection.

## Build and check

```bash
make docs-recipes
make docs-check
make docs-preview
```

The build uses the same digest-pinned Jekyll image as GitHub Pages. `docs-check` checks links, anchors, repository paths, headings and metadata. Output goes into ignored build state. `docs-preview` serves `http://127.0.0.1:61327/dikciz-launcher/`; press Ctrl+C to stop it. It binds only to loopback.

Open the home page, a long API reference and the illustrated tutorials. Check desktop and narrow screens, the mobile menu, wide tables and code blocks. Wait for the search index to load, then search for a specific command and follow a result. Inspect image loading and browser errors. The website checks also run through `make lint` in CI.

## Generated content

`make docs-recipes` renders the short packaged MCP recipes as website pages and translates resource links into browser links. Their editable source remains the [packaged recipe directory](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/mcp/recipes). `make docs-recipes-check` rejects stale copies. Regenerate the [offline search corpus](search.md) after documentation changes with `make dikciz-search-docs`.

The generated [command](command-reference.md) and [token](token-permissions.md) references retain their site metadata when regenerated from installed discovery. The [developer guide](source-development.md#regenerate-references) describes those inputs.

## Publication

GitHub Pages is configured for the default branch and `/docs`, not a separate generated branch. A source push triggers its build and deployment. Verify the deployed commit and the public home, a deep guide, `sitemap.xml` and `llms.txt` before treating publication as complete.

Canonical URLs, page descriptions, structured metadata and the sitemap come from the supported Jekyll plugins. `llms.txt` lists guides with short descriptions for clients that use that convention. Search engine indexing is separate from publishing: the site owner can add the URL to Google Search Console and submit `sitemap.xml` using their own Google account.
