(() => {
	const input = document.getElementById("song-chordmarkup");
	if (!input) return;

	const preview = document.getElementById("chord-editor-preview");
	const previewStatus = document.getElementById("chord-preview-status");
	const importButton = document.getElementById("import-chord-sheet");
	const importStatus = document.getElementById("chord-import-status");
	let observer;

	function message(element, text) {
		element.textContent = text;
		element.hidden = !text;
	}

	function renderPreview() {
		observer?.disconnect();
		observer = undefined;
		const source = input.value.trim();
		if (source) {
			try {
				const sheet = JSON.parse(source);
				const viewport = renderChordSheet(sheet, { preview: true });
				preview.replaceChildren(viewport);
				observer = fitChordSheet(preview, viewport, { allowSideways: false });
				message(previewStatus, sheet.enabled === false ? "Chord text is disabled for viewers." : "");
				return;
			} catch {
				message(previewStatus, "Enter valid version 1 chord markup with at least one chord row.");
			}
		} else {
			message(previewStatus, "");
		}
		const placeholder = document.createElement("p");
		placeholder.textContent = source ? "Preview unavailable." : "Add markup to see the landscape preview.";
		preview.replaceChildren(placeholder);
	}

	input.addEventListener("input", renderPreview);
	importButton.addEventListener("click", async () => {
		const previous = input.value;
		if (previous.trim() && !window.confirm("Replace the markup in the editor with the Word version?")) return;

		importButton.disabled = true;
		message(importStatus, "Extracting chord markup…");
		try {
			const response = await fetch("/admin/extract_chord_markup.php", {
				method: "POST",
				body: new URLSearchParams({ id: importButton.dataset.songId })
			});
			const { sheet, error } = await response.json();
			if (!response.ok || !sheet) throw new Error(error || "Could not extract the Word chord sheet.");
			if (input.value !== previous) {
				message(importStatus, "The markup changed during extraction. Extract again to replace it.");
				return;
			}
			let wasDisabled = false;
			try {
				wasDisabled = JSON.parse(previous)?.enabled === false;
			} catch {
				// There is no enabled setting to carry over from an empty or invalid draft.
			}
			if (wasDisabled) sheet.enabled = false;
			input.value = JSON.stringify(sheet, null, 2);
			renderPreview();
			message(importStatus, "Word markup loaded. Save the song to apply it.");
		} catch (error) {
			message(importStatus, error instanceof SyntaxError ? "Could not extract the Word chord sheet." : error.message);
		} finally {
			importButton.disabled = false;
		}
	});
	renderPreview();
})();
