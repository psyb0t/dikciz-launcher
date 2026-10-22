package org.fossify.home.dikciz

/** Maps live DOM nodes to saved source before widget JavaScript can change them. */
internal object DikcizHtmlItemEditor {
    const val REASON_NO_TOUCH = "no_touch"
    const val REASON_NO_TARGET = "no_target"
    const val REASON_UNMAPPED = "unmapped"
    const val REASON_CONTAINER = "container"
    const val REASON_STALE_SOURCE = "stale_source"
    const val REASON_SELECTED = "selected"
    const val REASON_INVALID = "invalid_result"
    val selectionReasons = setOf(
        REASON_NO_TARGET,
        REASON_UNMAPPED,
        REASON_CONTAINER,
        REASON_STALE_SOURCE,
        REASON_SELECTED,
    )

    fun install(): String {
        return "($INSTALL)();"
    }

    private val INSTALL = """
        function() {
            const template = document.createElement("template");
            for (const node of document.body.childNodes) {
                if (node !== document.currentScript) template.content.append(node.cloneNode(true));
            }
            const paths = new WeakMap();
            const selections = new Map();
            const itemSelector = "[data-dikciz-item],[data-dikciz-widget],article,.card,.dikciz-block,section,li";
            const cardSelector = "[data-dikciz-item],[data-dikciz-widget],article,.card,.dikciz-block,li";
            const forbiddenSelector = "html,body,script,style,link,meta";
            const executableSelector = "script,style,link,meta";
            const maximumLabelLength = 120;
            let nextSelection = 0;
            let pressedTarget;
            document.addEventListener("pointerdown", event => {
                pressedTarget = event.target;
            }, true);

            function roots() {
                return Array.from(document.body.children).filter(node => !node.matches("script[data-dikciz-internal]"));
            }

            function pair(saved, live, path) {
                if (!live || saved.outerHTML !== live.outerHTML) return;
                paths.set(live, path);
                Array.from(saved.children).forEach((child, index) => {
                    pair(child, live.children[index], path.concat(index));
                });
            }

            Array.from(template.content.children).forEach((node, index) => {
                pair(node, roots()[index], [index]);
            });

            function atPath(root, path) {
                return path.reduce((node, index) => node?.children[index], root);
            }

            window.dikcizHtmlItems = Object.freeze({
                refresh(source) {
                    template.innerHTML = source;
                    Array.from(template.content.children).forEach((node, index) => {
                        pair(node, roots()[index], [index]);
                    });
                },
                select(source) {
                    const hit = pressedTarget;
                    pressedTarget = null;
                    const reject = reason => JSON.stringify({ reason });
                    if (!(hit instanceof Element) || !hit.isConnected || hit.closest(executableSelector)) {
                        return reject("$REASON_NO_TARGET");
                    }
                    const item = hit.closest("[data-dikciz-item-id]") || hit.closest(itemSelector) || hit;
                    const path = paths.get(item);
                    if (!path) return reject("$REASON_UNMAPPED");
                    if (item.matches(forbiddenSelector) || (item.matches("main") && !item.matches(cardSelector))) return reject("$REASON_CONTAINER");
                    // Empty grid padding must not select every card in that grid.
                    if ((!item.matches(cardSelector) || item.matches("main")) && item.querySelector(itemSelector)) return reject("$REASON_CONTAINER");
                    const original = document.createElement("template");
                    original.innerHTML = source;
                    const saved = atPath(original.content, path);
                    const initial = atPath(template.content, path);
                    if (!saved || !initial || saved.outerHTML !== initial.outerHTML || !item.isConnected) {
                        return reject("$REASON_STALE_SOURCE");
                    }
                    selections.clear();
                    const id = ++nextSelection;
                    selections.set(id, { item, path, original });
                    const heading = item.querySelector("h1,h2,h3,h4,h5,h6");
                    const label = (heading?.textContent || item.getAttribute("aria-label") ||
                        item.textContent || item.tagName).trim().replace(/\s+/g, " ").slice(0, maximumLabelLength);
                    const itemIds = [saved, ...saved.querySelectorAll("[data-dikciz-item-id]")]
                        .map(node => node.getAttribute("data-dikciz-item-id")).filter(Boolean);
                    return JSON.stringify({ id, label, itemIds, html: saved.outerHTML, reason: "$REASON_SELECTED" });
                },
                replace(id, replacement) {
                    const selection = selections.get(id);
                    if (!selection || !selection.item.isConnected ||
                        paths.get(selection.item) !== selection.path) return null;
                    const copy = selection.original.cloneNode(true);
                    const target = atPath(copy.content, selection.path);
                    if (!target) return null;
                    if (replacement === null) {
                        target.remove();
                    } else {
                        const fragment = document.createElement("template");
                        fragment.innerHTML = replacement;
                        target.replaceWith(fragment.content);
                    }
                    selections.delete(id);
                    const html = copy.innerHTML;
                    return JSON.stringify({ html: html.trim() ? html : "<!-- Empty HTML widget -->" });
                },
            });
        }
    """.trimIndent()
}
