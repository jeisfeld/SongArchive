<?php
session_set_cookie_params ( [
	'path' => '/',
	'httponly' => true,
	'samesite' => 'Lax'
] );
session_start ();

$_SESSION ['include_hidden_search'] = true;
if (isset ( $_SERVER ['REMOTE_USER'] )) {
	$_SESSION ['vip_user'] = $_SERVER ['REMOTE_USER'];
}

$targetUrl = '/';
if (isset ( $_GET ['q'] ) && trim ( $_GET ['q'] ) !== '') {
	$targetUrl .= '?q=' . rawurlencode ( trim ( $_GET ['q'] ) );
}

header ( 'Cache-Control: no-store' );
header ( 'Location: ' . $targetUrl, true, 302 );
exit;
?>
