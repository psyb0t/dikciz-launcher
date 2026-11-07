"""Behavioral tests for the documentation corpus generator."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/generate-dikciz-search.py"
spec = importlib.util.spec_from_file_location("search_corpus", SCRIPT)
corpus = importlib.util.module_from_spec(spec)
spec.loader.exec_module(corpus)


def test_packaged_corpus_matches_current_documentation():
    expected = corpus.build()
    assert json.loads(corpus.OUTPUT.read_text()) == expected
    assert len(expected) > 54
    assert len({document["id"] for document in expected}) == len(expected)
    assert all(document["text"] and document["uri"].startswith("dikciz://docs/") for document in expected)
    assert all(len(document["text"]) <= corpus.MAXIMUM_SECTION for document in expected)
    assert any("library/command-reference/" in document["id"] for document in expected)
    assert any("library/token-permissions/" in document["id"] for document in expected)
    assert any("library/javascript-api/" in document["id"] for document in expected)
    assert any("library/lua-api/" in document["id"] for document in expected)


def test_corpus_preserves_code_and_splits_headings_deterministically(tmp_path):
    assets = tmp_path / "app/src/dikciz/assets"
    (assets / "search").mkdir(parents=True)
    (assets / "mcp/guides").mkdir(parents=True)
    (tmp_path / "docs").mkdir()
    (assets / "search/aliases.json").write_text(json.dumps({"guides/example": "example aliases"}))
    (assets / "mcp/guides/example.md").write_text("# Example\n\nA short guide.")
    source = "# Manual\n\n## First\n\n```xml\n<permission name=\"example\" />\n```\n\n## Second\n\n" + "long text " * 3000
    (tmp_path / "docs/manual.md").write_text(source)
    (tmp_path / "docs/command-reference.md").write_text("# Included command schemas")
    first = corpus.build(tmp_path)
    assert first == corpus.build(tmp_path)
    assert first[0]["aliases"] == "example aliases"
    assert any('<permission name="example" />' in document["text"] for document in first)
    assert any("Second" in document["title"] for document in first)
    assert all(len(document["text"]) <= corpus.MAXIMUM_SECTION for document in first)


def test_check_rejects_stale_output_without_rewriting_it(tmp_path):
    # Run the generator in a minimal separate checkout so the real asset stays untouched.
    (tmp_path / "scripts").mkdir()
    target = tmp_path / "app/src/dikciz/assets/search"
    target.mkdir(parents=True)
    (tmp_path / "app/src/dikciz/assets/mcp").mkdir()
    (tmp_path / "docs").mkdir()
    (tmp_path / ".testing").mkdir()
    (target / "aliases.json").write_text("{}")
    (target / "documentation.json").write_text("[]")
    (tmp_path / "docs/manual.md").write_text("# Fresh content\n\nA procedure.")
    (tmp_path / "scripts/generate-dikciz-search.py").write_text(SCRIPT.read_text())
    (tmp_path / "scripts/dikciz-control.py").write_text(
        "import logging\ndef configure_logging(path):\n    logging.basicConfig(level=logging.INFO)\n"
    )
    result = subprocess.run(
        [sys.executable, str(tmp_path / "scripts/generate-dikciz-search.py"), "--check"],
        capture_output=True, text=True, timeout=20,
    )
    assert result.returncode != 0 and "Search corpus is stale" in result.stderr
    assert (target / "documentation.json").read_text() == "[]"


def test_gallery_filter_preserves_code_examples():
    source = '<p><img src="picture.png"></p>\n```html\n<a href="example">Link</a>\n<img src="example.png">\n```'
    cleaned = corpus.without_galleries(source)
    assert "picture.png" not in cleaned
    assert '<a href="example">Link</a>' in cleaned
    assert '<img src="example.png">' in cleaned
