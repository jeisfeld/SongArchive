<?php
// Apache sets REMOTE_USER after authenticating this directory.
if (empty ( $_SERVER ['REMOTE_USER'] )) {
	http_response_code ( 403 );
	exit;
}

header ( "Cache-Control: no-store" );
$isAdminSearch = true;
require __DIR__ . '/../search.php';
