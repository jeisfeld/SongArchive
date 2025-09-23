<?php
header ( 'Content-Type: text/html; charset=UTF-8' );

require_once __DIR__ . '/../db_config.php';
function nullIfEmpty($value) {
	if ($value === null) {
		return null;
	}

	return trim ( $value ) === '' ? null : $value;
}
function fetchSong($conn, $songId) {
	$sql = "SELECT id, title, lyrics, lyrics_short, lyrics_paged, tabfilename, mp3filename, mp3filename2, author, keywords FROM songs WHERE id = ?";
	$stmt = $conn->prepare ( $sql );

	if (! $stmt) {
		error_log ( 'Failed to prepare statement for fetching song: ' . $conn->error );
		return null;
	}

	$stmt->bind_param ( 's', $songId );
	$stmt->execute ();
	$result = $stmt->get_result ();

	if (! $result) {
		error_log ( 'Failed to fetch song result: ' . $stmt->error );
		$stmt->close ();
		return null;
	}

	$song = $result->fetch_assoc ();
	$stmt->close ();

	return $song ?: null;
}
function fetchAllMeanings($conn) {
	$sql = "SELECT id, title, meaning FROM meaning ORDER BY title, id";
	$meanings = [ ];

	$result = $conn->query ( $sql );

	if ($result === false) {
		error_log ( 'Failed to fetch meanings: ' . $conn->error );
		return $meanings;
	}

	while ( $row = $result->fetch_assoc () ) {
		if (! isset ( $row ['id'] )) {
			continue;
		}

		$meaningId = ( int ) $row ['id'];

		$meanings [] = [ 
				'id' => $meaningId,
				'title' => $row ['title'] ?? '',
				'meaning' => $row ['meaning'] ?? ''
		];
	}

	$result->free ();

	return $meanings;
}
function fetchMeaningById($conn, $meaningId) {
	$meaningId = ( int ) $meaningId;

	if ($meaningId <= 0) {
		return null;
	}

	$stmt = $conn->prepare ( 'SELECT id, title, meaning FROM meaning WHERE id = ?' );

	if (! $stmt) {
		error_log ( 'Failed to prepare statement for fetching meaning: ' . $conn->error );
		return null;
	}

	$stmt->bind_param ( 'i', $meaningId );

	if (! $stmt->execute ()) {
		error_log ( 'Failed to execute statement for fetching meaning: ' . $stmt->error );
		$stmt->close ();
		return null;
	}

	$result = $stmt->get_result ();
	if (! $result) {
		error_log ( 'Failed to fetch meaning result set: ' . $stmt->error );
		$stmt->close ();
		return null;
	}

	$row = $result->fetch_assoc ();
	$stmt->close ();

	if (! $row) {
		return null;
	}

	return [ 
			'id' => isset ( $row ['id'] ) ? ( int ) $row ['id'] : null,
			'title' => isset ( $row ['title'] ) ? ( string ) $row ['title'] : '',
			'meaning' => isset ( $row ['meaning'] ) ? ( string ) $row ['meaning'] : ''
	];
}
function createMeaning($conn, $title, $meaningText) {
	$stmt = $conn->prepare ( 'INSERT INTO meaning (title, meaning) VALUES (?, ?)' );

	if (! $stmt) {
		error_log ( 'Failed to prepare insert statement for meaning: ' . $conn->error );
		return null;
	}

	$titleValue = nullIfEmpty ( $title );
	$meaningValue = nullIfEmpty ( $meaningText );

	$stmt->bind_param ( 'ss', $titleValue, $meaningValue );

	if (! $stmt->execute ()) {
		error_log ( 'Failed to execute insert statement for meaning: ' . $stmt->error );
		$stmt->close ();
		return null;
	}

	$newId = ( int ) $stmt->insert_id;
	if ($newId <= 0) {
		$newId = ( int ) $conn->insert_id;
	}

	$stmt->close ();

	if ($newId <= 0) {
		return null;
	}

	return fetchMeaningById ( $conn, $newId );
}
function updateMeaning($conn, $meaningId, $title, $meaningText) {
	$meaningId = ( int ) $meaningId;

	if ($meaningId <= 0) {
		return null;
	}

	$stmt = $conn->prepare ( 'UPDATE meaning SET title = ?, meaning = ? WHERE id = ?' );

	if (! $stmt) {
		error_log ( 'Failed to prepare update statement for meaning: ' . $conn->error );
		return null;
	}

	$titleValue = nullIfEmpty ( $title );
	$meaningValue = nullIfEmpty ( $meaningText );
	$meaningIdParam = $meaningId;

	$stmt->bind_param ( 'ssi', $titleValue, $meaningValue, $meaningIdParam );

	if (! $stmt->execute ()) {
		error_log ( 'Failed to execute update statement for meaning: ' . $stmt->error );
		$stmt->close ();
		return null;
	}

	$stmt->close ();

	return fetchMeaningById ( $conn, $meaningId );
}
function fetchSongMeaningIds($conn, $songId) {
	$sql = "SELECT meaning_id FROM song_meaning WHERE song_id = ? ORDER BY meaning_id";
	$stmt = $conn->prepare ( $sql );

	if (! $stmt) {
		error_log ( 'Failed to prepare statement for fetching song meanings: ' . $conn->error );
		return [ ];
	}

	$stmt->bind_param ( 's', $songId );

	if (! $stmt->execute ()) {
		error_log ( 'Failed to execute song meaning statement: ' . $stmt->error );
		$stmt->close ();
		return [ ];
	}

	$result = $stmt->get_result ();

	if (! $result) {
		error_log ( 'Failed to fetch song meaning result set: ' . $stmt->error );
		$stmt->close ();
		return [ ];
	}

	$meaningIds = [ ];

	while ( $row = $result->fetch_assoc () ) {
		if (isset ( $row ['meaning_id'] )) {
			$meaningIds [] = ( int ) $row ['meaning_id'];
		}
	}

	$stmt->close ();

	return $meaningIds;
}
function fetchAllAuthors($conn) {
	$sql = "SELECT author FROM songs WHERE author IS NOT NULL AND TRIM(author) <> ''";
	$result = $conn->query ( $sql );

	if ($result === false) {
		error_log ( 'Failed to fetch authors: ' . $conn->error );
		return [ ];
	}

	$authors = [ ];

	while ( $row = $result->fetch_assoc () ) {
		$authorField = isset ( $row ['author'] ) ? ( string ) $row ['author'] : '';

		if ($authorField === '') {
			continue;
		}

		$normalizedAuthorField = str_replace ( [ 
				"\r",
				"\n",
				";"
		], ',', $authorField );
		$parts = explode ( ',', $normalizedAuthorField );

		foreach ( $parts as $part ) {
			$trimmed = trim ( $part );

			if ($trimmed === '') {
				continue;
			}

			$lowerKey = function_exists ( 'mb_strtolower' ) ? mb_strtolower ( $trimmed, 'UTF-8' ) : strtolower ( $trimmed );

			if (! array_key_exists ( $lowerKey, $authors )) {
				$authors [$lowerKey] = $trimmed;
			}
		}
	}

	$result->free ();

	if (empty ( $authors )) {
		return [ ];
	}

	$uniqueAuthors = array_values ( $authors );
	natcasesort ( $uniqueAuthors );

	return array_values ( $uniqueAuthors );
}
function parseMeaningIdsFromInput($input) {
	if ($input === null || $input === '') {
		return [ ];
	}

	$rawValues = [ ];

	if (is_array ( $input )) {
		$rawValues = $input;
	}
	else {
		$decoded = json_decode ( $input, true );
		if (json_last_error () === JSON_ERROR_NONE && is_array ( $decoded )) {
			$rawValues = $decoded;
		}
		else {
			$rawValues = explode ( ',', ( string ) $input );
		}
	}

	$meaningIds = [ ];

	foreach ( $rawValues as $value ) {
		$stringValue = trim ( ( string ) $value );
		if ($stringValue === '') {
			continue;
		}

		if (! ctype_digit ( $stringValue )) {
			continue;
		}

		$meaningId = ( int ) $stringValue;

		if (! in_array ( $meaningId, $meaningIds, true )) {
			$meaningIds [] = $meaningId;
		}
	}

	return $meaningIds;
}
function updateSongMeaningRelationships($conn, $songId, array $meaningIds) {
	$deleteStmt = $conn->prepare ( 'DELETE FROM song_meaning WHERE song_id = ?' );

	if (! $deleteStmt) {
		error_log ( 'Failed to prepare delete statement for song meanings: ' . $conn->error );
		return false;
	}

	$deleteStmt->bind_param ( 's', $songId );

	if (! $deleteStmt->execute ()) {
		error_log ( 'Failed to delete existing song meanings: ' . $deleteStmt->error );
		$deleteStmt->close ();
		return false;
	}

	$deleteStmt->close ();

	if (empty ( $meaningIds )) {
		return true;
	}

	$insertStmt = $conn->prepare ( 'INSERT INTO song_meaning (song_id, meaning_id) VALUES (?, ?)' );

	if (! $insertStmt) {
		error_log ( 'Failed to prepare insert statement for song meanings: ' . $conn->error );
		return false;
	}

	$songIdParam = $songId;
	$meaningIdParam = 0;
	$insertStmt->bind_param ( 'si', $songIdParam, $meaningIdParam );

	foreach ( $meaningIds as $meaningId ) {
		$meaningIdParam = ( int ) $meaningId;

		if (! $insertStmt->execute ()) {
			error_log ( 'Failed to insert song meaning relation: ' . $insertStmt->error );
			$insertStmt->close ();
			return false;
		}
	}

	$insertStmt->close ();

	return true;
}

if ($_SERVER ['REQUEST_METHOD'] === 'POST' && isset ( $_POST ['meaning_action'] )) {
	$meaningAction = trim ( ( string ) $_POST ['meaning_action'] );
	$response = [ 
			'success' => false,
			'message' => '',
			'meaning' => null
	];

	if ($meaningAction === 'create') {
		$titleInput = isset ( $_POST ['title'] ) ? trim ( ( string ) $_POST ['title'] ) : '';
		$meaningInput = isset ( $_POST ['meaning'] ) ? trim ( ( string ) $_POST ['meaning'] ) : '';

		if ($titleInput === '' && $meaningInput === '') {
			$response ['message'] = 'Please provide a title or meaning text.';
		}
		else {
			$meaningRecord = createMeaning ( $conn, $titleInput, $meaningInput );

			if ($meaningRecord) {
				$response ['success'] = true;
				$response ['meaning'] = $meaningRecord;
				$response ['message'] = 'Meaning created successfully.';
			}
			else {
				$response ['message'] = 'Unable to create the meaning. Please try again.';
			}
		}
	}
	elseif ($meaningAction === 'update') {
		$meaningIdInput = isset ( $_POST ['meaning_id'] ) ? ( int ) $_POST ['meaning_id'] : 0;
		$titleInput = isset ( $_POST ['title'] ) ? trim ( ( string ) $_POST ['title'] ) : '';
		$meaningInput = isset ( $_POST ['meaning'] ) ? trim ( ( string ) $_POST ['meaning'] ) : '';

		if ($meaningIdInput <= 0) {
			$response ['message'] = 'Invalid meaning identifier provided.';
		}
		elseif ($titleInput === '' && $meaningInput === '') {
			$response ['message'] = 'Please provide a title or meaning text.';
		}
		else {
			$meaningRecord = updateMeaning ( $conn, $meaningIdInput, $titleInput, $meaningInput );

			if ($meaningRecord) {
				$response ['success'] = true;
				$response ['meaning'] = $meaningRecord;
				$response ['message'] = 'Meaning updated successfully.';
			}
			else {
				$response ['message'] = 'Unable to update the meaning. Please try again.';
			}
		}
	}
	else {
		$response ['message'] = 'Unsupported meaning action requested.';
	}

	header ( 'Content-Type: application/json; charset=UTF-8' );
	$jsonOptions = JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES;
	$jsonPayload = json_encode ( $response, $jsonOptions );

	if ($jsonPayload === false) {
		$jsonPayload = json_encode ( [ 
				'success' => false,
				'message' => 'Failed to encode response payload.'
		] );
	}

	echo $jsonPayload;
	$conn->close ();
	exit ();
}
function escapeHtml($value) {
	return htmlspecialchars ( ( string ) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8' );
}
function buildFilenameSuggestions($directory, array $priorityPrefixes = [ ]) {
	$suggestions = [ 
			'options' => [ ],
			'matching' => [ ],
			'directoryExists' => is_dir ( $directory )
	];

	if (! $suggestions ['directoryExists']) {
		return $suggestions;
	}

	$files = scandir ( $directory );
	if ($files === false) {
		return $suggestions;
	}

	$options = [ ];
	foreach ( $files as $file ) {
		if ($file === '.' || $file === '..') {
			continue;
		}

		$fullPath = $directory . DIRECTORY_SEPARATOR . $file;
		if (is_file ( $fullPath )) {
			$options [] = $file;
		}
	}

	if (empty ( $options )) {
		return $suggestions;
	}

	natcasesort ( $options );
	$options = array_values ( $options );

	$prefixes = [ ];
	foreach ( $priorityPrefixes as $prefix ) {
		$prefix = ( string ) $prefix;
		if ($prefix === '') {
			continue;
		}

		$prefixes [] = $prefix;
	}

	$prefixes = array_values ( array_unique ( $prefixes ) );

	$matching = [ ];
	if (! empty ( $prefixes )) {
		foreach ( $options as $filename ) {
			foreach ( $prefixes as $prefix ) {
				if (stripos ( $filename, $prefix ) === 0) {
					$matching [] = $filename;
					break;
				}
			}
		}

		$matching = array_values ( array_unique ( $matching ) );

		if (! empty ( $matching )) {
			$options = array_merge ( $matching, array_values ( array_diff ( $options, $matching ) ) );
		}
	}

	$suggestions ['options'] = $options;
	$suggestions ['matching'] = $matching;

	return $suggestions;
}
function renderFilenameNote(array $matching, array $options, $prefix, $directoryExists, $folderPath) {
	if (! $directoryExists) {
		echo "<div class=\"form-note\">Folder " . escapeHtml ( $folderPath ) . " is not available.</div>";
		return;
	}

	if (empty ( $options )) {
		echo "<div class=\"form-note\">No files found in " . escapeHtml ( $folderPath ) . ".</div>";
		return;
	}

	if ($prefix !== '' && empty ( $matching )) {
		echo "<div class=\"form-note\">No files found in " . escapeHtml ( $folderPath ) . " starting with " . escapeHtml ( $prefix ) . ".</div>";
	}
}

$id = isset ( $_GET ['id'] ) ? trim ( $_GET ['id'] ) : '';
$successMessage = '';
$errorMessage = '';
$updateSuccess = false;
$formValues = null;
$requestedMeaningIds = [ ];
$songMeaningIdsFromDatabase = [ ];
$allMeanings = [ ];

if ($_SERVER ['REQUEST_METHOD'] === 'POST') {
	$id = isset ( $_POST ['id'] ) ? trim ( $_POST ['id'] ) : $id;
	$title = isset ( $_POST ['title'] ) ? trim ( $_POST ['title'] ) : '';
	$lyrics = isset ( $_POST ['lyrics'] ) ? $_POST ['lyrics'] : '';
	$lyricsShortInput = isset ( $_POST ['lyrics_short'] ) ? $_POST ['lyrics_short'] : '';
	$lyricsPagedInput = isset ( $_POST ['lyrics_paged'] ) ? $_POST ['lyrics_paged'] : '';
	$tabfilenameInput = isset ( $_POST ['tabfilename'] ) ? trim ( $_POST ['tabfilename'] ) : '';
	$mp3filenameInput = isset ( $_POST ['mp3filename'] ) ? trim ( $_POST ['mp3filename'] ) : '';
	$mp3filename2Input = isset ( $_POST ['mp3filename2'] ) ? trim ( $_POST ['mp3filename2'] ) : '';
	$authorInput = isset ( $_POST ['author'] ) ? trim ( $_POST ['author'] ) : '';
	$keywordsInput = isset ( $_POST ['keywords'] ) ? trim ( $_POST ['keywords'] ) : '';

	$lyrics_short = nullIfEmpty ( $lyricsShortInput );
	$lyrics_paged = nullIfEmpty ( $lyricsPagedInput );
	$tabfilename = nullIfEmpty ( $tabfilenameInput );
	$mp3filename = nullIfEmpty ( $mp3filenameInput );
	$mp3filename2 = nullIfEmpty ( $mp3filename2Input );
	$author = nullIfEmpty ( $authorInput );
	$keywords = nullIfEmpty ( $keywordsInput );

	$meaningIdsInput = isset ( $_POST ['meaning_ids'] ) ? $_POST ['meaning_ids'] : '';
	$requestedMeaningIds = parseMeaningIdsFromInput ( $meaningIdsInput );

	$formValues = [ 
			'id' => $id,
			'title' => $title,
			'lyrics' => $lyrics,
			'lyrics_short' => $lyricsShortInput,
			'lyrics_paged' => $lyricsPagedInput,
			'tabfilename' => $tabfilenameInput,
			'mp3filename' => $mp3filenameInput,
			'mp3filename2' => $mp3filename2Input,
			'author' => $authorInput,
			'keywords' => $keywordsInput,
			'meaning_ids' => $requestedMeaningIds
	];

	if ($id === '') {
		$errorMessage = 'Missing song ID.';
	}
	else {
		$updateSql = "UPDATE songs SET title = ?, lyrics = ?, lyrics_short = ?, lyrics_paged = ?, tabfilename = ?, mp3filename = ?, mp3filename2 = ?, author = ?, keywords = ? WHERE id = ?";
		$updateStmt = $conn->prepare ( $updateSql );

		if (! $updateStmt) {
			error_log ( 'Failed to prepare update statement: ' . $conn->error );
			$errorMessage = 'Unable to update the song at this time.';
		}
		else {
			$updateStmt->bind_param ( 'ssssssssss', $title, $lyrics, $lyrics_short, $lyrics_paged, $tabfilename, $mp3filename, $mp3filename2, $author, $keywords, $id );

			$transactionStarted = $conn->begin_transaction ();
			if (! $transactionStarted) {
				error_log ( 'Failed to begin transaction for song update: ' . $conn->error );
			}

			if ($updateStmt->execute ()) {
				$relationshipsUpdated = updateSongMeaningRelationships ( $conn, $id, $requestedMeaningIds );

				if ($relationshipsUpdated) {
					if ($transactionStarted) {
						$conn->commit ();
					}

					$updateSuccess = true;
					$successMessage = 'Song updated successfully.';
				}
				else {
					if ($transactionStarted) {
						$conn->rollback ();
					}

					$successMessage = '';
					$errorMessage = 'Failed to update song meanings.';
				}
			}
			else {
				error_log ( 'Failed to execute update statement: ' . $updateStmt->error );

				if ($transactionStarted) {
					$conn->rollback ();
				}

				$errorMessage = 'Failed to update the song.';
			}

			$updateStmt->close ();
		}
	}
}

$songFromDatabase = null;
if ($id !== '') {
	$songFromDatabase = fetchSong ( $conn, $id );
	if (! $songFromDatabase && $errorMessage === '') {
		$errorMessage = 'Song not found.';
	}
	elseif ($songFromDatabase) {
		$songMeaningIdsFromDatabase = fetchSongMeaningIds ( $conn, $id );
	}
}
elseif ($errorMessage === '') {
	$errorMessage = 'No song ID provided.';
}

$allMeanings = fetchAllMeanings ( $conn );
$allAuthors = fetchAllAuthors ( $conn );

if ($updateSuccess && $songFromDatabase) {
	$songData = $songFromDatabase;
}
elseif ($formValues) {
	$songData = $formValues;
}
else {
	$songData = $songFromDatabase;
}

$selectedMeaningIdsForView = [ ];
if ($_SERVER ['REQUEST_METHOD'] === 'POST') {
	if ($updateSuccess) {
		$selectedMeaningIdsForView = $songMeaningIdsFromDatabase;
	}
	else {
		$selectedMeaningIdsForView = $requestedMeaningIds;
	}
}
else {
	$selectedMeaningIdsForView = $songMeaningIdsFromDatabase;
}

$selectedMeaningIdsForView = array_values ( array_map ( 'intval', $selectedMeaningIdsForView ) );

$conn->close ();

$meaningLookup = [ ];
foreach ( $allMeanings as $meaningEntry ) {
	if (! isset ( $meaningEntry ['id'] )) {
		continue;
	}

	$meaningLookup [( string ) $meaningEntry ['id']] = $meaningEntry;
}

$selectedMeaningDetails = [ ];
foreach ( $selectedMeaningIdsForView as $meaningId ) {
	$key = ( string ) $meaningId;

	if (isset ( $meaningLookup [$key] )) {
		$selectedMeaningDetails [] = $meaningLookup [$key];
	}
}

$selectedMeaningDetailsForDisplay = $selectedMeaningDetails;
usort ( $selectedMeaningDetailsForDisplay, function ($a, $b) {
	$titleA = isset ( $a ['title'] ) ? ( string ) $a ['title'] : '';
	$titleB = isset ( $b ['title'] ) ? ( string ) $b ['title'] : '';

	return strcasecmp ( $titleA, $titleB );
} );

$jsonEncodeOptions = JSON_HEX_TAG | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_HEX_AMP | JSON_UNESCAPED_UNICODE;

$selectedMeaningIdsJson = json_encode ( $selectedMeaningIdsForView, $jsonEncodeOptions );
if ($selectedMeaningIdsJson === false) {
	$selectedMeaningIdsJson = '[]';
}

$allMeaningsJson = json_encode ( $allMeanings, $jsonEncodeOptions );
if ($allMeaningsJson === false) {
	$allMeaningsJson = '[]';
}

$authorSuggestionsJson = json_encode ( $allAuthors, $jsonEncodeOptions );
if ($authorSuggestionsJson === false) {
	$authorSuggestionsJson = '[]';
}

$songImagesDirectory = __DIR__ . '/../img/songs';
$audioSongsDirectory = __DIR__ . '/../audio/songs';
$songIdForSuggestions = isset ( $songData ['id'] ) ? ( string ) $songData ['id'] : ( string ) $id;

$tabFilenamePrefix = '';
$tabFilenamePrefixes = [ ];
if ($songIdForSuggestions !== '') {
	$tabFilenamePrefix = substr ( $songIdForSuggestions, 0, 4 );
	if ($tabFilenamePrefix !== '') {
		$tabFilenamePrefixes [] = $tabFilenamePrefix;
	}
}

$tabSuggestions = buildFilenameSuggestions ( $songImagesDirectory, $tabFilenamePrefixes );
$tabFilenameOptions = $tabSuggestions ['options'];
$matchingTabFilenames = $tabSuggestions ['matching'];
$tabDirectoryExists = $tabSuggestions ['directoryExists'];

$mp3FilenamePrefixes = [ ];
$mp3FilenamePrefix = '';
$trimmedSongId = $songIdForSuggestions !== '' ? ltrim ( $songIdForSuggestions, '0' ) : '';

if ($trimmedSongId !== '') {
	$mp3FilenamePrefix = $trimmedSongId;
	$mp3FilenamePrefixes [] = $trimmedSongId;
}

if ($songIdForSuggestions !== '' && $songIdForSuggestions !== $trimmedSongId) {
	$mp3FilenamePrefixes [] = $songIdForSuggestions;
}

$uniqueMp3Prefixes = [ ];
foreach ( $mp3FilenamePrefixes as $prefix ) {
	if ($prefix === '') {
		continue;
	}

	if (! in_array ( $prefix, $uniqueMp3Prefixes, true )) {
		$uniqueMp3Prefixes [] = $prefix;
	}
}

$mp3FilenamePrefixes = $uniqueMp3Prefixes;

if ($mp3FilenamePrefix === '' && ! empty ( $mp3FilenamePrefixes )) {
	$mp3FilenamePrefix = $mp3FilenamePrefixes [0];
}

$mp3Suggestions = buildFilenameSuggestions ( $audioSongsDirectory, $mp3FilenamePrefixes );
$mp3FilenameOptions = $mp3Suggestions ['options'];
$matchingMp3Filenames = $mp3Suggestions ['matching'];
$mp3DirectoryExists = $mp3Suggestions ['directoryExists'];

$songIdValue = '';
if (is_array ( $songData ) && array_key_exists ( 'id', $songData )) {
	$songIdValue = ( string ) $songData ['id'];
}
else {
	$songIdValue = ( string ) $id;
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Edit Song <?php echo escapeHtml($songIdValue); ?></title>
<style>
body {
	font-family: Arial, sans-serif;
	margin: 0;
	padding: 20px;
	background-color: #f7f7f2;
	color: #333;
}

.edit-song-container {
	max-width: 960px;
	margin: 0 auto;
}

h1 {
	font-size: 1.75rem;
	margin-bottom: 20px;
}

.form-group {
	margin-bottom: 15px;
}

.form-group label {
	display: block;
	font-weight: 600;
	margin-bottom: 6px;
}

.form-group input[type="text"], .form-group textarea {
	width: 100%;
	padding: 10px;
	border: 1px solid #c5c5c5;
	border-radius: 4px;
	box-sizing: border-box;
	font-size: 1rem;
	font-family: inherit;
}

.form-group textarea {
	min-height: 120px;
	resize: vertical;
	line-height: 1.4;
}

.form-group--with-suggestions {
	position: relative;
}

.author-suggestions {
	display: none;
	position: absolute;
	top: calc(100% - 4px);
	left: 0;
	right: 0;
	background-color: #fff;
	border: 1px solid #c5c5c5;
	border-top: none;
	border-radius: 0 0 4px 4px;
	box-shadow: 0 6px 12px rgba(0, 0, 0, 0.12);
	max-height: 220px;
	overflow-y: auto;
	z-index: 20;
}

.author-suggestions.is-visible {
	display: block;
}

.author-suggestion {
	padding: 8px 12px;
	cursor: pointer;
	font-size: 0.95rem;
	border-top: 1px solid #e6e6e6;
}

.author-suggestion:first-child {
	border-top: none;
}

.author-suggestion:hover, .author-suggestion.is-active {
	background-color: #e8f1ff;
}

.form-note {
	margin-top: 6px;
	font-size: 0.9rem;
	color: #555;
}

.linked-meanings-summary {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	padding: 10px;
	margin-bottom: 10px;
	border: 1px solid #d0d0d0;
	border-radius: 4px;
	background-color: #ffffff;
	min-height: 44px;
	align-items: center;
	box-sizing: border-box;
}

.linked-meanings-summary .linked-meanings-empty {
	color: #666;
	font-size: 0.95rem;
}

.link-meanings-btn {
	margin-top: 2px;
	padding: 8px 16px;
	font-size: 1rem;
	border-radius: 4px;
	border: 1px solid transparent;
	background-color: #1c6dd0;
	color: #fff;
	cursor: pointer;
	display: inline-flex;
	align-items: center;
	gap: 6px;
}

.link-meanings-btn:hover {
	background-color: #1559a6;
}

.link-meanings-btn:focus {
	outline: 2px solid #0f3f7f;
	outline-offset: 2px;
}

.meaning-pill {
	display: inline-flex;
	align-items: center;
	padding: 6px 12px;
	border-radius: 999px;
	background-color: #e8f1ff;
	border: 1px solid #bcd4ff;
	font-size: 0.95rem;
	color: #1a3c6e;
}

.meaning-pill-title {
	font-weight: 600;
}

.meaning-modal {
	position: fixed;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	display: none;
	align-items: center;
	justify-content: center;
	z-index: 1000;
}

.meaning-modal.is-open {
	display: flex;
}

.meaning-modal__backdrop {
	position: absolute;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	background-color: rgba(0, 0, 0, 0.45);
}

.meaning-modal__dialog {
	position: relative;
	background-color: #ffffff;
	width: min(90%, 760px);
	max-height: 80vh;
	border-radius: 6px;
	box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
	display: flex;
	flex-direction: column;
	overflow: hidden;
	z-index: 1001;
}

.meaning-modal__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 16px 20px;
	border-bottom: 1px solid #e1e1e1;
}

.meaning-modal__header h2 {
	margin: 0;
	font-size: 1.3rem;
}

.meaning-modal__close {
	background: none;
	border: none;
	font-size: 1.5rem;
	cursor: pointer;
	color: #555;
	line-height: 1;
}

.meaning-modal__close:hover {
	color: #000;
}

.meaning-modal__body {
	padding: 16px 20px;
	overflow-y: auto;
}

.meaning-modal__search {
	margin-bottom: 12px;
}

.meaning-modal__search input {
	width: 100%;
	padding: 9px 12px;
	border-radius: 4px;
	border: 1px solid #c5c5c5;
	font-size: 1rem;
	box-sizing: border-box;
}

.meaning-modal__search input:focus {
	outline: 2px solid #1c6dd0;
	outline-offset: 1px;
}

.meaning-modal__actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	margin-bottom: 12px;
}

.meaning-modal__add {
	padding: 8px 14px;
	border-radius: 4px;
	border: 1px solid #1c6dd0;
	background-color: #1c6dd0;
	color: #fff;
	cursor: pointer;
	font-size: 0.95rem;
}

.meaning-modal__add:hover {
	background-color: #1559a6;
}

.meaning-modal__feedback {
	margin-bottom: 12px;
	font-size: 0.95rem;
	min-height: 1.2em;
}

.meaning-modal__feedback[aria-hidden="true"] {
	display: none;
}

.meaning-modal__feedback--error {
	color: #a30000;
}

.meaning-modal__feedback--success {
	color: #1a7f37;
}

.meaning-editor {
	border: 1px solid #dcdcdc;
	border-radius: 4px;
	padding: 12px;
	background-color: #ffffff;
	margin-bottom: 16px;
}

.meaning-editor--hidden {
	display: none;
}

.meaning-editor__title {
	margin: 0 0 12px;
	font-size: 1.05rem;
}

.meaning-editor__field {
	margin-bottom: 10px;
}

.meaning-editor__field label {
	display: block;
	font-weight: 600;
	margin-bottom: 4px;
}

.meaning-editor__field input, .meaning-editor__field textarea {
	width: 100%;
	padding: 8px 10px;
	border-radius: 4px;
	border: 1px solid #c5c5c5;
	font-size: 0.95rem;
	box-sizing: border-box;
}

.meaning-editor__field textarea {
	min-height: 100px;
	resize: vertical;
	line-height: 1.4;
}

.meaning-editor__buttons {
	display: flex;
	gap: 8px;
	justify-content: flex-end;
}

.meaning-editor__cancel, .meaning-editor__save {
	padding: 8px 16px;
	border-radius: 4px;
	border: 1px solid transparent;
	cursor: pointer;
	font-size: 0.95rem;
}

.meaning-editor__cancel {
	background-color: #f0f0f0;
	border-color: #d0d0d0;
	color: #333;
}

.meaning-editor__cancel:hover {
	background-color: #e2e2e2;
}

.meaning-editor__save {
	background-color: #1c6dd0;
	color: #fff;
}

.meaning-editor__save:hover {
	background-color: #1559a6;
}

.meaning-editor__save:disabled, .meaning-editor__cancel:disabled, .meaning-modal__add:disabled {
	opacity: 0.65;
	cursor: not-allowed;
}

.meaning-modal__list {
	max-height: 48vh;
	overflow-y: auto;
	border: 1px solid #dcdcdc;
	border-radius: 4px;
	background-color: #fafafa;
}

.meaning-option {
	display: flex;
	align-items: flex-start;
	gap: 10px;
	padding: 10px 12px;
	border-bottom: 1px solid #e5e5e5;
	cursor: pointer;
}

.meaning-option:last-child {
	border-bottom: none;
}

.meaning-option input[type="checkbox"] {
	margin-top: 4px;
	cursor: pointer;
}

.meaning-option-details {
	flex: 1;
}

.meaning-option-title {
	font-weight: 600;
	margin-bottom: 4px;
}

.meaning-option-text {
	font-size: 0.92rem;
	color: #555;
	white-space: pre-line;
}

.meaning-option--selected {
	background-color: #e9f7ef;
}

.meaning-option-actions {
	display: flex;
	align-items: center;
	margin-left: auto;
}

.meaning-option-edit {
	background-color: transparent;
	border: 1px solid #1c6dd0;
	color: #1c6dd0;
	border-radius: 4px;
	padding: 4px 10px;
	font-size: 0.85rem;
	cursor: pointer;
	white-space: nowrap;
}

.meaning-option-edit:hover {
	background-color: #1c6dd0;
	color: #fff;
}

.meaning-modal__empty {
	padding: 16px;
	text-align: center;
	color: #666;
	font-size: 0.95rem;
}

.meaning-modal__footer {
	display: flex;
	justify-content: flex-end;
	gap: 10px;
	padding: 14px 20px;
	border-top: 1px solid #e1e1e1;
	background-color: #f7f7f7;
}

.meaning-modal__cancel, .meaning-modal__save {
	padding: 9px 18px;
	border-radius: 4px;
	border: 1px solid transparent;
	cursor: pointer;
	font-size: 1rem;
}

.meaning-modal__cancel {
	background-color: #f0f0f0;
	border-color: #ccc;
	color: #333;
}

.meaning-modal__cancel:hover {
	background-color: #e0e0e0;
}

.meaning-modal__save {
	background-color: #4CAF50;
	border-color: #4CAF50;
	color: #fff;
}

.meaning-modal__save:hover {
	background-color: #45a049;
}

.short-textarea {
	min-height: 80px;
}

.readonly-input {
	background-color: #f0f0f0;
	color: #555;
}

.button-row {
	display: flex;
	justify-content: flex-end;
	gap: 10px;
	margin-top: 24px;
}

.button-row button {
	padding: 10px 18px;
	font-size: 1rem;
	border-radius: 4px;
	border: 1px solid transparent;
	cursor: pointer;
}

.button-row .delete-btn {
	background-color: #d9534f;
	border-color: #d43f3a;
	color: #fff;
}

.button-row .delete-btn:hover {
	background-color: #c9302c;
}

.button-row .save-btn {
	background-color: #4CAF50;
	border-color: #4CAF50;
	color: #fff;
}

.button-row .save-btn:hover {
	background-color: #45a049;
}

.button-row .cancel-btn {
	background-color: #f0f0f0;
	border-color: #ccc;
	color: #333;
}

.button-row .cancel-btn:hover {
	background-color: #e0e0e0;
}

.alert {
	padding: 12px 16px;
	border-radius: 4px;
	margin-bottom: 18px;
	border: 1px solid transparent;
}

.alert-success {
	background-color: #d4edda;
	border-color: #c3e6cb;
	color: #155724;
}

.alert-error {
	background-color: #f8d7da;
	border-color: #f5c6cb;
	color: #721c24;
}
</style>
</head>
<body>
	<div class="edit-song-container">
		<h1>Edit Song <?php echo escapeHtml($songIdValue); ?></h1>

        <?php if ($successMessage): ?>
            <div class="alert alert-success"><?php echo escapeHtml($successMessage); ?></div>
        <?php endif; ?>

        <?php if ($errorMessage): ?>
            <div class="alert alert-error"><?php echo escapeHtml($errorMessage); ?></div>
        <?php endif; ?>

        <?php if ($songData): ?>
            <form method="post" id="edit-song-form">
			<input type="hidden" name="id" value="<?php echo escapeHtml($songIdValue); ?>">

			<div class="form-group">
				<label for="song-id">ID</label> <input type="text" id="song-id" class="readonly-input"
					value="<?php echo escapeHtml($songIdValue); ?>" readonly>
			</div>

			<div class="form-group">
				<label for="song-title">Title</label> <input type="text" id="song-title" name="title"
					value="<?php echo escapeHtml($songData['title'] ?? ''); ?>">
			</div>

			<div class="form-group">
				<label for="song-lyrics">Lyrics</label>
				<textarea id="song-lyrics" name="lyrics" rows="12"><?php echo htmlspecialchars($songData['lyrics'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
			</div>

			<div class="form-group">
				<label for="song-lyrics-short">Lyrics (Short)</label>
				<textarea id="song-lyrics-short" name="lyrics_short" class="short-textarea" rows="6"><?php echo htmlspecialchars($songData['lyrics_short'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
			</div>

			<div class="form-group">
				<label for="song-lyrics-paged">Lyrics (Paged)</label>
				<textarea id="song-lyrics-paged" name="lyrics_paged" class="short-textarea" rows="6"><?php echo htmlspecialchars($songData['lyrics_paged'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
			</div>

			<div class="form-group">
				<label for="tabfilename">Tab Filename</label> <input type="text" id="tabfilename" name="tabfilename"
					list="tabfilename-options" value="<?php echo escapeHtml($songData['tabfilename'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingTabFilenames, $tabFilenameOptions, $tabFilenamePrefix, $tabDirectoryExists, 'img/songs/'); ?>
                </div>

			<datalist id="tabfilename-options">
                    <?php foreach ($tabFilenameOptions as $filenameOption): ?>
                        <option value="<?php echo escapeHtml($filenameOption); ?>"></option>
                    <?php endforeach; ?>
                </datalist>

			<div class="form-group">
				<label for="mp3filename">MP3 Filename</label> <input type="text" id="mp3filename" name="mp3filename"
					list="mp3filename-options" value="<?php echo escapeHtml($songData['mp3filename'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingMp3Filenames, $mp3FilenameOptions, $mp3FilenamePrefix, $mp3DirectoryExists, 'audio/songs/'); ?>
                </div>

			<div class="form-group">
				<label for="mp3filename2">MP3 Filename 2</label> <input type="text" id="mp3filename2" name="mp3filename2"
					list="mp3filename-options" value="<?php echo escapeHtml($songData['mp3filename2'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingMp3Filenames, $mp3FilenameOptions, $mp3FilenamePrefix, $mp3DirectoryExists, 'audio/songs/'); ?>
                </div>

			<datalist id="mp3filename-options">
                    <?php foreach ($mp3FilenameOptions as $filenameOption): ?>
                        <option value="<?php echo escapeHtml($filenameOption); ?>"></option>
                    <?php endforeach; ?>
                </datalist>

			<div class="form-group form-group--with-suggestions">
				<label for="song-author">Author(s)</label> <input type="text" id="song-author" name="author" autocomplete="off"
					value="<?php echo escapeHtml($songData['author'] ?? ''); ?>">
				<div class="author-suggestions" id="author-suggestions" role="listbox" aria-label="Author suggestions"
					aria-hidden="true"></div>
			</div>

			<div class="form-group">
				<label for="song-keywords">Keywords</label> <input type="text" id="song-keywords" name="keywords"
					value="<?php echo escapeHtml($songData['keywords'] ?? ''); ?>">
			</div>

			<div class="form-group">
				<label>Linked Meanings</label>
				<div class="linked-meanings-summary" id="selected-meanings-summary">
                        <?php if (! empty($selectedMeaningDetailsForDisplay)): ?>
                            <?php foreach ($selectedMeaningDetailsForDisplay as $meaningItem): ?>
                                <?php
											$meaningTitle = isset ( $meaningItem ['title'] ) && $meaningItem ['title'] !== '' ? $meaningItem ['title'] : ('Meaning #' . (isset ( $meaningItem ['id'] ) ? $meaningItem ['id'] : '?'));
											?>
                                <span class="meaning-pill"> <span class="meaning-pill-title"><?php echo escapeHtml($meaningTitle); ?></span>
					</span>
                            <?php endforeach; ?>
                        <?php else: ?>
                            <span class="linked-meanings-empty">No meanings linked.</span>
                        <?php endif; ?>
                    </div>
				<button type="button" class="link-meanings-btn" id="open-meanings-modal">Link Meanings</button>
				<input type="hidden" name="meaning_ids" id="meaning-ids-input"
					value="<?php echo escapeHtml($selectedMeaningIdsJson); ?>">
			</div>

			<div class="button-row">
				<button type="button" class="cancel-btn" id="cancel-edit">Cancel</button>
                    <?php if ($songData && $songIdValue !== ''): ?>
                        <button type="button" class="delete-btn" id="delete-song">Delete</button>
                    <?php endif; ?>
                    <button type="submit" class="save-btn">Save</button>
			</div>
		</form>
		<div class="meaning-modal" id="meaning-modal" aria-hidden="true" role="dialog" aria-labelledby="meaning-modal-title">
			<div class="meaning-modal__backdrop" data-meaning-modal-dismiss="true"></div>
			<div class="meaning-modal__dialog">
				<div class="meaning-modal__header">
					<h2 id="meaning-modal-title">Link Meanings</h2>
					<button type="button" class="meaning-modal__close" id="meaning-modal-close" aria-label="Close">&times;</button>
				</div>
				<div class="meaning-modal__body">
					<div class="meaning-modal__search">
						<input type="text" id="meaning-search" placeholder="Filter by title or meaning">
					</div>
					<div class="meaning-modal__actions">
						<button type="button" class="meaning-modal__add" id="add-meaning-button">Add Meaning</button>
					</div>
					<div class="meaning-modal__feedback" id="meaning-modal-feedback" role="alert" aria-live="polite" aria-hidden="true"></div>
					<div class="meaning-editor meaning-editor--hidden" id="meaning-editor" aria-hidden="true">
						<h3 class="meaning-editor__title" id="meaning-editor-heading">Add Meaning</h3>
						<div class="meaning-editor__field">
							<label for="meaning-editor-title-input">Title</label> <input type="text" id="meaning-editor-title-input"
								placeholder="Enter meaning title">
						</div>
						<div class="meaning-editor__field">
							<label for="meaning-editor-text-input">Meaning</label>
							<textarea id="meaning-editor-text-input" rows="5" placeholder="Enter meaning description"></textarea>
						</div>
						<div class="meaning-editor__buttons">
							<button type="button" class="meaning-editor__cancel" id="meaning-editor-cancel">Cancel</button>
							<button type="button" class="meaning-editor__save" id="meaning-editor-save">Save Meaning</button>
						</div>
					</div>
					<div class="meaning-modal__list" id="meaning-options"></div>
				</div>
				<div class="meaning-modal__footer">
					<button type="button" class="meaning-modal__cancel" id="meaning-modal-cancel">Cancel</button>
					<button type="button" class="meaning-modal__save" id="meaning-modal-save">Save</button>
				</div>
			</div>
		</div>
        <?php elseif (! $errorMessage): ?>
            <p>No song data available.</p>
        <?php endif; ?>
    </div>

	<script>
        (function() {
            var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
            var cancelButton = document.getElementById('cancel-edit');
            if (cancelButton) {
                cancelButton.addEventListener('click', function() {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({ type: 'closeEditModal' }, modalOrigin);
                    } else if (document.referrer) {
                        window.location.href = document.referrer;
                    } else {
                        window.location.href = 'index.html';
                    }
                });
            }

            var deleteButton = document.getElementById('delete-song');
            if (deleteButton) {
                var deleteUrl = <?php echo json_encode('deletesong.php?id=' . rawurlencode($songIdValue)); ?>;
                deleteButton.addEventListener('click', function() {
                    window.location.href = deleteUrl;
                });
            }

            var allMeaningsData = <?php echo $allMeaningsJson; ?>;
            var initialSelectedMeaningIds = <?php echo $selectedMeaningIdsJson; ?>;
            var authorSuggestions = <?php echo $authorSuggestionsJson; ?>;

            if (! Array.isArray(authorSuggestions)) {
                authorSuggestions = [];
            }

            var meaningIdsInput = document.getElementById('meaning-ids-input');
            var summaryContainer = document.getElementById('selected-meanings-summary');
            var linkMeaningsButton = document.getElementById('open-meanings-modal');
            var modalElement = document.getElementById('meaning-modal');

            if (meaningIdsInput && summaryContainer && modalElement) {
                var modalBackdrop = modalElement.querySelector('.meaning-modal__backdrop');
                var modalCloseButton = document.getElementById('meaning-modal-close');
                var modalCancelButton = document.getElementById('meaning-modal-cancel');
                var modalSaveButton = document.getElementById('meaning-modal-save');
                var searchInput = document.getElementById('meaning-search');
                var meaningListContainer = document.getElementById('meaning-options');
                var addMeaningButton = document.getElementById('add-meaning-button');
                var meaningModalFeedback = document.getElementById('meaning-modal-feedback');
                var meaningEditorContainer = document.getElementById('meaning-editor');
                var meaningEditorHeading = document.getElementById('meaning-editor-heading');
                var meaningEditorTitleInput = document.getElementById('meaning-editor-title-input');
                var meaningEditorTextInput = document.getElementById('meaning-editor-text-input');
                var meaningEditorCancelButton = document.getElementById('meaning-editor-cancel');
                var meaningEditorSaveButton = document.getElementById('meaning-editor-save');
                var meaningEditorSaveButtonDefaultText = meaningEditorSaveButton ? meaningEditorSaveButton.textContent : 'Save Meaning';
                var isMeaningEditorSaving = false;
                var editingMeaningId = null;

                if (! Array.isArray(allMeaningsData)) {
                    allMeaningsData = [];
                }

                if (! Array.isArray(initialSelectedMeaningIds)) {
                    initialSelectedMeaningIds = [];
                }

                var meaningLookup = {};
                for (var i = 0; i < allMeaningsData.length; i++) {
                    var meaningEntry = allMeaningsData[i] || {};
                    if (typeof meaningEntry.id === 'undefined' || meaningEntry.id === null) {
                        continue;
                    }

                    var meaningKey = String(meaningEntry.id);
                    meaningLookup[meaningKey] = {
                        id: meaningEntry.id,
                        title: typeof meaningEntry.title === 'string' ? meaningEntry.title : '',
                        meaning: typeof meaningEntry.meaning === 'string' ? meaningEntry.meaning : ''
                    };
                }

                sortMeaningsArray();

                function addClass(element, className) {
                    if (! element || ! className) {
                        return;
                    }

                    if (element.classList) {
                        element.classList.add(className);
                    } else if (element.className.indexOf(className) === -1) {
                        element.className = (element.className ? element.className + ' ' : '') + className;
                    }
                }

                function removeClass(element, className) {
                    if (! element || ! className) {
                        return;
                    }

                    if (element.classList) {
                        element.classList.remove(className);
                    } else {
                        element.className = element.className.replace(new RegExp('\\b' + className + '\\b', 'g'), '').replace(/\s{2,}/g, ' ').trim();
                    }
                }

                function normalizeSelectedIds(ids) {
                    var normalized = [];

                    if (! Array.isArray(ids)) {
                        return normalized;
                    }

                    for (var i = 0; i < ids.length; i++) {
                        var value = ids[i];

                        if (value === null || typeof value === 'undefined') {
                            continue;
                        }

                        var stringValue = String(value);

                        if (stringValue === '') {
                            continue;
                        }

                        if (normalized.indexOf(stringValue) === -1) {
                            normalized.push(stringValue);
                        }
                    }

                    return normalized;
                }

                function sortMeaningsArray() {
                    if (! Array.isArray(allMeaningsData)) {
                        return;
                    }

                    allMeaningsData.sort(function(a, b) {
                        var titleA = a && typeof a.title === 'string' ? a.title.toLowerCase() : '';
                        var titleB = b && typeof b.title === 'string' ? b.title.toLowerCase() : '';

                        if (titleA < titleB) {
                            return -1;
                        }

                        if (titleA > titleB) {
                            return 1;
                        }

                        var idA = a && typeof a.id !== 'undefined' ? parseInt(a.id, 10) : 0;
                        var idB = b && typeof b.id !== 'undefined' ? parseInt(b.id, 10) : 0;

                        if (isNaN(idA)) {
                            idA = 0;
                        }

                        if (isNaN(idB)) {
                            idB = 0;
                        }

                        if (idA < idB) {
                            return -1;
                        }

                        if (idA > idB) {
                            return 1;
                        }

                        return 0;
                    });
                }

                function updateMeaningModalFeedback(message, type) {
                    if (! meaningModalFeedback) {
                        return;
                    }

                    var feedbackMessage = message ? String(message) : '';
                    meaningModalFeedback.textContent = feedbackMessage;
                    meaningModalFeedback.className = 'meaning-modal__feedback';

                    if (feedbackMessage === '') {
                        meaningModalFeedback.setAttribute('aria-hidden', 'true');
                        return;
                    }

                    if (type === 'error') {
                        addClass(meaningModalFeedback, 'meaning-modal__feedback--error');
                    } else if (type === 'success') {
                        addClass(meaningModalFeedback, 'meaning-modal__feedback--success');
                    }

                    meaningModalFeedback.setAttribute('aria-hidden', 'false');
                }

                function closeMeaningEditor() {
                    editingMeaningId = null;

                    if (meaningEditorHeading) {
                        meaningEditorHeading.textContent = 'Add Meaning';
                    }

                    if (meaningEditorTitleInput) {
                        meaningEditorTitleInput.value = '';
                    }

                    if (meaningEditorTextInput) {
                        meaningEditorTextInput.value = '';
                    }

                    if (meaningEditorContainer) {
                        addClass(meaningEditorContainer, 'meaning-editor--hidden');
                        meaningEditorContainer.setAttribute('aria-hidden', 'true');
                    }
                }

                function openMeaningEditorForNew() {
                    if (isMeaningEditorSaving) {
                        return;
                    }

                    editingMeaningId = null;

                    if (meaningEditorHeading) {
                        meaningEditorHeading.textContent = 'Add Meaning';
                    }

                    if (meaningEditorTitleInput) {
                        meaningEditorTitleInput.value = '';
                    }

                    if (meaningEditorTextInput) {
                        meaningEditorTextInput.value = '';
                    }

                    if (meaningEditorContainer) {
                        removeClass(meaningEditorContainer, 'meaning-editor--hidden');
                        meaningEditorContainer.setAttribute('aria-hidden', 'false');
                    }

                    updateMeaningModalFeedback('', '');

                    if (meaningEditorTitleInput && typeof meaningEditorTitleInput.focus === 'function') {
                        meaningEditorTitleInput.focus();
                    }
                }

                function openMeaningEditorForEdit(meaningId) {
                    if (isMeaningEditorSaving) {
                        return;
                    }

                    var meaningKey = String(meaningId);
                    if (! Object.prototype.hasOwnProperty.call(meaningLookup, meaningKey)) {
                        updateMeaningModalFeedback('Unable to find the requested meaning.', 'error');
                        return;
                    }

                    editingMeaningId = meaningKey;
                    var meaningData = meaningLookup[meaningKey] || {};

                    if (meaningEditorHeading) {
                        var headingId = typeof meaningData.id !== 'undefined' ? meaningData.id : meaningKey;
                        meaningEditorHeading.textContent = 'Edit Meaning #' + headingId;
                    }

                    if (meaningEditorTitleInput) {
                        meaningEditorTitleInput.value = meaningData.title || '';
                    }

                    if (meaningEditorTextInput) {
                        meaningEditorTextInput.value = meaningData.meaning || '';
                    }

                    if (meaningEditorContainer) {
                        removeClass(meaningEditorContainer, 'meaning-editor--hidden');
                        meaningEditorContainer.setAttribute('aria-hidden', 'false');
                    }

                    updateMeaningModalFeedback('', '');

                    if (meaningEditorTitleInput && typeof meaningEditorTitleInput.focus === 'function') {
                        meaningEditorTitleInput.focus();
                        if (typeof meaningEditorTitleInput.setSelectionRange === 'function') {
                            var titleLength = meaningEditorTitleInput.value ? meaningEditorTitleInput.value.length : 0;
                            try {
                                meaningEditorTitleInput.setSelectionRange(titleLength, titleLength);
                            } catch (selectionError) {
                                // Ignore selection errors.
                            }
                        }
                    }
                }

                function setMeaningEditorSaving(isSaving) {
                    isMeaningEditorSaving = !! isSaving;

                    if (meaningEditorSaveButton) {
                        meaningEditorSaveButton.disabled = isMeaningEditorSaving;
                        if (meaningEditorSaveButtonDefaultText !== '') {
                            meaningEditorSaveButton.textContent = isMeaningEditorSaving ? 'Saving...' : meaningEditorSaveButtonDefaultText;
                        }
                    }

                    if (meaningEditorCancelButton) {
                        meaningEditorCancelButton.disabled = isMeaningEditorSaving;
                    }

                    if (addMeaningButton) {
                        addMeaningButton.disabled = isMeaningEditorSaving;
                    }
                }

                function saveMeaningEditorChanges() {
                    if (isMeaningEditorSaving) {
                        return;
                    }

                    if (! window.fetch) {
                        updateMeaningModalFeedback('Saving meanings requires a modern browser.', 'error');
                        return;
                    }

                    var titleValue = meaningEditorTitleInput ? meaningEditorTitleInput.value : '';
                    var meaningValue = meaningEditorTextInput ? meaningEditorTextInput.value : '';
                    var trimmedTitle = titleValue ? titleValue.trim() : '';
                    var trimmedMeaning = meaningValue ? meaningValue.trim() : '';

                    if (trimmedTitle === '' && trimmedMeaning === '') {
                        updateMeaningModalFeedback('Please provide a title or meaning text before saving.', 'error');
                        if (meaningEditorTitleInput && typeof meaningEditorTitleInput.focus === 'function') {
                            meaningEditorTitleInput.focus();
                        }
                        return;
                    }

                    var meaningIdBeingEdited = editingMeaningId;

                    var formData = new FormData();
                    formData.append('meaning_action', meaningIdBeingEdited === null ? 'create' : 'update');

                    if (meaningIdBeingEdited !== null) {
                        formData.append('meaning_id', meaningIdBeingEdited);
                    }

                    formData.append('title', titleValue);
                    formData.append('meaning', meaningValue);

                    setMeaningEditorSaving(true);
                    updateMeaningModalFeedback('', '');

                    fetch(window.location.href, {
                        method: 'POST',
                        body: formData,
                        headers: {
                            'Accept': 'application/json'
                        }
                    })
                        .then(function(response) {
                            return response.json().catch(function() {
                                return { success: false, message: 'Received an invalid response from the server.' };
                            });
                        })
                        .then(function(data) {
                            if (data && data.success && data.meaning && typeof data.meaning.id !== 'undefined') {
                                var savedMeaning = data.meaning;
                                var savedMeaningId = String(savedMeaning.id);
                                var normalizedMeaning = {
                                    id: savedMeaning.id,
                                    title: typeof savedMeaning.title === 'string' ? savedMeaning.title : '',
                                    meaning: typeof savedMeaning.meaning === 'string' ? savedMeaning.meaning : ''
                                };

                                meaningLookup[savedMeaningId] = normalizedMeaning;

                                var replacedExisting = false;
                                for (var index = 0; index < allMeaningsData.length; index++) {
                                    if (String(allMeaningsData[index].id) === savedMeaningId) {
                                        allMeaningsData[index] = normalizedMeaning;
                                        replacedExisting = true;
                                        break;
                                    }
                                }

                                if (! replacedExisting) {
                                    allMeaningsData.push(normalizedMeaning);
                                }

                                sortMeaningsArray();

                                if (meaningIdBeingEdited === null) {
                                    if (searchInput) {
                                        searchInput.value = '';
                                    }

                                    if (selectedMeaningIds.indexOf(savedMeaningId) === -1) {
                                        selectedMeaningIds.push(savedMeaningId);
                                    }

                                    selectedMeaningIds = normalizeSelectedIds(selectedMeaningIds);
                                    syncHiddenInput();
                                    updateSummary();
                                    updateMeaningModalFeedback('Meaning added and selected.', 'success');
                                } else {
                                    syncHiddenInput();
                                    updateSummary();
                                    updateMeaningModalFeedback('Meaning updated.', 'success');
                                }

                                closeMeaningEditor();
                                renderMeaningList();
                            } else {
                                var errorMessage = data && data.message ? data.message : 'Failed to save the meaning.';
                                updateMeaningModalFeedback(errorMessage, 'error');
                            }
                        })
                        .catch(function() {
                            updateMeaningModalFeedback('An unexpected error occurred while saving the meaning.', 'error');
                        })
                        .then(function() {
                            setMeaningEditorSaving(false);
                        });
                }

                var selectedMeaningIds = normalizeSelectedIds(initialSelectedMeaningIds);
                var validatedSelection = [];
                for (var j = 0; j < selectedMeaningIds.length; j++) {
                    var selectionId = selectedMeaningIds[j];

                    if (Object.prototype.hasOwnProperty.call(meaningLookup, selectionId) && validatedSelection.indexOf(selectionId) === -1) {
                        validatedSelection.push(selectionId);
                    }
                }
                selectedMeaningIds = validatedSelection;

                var modalSelectionSnapshot = [];
                var modalIsOpen = false;

                function createSelectionSnapshot() {
                    return selectedMeaningIds.slice();
                }

                function restoreSelectionFromSnapshot(snapshot) {
                    selectedMeaningIds = normalizeSelectedIds(snapshot);
                    var revalidated = [];
                    for (var i = 0; i < selectedMeaningIds.length; i++) {
                        var id = selectedMeaningIds[i];
                        if (Object.prototype.hasOwnProperty.call(meaningLookup, id) && revalidated.indexOf(id) === -1) {
                            revalidated.push(id);
                        }
                    }
                    selectedMeaningIds = revalidated;
                }

                function syncHiddenInput() {
                    if (! meaningIdsInput) {
                        return;
                    }

                    var numericValues = [];
                    for (var i = 0; i < selectedMeaningIds.length; i++) {
                        var parsed = parseInt(selectedMeaningIds[i], 10);
                        if (! isNaN(parsed)) {
                            numericValues.push(parsed);
                        }
                    }

                    meaningIdsInput.value = JSON.stringify(numericValues);
                }

                function buildMeaningDisplayTitle(meaningData) {
                    if (! meaningData) {
                        return '';
                    }

                    if (meaningData.title && meaningData.title !== '') {
                        return meaningData.title;
                    }

                    var identifier = typeof meaningData.id !== 'undefined' ? meaningData.id : '';
                    return identifier !== '' ? 'Meaning #' + identifier : 'Meaning';
                }

                function updateSummary() {
                    if (! summaryContainer) {
                        return;
                    }

                    while (summaryContainer.firstChild) {
                        summaryContainer.removeChild(summaryContainer.firstChild);
                    }

                    var items = [];
                    for (var i = 0; i < selectedMeaningIds.length; i++) {
                        var id = selectedMeaningIds[i];
                        if (Object.prototype.hasOwnProperty.call(meaningLookup, id)) {
                            items.push(meaningLookup[id]);
                        }
                    }

                    if (items.length === 0) {
                        var emptyElement = document.createElement('span');
                        emptyElement.className = 'linked-meanings-empty';
                        emptyElement.textContent = 'No meanings linked.';
                        summaryContainer.appendChild(emptyElement);
                        return;
                    }

                    items.sort(function(a, b) {
                        var titleA = (a.title || '').toLowerCase();
                        var titleB = (b.title || '').toLowerCase();

                        if (titleA < titleB) {
                            return -1;
                        }

                        if (titleA > titleB) {
                            return 1;
                        }

                        return 0;
                    });

                    for (var i = 0; i < items.length; i++) {
                        var pill = document.createElement('span');
                        pill.className = 'meaning-pill';

                        var pillTitle = document.createElement('span');
                        pillTitle.className = 'meaning-pill-title';
                        pillTitle.textContent = buildMeaningDisplayTitle(items[i]);

                        pill.appendChild(pillTitle);
                        summaryContainer.appendChild(pill);
                    }
                }

                function renderMeaningList() {
                    if (! meaningListContainer) {
                        return;
                    }

                    while (meaningListContainer.firstChild) {
                        meaningListContainer.removeChild(meaningListContainer.firstChild);
                    }

                    var filterValue = '';
                    if (searchInput && typeof searchInput.value === 'string') {
                        filterValue = searchInput.value.trim().toLowerCase();
                    }

                    var matches = 0;

                    for (var i = 0; i < allMeaningsData.length; i++) {
                        var meaningEntry = allMeaningsData[i] || {};
                        if (typeof meaningEntry.id === 'undefined' || meaningEntry.id === null) {
                            continue;
                        }

                        var meaningId = String(meaningEntry.id);
                        var titleText = typeof meaningEntry.title === 'string' ? meaningEntry.title : '';
                        var meaningText = typeof meaningEntry.meaning === 'string' ? meaningEntry.meaning : '';
                        var searchableText = (titleText + ' ' + meaningText).toLowerCase();

                        if (filterValue && searchableText.indexOf(filterValue) === -1) {
                            continue;
                        }

                        matches++;

                        var optionLabel = document.createElement('label');
                        optionLabel.className = 'meaning-option';

                        var checkbox = document.createElement('input');
                        checkbox.type = 'checkbox';
                        checkbox.value = meaningId;
                        checkbox.checked = selectedMeaningIds.indexOf(meaningId) !== -1;

                        var detailsContainer = document.createElement('div');
                        detailsContainer.className = 'meaning-option-details';

                        var titleElement = document.createElement('div');
                        titleElement.className = 'meaning-option-title';
                        titleElement.textContent = titleText !== '' ? titleText : buildMeaningDisplayTitle({ id: meaningEntry.id, title: titleText });

                        detailsContainer.appendChild(titleElement);

                        if (meaningText !== '') {
                            var meaningTextElement = document.createElement('div');
                            meaningTextElement.className = 'meaning-option-text';
                            meaningTextElement.textContent = meaningText;
                            detailsContainer.appendChild(meaningTextElement);
                        }

                        optionLabel.appendChild(checkbox);
                        optionLabel.appendChild(detailsContainer);

                        var actionsContainer = document.createElement('div');
                        actionsContainer.className = 'meaning-option-actions';

                        var editButton = document.createElement('button');
                        editButton.type = 'button';
                        editButton.className = 'meaning-option-edit';
                        editButton.textContent = 'Edit';

                        actionsContainer.appendChild(editButton);
                        optionLabel.appendChild(actionsContainer);

                        if (checkbox.checked) {
                            addClass(optionLabel, 'meaning-option--selected');
                        }

                        (function(id, labelElement, checkboxElement, editButtonElement) {
                            checkboxElement.addEventListener('change', function(event) {
                                if (event.target.checked) {
                                    if (selectedMeaningIds.indexOf(id) === -1) {
                                        selectedMeaningIds.push(id);
                                    }
                                    addClass(labelElement, 'meaning-option--selected');
                                } else {
                                    var removeIndex = selectedMeaningIds.indexOf(id);
                                    if (removeIndex !== -1) {
                                        selectedMeaningIds.splice(removeIndex, 1);
                                    }
                                    removeClass(labelElement, 'meaning-option--selected');
                                }
                            });

                            if (editButtonElement) {
                                editButtonElement.addEventListener('click', function(event) {
                                    event.preventDefault();
                                    event.stopPropagation();
                                    openMeaningEditorForEdit(id);
                                });
                            }
                        })(meaningId, optionLabel, checkbox, editButton);

                        meaningListContainer.appendChild(optionLabel);
                    }

                    if (matches === 0) {
                        var emptyMessage = document.createElement('div');
                        emptyMessage.className = 'meaning-modal__empty';

                        if (! allMeaningsData.length) {
                            emptyMessage.textContent = 'No meanings are available yet.';
                        } else {
                            emptyMessage.textContent = 'No meanings found for the current filter.';
                        }

                        meaningListContainer.appendChild(emptyMessage);
                    }
                }

                function openMeaningModal() {
                    modalSelectionSnapshot = createSelectionSnapshot();
                    if (searchInput) {
                        searchInput.value = '';
                    }
                    closeMeaningEditor();
                    updateMeaningModalFeedback('', '');
                    setMeaningEditorSaving(false);
                    renderMeaningList();
                    addClass(modalElement, 'is-open');
                    modalElement.setAttribute('aria-hidden', 'false');
                    modalIsOpen = true;

                    if (searchInput) {
                        setTimeout(function() {
                            searchInput.focus();
                        }, 0);
                    }
                }

                function closeMeaningModal() {
                    removeClass(modalElement, 'is-open');
                    modalElement.setAttribute('aria-hidden', 'true');
                    modalIsOpen = false;
                    modalSelectionSnapshot = [];
                    closeMeaningEditor();
                    updateMeaningModalFeedback('', '');
                    setMeaningEditorSaving(false);
                }

                function handleModalCancel() {
                    restoreSelectionFromSnapshot(modalSelectionSnapshot);
                    syncHiddenInput();
                    updateSummary();
                    closeMeaningModal();
                }

                function handleModalSave() {
                    syncHiddenInput();
                    updateSummary();
                    closeMeaningModal();
                }

                if (linkMeaningsButton) {
                    linkMeaningsButton.addEventListener('click', function() {
                        openMeaningModal();
                    });
                }

                if (modalBackdrop) {
                    modalBackdrop.addEventListener('click', function() {
                        handleModalCancel();
                    });
                }

                if (modalCloseButton) {
                    modalCloseButton.addEventListener('click', function() {
                        handleModalCancel();
                    });
                }

                if (modalCancelButton) {
                    modalCancelButton.addEventListener('click', function() {
                        handleModalCancel();
                    });
                }

                if (modalSaveButton) {
                    modalSaveButton.addEventListener('click', function() {
                        handleModalSave();
                    });
                }

                if (addMeaningButton) {
                    addMeaningButton.addEventListener('click', function() {
                        openMeaningEditorForNew();
                    });
                }

                if (meaningEditorCancelButton) {
                    meaningEditorCancelButton.addEventListener('click', function() {
                        if (isMeaningEditorSaving) {
                            return;
                        }

                        closeMeaningEditor();
                        updateMeaningModalFeedback('', '');
                    });
                }

                if (meaningEditorSaveButton) {
                    meaningEditorSaveButton.addEventListener('click', function() {
                        saveMeaningEditorChanges();
                    });
                }

                if (meaningEditorTitleInput) {
                    meaningEditorTitleInput.addEventListener('keydown', function(event) {
                        var key = event.key || event.keyCode;

                        if (key === 'Enter' || key === 13) {
                            event.preventDefault();
                            saveMeaningEditorChanges();
                        }
                    });
                }

                if (meaningEditorTextInput) {
                    meaningEditorTextInput.addEventListener('keydown', function(event) {
                        var key = event.key || event.keyCode;

                        if ((key === 'Enter' || key === 13) && (event.ctrlKey || event.metaKey)) {
                            event.preventDefault();
                            saveMeaningEditorChanges();
                        }
                    });
                }

                if (searchInput) {
                    searchInput.addEventListener('input', function() {
                        renderMeaningList();
                    });

                    searchInput.addEventListener('keydown', function(event) {
                        var key = event.key || event.keyCode;
                        if (key === 'Enter' || key === 13) {
                            event.preventDefault();
                        }
                    });
                }

                document.addEventListener('keydown', function(event) {
                    if (! modalIsOpen) {
                        return;
                    }

                    var key = event.key || event.keyCode;

                    if (key === 'Escape' || key === 'Esc' || key === 27) {
                        event.preventDefault();
                        handleModalCancel();
                    }
                });

                syncHiddenInput();
                updateSummary();
            }

            var authorInput = document.getElementById('song-author');
            var authorSuggestionsContainer = document.getElementById('author-suggestions');

            if (authorInput && authorSuggestionsContainer && authorSuggestions.length) {
                var MAX_AUTHOR_SUGGESTIONS = 12;
                var filteredAuthorSuggestions = [];
                var highlightedAuthorSuggestionIndex = -1;
                var latestAuthorSegmentInfo = null;
                var hideAuthorSuggestionsTimeoutId = null;

                function clearHideAuthorSuggestionsTimeout() {
                    if (hideAuthorSuggestionsTimeoutId !== null) {
                        clearTimeout(hideAuthorSuggestionsTimeoutId);
                        hideAuthorSuggestionsTimeoutId = null;
                    }
                }

                function getCurrentAuthorSegmentInfo() {
                    var inputValue = typeof authorInput.value === 'string' ? authorInput.value : '';
                    var selectionStart = typeof authorInput.selectionStart === 'number' ? authorInput.selectionStart : inputValue.length;
                    var selectionEnd = typeof authorInput.selectionEnd === 'number' ? authorInput.selectionEnd : selectionStart;

                    if (selectionEnd < selectionStart) {
                        var temp = selectionStart;
                        selectionStart = selectionEnd;
                        selectionEnd = temp;
                    }

                    var beforeCursor = inputValue.slice(0, selectionStart);
                    var lastCommaIndex = beforeCursor.lastIndexOf(',');
                    var segmentStart = lastCommaIndex === -1 ? 0 : lastCommaIndex + 1;
                    var nextCommaIndex = inputValue.indexOf(',', selectionEnd);
                    var segmentEnd = nextCommaIndex === -1 ? inputValue.length : nextCommaIndex;
                    var segmentBeforeCursor = inputValue.slice(segmentStart, selectionStart);
                    var leadingSpacesMatch = segmentBeforeCursor.match(/^\s*/);
                    var leadingSpaces = leadingSpacesMatch ? leadingSpacesMatch[0] : '';
                    var typedFragment = segmentBeforeCursor.slice(leadingSpaces.length);
                    var query = typedFragment.trim();

                    return {
                        segmentStart: segmentStart,
                        segmentEnd: segmentEnd,
                        beforeSegmentText: inputValue.slice(0, segmentStart),
                        afterSegmentText: inputValue.slice(segmentEnd),
                        leadingSpaces: leadingSpaces,
                        query: query
                    };
                }

                function hideAuthorSuggestions() {
                    clearHideAuthorSuggestionsTimeout();
                    filteredAuthorSuggestions = [];
                    highlightedAuthorSuggestionIndex = -1;
                    authorSuggestionsContainer.innerHTML = '';
                    authorSuggestionsContainer.classList.remove('is-visible');
                    authorSuggestionsContainer.setAttribute('aria-hidden', 'true');
                }

                function highlightAuthorSuggestion(index) {
                    var suggestionItems = authorSuggestionsContainer.querySelectorAll('.author-suggestion');

                    if (! suggestionItems.length) {
                        highlightedAuthorSuggestionIndex = -1;
                        return;
                    }

                    if (index < 0 || index >= suggestionItems.length) {
                        for (var i = 0; i < suggestionItems.length; i++) {
                            suggestionItems[i].classList.remove('is-active');
                            suggestionItems[i].setAttribute('aria-selected', 'false');
                        }

                        highlightedAuthorSuggestionIndex = -1;
                        return;
                    }

                    for (var j = 0; j < suggestionItems.length; j++) {
                        if (j === index) {
                            suggestionItems[j].classList.add('is-active');
                            suggestionItems[j].setAttribute('aria-selected', 'true');

                            if (typeof suggestionItems[j].scrollIntoView === 'function') {
                                try {
                                    suggestionItems[j].scrollIntoView({ block: 'nearest' });
                                } catch (scrollError) {
                                    suggestionItems[j].scrollIntoView();
                                }
                            }
                        } else {
                            suggestionItems[j].classList.remove('is-active');
                            suggestionItems[j].setAttribute('aria-selected', 'false');
                        }
                    }

                    highlightedAuthorSuggestionIndex = index;
                }

                function renderAuthorSuggestions(list) {
                    authorSuggestionsContainer.innerHTML = '';

                    if (! list.length) {
                        hideAuthorSuggestions();
                        return;
                    }

                    var fragment = document.createDocumentFragment();

                    list.forEach(function(suggestion, index) {
                        if (typeof suggestion !== 'string' || suggestion === '') {
                            return;
                        }

                        var optionElement = document.createElement('div');
                        optionElement.className = 'author-suggestion';
                        optionElement.textContent = suggestion;
                        optionElement.setAttribute('role', 'option');
                        optionElement.setAttribute('aria-selected', 'false');

                        optionElement.addEventListener('mousedown', function(event) {
                            event.preventDefault();
                            selectAuthorSuggestion(suggestion);
                        });

                        optionElement.addEventListener('mouseenter', function() {
                            highlightAuthorSuggestion(index);
                        });

                        fragment.appendChild(optionElement);
                    });

                    authorSuggestionsContainer.appendChild(fragment);
                    authorSuggestionsContainer.classList.add('is-visible');
                    authorSuggestionsContainer.setAttribute('aria-hidden', 'false');
                    highlightAuthorSuggestion(-1);
                }

                function updateAuthorSuggestions() {
                    clearHideAuthorSuggestionsTimeout();

                    if (! authorSuggestions.length) {
                        hideAuthorSuggestions();
                        return;
                    }

                    latestAuthorSegmentInfo = getCurrentAuthorSegmentInfo();

                    if (! latestAuthorSegmentInfo) {
                        hideAuthorSuggestions();
                        return;
                    }

                    var query = latestAuthorSegmentInfo.query.toLowerCase();
                    var prefixMatches = [];
                    var otherMatches = [];

                    for (var i = 0; i < authorSuggestions.length; i++) {
                        var suggestion = authorSuggestions[i];

                        if (typeof suggestion !== 'string' || suggestion === '') {
                            continue;
                        }

                        var suggestionLower = suggestion.toLowerCase();

                        if (query === '') {
                            prefixMatches.push(suggestion);
                        } else if (suggestionLower.indexOf(query) === 0) {
                            prefixMatches.push(suggestion);
                        } else if (suggestionLower.indexOf(query) !== -1) {
                            otherMatches.push(suggestion);
                        }
                    }

                    var combined = prefixMatches.concat(otherMatches);
                    filteredAuthorSuggestions = combined.slice(0, MAX_AUTHOR_SUGGESTIONS);

                    if (! filteredAuthorSuggestions.length) {
                        hideAuthorSuggestions();
                        return;
                    }

                    renderAuthorSuggestions(filteredAuthorSuggestions);
                }

                function selectAuthorSuggestion(value) {
                    if (typeof value !== 'string' || value === '') {
                        return;
                    }

                    clearHideAuthorSuggestionsTimeout();

                    var segmentInfo = latestAuthorSegmentInfo || getCurrentAuthorSegmentInfo();

                    if (! segmentInfo) {
                        return;
                    }

                    var beforeText = segmentInfo.beforeSegmentText;
                    var leadingSpaces = segmentInfo.leadingSpaces;
                    var afterText = segmentInfo.afterSegmentText;

                    if (leadingSpaces === '' && beforeText !== '' && beforeText.slice(-1) === ',') {
                        leadingSpaces = ' ';
                    }

                    var newValue = beforeText + leadingSpaces + value + afterText;
                    authorInput.value = newValue;

                    var caretPosition = (beforeText + leadingSpaces + value).length;

                    if (typeof authorInput.setSelectionRange === 'function') {
                        try {
                            authorInput.setSelectionRange(caretPosition, caretPosition);
                        } catch (selectionError) {
                            // Ignore selection errors that may occur in older browsers.
                        }
                    }

                    if (typeof authorInput.focus === 'function') {
                        authorInput.focus();
                    }

                    hideAuthorSuggestions();
                    latestAuthorSegmentInfo = null;
                }

                function moveAuthorHighlight(direction) {
                    if (! filteredAuthorSuggestions.length) {
                        return;
                    }

                    var nextIndex = highlightedAuthorSuggestionIndex + direction;

                    if (nextIndex < 0) {
                        nextIndex = filteredAuthorSuggestions.length - 1;
                    } else if (nextIndex >= filteredAuthorSuggestions.length) {
                        nextIndex = 0;
                    }

                    highlightAuthorSuggestion(nextIndex);
                }

                authorInput.addEventListener('input', function() {
                    updateAuthorSuggestions();
                });

                authorInput.addEventListener('focus', function() {
                    updateAuthorSuggestions();
                });

                authorInput.addEventListener('click', function() {
                    updateAuthorSuggestions();
                });

                authorInput.addEventListener('keydown', function(event) {
                    var key = event.key || event.keyCode;
                    var suggestionsVisible = authorSuggestionsContainer.classList.contains('is-visible');

                    if (key === 'ArrowDown' || key === 40) {
                        if (! suggestionsVisible) {
                            updateAuthorSuggestions();
                        }

                        if (filteredAuthorSuggestions.length) {
                            event.preventDefault();
                            moveAuthorHighlight(1);
                        }
                    } else if (key === 'ArrowUp' || key === 38) {
                        if (filteredAuthorSuggestions.length) {
                            event.preventDefault();
                            moveAuthorHighlight(-1);
                        }
                    } else if (key === 'Enter' || key === 13) {
                        if (filteredAuthorSuggestions.length && highlightedAuthorSuggestionIndex !== -1) {
                            event.preventDefault();
                            selectAuthorSuggestion(filteredAuthorSuggestions[highlightedAuthorSuggestionIndex]);
                        }
                    } else if (key === 'Escape' || key === 'Esc' || key === 27) {
                        if (suggestionsVisible) {
                            event.preventDefault();
                            hideAuthorSuggestions();
                        }
                    } else if (key === 'Tab' || key === 9) {
                        if (filteredAuthorSuggestions.length && highlightedAuthorSuggestionIndex !== -1) {
                            selectAuthorSuggestion(filteredAuthorSuggestions[highlightedAuthorSuggestionIndex]);
                        }

                        hideAuthorSuggestions();
                    }
                });

                authorInput.addEventListener('blur', function() {
                    clearHideAuthorSuggestionsTimeout();
                    hideAuthorSuggestionsTimeoutId = window.setTimeout(function() {
                        hideAuthorSuggestions();
                    }, 150);
                });

                authorSuggestionsContainer.addEventListener('mouseenter', function() {
                    clearHideAuthorSuggestionsTimeout();
                });

                authorSuggestionsContainer.addEventListener('mouseleave', function() {
                    highlightAuthorSuggestion(-1);
                });

                authorSuggestionsContainer.addEventListener('mousedown', function(event) {
                    event.preventDefault();
                });
            }
        })();
    </script>

    <?php if ($updateSuccess): ?>
        <script>
            (function() {
                var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
                if (window.parent && window.parent !== window) {
                    window.parent.postMessage({ type: 'songUpdated', songId: <?php echo json_encode($id); ?> }, modalOrigin);
                }
            })();
        </script>
    <?php endif; ?>
</body>
</html>
