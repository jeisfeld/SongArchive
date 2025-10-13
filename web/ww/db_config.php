<?php
// Database configuration
$DB_HOST = "rdbms.strato.de";
$DB_USER = "dbu411433";
$DB_PASS = "NYzVlqfDrdVHpBBUTZFSVZWXd";
$DB_NAME = "dbs13808393";

// Create a MySQL connection
$conn = new mysqli($DB_HOST, $DB_USER, $DB_PASS, $DB_NAME);

// Check the connection
if ($conn->connect_error) {
    die(json_encode(["error" => "Database connection failed"]));
}
?>
