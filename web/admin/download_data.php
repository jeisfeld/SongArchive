<?php
header("Content-Type: application/json");
header("Access-Control-Allow-Origin: *"); 

// Include database config
require_once "../db_config.php";

if ($conn->connect_error) {
    die(json_encode(["error" => "Database connection failed"]));
}

$response = [];

// Get flag
$user = isset ( $_GET ['user'] ) ? trim ( $_GET ['user'] ) : "";
if ($user == 'private') {
	$sqladd = "";
}
else {
	$sqladd = " where id not like 'X%'";
}

// Fetch songs
$sql_songs = "SELECT id, title, lyrics, lyrics_short, lyrics_paged, author, keywords, tabfilename, mp3filename, mp3filename2 FROM songs" . $sqladd . " ORDER BY id";
$result_songs = $conn->query($sql_songs);
$songs = [];
if ($result_songs->num_rows > 0) {
	while ($row = $result_songs->fetch_assoc()) {
		$songs[] = $row;
	}
}
$response['songs'] = $songs;

// Fetch meaning
$sql_meaning = "SELECT id, title, meaning FROM meaning ORDER BY id";
$result_meaning = $conn->query($sql_meaning);
$meanings = [];
if ($result_meaning->num_rows > 0) {
	while ($row = $result_meaning->fetch_assoc()) {
		$meanings[] = $row;
	}
}
$response['meanings'] = $meanings;

// Fetch song_meaning relationships
$sql_song_meaning = "SELECT song_id, meaning_id FROM song_meaning ORDER BY song_id, meaning_id";
$result_song_meaning = $conn->query($sql_song_meaning);
$song_meanings = [];
if ($result_song_meaning->num_rows > 0) {
	while ($row = $result_song_meaning->fetch_assoc()) {
		$song_meanings[] = $row;
	}
}
$response['song_meanings'] = $song_meanings;

// Send JSON response
echo json_encode($response);

$conn->close();
?>
