<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *"); 

// Include database config
require_once "db_config.php";

if ($conn->connect_error) {
    die(json_encode(["error" => "Database connection failed"]));
}

$sql = "SELECT id, title, lyrics, author, keywords, tabfilename, mp3filename, mp3filename2 FROM songs ORDER BY id";
$result = $conn->query($sql);

$songs = [];
if ($result->num_rows > 0) {
    while ($row = $result->fetch_assoc()) {
        $songs[] = $row;
    }
}

echo json_encode($songs);
$conn->close();
?>
