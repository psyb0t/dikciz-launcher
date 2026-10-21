# Edit or delete one HTML card

Read the current widget and its saved HTML before editing. A page-sized HTML widget can contain many cards. The widget's top move handle controls the entire widget; a long press inside content targets a source-backed HTML item.

1. Inspect the current page and locate the intended card. Long press inside that card rather than its top-level widget handle.
2. Read the native **HTML item** menu. Confirm its title identifies the intended item before choosing **Edit** or **Delete**.
3. Edit only that item's fragment or confirm scoped deletion. Page and widget locks still protect the change.
4. Read the saved widget again. Verify the containing widget, page and sibling items remain. Inspect the rendered result too.

Source-backed `article`, `section`, `li`, `.card`, `.dikciz-block` and elements with `data-dikciz-item` or `data-dikciz-widget` can persist changes. A nested press selects the nearest item. Script-generated DOM without a saved source target is refused rather than silently deleting its container. For dynamic content, edit the render source or data that creates the card. Mark a custom saved card with `data-dikciz-item`.

If item selection is ambiguous, stop before deleting. Download the saved widget record and make a scoped source edit, preserving siblings. Do not call whole-widget or whole-page delete to remove a card.

[HTML guide](dikciz://docs/guides/html).
