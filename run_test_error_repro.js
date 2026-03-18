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
        let searchMatches = [];

        window.performSearchFromNative = function(query) {
            query = query.trim();
            if (!query) return;

            // 1. Clear previous
            // clearHighlights();
            searchMatches = [];

            // 2. Find matches (Text Node Traversal)
            setTimeout(() => {
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

                            const start = Math.max(0, match.index - 20);
                            const end = Math.min(text.length, match.index + match[0].length + 20);

                            // Let's print exactly what's happening here
                            console.log("Match[0]:", match[0]);
                            console.log("Escaped Match[0]:", escapeRegex(match[0]));

                            let snippet = text.substring(start, end).replace(new RegExp(escapeRegex(match[0]), 'gi'), \`<b>\${match[0]}</b>\`);
                            snippet = snippet.replace(/[\\r\\n]+/g, ' ');

                            searchMatches.push({
                                id: 'search-match-' + matchCount,
                                text: '... ' + snippet + ' ...',
                            });

                            lastIdx = match.index + match[0].length;
                            matchCount++;
                        }
                        frag.appendChild(document.createTextNode(text.substring(lastIdx)));
                        parent.replaceChild(frag, textNode);
                    });

                    const serialized = searchMatches.map(m => m.id + "|||" + m.text).join("|||");
                    console.log("Success:", serialized || "NONE");
                } catch (e) {
                    console.log("error|||" + e.toString());
                }
            }, 0);
        };

        function escapeRegex(string) {
            return string.replace(/[.*+?^%\${}()|[\]\\]/g, '\\$&'); // $& means the whole matched string
        }
    </script>
</body>
</html>
`;

const dom = new JSDOM(html, { runScripts: "dangerously" });
dom.window.performSearchFromNative("20");
