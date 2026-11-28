function renderChordSheet(sheet, { preview = false, importNotice = "" } = {}) {
	const lineKinds = ["chords", "lyrics", "note", "pitch"];
	const runTones = ["alternate", "chords", "comment", "detail", "note", "pitch"];
	if ((!preview && sheet?.enabled === false) || sheet?.version !== 1 || typeof sheet.title !== "string" || !Array.isArray(sheet.lines)
		|| !Array.isArray(sheet.details) || !sheet.details.every(detail => typeof detail === "string") || !sheet.lines.some(line =>
			line?.kind === "chords" && (line.runs?.some(run => run?.text?.trim()) || line.aside?.trim()))) {
		throw new Error("Invalid chord sheet");
	}

	const viewport = document.createElement("div");
	viewport.className = "chord-sheet-viewport";
	viewport.tabIndex = 0;
	if (importNotice && sheet.lines.some((line, index) => line?.kind === "note"
		&& sheet.lines[index - 1]?.kind === "note" && typeof line.paragraphStart !== "boolean")) {
		const notice = document.createElement("p");
		notice.className = "chord-sheet-import-notice";
		notice.setAttribute("role", "status");
		notice.textContent = importNotice;
		viewport.append(notice);
	}
	const article = document.createElement("article");
	article.className = "chord-sheet";
	article.append(renderChordHeading(sheet));

	for (const [index, line] of sheet.lines.entries()) {
		if (!lineKinds.includes(line?.kind) || !Array.isArray(line.runs) ||
			(line.aside != null && typeof line.aside !== "string")) throw new Error("Invalid chord row");
		const row = document.createElement("div");
		row.className = `chord-sheet-row chord-sheet-${line.kind}`;
		if (line.kind === "note") {
			const paragraphStart = typeof line.paragraphStart === "boolean"
				? line.paragraphStart : sheet.lines[index - 1]?.kind !== "note";
			row.classList.toggle("chord-sheet-note-start", paragraphStart);
			row.classList.toggle("chord-sheet-note-end", sheet.lines[index + 1]?.kind !== "note");
		}
		const main = document.createElement("span");
		main.className = "chord-sheet-main";
		for (const run of line.runs) {
			if (typeof run?.text !== "string") throw new Error("Invalid chord text");
			const span = document.createElement(run.superscript ? "sup" : "span");
			span.textContent = run.text;
			if (runTones.includes(run.tone)) span.classList.add(`chord-sheet-tone-${run.tone}`);
			if (run.underline) {
				span.style.textDecorationLine = "underline";
				if (run.underline === "dotted") span.style.textDecorationStyle = "dotted";
			}
			if (run.bold) span.style.fontWeight = "bold";
			if (run.italic) span.style.fontStyle = "italic";
			main.append(span);
		}
		row.append(main);
		if (line.aside) {
			const aside = document.createElement("span");
			aside.className = "chord-sheet-aside";
			if (line.asideTone === "pitch") aside.classList.add("chord-sheet-tone-pitch");
			aside.textContent = line.aside;
			row.append(aside);
		}
		article.append(row);
	}
	viewport.append(article);
	return viewport;
}

function renderChordHeading(sheet) {
	function runsFor(index, text) {
		const runs = sheet.headingRuns?.[index];
		return Array.isArray(runs) && runs.every(run => typeof run?.text === "string")
			&& runs.map(run => run.text).join("") === text ? runs : [{ text }];
	}
	function appendRuns(element, runs) {
		for (const run of runs) {
			if (run.tone === "comment") {
				const comment = document.createElement("span");
				comment.className = "chord-sheet-tone-comment";
				comment.textContent = run.text;
				element.append(comment);
			} else {
				element.append(run.text);
			}
		}
	}

	const header = document.createElement("header");
	const title = document.createElement("h2");
	appendRuns(title, runsFor(0, sheet.title));
	const credits = [];
	const capo = [];
	sheet.details.forEach((detail, index) => {
		const runs = runsFor(index + 1, detail);
		const match = /^((?:drop-d,\s*|\(?[\d¾:/]+\)?\s*)?capo\s*\d+)\b\s*(.*)$/i.exec(detail);
		if (!match) {
			credits.push(runs);
		} else {
			capo.push(match[1]);
			if (match[2]) {
				let offset = detail.length - match[2].length;
				credits.push(runs.flatMap(run => {
					const text = run.text.slice(offset);
					offset = Math.max(0, offset - run.text.length);
					return text ? [{ ...run, text }] : [];
				}));
			}
		}
	});

	const creditText = document.createElement("span");
	creditText.className = "chord-sheet-credits";
	credits.forEach((runs, index) => {
		if (index > 0) creditText.append(" · ");
		appendRuns(creditText, runs);
	});
	header.append(title, creditText);
	if (capo.length > 0) {
		const capoText = document.createElement("span");
		capoText.className = "chord-sheet-capo";
		capoText.textContent = capo.join(" · ");
		header.append(capoText);
	}
	return header;
}

function fitChordText(element) {
	element.style.fontSize = "";
	const width = element.clientWidth;
	const textWidth = element.scrollWidth;
	if (textWidth > width) {
		element.style.fontSize = `${Math.max(1, width - 2) / textWidth}em`;
	}
}

function fitChordSheet(container, viewport, { allowSideways = true } = {}) {
	const sheet = viewport.querySelector(".chord-sheet");
	const notice = viewport.querySelector(".chord-sheet-import-notice");
	const notes = sheet.querySelectorAll(".chord-sheet-note .chord-sheet-main");
	const fittedText = [sheet.firstElementChild, ...notes];
	fittedText.forEach(fitChordText);
	const baseFontSize = parseFloat(getComputedStyle(sheet).fontSize);
	const sheetWidth = sheet.offsetWidth;
	const sheetHeight = sheet.offsetHeight;
	const wide = sheetWidth > sheetHeight;
	const minimumScale = wide && notes.length > 0 ? 0.65 : 0.8;
	const pairCount = sheet.querySelectorAll(".chord-sheet-lyrics + .chord-sheet-chords").length;
	let fittedSize = baseFontSize;
	let fittedGap = 0;
	let zoom = 1;

	function sizeSheet(scale, gap) {
		sheet.style.fontSize = `${fittedSize * scale}px`;
		sheet.style.setProperty("--chord-pair-gap", `${gap * scale}px`);
		fittedText.forEach(fitChordText);
	}

	function fit() {
		const sideways = allowSideways && wide && container.clientWidth < container.clientHeight;
		viewport.classList.toggle("chord-sheet-sideways", sideways);

		const padding = getComputedStyle(viewport);
		const width = viewport.offsetWidth - parseFloat(padding.paddingLeft) - parseFloat(padding.paddingRight);
		const height = viewport.offsetHeight - parseFloat(padding.paddingTop) - parseFloat(padding.paddingBottom)
			- (notice?.offsetHeight ?? 0);
		const scale = Math.min((width - 2) / sheetWidth, (height - 2) / sheetHeight);
		// Keep dense sheets readable and let the viewport scroll.
		fittedSize = baseFontSize * Math.max(minimumScale, scale);
		sizeSheet(1, 0);
		const spareHeight = Math.max(0, height - sheet.offsetHeight - 2);
		fittedGap = wide && pairCount > 0 ? Math.min(fittedSize * 0.375, spareHeight / pairCount) : 0;
		if (zoom === 1) {
			sheet.style.setProperty("--chord-pair-gap", `${fittedGap}px`);
		} else {
			sizeSheet(zoom, fittedGap);
		}
	}

	fit();
	enableChordSheetGestures(viewport, (factor, anchor, previousAnchor = anchor) => {
		const nextZoom = factor === null ? 1 : Math.min(5, Math.max(0.5, zoom * factor));
		const ratio = nextZoom / zoom;
		const x = viewport.scrollLeft + previousAnchor.x - sheet.offsetLeft;
		const y = viewport.scrollTop + previousAnchor.y - sheet.offsetTop;
		if (nextZoom !== zoom) {
			zoom = nextZoom;
			sizeSheet(zoom, fittedGap);
		}
		viewport.scrollLeft = sheet.offsetLeft + x * ratio - anchor.x;
		viewport.scrollTop = sheet.offsetTop + y * ratio - anchor.y;
	});
	const observer = new ResizeObserver(fit);
	observer.observe(container);
	return observer;
}

function enableChordSheetGestures(viewport, zoomAt) {
	function localPoint(clientX, clientY) {
		const bounds = viewport.getBoundingClientRect();
		return viewport.classList.contains("chord-sheet-sideways")
			? { x: clientY - bounds.top, y: bounds.right - clientX }
			: { x: clientX - bounds.left, y: clientY - bounds.top };
	}

	function pinch(touches) {
		const a = touches[0];
		const b = touches[1];
		return {
			center: localPoint((a.clientX + b.clientX) / 2, (a.clientY + b.clientY) / 2),
			distance: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
		};
	}

	let previousPinch;
	viewport.addEventListener("touchstart", event => {
		if (event.touches.length === 2) {
			event.preventDefault();
			previousPinch = pinch(event.touches);
		}
	}, { passive: false });
	viewport.addEventListener("touchmove", event => {
		if (event.touches.length !== 2) return;
		event.preventDefault();
		const current = pinch(event.touches);
		if (previousPinch?.distance > 0) {
			zoomAt(current.distance / previousPinch.distance, current.center, previousPinch.center);
		}
		previousPinch = current;
	}, { passive: false });
	viewport.addEventListener("touchend", () => { previousPinch = undefined; });
	viewport.addEventListener("touchcancel", () => { previousPinch = undefined; });

	viewport.addEventListener("wheel", event => {
		if (!event.ctrlKey) return;
		event.preventDefault();
		const delta = event.deltaMode === 0 ? event.deltaY : 16 * event.deltaY;
		zoomAt(Math.exp(-delta * 0.002), localPoint(event.clientX, event.clientY));
	}, { passive: false });
	viewport.addEventListener("keydown", event => {
		const factor = { "+": 1.2, "=": 1.2, "-": 1 / 1.2, "0": null }[event.key];
		if (factor === undefined || event.ctrlKey || event.metaKey || event.altKey) return;
		event.preventDefault();
		zoomAt(factor, { x: viewport.clientWidth / 2, y: viewport.clientHeight / 2 });
	});

	let drag;
	viewport.addEventListener("pointerdown", event => {
		if (event.pointerType !== "mouse" || event.button !== 0
			|| (viewport.scrollWidth <= viewport.clientWidth && viewport.scrollHeight <= viewport.clientHeight)) return;
		event.preventDefault();
		viewport.focus({ preventScroll: true });
		viewport.setPointerCapture(event.pointerId);
		drag = {
			id: event.pointerId, start: localPoint(event.clientX, event.clientY),
			left: viewport.scrollLeft, top: viewport.scrollTop
		};
	});
	viewport.addEventListener("pointermove", event => {
		if (drag?.id !== event.pointerId) return;
		const point = localPoint(event.clientX, event.clientY);
		viewport.scrollLeft = drag.left + drag.start.x - point.x;
		viewport.scrollTop = drag.top + drag.start.y - point.y;
	});
	viewport.addEventListener("lostpointercapture", () => { drag = undefined; });
}
