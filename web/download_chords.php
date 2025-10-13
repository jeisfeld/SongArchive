<?php
$folder = "img/songs/"; // Folder where JPG files are stored
$zipFile = "chords.zip"; // Name of the zip file to be created

// If zip file doesn't exist or is outdated, create a new one
if (!file_exists($zipFile)) {
	$zip = new ZipArchive();
	if ($zip->open($zipFile, ZipArchive::CREATE | ZipArchive::OVERWRITE) === TRUE) {
		$files = glob($folder . "*.jpg"); // Get all JPGs in the folder
		foreach ($files as $file) {
			$zip->addFile($file, basename($file)); // Add each file to the zip
		}
		$zip->close();
	} else {
		die(json_encode(["error" => "Could not create zip file"]));
	}
}

// Serve the ZIP file
header("Content-Type: application/zip");
header("Content-Disposition: attachment; filename=" . basename($zipFile));
header("Content-Length: " . filesize($zipFile));
readfile($zipFile);
exit;
?>