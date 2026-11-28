let searchAbortController = new AbortController(); // Create a controller
let searchTimeout = null;
let currentSong = null;
let lastSearchResults = [];
let chordSheetResizeObserver;

function getAdminModule() {
	if (typeof window === "undefined") {
		return null;
	}

	return window.songbookAdmin || null;
}

function isAdminView() {
	return typeof document !== "undefined"
		&& document.body
		&& document.body.classList
		&& document.body.classList.contains("admin-view");
}

// functions related to song search
function songSearchUrl(query) {
	const path = isAdminView() ? "/admin/search.php" : "/search.php";
	return path + "?q=" + encodeURIComponent(query);
}

function searchSongs(inputquery = null) {
	clearTimeout(searchTimeout); // Reset timeout on every keypress

	let query = inputquery == null ? document.getElementById("searchBox").value.trim() : inputquery;
	if (query === "") {
		query = "*";
	}

	if (query === "*") {
		performSearch(query);
	}
	else {
		searchTimeout = setTimeout(() => {
			performSearch(query); // Call the async function inside setTimeout
		}, 200); // Trigger search 100ms after typing stops
	}
}

async function performSearch(query) {
	// Abort the previous fetch request
	searchAbortController.abort();

	// Create a new AbortController for the current request
	searchAbortController = new AbortController();
	const signal = searchAbortController.signal;

	try {
		const requestBody = new URLSearchParams();
		if (isAdminView()) {
			requestBody.set("include_hidden", "1");
		}

		let response = await fetch(songSearchUrl(query), {
			method: "POST",
			body: requestBody,
			signal
		});
		let songs = await response.json();
		displayResult(songs);
	}
	catch (err) {
		if (err.name === "AbortError") {
			console.log("Previous request aborted");
		}
		else {
			console.error("Fetch error:", err);
		}
	}
}

function displayResult(songs) {
	lastSearchResults = Array.isArray(songs) ? songs : [];
	let displaySongs = lastSearchResults;
	const hasCurrentSong = currentSong && lastSearchResults.some(song => song.id === currentSong.id);

	if (currentSong && !hasCurrentSong) {
		displaySongs = [currentSong, ...lastSearchResults];
	}

	let tableHTML = "";
	displaySongs.forEach((song, index) => {
		const adminModule = getAdminModule();
		const adminActions = adminModule && typeof adminModule.renderActionButtons === "function"
			? adminModule.renderActionButtons(song)
			: "";
		const isCurrentSong = currentSong && song.id === currentSong.id;
		const hasChordMarkup = Number(song.has_chordmarkup) === 1;
		const language = texts[userLang];

		tableHTML += `
                        <tr class="${isCurrentSong ? "playing-row" : ""}">
                                <td><a href="/?q=${song.id}" class="unformatted-link" target="_blank">${song.id}</a></td>
                                <td>${song.title}</td>
                                <td class="author-col">${formatAuthors(song.author || "")}</td>
                                <td class="actions">
                                        <div class="actions-container">
                                                ${adminActions}
                                                <img src="/img/text2.png" alt="View Lyrics" class="icon-btn" onclick="showLyrics('${song.id}', '${song.title}')">
                                                ${song.tabfilename || hasChordMarkup ? `
                                                    <button type="button" class="chord-sheet-button" data-chord-index="${index}" aria-label="${language.viewChords}">
                                                        <img src="/img/chords2.png" alt="" class="icon-btn">
                                                    </button>` : ""}
								${song.mp3filename ? `
									${isCurrentSong
										? `<img src="/img/stop2.svg" alt="Stop Audio" class="icon-btn" onclick="stopAudio()">`
										: `<img src="/img/play2.png" alt="Play Audio" class="icon-btn"
										     onclick="playAudio('${song.mp3filename}',
						                        '${song.mp3filename2 ? song.mp3filename2 : ''}', 
													'${song.id}', 
						                        '${song.title}', 
						                        '${song.author ? song.author : ''}', 
						                        '${song.tabfilename ? song.tabfilename.replace('.txt', '.jpg') : ''}',
						                        ${hasChordMarkup})">`}
						` : ""}
					</div>
				</td>
			</tr>
		`;
	});

	const results = document.getElementById("results");
	results.innerHTML = tableHTML;
	results.querySelectorAll("[data-chord-index]").forEach(button => {
		button.addEventListener("click", () => {
			showChords(displaySongs[Number(button.dataset.chordIndex)]);
		});
	});
}

function toggleClearButton() {
	const searchBox = document.getElementById("searchBox");
	const clearButton = document.getElementById("clearButton");

	if (!clearButton || !searchBox) {
		return;
	}

	clearButton.classList.add("visible"); // Button always visible

	const hasValue = searchBox.value.length > 0;
	const adminModule = getAdminModule();

	if (adminModule && typeof adminModule.updateClearButton === "function") {
		const handled = adminModule.updateClearButton(clearButton, hasValue);
		if (handled) {
			return;
		}
	}

	if (hasValue) {
		clearButton.textContent = "✕";
		clearButton.onclick = clearSearch;
		clearButton.title = "Clear search";
		clearButton.setAttribute("aria-label", "Clear search");
	} else {
		clearButton.textContent = "\uD83D\uDD00"; // Shuffle icon
		clearButton.onclick = shuffleSongs;
		clearButton.title = "Shuffle songs";
		clearButton.setAttribute("aria-label", "Shuffle songs");
	}
}

function clearSearch() {
	let searchBox = document.getElementById("searchBox");
	searchBox.value = ""; // Clear input field
	toggleClearButton();
	document.getElementById("searchBox").focus();
	searchSongs(); // Trigger search update
}

function shuffleSongs() {
	clearTimeout(searchTimeout);
	performSearch("@@shuffle@@");
}


function parseAuthorParts(authorStr) {
        if (!authorStr || !authorStr.trim()) {
                return [];
        }

        const parts = authorStr.split(',');

        return parts.map((rawPart, index) => {
                let trimmed = rawPart.trim();
                let useAmpersand = false;

                if (trimmed.endsWith('&')) {
                        useAmpersand = true;
                        trimmed = trimmed.slice(0, -1).trimEnd();
                }

                return {
                        value: trimmed,
                        separator: index < parts.length - 1 ? (useAmpersand ? ' & ' : ', ') : ''
                };
        });
}

if (typeof window !== "undefined") {
        window.parseAuthorParts = parseAuthorParts;
}

function formatAuthors(authorStr) {
        const authorParts = parseAuthorParts(authorStr);

        return authorParts
                .map((part) => {
                        const urlMatch = part.value.match(/^(.+?)\s*\[(https?:\/\/)?([^\]]+)\]$/);
                        if (urlMatch) {
                                const name = urlMatch[1].trim();
                                const url = (urlMatch[2] ? urlMatch[2] : "https://") + urlMatch[3].trim();
                                return `<a href="${url}" target="_blank" rel="noopener noreferrer">${name}</a>${part.separator}`;
                        }

                        // Just a name
                        return `${part.value}${part.separator}`;
                })
                .join('');
}

// functions related to audio popup

function playAudio(mp3filename1, mp3filename2 = "", id = "", title = "", author = "", imageFilename = "", hasChordMarkup = false) {
	if (!mp3filename1) return;

	currentSong = {
		id,
		title,
		author,
		tabfilename: imageFilename,
		has_chordmarkup: hasChordMarkup,
		mp3filename: mp3filename1,
		mp3filename2: mp3filename2 || "",
		mp3files: [mp3filename1, mp3filename2].filter(Boolean),
		currentIndex: 0
	};

	renderAudioPlayer();
	displayResult(lastSearchResults);
}

function renderAudioPlayer() {
	const audioSection = document.getElementById("audio-player-section");
	const audioPlayer = document.getElementById("audio-player");

	if (!audioSection || !audioPlayer) {
		return;
	}

	if (!currentSong) {
		audioSection.classList.add("hidden");
		audioPlayer.innerHTML = "";
		return;
	}

	const { mp3files, currentIndex } = currentSong;
	const audioFilename = mp3files[currentIndex];
	const isNarrowScreen = window.matchMedia("(max-width: 600px)").matches;
	const controlsList = isNarrowScreen ? 'controlslist="nodownload noplaybackrate noremoteplayback"' : 'controlslist="noremoteplayback"';

	let audioHTML = `
        <div class="audio-player-row">
            <audio id="audio-player-element" controls autoplay ${controlsList} disablepictureinpicture>
                <source src="/audio/songs/${encodeURIComponent(audioFilename)}" type="audio/mpeg">
                Your browser does not support the audio element.
            </audio>
            <div class="audio-player-controls">
    `;
	if (mp3files.length > 1) {
		audioHTML += `
			<button class="audio-control-btn" id="next-track-btn" onclick="playNextTrack()" aria-label="Next track">
				<img src="/img/next2.svg" alt="Next Track">
			</button>
		`;
	}
	audioHTML += `
				<button class="audio-control-btn" id="stop-track-btn" onclick="stopAudio()" aria-label="Stop">
					<img src="/img/stop2.svg" alt="Stop">
				</button>
            </div>
        </div>
    `;

	audioPlayer.innerHTML = audioHTML;
	audioSection.classList.remove("hidden");

	setTimeout(() => {
		const audioElement = document.getElementById("audio-player-element");
		if (audioElement) {
			audioElement.play();
		}
	}, 100);
}

function playNextTrack() {
	if (!currentSong || currentSong.mp3files.length < 2) {
		return;
	}

	currentSong.currentIndex = (currentSong.currentIndex + 1) % currentSong.mp3files.length;
	renderAudioPlayer();
}

function stopAudio() {
	const audioElement = document.getElementById("audio-player-element");
	if (audioElement) {
		audioElement.pause();
		audioElement.currentTime = 0;
	}

	currentSong = null;
	renderAudioPlayer();
	displayResult(lastSearchResults);
}

let hideControlsTimeout; // Store timeout globally

function showLyrics(id, title, popupid = 'popup') {
	fetch("/search.php?q=" + encodeURIComponent(id))
		.then(response => response.json())
		.then(songs => {
			if (!songs || songs.length === 0 || !songs[0].lyrics) {
				document.getElementById(popupid + "-body").innerHTML = `<h2>${title}</h2><p>No lyrics available.</p>`;
			} else {
				let songLyrics = songs[0].lyrics.replace(/\|/g, '').replace(/\n/g, '<br>');
				if (songs[0].lyrics_short && isMobileLandscape()) {
					songLyrics = songs[0].lyrics_short.replace(/\|/g, '').replace(/\n/g, '<br>');
				}


				let meaningsButton = '';
				if (songs[0].meanings.length > 0) {
					meaningsButton = `
						<div class="control-btn-grp">
					        <button class="control-btn" id="show-meanings-btn" onclick='showMeanings(${JSON.stringify(songs[0].meanings)})'>
					            <svg width="24" height="24" viewBox="0 0 50 50" fill="black" xmlns="http://www.w3.org/2000/svg">
					                <path d="M 25 2 C 12.309295 2 2 12.309295 2 25 C 2 37.690705 12.309295 48 25 48 C 37.690705 48 48 37.690705 48 25 C 48 12.309295 37.690705 2 25 2 z M 25 4 C 36.609824 4 46 13.390176 46 25 C 46 36.609824 36.609824 46 25 46 C 13.390176 46 4 36.609824 4 25 C 4 13.390176 13.390176 4 25 4 z M 25 11 A 3 3 0 0 0 22 14 A 3 3 0 0 0 25 17 A 3 3 0 0 0 28 14 A 3 3 0 0 0 25 11 z M 21 21 L 21 23 L 22 23 L 23 23 L 23 36 L 22 36 L 21 36 L 21 38 L 22 38 L 23 38 L 27 38 L 28 38 L 29 38 L 29 36 L 28 36 L 27 36 L 27 21 L 26 21 L 22 21 L 21 21 z"/>
					            </svg>
					        </button>
						</div>
				    `;
				}

				document.getElementById(popupid + "-body").innerHTML = `
                    <div class="lyrics-popup-content">
                        <div class="font-controls" id="font-controls">
							<div class="control-btn-grp">
	                            <button class="control-btn" onclick="adjustFontSize(0.8696)">A-</button>
	                            <button class="control-btn" onclick="adjustFontSize(1.15)">A+</button>
							</div>
							<div class="control-btn-grp">
								<button class="control-btn" onclick="adjustLineSpacing(-0.1)">
								    <svg width="24" height="24" viewBox="0 0 24 24" fill="black" xmlns="http://www.w3.org/2000/svg">
								        <path d="M12 10L8 4H16L12 10Z M12 14L16 20H8L12 14Z"/>
								    </svg>
								</button>
								<button class="control-btn" onclick="adjustLineSpacing(0.1)">
								    <svg width="24" height="24" viewBox="0 0 24 24" fill="black" xmlns="http://www.w3.org/2000/svg">
								        <path d="M12 3L8 9H16L12 3Z M12 21L8 15H16L12 21Z"/>
								    </svg>
								</button>
							</div>
							<div class="control-btn-grp">
								<button class="control-btn" id="toggle-align-btn" onclick="toggleTextAlignment()">
								    <svg id="toggle-align-icon" width="24" height="24" viewBox="0 0 24 24" fill="black" xmlns="http://www.w3.org/2000/svg">
								        <path d="M7 6h10M5 12h14M8 18h8" stroke="var(--text-color)" stroke-width="2" stroke-linecap="round"/>
								    </svg>
								</button>
							</div>
							${meaningsButton}
                        </div>
                        <div class="lyrics-content" id="lyrics-content">${songLyrics}</div>
                    </div>
                `;

				ensureTextPositioning();
				setupFontControls(popupid);
				if (isMobileLandscape()) {
					toggleTextAlignment();
				}
			}

			document.getElementById(popupid).style.display = "flex";
			document.body.classList.add("no-scroll"); // Disable main page scrolling
			currentLineHeight = 1.5;
			enterFullscreen();
		});
}

function showMeanings(meanings) {
	console.log(meanings);
	let content = '';

	meanings.forEach(meaning => {
		content += `<h3>${meaning.title}</h3>`;
		content += `<p>${meaning.meaning.replace(/\n/g, '<br>')}</p>`;
	});

	document.getElementById('modal-content').innerHTML = content;
	document.getElementById('modal-main').style.display = 'block';
}

function setupFontControls(popupid) {
	let fontControls = document.getElementById("font-controls");
	let lyricsContent = document.getElementById("lyrics-content");

	if (!fontControls || !lyricsContent) return;

	// Function to hide font controls
	function hideControls() {
		fontControls.classList.add("hidden");
	}

	// Function to show controls & restart the hide timer
	function showControls() {
		fontControls.classList.remove("hidden");

		// Restart the hide timer
		clearTimeout(hideControlsTimeout);
		hideControlsTimeout = setTimeout(hideControls, 4000);
	}

	// Auto-hide font controls after 4 seconds
	hideControlsTimeout = setTimeout(hideControls, 4000);

	// Show controls & reset timer when clicking anywhere in popup (buttons included)
	document.getElementById(popupid + "-body").addEventListener("click", showControls);
}

function adjustFontSize(change) {
	let lyricsContent = document.getElementById("lyrics-content");
	if (!lyricsContent) return;

	let currentSize = parseFloat(window.getComputedStyle(lyricsContent).fontSize);
	let newSize = Math.max(10, Math.round(currentSize * change));
	lyricsContent.style.fontSize = newSize + "px";

	ensureTextPositioning(); // Recheck positioning after resizing
}

let currentLineHeight = 1.5; // Explicitly track line height

function adjustLineSpacing(change) {
	let lyricsContent = document.getElementById("lyrics-content");
	if (!lyricsContent) return;

	currentLineHeight = Math.max(1, currentLineHeight + change); // Prevent going below 1.0
	lyricsContent.style.lineHeight = currentLineHeight.toFixed(2); // Apply the new value

	ensureTextPositioning();
}

function adjustTextSize() {
	let lyricsContent = document.getElementById("lyrics-content");
	if (!lyricsContent) return;

	let screenWidth = window.innerWidth;
	let screenHeight = window.innerHeight;
	let baseSize = Math.min(screenWidth, screenHeight) * 0.05;

	lyricsContent.style.fontSize = `${Math.max(baseSize, 16)}px`;
}

function ensureTextPositioning() {
	let lyricsContent = document.getElementById("lyrics-content");
	if (!lyricsContent) return;

	// If the lyrics content is shorter than 80% of the screen, center it
	if (lyricsContent.scrollHeight < window.innerHeight) {
		lyricsContent.style.margin = "auto 0"; // Center vertically
	} else {
		lyricsContent.style.margin = "0"; // Keep text at the top
	}
}

function toggleTextAlignment() {
	let lyricsContent = document.getElementById("lyrics-content");
	let toggleIcon = document.getElementById("toggle-align-icon");

	if (!lyricsContent || !toggleIcon) return;

	if (lyricsContent.classList.contains("text-centered")) {
		lyricsContent.classList.remove("text-centered");
		lyricsContent.style.textAlign = "left";

		// Switch to "left-aligned" text icon
		toggleIcon.innerHTML = '<path d="M7 6h10M5 12h14M8 18h8" stroke="var(--text-color)" stroke-width="2" stroke-linecap="round"/>';
	} else {
		lyricsContent.classList.add("text-centered");
		lyricsContent.style.textAlign = "center";

		// Switch to "centered text" icon
		toggleIcon.innerHTML = '<path d="M4 6h10M4 12h14M4 18h8" stroke="var(--text-color)" stroke-width="2" stroke-linecap="round"/>';
	}
}

function autoRotateAndSizeImage() {
	const img = document.getElementById("popup-image");
	if (!img) return;

	// Determine if we need to rotate
	let isPortrait = window.innerHeight > window.innerWidth;
	let rotation = isPortrait ? 90 : 0;

	// Apply rotation first
	img.style.transform = `rotate(${rotation}deg)`;
	img.dataset.rotation = rotation; // Store rotation for touch interactions

	// Now apply correct sizing AFTER rotation
	setTimeout(() => {
		if (rotation === 90) {
			img.style.width = "100vh";  // Height becomes new width
			img.style.height = "100vw"; // Width becomes new height
		} else {
			img.style.width = "100vw";
			img.style.height = "100vh";
		}
		img.style.objectFit = "contain"; // Maintain aspect ratio
	}, 100);
}

async function showChords(song) {
	if (Number(song.has_chordmarkup) !== 1) {
		showImage(song.tabfilename);
		return;
	}

	const popup = document.getElementById("popup");
	const body = document.getElementById("popup-body");
	const status = document.createElement("p");
	status.setAttribute("role", "status");
	status.textContent = texts[userLang].chordsLoading;
	body.replaceChildren(status);
	popup.style.display = "flex";
	document.body.classList.add("no-scroll");
	enterFullscreen();

	try {
		const response = await fetch(songSearchUrl(song.id), { cache: "no-store" });
		if (!response.ok) throw new Error("Chord sheet request failed");
		const songs = await response.json();
		if (!status.isConnected) return;
		const sheet = JSON.parse(songs[0]?.chordmarkup);
		const viewport = renderChordSheet(sheet);
		body.replaceChildren(viewport);
		fitChordSheet(popup, viewport);
		viewport.focus({ preventScroll: true });
	} catch (error) {
		if (!status.isConnected) return;
		if (song.tabfilename) {
			showImage(song.tabfilename);
		} else {
			status.textContent = texts[userLang].chordsUnavailable;
		}
	}
}

function renderChordSheet(sheet) {
	const lineKinds = ["chords", "lyrics", "note", "pitch"];
	const runTones = ["alternate", "chords", "comment", "detail", "note", "pitch"];
	if (sheet?.enabled === false || sheet?.version !== 1 || typeof sheet.title !== "string" || !Array.isArray(sheet.lines)
		|| !Array.isArray(sheet.details) || !sheet.lines.some(line =>
			line?.kind === "chords" && (line.runs?.some(run => run?.text?.trim()) || line.aside?.trim()))) {
		throw new Error("Invalid chord sheet");
	}

	const viewport = document.createElement("div");
	viewport.className = "chord-sheet-viewport";
	viewport.tabIndex = 0;
	if (isAdminView() && sheet.lines.some((line, index) => line?.kind === "note"
		&& sheet.lines[index - 1]?.kind === "note" && typeof line.paragraphStart !== "boolean")) {
		const notice = document.createElement("p");
		notice.className = "chord-sheet-import-notice";
		notice.setAttribute("role", "status");
		notice.textContent = texts[userLang].chordParagraphsMissing;
		viewport.append(notice);
	}
	const article = document.createElement("article");
	article.className = "chord-sheet";
	article.append(renderChordHeading(sheet));

	for (const [index, line] of sheet.lines.entries()) {
		if (!lineKinds.includes(line.kind) || !Array.isArray(line.runs)) throw new Error("Invalid chord row");
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
			if (typeof run.text !== "string") throw new Error("Invalid chord text");
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

function fitChordSheet(popup, viewport) {
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
		const sideways = wide && popup.clientWidth < popup.clientHeight;
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
	chordSheetResizeObserver?.disconnect();
	chordSheetResizeObserver = new ResizeObserver(fit);
	chordSheetResizeObserver.observe(popup);
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

function showImage(tabfilename, popupid = 'popup') {
	if (!tabfilename) return;

	document.getElementById(popupid + "-body").innerHTML = `
         <img id="popup-image" src="/img/songs/${encodeURIComponent(tabfilename)}">
     `;
	document.getElementById(popupid).style.display = "flex";
	document.body.classList.add("no-scroll"); // Disable main page scrolling
	enterFullscreen();

	// Allow image to load first, then apply rotation, resizing, and touch interactions
	setTimeout(() => {
		autoRotateAndSizeImage();
		enableImageInteractions();
	}, 50);
}

function enableImageInteractions() {
	const img = document.getElementById("popup-image");
	if (!img) return;
	let scale = 1; // Default zoom level
	let lastTouchDistance = 0;
	let isDragging = false;
	let startX = 0, startY = 0, translateX = 0, translateY = 0;

	// Store rotation angle (preserved during zoom & pan)
	let rotation = img.style.transform.includes("rotate(90deg)") ? 90 : 0;
	img.dataset.rotation = rotation; // Save rotation to dataset

	img.addEventListener("touchstart", (e) => {
		if (e.touches.length === 2) {
			lastTouchDistance = getTouchDistance(e.touches);
		} else if (e.touches.length === 1) {
			isDragging = true;
			startX = e.touches[0].clientX - translateX;
			startY = e.touches[0].clientY - translateY;
		}
	});

	img.addEventListener("touchmove", (e) => {
		if (e.touches.length === 2) {
			// Handle pinch-to-zoom
			e.preventDefault();
			const newDistance = getTouchDistance(e.touches);
			scale *= newDistance / lastTouchDistance;
			lastTouchDistance = newDistance;
		} else if (e.touches.length === 1 && isDragging) {
			// Handle panning (dragging)
			e.preventDefault();
			translateX = e.touches[0].clientX - startX;
			translateY = e.touches[0].clientY - startY;
		}

		// Apply transform with preserved rotation
		img.style.transform = `translate(${translateX}px, ${translateY}px) scale(${scale}) rotate(${rotation}deg)`;
	});

	img.addEventListener("touchend", () => {
		isDragging = false;
	});

	function getTouchDistance(touches) {
		const dx = touches[0].clientX - touches[1].clientX;
		const dy = touches[0].clientY - touches[1].clientY;
		return Math.sqrt(dx * dx + dy * dy);
	}
}

function enterFullscreen() {
	if (document.fullscreenElement) return;
	let elem = document.documentElement; // Fullscreen for the entire document

	if (elem.requestFullscreen) {
		elem.requestFullscreen().catch(() => {});
	} else if (elem.mozRequestFullScreen) { // Firefox
		elem.mozRequestFullScreen();
	} else if (elem.webkitRequestFullscreen) { // Chrome, Safari, Edge
		elem.webkitRequestFullscreen();
	} else if (elem.msRequestFullscreen) { // IE/Edge
		elem.msRequestFullscreen();
	}
}

function closePopup(id) {
	document.getElementById(id).style.display = "none";
	document.getElementById(id + "-body").innerHTML = "";

	if (id == "popup") {
		chordSheetResizeObserver?.disconnect();
		chordSheetResizeObserver = undefined;
		document.body.classList.remove("no-scroll"); // Re-enable main page scrolling after closing last popup
	}

	// Exit fullscreen mode when closing
	exitFullscreen();
}

function exitFullscreen() {
	if (document.fullscreenElement || document.mozFullScreenElement || document.webkitFullscreenElement) {
		if (document.exitFullscreen) {
			document.exitFullscreen();
		} else if (document.mozCancelFullScreen) { // Firefox
			document.mozCancelFullScreen();
		} else if (document.webkitExitFullscreen) { // Chrome, Safari, Edge
			document.webkitExitFullscreen();
		} else if (document.msExitFullscreen) { // IE/Edge
			document.msExitFullscreen();
		}
	}
}

function isMobileLandscape() {
	// Check if the device is mobile
	const isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

	// Check if the orientation is landscape
	const isLandscape = window.matchMedia("(orientation: landscape)").matches;

	return isMobile && isLandscape;
}

// Close pop-up when pressing Escape key
document.addEventListener("keydown", function(event) {
	if (event.key === "Escape") {
		const openPopup = ["popup2", "popup"].find(id => document.getElementById(id).style.display !== "none");
		if (openPopup) closePopup(openPopup);
	}
});

// Function to get URL parameters
function getQueryParam(param) {
	let urlParams = new URLSearchParams(window.location.search);
	return urlParams.get(param) || "";
}

document.addEventListener("DOMContentLoaded", function() {
	let query = getQueryParam("q"); // Get search term from URL
	if (query) {
		document.getElementById("searchBox").value = query; // Set search box value
		toggleClearButton();
		performSearch(query); // Run search with parameter
	} else {
		toggleClearButton();
		performSearch("*"); // Default: Load full list
	}
});


// Define the text for English and German versions.
const texts = {
	en: {
		title: "Title",
		author: "Author(s)",
		actions: "Actions",
		impressum: "Imprint",
		searchBox: "Search...",
		heilsamelieder: "Circle Songs",
		viewChords: "View chords",
		chordParagraphsMissing: "This import has no paragraph markers for some additional text. Reimport the song from Word to restore the spacing.",
		chordsLoading: "Loading chords…",
		chordsUnavailable: "Chords are unavailable."
	},
	de: {
		title: "Titel",
		author: "Urheber:innen",
		actions: "Aktionen",
		impressum: "Impressum",
		searchBox: "Suchen...",
		heilsamelieder: "Heilsame Lieder",
		viewChords: "Akkorde anzeigen",
		chordParagraphsMissing: "In diesem Import fehlen Absatzmarken für Zusatztext. Importiere das Lied erneut aus Word, um die Abstände wiederherzustellen.",
		chordsLoading: "Akkorde werden geladen …",
		chordsUnavailable: "Akkorde sind nicht verfügbar."
	}
};

// Detect the browser language; default to English if not German.
const userLang = navigator.language.slice(0, 2) === 'de' ? 'de' : 'en';

// Update text content for each element based on the detected language.
document.getElementById("title").textContent = texts[userLang].title;
document.getElementById("author").textContent = texts[userLang].author;
document.getElementById("actions").textContent = texts[userLang].actions;
document.getElementById("impressum-link").textContent = texts[userLang].impressum;
document.getElementById("searchBox").placeholder = texts[userLang].searchBox;
document.getElementById("heilsame-lieder").textContent = texts[userLang].heilsamelieder;
document.title = texts[userLang].heilsamelieder;

// handle modal impressum

// Get the modal and related elements
const modal = document.getElementById('modal-main');
const openImpressumLink = document.getElementById('impressum-link');
const closeModalBtn = document.getElementById('close-modal');
const impressumContent = document.getElementById('modal-content');

function hideModal() {
	if (!modal) {
		return;
	}

	modal.style.display = 'none';

	if (impressumContent) {
		impressumContent.innerHTML = '';
	}

	if (modal.dataset) {
		delete modal.dataset.mode;
	}
}

// When the link is clicked, load the impressum.html file and display the modal
if (openImpressumLink) {
	openImpressumLink.addEventListener('click', function(event) {
		event.preventDefault();
		// Fetch the external impressum.html content
		fetch('/impressum.html')
			.then(response => {
				if (!response.ok) {
					throw new Error('Network response was not ok');
				}
				return response.text();
			})
			.then(data => {
				if (!impressumContent || !modal) {
					return;
				}
				// Insert the fetched content into the modal
				impressumContent.innerHTML = data;
				modal.dataset.mode = 'impressum';
				// Display the modal
				modal.style.display = 'block';
			})
			.catch(error => {
				if (impressumContent && modal) {
					impressumContent.innerHTML = '<p>Error loading content.</p>';
					modal.dataset.mode = 'impressum';
					modal.style.display = 'block';
				}
				console.error('Error fetching Impressum:', error);
			});
	});
}

// When the close button is clicked, hide the modal
if (closeModalBtn) {
	closeModalBtn.addEventListener('click', hideModal);
}

// Also hide the modal if the user clicks outside of the modal content
window.addEventListener('click', function(event) {
	if (modal && event.target === modal) {
		hideModal();
	}
});

let userAgent = navigator.userAgent || navigator.vendor || window.opera;
let linkElement = document.getElementById("androidapp-link");

if (/Android/i.test(userAgent)) {
	linkElement.style.display = "block"; // Show link
} else {
	linkElement.style.display = "none"; // Hide link
}

if (document.getElementById("clearButton")) {
	toggleClearButton();
}

if (typeof window !== "undefined") {
	window.songbookReady = true;

	if (typeof document !== "undefined") {
		let readyEvent = null;

		if (typeof window.CustomEvent === "function") {
			readyEvent = new CustomEvent("songbook:ready");
		} else if (document.createEvent) {
			readyEvent = document.createEvent("Event");
			readyEvent.initEvent("songbook:ready", true, true);
		}

		if (readyEvent) {
			document.dispatchEvent(readyEvent);
		}
	}
}
