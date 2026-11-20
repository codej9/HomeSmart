// Fetches the alerts panel every 30 seconds and swaps it in only when it changed.
(function () {
    'use strict';

    var root = document.querySelector('[data-refresh-url]');
    if (!root) {
        return;
    }
    var url = root.getAttribute('data-refresh-url');

    function refresh() {
        fetch(url, {
            headers: {'X-HomeSmart-Poll': '1'},
            redirect: 'manual',
            credentials: 'same-origin'
        }).then(function (response) {
            if (response.status === 401) {
                window.location.reload();
                return null;
            }
            return response.status === 200 ? response.text() : null;
        }).then(function (html) {
            if (html === null) {
                return;
            }
            var template = document.createElement('template');
            template.innerHTML = html;
            // The page and the poll differ in whitespace around the fragment, so compare trimmed
            if (template.innerHTML.trim() !== root.innerHTML.trim()) {
                root.innerHTML = template.innerHTML;
            }
        }).catch(function () {
            // keep the current panel
        });
    }

    setInterval(refresh, 30000);
})();
