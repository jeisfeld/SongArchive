<?php
header ( 'Content-Type: application/json; charset=UTF-8' );

require_once __DIR__ . '/../db_config.php';
function respondJson(int $statusCode, array $payload): void {
	http_response_code ( $statusCode );
	echo json_encode ( $payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES );
	exit ();
}

if ($_SERVER ['REQUEST_METHOD'] !== 'POST') {
	respondJson ( 405, [ 
			'error' => 'Method not allowed.'
	] );
}

$id = isset ( $_POST ['id'] ) ? trim ( ( string ) $_POST ['id'] ) : '';
$title = isset ( $_POST ['title'] ) ? trim ( ( string ) $_POST ['title'] ) : '';
$lyrics = isset ( $_POST ['lyrics'] ) ? ( string ) $_POST ['lyrics'] : '';

if ($id === '' || $title === '' || trim ( $lyrics ) === '') {
	respondJson ( 400, [ 
			'error' => 'Missing required fields.'
	] );
}

if (strpos ( $id, 'Y' ) !== 0) {
	respondJson ( 403, [ 
			'error' => 'Only song IDs starting with "Y" may be added through this endpoint.'
	] );
}

$columns = [ 
		'id',
		'title',
		'lyrics'
];
$placeholders = [ 
		'?',
		'?',
		'?'
];
$types = 'sss';
$params = [ 
		$id,
		$title,
		$lyrics
];

$optionalFields = [ 
		'lyrics_short',
		'lyrics_paged',
		'tabfilename',
		'mp3filename',
		'mp3filename2',
		'author',
		'keywords'
];

foreach ( $optionalFields as $fieldName ) {
	if (array_key_exists ( $fieldName, $_POST )) {
		$columns [] = $fieldName;
		$placeholders [] = '?';
		$types .= 's';
		$params [] = ( string ) $_POST [$fieldName];
	}
}

$insertSql = sprintf ( 'INSERT INTO songs (%s) VALUES (%s)', implode ( ', ', $columns ), implode ( ', ', $placeholders ) );

$insertStmt = $conn->prepare ( $insertSql );

if (! $insertStmt) {
	respondJson ( 500, [ 
			'error' => 'Failed to prepare insert statement.'
	] );
}

$bindParams = array_merge ( [ 
		$types
], $params );

$refs = [ ];
foreach ( $bindParams as $key => $value ) {
	$refs [$key] = &$bindParams [$key];
}

if (! call_user_func_array ( [ 
		$insertStmt,
		'bind_param'
], $refs )) {
	$insertStmt->close ();
	respondJson ( 500, [ 
			'error' => 'Failed to bind parameters.'
	] );
}

if (! $insertStmt->execute ()) {
	if ($insertStmt->errno === 1062) {
		$insertStmt->close ();
		respondJson ( 409, [ 
				'error' => 'A song with this ID already exists.'
		] );
	}

	$errorMessage = 'Failed to insert the song.';
	if ($insertStmt->error) {
		error_log ( 'addsong_external.php insert error: ' . $insertStmt->error );
	}

	$insertStmt->close ();
	respondJson ( 500, [ 
			'error' => $errorMessage
	] );
}

$insertStmt->close ();

if (isset ( $conn ) && $conn instanceof mysqli) {
	$conn->close ();
}

respondJson ( 201, [ 
		'message' => 'Song added successfully.'
] );
