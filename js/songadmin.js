(function() {
        if (typeof document === "undefined" || typeof window === "undefined") {
                return;
        }

        if (!document.body || !document.body.classList.contains("admin-view")) {
                return;
        }

        const pageOrigin = window.location.origin || `${window.location.protocol}//${window.location.host}`;
        let adminInitialised = false;

        function escapeAttribute(value) {
                return String(value ?? "")
                        .replace(/&/g, "&amp;")
                        .replace(/"/g, "&quot;")
                        .replace(/'/g, "&#39;")
                        .replace(/</g, "&lt;")
                        .replace(/>/g, "&gt;");
        }

        function getModalElements() {
                return {
                        modal: document.getElementById("modal-main"),
                        content: document.getElementById("modal-content")
                };
        }

        function showIframeModal(url, mode, titleText) {
                const { modal, content } = getModalElements();

                if (!modal || !content) {
                        window.location.href = url;
                        return;
                }

                modal.dataset.mode = mode;
                content.innerHTML = '<div class="modal-loading">Loading...</div>';

                const iframe = document.createElement("iframe");
                iframe.src = url;
                iframe.className = "modal-iframe";
                iframe.setAttribute("title", titleText);
                iframe.onload = () => {
                        const loader = content.querySelector(".modal-loading");
                        if (loader) {
                                loader.remove();
                        }
                };

                content.appendChild(iframe);
                modal.style.display = "block";
        }

        function openEditModal(songId) {
                if (!songId) {
                        return;
                }

                const editUrl = `/admin/editsong.php?id=${encodeURIComponent(songId)}`;
                showIframeModal(editUrl, "edit", `Edit song ${songId}`);
        }

        function openAddModal() {
                const addUrl = "/admin/addsong.php";
                showIframeModal(addUrl, "add", "Add new song");
        }

        function renderActionButtons(song) {
                if (!song || !song.id) {
                        return "";
                }

                const safeId = escapeAttribute(song.id);
                return `<img src="/img/edit.svg" alt="Edit Song" class="icon-btn edit-btn" data-song-id="${safeId}" title="Edit song" role="button" tabindex="0">`;
        }

        function updateClearButton(clearButton, hasValue) {
                if (!clearButton) {
                        return false;
                }

                if (hasValue) {
                        return false;
                }

                clearButton.textContent = "⊕";
                clearButton.onclick = openAddModal;
                clearButton.title = "Add new song";
                clearButton.setAttribute("aria-label", "Add new song");

                return true;
        }

        function handleResultsClick(event) {
                const target = event.target.closest ? event.target.closest('.edit-btn') : event.target;

                if (!target || !target.classList || !target.classList.contains('edit-btn')) {
                        return;
                }

                event.preventDefault();
                const songId = target.getAttribute('data-song-id');
                openEditModal(songId);
        }

        function handleResultsKeydown(event) {
                if (!event || (event.key !== 'Enter' && event.key !== ' ')) {
                        return;
                }

                const target = event.target.closest ? event.target.closest('.edit-btn') : event.target;

                if (!target || !target.classList || !target.classList.contains('edit-btn')) {
                        return;
                }

                event.preventDefault();
                const songId = target.getAttribute('data-song-id');
                openEditModal(songId);
        }

        function handleMessage(event) {
                const originMatches = !event.origin || event.origin === pageOrigin || (pageOrigin === 'null' && event.origin === 'null');

                if (!originMatches || !event.data || typeof event.data !== 'object') {
                        return;
                }

                const { type, songId } = event.data;

                if (type === 'closeEditModal') {
                        if (typeof hideModal === 'function') {
                                hideModal();
                        }
                        return;
                }

                if (type === 'songUpdated') {
                        if (typeof hideModal === 'function') {
                                hideModal();
                        }
                        if (typeof searchSongs === 'function') {
                                searchSongs();
                        }
                        return;
                }

                if (type === 'songInserted') {
                        if (typeof hideModal === 'function') {
                                hideModal();
                        }

                        if (songId) {
                                openEditModal(songId);
                        }

                        if (typeof searchSongs === 'function') {
                                searchSongs();
                        }
                }
        }

        function initialiseAdminFeatures() {
                if (adminInitialised) {
                        return;
                }

                adminInitialised = true;
                document.removeEventListener('songbook:ready', initialiseAdminFeatures);
                const resultsBody = document.getElementById('results');

                if (resultsBody) {
                        resultsBody.addEventListener('click', handleResultsClick);
                        resultsBody.addEventListener('keydown', handleResultsKeydown);
                }

                window.addEventListener('message', handleMessage);

                if (typeof toggleClearButton === 'function') {
                        toggleClearButton();
                }
        }

        window.songbookAdmin = {
                renderActionButtons,
                updateClearButton,
                openEditModal,
                openAddModal
        };

        if (window.songbookReady) {
                initialiseAdminFeatures();
        } else {
                document.addEventListener('songbook:ready', initialiseAdminFeatures);
        }
})();

