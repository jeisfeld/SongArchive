<?php
header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store');

function extractionError(int $status, string $message): void {
	http_response_code($status);
	echo json_encode(['error' => $message]);
	exit;
}

if (empty($_SERVER['REMOTE_USER'])) {
	extractionError(403, 'Admin authentication is required.');
}
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
	header('Allow: POST');
	extractionError(405, 'Use POST to extract a chord sheet.');
}

$id = $_POST['id'] ?? null;
if (!is_string($id) || !preg_match('/\A[XY0-9][0-9]{3}[a-z]?\z/iD', $id)) {
	extractionError(400, 'This song ID is outside the Word importer’s supported format.');
}
$id = strtoupper(substr($id, 0, 1)) . strtolower(substr($id, 1));

$document = dirname(__DIR__) . '/Chants.docx';
if (!is_file($document) || !is_readable($document)) {
	extractionError(404, 'Place a readable Chants.docx in the website root.');
}

$importer = dirname(__DIR__, 2) . '/tools/import_chants.py';
if (!function_exists('proc_open') || !is_file($importer) || !is_readable($importer)) {
	extractionError(503, 'The Word importer is unavailable on this server.');
}

$diagnostics = tmpfile();
if ($diagnostics === false) {
	extractionError(503, 'The Word importer could not start.');
}

$process = proc_open(
	['python3', $importer, $document, '--song', $id],
	[0 => ['file', '/dev/null', 'r'], 1 => ['pipe', 'w'], 2 => $diagnostics],
	$pipes
);
if (!is_resource($process)) {
	fclose($diagnostics);
	extractionError(503, 'The Word importer could not start.');
}
$output = stream_get_contents($pipes[1]);
fclose($pipes[1]);
$status = proc_close($process);
rewind($diagnostics);
$details = stream_get_contents($diagnostics);
fclose($diagnostics);

if ($status === 3) {
	extractionError(404, 'No chord sheet for ' . $id . ' was found in Chants.docx.');
}
if ($status !== 0) {
	error_log('Word chord extraction failed: ' . trim($details));
	if ($status === 127) {
		extractionError(503, 'Python 3 is unavailable on this server.');
	}
	extractionError(422, 'Could not extract chord markup. Check Chants.docx and the song variant map.');
}

require_once __DIR__ . '/../chord_markup.php';
$sheets = $output === false ? null : json_decode($output);
$sheet = $sheets->{$id} ?? null;
if (!validChordSheet($sheet)) {
	extractionError(422, 'The Word importer did not return a valid chord sheet.');
}
echo json_encode(['sheet' => $sheet], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
