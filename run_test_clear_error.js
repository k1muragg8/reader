const jsdom = require("jsdom");
const { JSDOM } = jsdom;

const html = `
<!DOCTYPE html>
<html>
<body>
    <div id="reader-text">
        <p>This is a test paragraph with numbers 20 and 200.</p>
    </div>
    <script>
        const textContainer = document.getElementById('reader-text');

        function escapeRegex(string) {
            return string.replace(/[.*+?^%\${}()|[\]\\]/g, '\\$&');
        }

        function clearHighlights() {
            const highlights = textContainer.querySelectorAll('.search-highlight');
            highlights.forEach(span => {
                const parent = span.parentNode;
                if (!parent) return;
                const textNode = document.createTextNode(span.textContent);
                parent.replaceChild(textNode, span);

                if (textNode.previousSibling && textNode.previousSibling.nodeType === 3) {
                    textNode.nodeValue = textNode.previousSibling.nodeValue + textNode.nodeValue;
                    parent.removeChild(textNode.previousSibling);
                }
                if (textNode.nextSibling && textNode.nextSibling.nodeType === 3) {
                    textNode.nodeValue = textNode.nodeValue + textNode.nextSibling.nodeValue;
                    parent.removeChild(textNode.nextSibling);
                }
            });
        }

        window.performSearchFromNative = function(query) {
            query = query.trim();
            if (!query) {
                console.log("NONE");
                return;
            }

            try {
                clearHighlights();
            } catch (e) {
                console.error("ERROR in clearHighlights:", e);
            }

            try {
                const regex = new RegExp(escapeRegex(query), 'gi');
                const walker = document.createTreeWalker(textContainer, NodeFilter.SHOW_TEXT, null, false);
                let node;
                const nodesToReplace = [];

                while(node = walker.nextNode()) {
                    if (node.parentNode && (node.parentNode.tagName === 'SCRIPT' || node.parentNode.tagName === 'STYLE')) continue;
                    if (regex.test(node.nodeValue)) {
                        nodesToReplace.push(node);
                    }
                    regex.lastIndex = 0; // reset
                }

                let matchCount = 0;
                nodesToReplace.forEach(textNode => {
                    const text = textNode.nodeValue;
                    const parent = textNode.parentNode;
                    const frag = document.createDocumentFragment();
                    let lastIdx = 0;
                    let match;
                    regex.lastIndex = 0;

                    while ((match = regex.exec(text)) !== null) {
                        if (match.index === regex.lastIndex) regex.lastIndex++;

                        frag.appendChild(document.createTextNode(text.substring(lastIdx, match.index)));

                        const span = document.createElement('span');
                        span.className = 'search-highlight';
                        span.id = 'search-match-' + matchCount;
                        span.textContent = match[0];
                        frag.appendChild(span);

                        lastIdx = match.index + match[0].length;
                        matchCount++;
                    }
                    frag.appendChild(document.createTextNode(text.substring(lastIdx)));
                    parent.replaceChild(frag, textNode);
                });
            } catch (e) {
                console.error("ERROR in search:", e);
            }
        };
    </script>
</body>
</html>
`;

const dom = new JSDOM(html, { runScripts: "dangerously" });
console.log("Search '2'");
dom.window.performSearchFromNative("2");
console.log("Search '20'");
dom.window.performSearchFromNative("20");
