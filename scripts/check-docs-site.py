"""Check a built Pages site. Args: site_dir baseurl repo_url repo_root branch."""

import html.parser
import json
import os
import sys
from urllib.parse import unquote, urlsplit

SITE, BASE, REPO, ROOT, BRANCH = sys.argv[1:6]
DESCRIPTION_RANGE = (50, 170)
GITHUB_REF_PREFIXES = (f"{REPO}/blob/{BRANCH}/", f"{REPO}/tree/{BRANCH}/")


class Page(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids, self.links, self.meta = set(), [], {}
        self.jsonld, self.in_jsonld, self.h1, self.title = "", False, 0, ""
        self.in_title = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag in ("img", "script") and a.get("src"):
            self.links.append(a["src"])
        if tag == "link" and a.get("rel") == "stylesheet" and a.get("href"):
            self.links.append(a["href"])
        if tag == "a" and "href" in a:
            self.links.append(a["href"])
        if tag == "meta" and (a.get("name") or a.get("property")):
            self.meta[a.get("name") or a.get("property")] = a.get("content", "")
        if tag == "link" and a.get("rel") == "canonical":
            self.meta["canonical"] = a.get("href", "")
        if tag == "script" and a.get("type") == "application/ld+json":
            self.in_jsonld = True
        if tag == "h1":
            self.h1 += 1
        if tag == "title":
            self.in_title = True

    def handle_endtag(self, tag):
        if tag == "script":
            self.in_jsonld = False
        if tag == "title":
            self.in_title = False

    def handle_data(self, data):
        if self.in_jsonld:
            self.jsonld += data
        if self.in_title:
            self.title += data


pages = {}
for directory, _, files in os.walk(SITE):
    for file in files:
        if file.endswith(".html"):
            path = os.path.join(directory, file)
            parser = Page()
            with open(path, encoding="utf-8") as handle:
                raw = handle.read()
            parser.feed(raw)
            parser.raw = raw
            pages[os.path.relpath(path, SITE)] = parser

errors = []
if not pages:
    errors.append("no HTML pages found")


def resolve(source, target):
    if target.startswith(BASE + "/") or target == BASE:
        relative = target[len(BASE) :].lstrip("/")
    else:
        relative = os.path.normpath(os.path.join(os.path.dirname(source), target))
    if relative in ("", "."):
        return "index.html"
    for candidate in (
        relative,
        os.path.join(relative, "index.html"),
        relative + ".html",
    ):
        if os.path.isfile(os.path.join(SITE, candidate)):
            return candidate
    return None


for source, page in pages.items():
    for href in page.links:
        href = unquote(href)
        if href.startswith(GITHUB_REF_PREFIXES):
            path = href.split(f"/{BRANCH}/", 1)[1].split("#")[0]
            if not os.path.exists(os.path.join(ROOT, path)):
                errors.append(f"{source}: repo path missing: {path}")
            continue
        if href.startswith(("http:", "https:", "mailto:", "//", "data:")):
            continue
        if href.startswith("#"):
            target, anchor = source, href[1:]
        else:
            parts = urlsplit(href)
            path, anchor = parts.path, parts.fragment
            target = resolve(source, path)
        if target is None:
            errors.append(f"{source}: broken link: {href}")
            continue
        if anchor and anchor not in pages.get(target, page).ids:
            errors.append(f"{source}: broken anchor: {href}")
    if source == "404.html":
        continue
    if page.h1 != 1:
        errors.append(f"{source}: {page.h1} h1 tags")
    description = page.meta.get("description", "")
    if not DESCRIPTION_RANGE[0] <= len(description) <= DESCRIPTION_RANGE[1]:
        errors.append(f"{source}: description length {len(description)}")
    canonical = page.meta.get("canonical", "")
    expected_path = BASE + "/" + source.removesuffix("index.html")
    if not canonical.endswith(expected_path) or not canonical.startswith(
        "https://psyb0t.github.io/"
    ):
        errors.append(f"{source}: canonical {page.meta.get('canonical')}")
    if not page.jsonld:
        errors.append(f"{source}: no JSON-LD")
    else:
        json.loads(page.jsonld)
    if ".md" in "".join(h for h in page.links if not h.startswith("http")):
        errors.append(f"{source}: unconverted .md link")

for name, values in (
    ("title", [p.title for p in pages.values()]),
    ("description", [p.meta.get("description") for p in pages.values()]),
):
    duplicates = {v for v in values if values.count(v) > 1}
    if duplicates:
        errors.append(f"duplicate {name}: {duplicates}")

print(
    f"pages={len(pages)} links={sum(len(p.links) for p in pages.values())} errors={len(errors)}"
)
print("\n".join(sorted(errors)))
sys.exit(1 if errors else 0)
