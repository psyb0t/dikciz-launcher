# Edit part of an HTML document

An HTML widget is one WebView with its own HTML, CSS, JavaScript and state. Sections and `data-*` attributes are ordinary document content. Long press anywhere for whole-widget controls. Edit exposes the complete source. Delete removes the entire widget, so do not use it to remove one section.

1. Read the saved widget and identify the intended section and the JavaScript that renders it.
2. Change the relevant saved markup, CSS, JavaScript or state. Preserve sibling content and unrelated widget fields.
3. Submit the change through the shared widget patch action or a preserved complete configuration replacement. Page and widget locks apply.
4. Read back the widget and inspect its rendered result. Live DOM patches alone do not persist source edits.

Optional `selfWidget.item(id)` namespaces remain available for a document's own state and files. They do not create native child widgets. Complete reusable documents belong in the HTML widget library.

[HTML guide](dikciz://docs/guides/html).
