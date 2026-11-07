"""Build deterministic, model-free search documents from shipped guides and public manuals."""

import argparse
import hashlib
import importlib.util
import json
import logging
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/dikciz/assets/search/documentation.json"
ALIASES = OUTPUT.with_name("aliases.json")
GUIDES = ROOT / "app/src/dikciz/assets/mcp"
MAXIMUM_SECTION = 12000
EXCLUDED = {"changelog.md", "404.md"}
LOGGER = logging.getLogger(__name__)
HEADING = re.compile(r"^#{1,6}\s+(.+)$")
HTML_GALLERY = re.compile(r"^\s*</?(?:table|tr|td|img|a)(?:\s|>|/)", re.IGNORECASE)


def sections(text):
    title = ""
    lines = []
    fenced = False
    for line in text.splitlines():
        if line.lstrip().startswith(("```", "~~~")):
            fenced = not fenced
        heading = HEADING.match(line) if not fenced else None
        if heading and lines:
            yield title, "\n".join(lines).strip()
            lines = []
        if heading:
            title = heading.group(1)
        lines.append(line)
    if lines:
        yield title, "\n".join(lines).strip()


def without_galleries(text):
    lines = []
    fenced = False
    for line in text.splitlines():
        if line.lstrip().startswith(("```", "~~~")):
            fenced = not fenced
        if not fenced and (HTML_GALLERY.match(line) or "<img " in line.lower()):
            continue
        lines.append(line)
    return "\n".join(lines)


def chunks(text):
    while len(text) > MAXIMUM_SECTION:
        boundary = text.rfind("\n", 0, MAXIMUM_SECTION)
        if boundary <= 0:
            boundary = MAXIMUM_SECTION
        yield text[:boundary]
        text = text[boundary:].lstrip("\n")
    if text:
        yield text


def build(root=ROOT):
    aliases = json.loads((root / ALIASES.relative_to(ROOT)).read_text())
    documents = []
    guides = root / GUIDES.relative_to(ROOT)
    for path in sorted(guides.rglob("*.md")):
        slug = path.relative_to(guides).with_suffix("").as_posix()
        text = path.read_text()
        text = re.sub(r"\A---\n.*?\n---\n", "", text, count=1, flags=re.DOTALL)
        text = text.replace("<!-- {% raw %} -->\n", "").replace(
            "<!-- {% endraw %} -->", ""
        )
        title = next(
            (
                line.lstrip("# ").strip()
                for line in text.splitlines()
                if line.startswith("# ")
            ),
            slug,
        )
        documents.append(
            {
                "id": slug,
                "title": title,
                "text": text,
                "aliases": aliases.get(slug, ""),
                "uri": "dikciz://docs/" + slug,
            }
        )
    for path in sorted((root / "docs").rglob("*.md")):
        if path.name in EXCLUDED or path.stem.startswith("recipe-"):
            continue
        relative = path.relative_to(root).as_posix()
        text = path.read_text()
        text = re.sub(r"\A---\n.*?\n---\n", "", text, count=1, flags=re.DOTALL)
        text = text.replace("<!-- {% raw %} -->\n", "").replace(
            "<!-- {% endraw %} -->", ""
        )
        title = next(
            (
                line.lstrip("# ").strip()
                for line in text.splitlines()
                if line.startswith("# ")
            ),
            path.stem,
        )
        for number, (heading, section) in enumerate(sections(text)):
            # Screenshots and HTML gallery markup add no useful API instructions.
            section = without_galleries(section)
            for part, chunk in enumerate(chunks(section)):
                identifier = (
                    "library/"
                    + path.relative_to(root / "docs").with_suffix("").as_posix()
                    + "/"
                    + str(number)
                    + "/"
                    + str(part)
                )
                documents.append(
                    {
                        "id": identifier,
                        "title": title + (": " + heading if heading != title else ""),
                        "text": chunk,
                        "aliases": aliases.get(relative, ""),
                        "uri": "dikciz://docs/" + identifier,
                    }
                )
    if not documents or len({item["id"] for item in documents}) != len(documents):
        raise ValueError("Search documents must have unique IDs")
    return documents


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when the packaged corpus differs from current documentation",
    )
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location(
        "search_generation_logging", ROOT / "scripts/dikciz-control.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.configure_logging(ROOT / ".testing/search-corpus-generation.log")
    try:
        documents = build()
        rendered = json.dumps(documents, ensure_ascii=False, indent=2) + "\n"
        if args.check:
            if not OUTPUT.exists() or OUTPUT.read_text() != rendered:
                raise ValueError("Search corpus is stale; run make dikciz-search-docs")
        else:
            OUTPUT.parent.mkdir(parents=True, exist_ok=True)
            temporary = OUTPUT.with_suffix(".json.new")
            temporary.write_text(rendered)
            temporary.replace(OUTPUT)
        LOGGER.info(
            "Search corpus verified",
            extra={
                "document_count": len(documents),
                "content_hash": hashlib.sha256(rendered.encode()).hexdigest(),
            },
        )
    except (OSError, ValueError, TypeError):
        LOGGER.exception("Search corpus generation failed")
        raise


if __name__ == "__main__":
    main()
