<?php
// This config is copied only into the disposable test image.
mysqli_report(MYSQLI_REPORT_OFF);
$conn = new mysqli('database', 'archive_fixture', 'fixture-password', 'archive_fixture');
$conn->set_charset('utf8mb4');
