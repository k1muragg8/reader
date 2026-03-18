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

        window.performSearchFromNative = function(query) {
            console.log("Called with " + query);
            try {
                // escapeRegex is defined AFTER performSearchFromNative in the file?
                const regex = new RegExp(escapeRegex(query), 'gi');
                console.log("Regex created");
            } catch (e) {
                console.error("ERROR in search:", e.message);
            }
        };

        function escapeRegex(string) {
            return string.replace(/[.*+?^%\${}()|[\]\\]/g, '\\$&');
        }
    </script>
</body>
</html>
`;

const dom = new JSDOM(html, { runScripts: "dangerously" });
dom.window.performSearchFromNative("20");
