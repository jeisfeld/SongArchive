<?php
header ( "Access-Control-Allow-Origin: *" );
header ( "Content-Type: application/json" );

// Include database config
require_once "db_config.php";

// Get query
$query = isset ( $_GET ['q'] ) ? trim ( $_GET ['q'] ) : "";

// Use regex to check if query is a valid ID (numeric OR numeric + single letter)
$is_valid_id = preg_match ( '/^[X\d]\d{3}[a-zA-Z]?$/', $query );
$isSingleLetter = preg_match ( '/^[a-zA-Z]$/', $query );

function interpolateQuery($query, $params) {
	foreach ($params as $key => $value) {
		// Escape string values and format for SQL
		$replacement = is_numeric($value) ? $value : "'" . addslashes($value) . "'";
		// Support both named (:param) and positional (?) placeholders
		$query = preg_replace('/\?/', $replacement, $query, 1);
		$query = str_replace(":$key", $replacement, $query);
	}
	// Replace newlines, tabs, and multiple spaces with a single space
	return preg_replace('/\s+/', ' ', trim($query));
}

// SQL Query for valid id
if ($is_valid_id) {
	// If searching by ID
	$sql = "SELECT s.id, s.title, s.lyrics, s.lyrics_short, s.tabfilename, s.mp3filename, s.mp3filename2, s.author,
               m.title AS meaning_title, m.meaning AS meaning_text
        FROM songs s
        LEFT JOIN song_meaning sm ON s.id = sm.song_id
        LEFT JOIN meaning m ON sm.meaning_id = m.id
        WHERE s.id = ?";

	$stmt = $conn->prepare ( $sql );
	$stmt->bind_param ( "s", $query );
	$stmt->execute ();
	$result = $stmt->get_result ();

	// Fetch data
	$songs = [ ];

	while ( $row = $result->fetch_assoc () ) {
		$song_id = $row ['id'];

		// If song is not yet in the array, initialize it
		if (! isset ( $songs [$song_id] )) {
			$songs [$song_id] = [ 
					'id' => $row ['id'],
					'title' => $row ['title'],
					'lyrics' => $row ['lyrics'],
					'lyrics_short' => $row ['lyrics_short'],
					'tabfilename' => $row ['tabfilename'],
					'mp3filename' => $row ['mp3filename'],
					'mp3filename2' => $row ['mp3filename2'],
					'author' => $row ['author'],
					'meanings' => [ ] // Initialize meanings array
			];
		}

		// Add meaning if it's not null (avoiding empty records)
		if ($row ['meaning_title'] !== null) {
			$songs [$song_id] ['meanings'] [] = [ 
					'title' => $row ['meaning_title'],
					'meaning' => $row ['meaning_text']
			];
		}
	}

	// Return JSON
	echo json_encode ( array_values ( $songs ), JSON_PRETTY_PRINT );
	$stmt->close ();
	$conn->close ();
	return;
}

// now the case where input is not exact id.
if ($query === "*" || $query === "") {
	$sql = "SELECT id, title, tabfilename, mp3filename, mp3filename2, author FROM songs where id not like 'X%' order by id";
}
else if ($isSingleLetter) {
	$sql = "SELECT id, title, author, tabfilename, mp3filename, mp3filename2, author FROM songs
            WHERE (title REGEXP CONCAT('(^| )', ?, '.*')
               OR lyrics REGEXP CONCAT('(^| )', ?, '.*'))
			AND id not like 'X%'
            ORDER BY CASE WHEN title REGEXP CONCAT('(^| )', ?, '.*') THEN 1 ELSE 2 END, CAST(id AS UNSIGNED) ASC";
	$params = [ 
			$query,
			$query,
			$query
	];
	$types = "sss";
}
else {
	$normalizedQuery = str_replace ( [ 
			"´",
			"`",
			"’"
	], "'", $query );

	$words = explode ( " ", $normalizedQuery );
	$conditions = [ "id not like 'X%'" ];
	$titleMatch = [ ];
	$lyricsMatch = [ ];
	$selectParams = [ ];
	$whereParams = [ ];
	$types = "";

	$normalizedTitle = "REPLACE(REPLACE(REPLACE(title, '´', ''''), '`', ''''), '’', '''')";
	$normalizedLyrics = "REPLACE(REPLACE(REPLACE(lyrics, '´', ''''), '`', ''''), '’', '''')";
	$normalizedAuthor = "REPLACE(REPLACE(REPLACE(author, '´', ''''), '`', ''''), '’', '''')";
	$normalizedKeywords = "REPLACE(REPLACE(REPLACE(keywords, '´', ''''), '`', ''''), '’', '''')";
	
	// Full-string match (apply replacement)
	$fullMatchTitle = "IF($normalizedTitle LIKE ? OR $normalizedTitle LIKE ? OR $normalizedTitle LIKE ?, 1, 0)";
	$fullMatchLyrics = "IF($normalizedLyrics LIKE ? OR $normalizedLyrics LIKE ? OR $normalizedLyrics LIKE ?, 1, 0)";
	$fullMatchKeywords = "IF($normalizedKeywords LIKE ? OR $normalizedKeywords LIKE ? OR $normalizedKeywords LIKE ?, 1, 0)";
	
	// Full phrase match params
	$selectParams [] = $normalizedQuery . "%"; // Full match title
	$selectParams [] = "% " . $normalizedQuery . "%";
	$selectParams [] = "%\n" . $normalizedQuery . "%";
	$selectParams [] = $normalizedQuery . "%"; // Full match lyrics
	$selectParams [] = "% " . $normalizedQuery . "%";
	$selectParams [] = "%\n" . $normalizedQuery . "%";
	$selectParams [] = $normalizedQuery . "%"; // Full match keywords
	$selectParams [] = "% " . $normalizedQuery . "%";
	$selectParams [] = "%\n" . $normalizedQuery . "%";
	$types .= "sssssssss";

	$selectParamsMatch = [ ];
	$selectParamsWordMatch = [ ];
	
	// Process each word separately for WHERE and ORDER BY
	foreach ( $words as $word ) {
		// WHERE conditions
		$conditions [] = "(id LIKE ? OR $normalizedTitle LIKE ? OR $normalizedLyrics LIKE ? OR $normalizedAuthor LIKE ? OR $normalizedKeywords LIKE ?)";
		$whereParams [] = "%" . $word . "%";
		$whereParams [] = "%" . $word . "%";
		$whereParams [] = "%" . $word . "%";
		$whereParams [] = "%" . $word . "%";
		$whereParams [] = "%" . $word . "%";
		$types .= "sssss";

		// ORDER BY conditions
		$titleWordMatch [] = "IF($normalizedTitle LIKE ? OR $normalizedTitle LIKE ? OR $normalizedTitle LIKE ?, 1, 0)";
		$lyricsWordMatch [] = "IF($normalizedLyrics LIKE ? OR $normalizedLyrics LIKE ? OR $normalizedLyrics LIKE ?, 1, 0)";
		$authorWordMatch [] = "IF($normalizedAuthor LIKE ? OR $normalizedAuthor LIKE ? OR $normalizedAuthor LIKE ?, 1, 0)";
		$keywordsWordMatch [] = "IF($normalizedKeywords LIKE ? OR $normalizedKeywords LIKE ? OR $normalizedKeywords LIKE ?, 1, 0)";
		$titleMatch [] = "IF($normalizedTitle LIKE ?, 1, 0)";
		$lyricsMatch [] = "IF($normalizedLyrics LIKE ?, 1, 0)";
		$selectParamsWordMatch [] = $word . "%";
		$selectParamsWordMatch [] = "% " . $word . "%";
		$selectParamsWordMatch [] = "%\n" . $word . "%";
		$selectParamsMatch [] = "%" . $word . "%";
		$types .= "ssssssssssssss";
	}

	$selectParams = array_merge($selectParams, $selectParamsWordMatch); // titleWordMatch
	$selectParams = array_merge($selectParams, $selectParamsWordMatch); // lyricsWordMatch
	$selectParams = array_merge($selectParams, $selectParamsWordMatch); // authorWordMatch
	$selectParams = array_merge($selectParams, $selectParamsWordMatch); // keywordsWordMatch
	$selectParams = array_merge($selectParams, $selectParamsMatch); // titleMatch
	$selectParams = array_merge($selectParams, $selectParamsMatch); // lyricsMatch
	
	// Construct SQL query with ranking priority
	$sql = "SELECT id, title, tabfilename, mp3filename, mp3filename2, author, keywords,
                ($fullMatchTitle) AS full_title_match,
                ($fullMatchLyrics) AS full_lyrics_match,
                ($fullMatchKeywords) AS full_keywords_match,
                (" . implode ( " + ", $titleWordMatch ) . ") AS title_word_match_count,
                (" . implode ( " + ", $lyricsWordMatch ) . ") AS lyrics_word_match_count,
                (" . implode ( " + ", $authorWordMatch ) . ") AS author_word_match_count,
                (" . implode ( " + ", $keywordsWordMatch ) . ") AS keywords_word_match_count,
                (" . implode ( " + ", $titleMatch ) . ") AS title_match_count,
                (" . implode ( " + ", $lyricsMatch ) . ") AS lyrics_match_count
            FROM songs
            WHERE " . implode ( " AND ", $conditions ) . "
            ORDER BY
                full_title_match DESC,
                full_lyrics_match DESC,
                full_keywords_match DESC,
                title_word_match_count DESC,
                lyrics_word_match_count DESC,
                author_word_match_count DESC,
                keywords_word_match_count DESC,
                title_match_count DESC,
                lyrics_match_count DESC,
                id ASC";

	$params = array_merge ( $selectParams, $whereParams );
}

// Execute SQL
$stmt = $conn->prepare ( $sql );
if (@$params) {
	$stmt->bind_param ( $types, ...$params );
}
$stmt->execute ();
$result = $stmt->get_result ();

// Fetch data
$songs = [ ];
while ( $row = $result->fetch_assoc () ) {
	$songs [] = $row;
}
// error_log ( "Result size for " . $query . ": " . count ( $songs ) );
// error_log ( "SQL: " . interpolateQuery($sql, $params));

// Return JSON
echo json_encode ( $songs );
$stmt->close ();
$conn->close ();
?>
