#!/usr/bin/env python3
"""Prepare docs/ for the Docusaurus site in website/.

    python3 scripts/prepare-docs-site.py

docs/ stays plain Markdown that reads correctly on GitHub. This script copies it into
website/.content (the Docusaurus docs directory) and website/.assets (static files):

  - `permalink` becomes `slug`, so every published URL stays the same;
  - `parent`, `grand_parent` and `nav_order` become website/.content/sidebar.json;
  - images are copied next to the pages and into the static directory;
  - llms.txt is docs/llms.txt followed by one line per page.

It refuses Liquid or Jekyll syntax, which Docusaurus would print literally.
"""

import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
CONTENT = ROOT / "website" / ".content"
ASSETS = ROOT / "website" / ".assets"
SITE_ORIGIN = "https://psyb0t.github.io"
BASE_URL = "/dikciz-launcher"
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.S)
# A Liquid tag, a filter such as relative_url, or a site/page variable. Lua tables like {{type=...}} pass.
LIQUID = re.compile(r"\{%|\{\{[^}]*\|\s*\w+|\{\{\s*(site|page|item)\.")
# Front matter keys that only describe navigation; the sidebar carries them instead.
NAVIGATION_KEYS = {"parent", "grand_parent", "nav_order", "has_children", "nav_exclude", "search_exclude", "sitemap", "seo"}
KEPT_KEYS = ("title", "description", "image")


def parse_front_matter(text, path):
    match = FRONT_MATTER.match(text)
    if not match:
        raise SystemExit(f"{path}: missing front matter")
    fields = {}
    for line in match.group(1).splitlines():
        if not line.strip() or line.startswith((" ", "\t")):
            continue
        key, _, value = line.partition(":")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        fields[key.strip()] = value
    unknown = set(fields) - NAVIGATION_KEYS - set(KEPT_KEYS) - {"permalink"}
    if unknown:
        raise SystemExit(f"{path}: unsupported front matter {sorted(unknown)}")
    return fields, text[match.end():]


def yaml_string(value):
    return json.dumps(value, ensure_ascii=False)


def read(path):
    fields, body = parse_front_matter(path.read_text(encoding="utf-8"), path)
    if not fields.get("permalink"):
        raise SystemExit(f"{path}: missing permalink")
    return fields, body


def slug_of(fields):
    permalink = fields["permalink"]
    return "/" if permalink == "/" else permalink.rstrip("/")


def page_url(slug):
    return BASE_URL + ("/" if slug == "/" else slug + "/")


def convert(path, fields, body, slugs):
    if LIQUID.search(body):
        line = body[:LIQUID.search(body).start()].count("\n") + 1
        raise SystemExit(f"{path}: Liquid syntax near body line {line}; write plain Markdown")
    slug = slug_of(fields)
    header = [f"{key}: {yaml_string(fields[key])}" for key in KEPT_KEYS if key in fields]
    header.append(f"slug: {yaml_string(slug)}")
    # Raw HTML is not rewritten by the bundler, so image sources need the base URL. Docusaurus
    # checks raw links as page routes, so a click-to-enlarge link uses the full image URL.
    body = re.sub(r'src="images/', f'src="{BASE_URL}/images/', body)
    body = re.sub(r'href="images/', f'href="{SITE_ORIGIN}{BASE_URL}/images/', body)

    def page_link(match):
        name, anchor = match.group(1), match.group(2) or ""
        if name not in slugs:
            raise SystemExit(f"{path}: raw HTML links to missing page {name}")
        return f'href="{page_url(slugs[name])}{anchor}"'
    body = re.sub(r'href="([a-z0-9-]+\.md)(#[^"]*)?"', page_link, body)
    (CONTENT / path.name).write_text("---\n" + "\n".join(header) + "\n---\n" + body, encoding="utf-8")
    return fields, slug


def build_sidebar(pages):
    by_title = {fields["title"]: name for name, (fields, _) in pages.items()}
    children = {}
    roots = []
    for name, (fields, _) in pages.items():
        if fields.get("nav_exclude") == "true":
            continue
        parent = fields.get("parent")
        if parent and parent not in by_title:
            raise SystemExit(f"{name}: parent {parent!r} is not a page title")
        (children.setdefault(by_title[parent], []) if parent else roots).append(name)

    def order(name):
        fields = pages[name][0]
        return (float(fields.get("nav_order", "1e9")), fields["title"].lower())

    def item(name):
        doc_id = name.removesuffix(".md")
        kids = sorted(children.get(name, []), key=order)
        if not kids:
            return doc_id
        return {
            "type": "category",
            "label": pages[name][0]["title"],
            "link": {"type": "doc", "id": doc_id},
            "items": [item(kid) for kid in kids],
        }

    return [item(name) for name in sorted(roots, key=order)]


def build_llms(pages):
    header = (DOCS / "llms.txt").read_text(encoding="utf-8").rstrip() + "\n\n"
    rows = sorted(
        (fields["title"], slug, fields["description"])
        for fields, slug in pages.values()
        if fields.get("description") and fields.get("search_exclude") != "true"
    )
    lines = [f"- [{title}]({SITE_ORIGIN}{page_url(slug)}): {description}" for title, slug, description in rows]
    (ASSETS / "llms.txt").write_text(header + "\n".join(lines) + "\n", encoding="utf-8")


def main():
    for directory in (CONTENT, ASSETS):
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    sources = {path.name: read(path) for path in sorted(DOCS.glob("*.md"))}
    slugs = {name: slug_of(fields) for name, (fields, _) in sources.items()}
    pages = {name: convert(DOCS / name, fields, body, slugs) for name, (fields, body) in sources.items()}
    for target in (CONTENT / "images", ASSETS / "images"):
        shutil.copytree(DOCS / "images", target)
    (CONTENT / "sidebar.json").write_text(json.dumps(build_sidebar(pages), indent=2) + "\n", encoding="utf-8")
    build_llms(pages)
    print(f"prepared {len(pages)} pages")


if __name__ == "__main__":
    sys.exit(main())
