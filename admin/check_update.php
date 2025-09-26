<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *"); 

// Include database config
require_once "../db_config.php";

if ($conn->connect_error) {
    die(json_encode(["error" => "Database connection failed"]));
}

$response = [
		'tab_count' => null,
		'chords_zip_size' => null,
];

$sql = "SELECT COUNT(*) as count FROM songs WHERE tabfilename IS NOT NULL";
$result = $conn->query($sql);
if ($result) {
	$row = $result->fetch_assoc();
	$response['tab_count'] = (int) $row['count'];
}
$conn->close();

$zipPath = 'chords.zip';
if (file_exists($zipPath)) {
	$response['chords_zip_size'] = filesize($zipPath);
}

// Send JSON response
echo json_encode($response);
?>
