<?php
header('Content-Type: text/html; charset=UTF-8');

require_once __DIR__ . '/../db_config.php';

function nullIfEmpty($value) {
    if ($value === null) {
        return null;
    }

    return trim($value) === '' ? null : $value;
}

function fetchSong($conn, $songId) {
    $sql = "SELECT id, title, lyrics, lyrics_short, lyrics_paged, tabfilename, mp3filename, mp3filename2, author, keywords FROM songs WHERE id = ?";
    $stmt = $conn->prepare($sql);

    if (! $stmt) {
        error_log('Failed to prepare statement for fetching song: ' . $conn->error);
        return null;
    }

    $stmt->bind_param('s', $songId);
    $stmt->execute();
    $result = $stmt->get_result();

    if (! $result) {
        error_log('Failed to fetch song result: ' . $stmt->error);
        $stmt->close();
        return null;
    }

    $song = $result->fetch_assoc();
    $stmt->close();

    return $song ?: null;
}

function escapeHtml($value) {
    return htmlspecialchars((string) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function buildFilenameSuggestions($directory, array $priorityPrefixes = []) {
    $suggestions = [
        'options' => [],
        'matching' => [],
        'directoryExists' => is_dir($directory),
    ];

    if (! $suggestions['directoryExists']) {
        return $suggestions;
    }

    $files = scandir($directory);
    if ($files === false) {
        return $suggestions;
    }

    $options = [];
    foreach ($files as $file) {
        if ($file === '.' || $file === '..') {
            continue;
        }

        $fullPath = $directory . DIRECTORY_SEPARATOR . $file;
        if (is_file($fullPath)) {
            $options[] = $file;
        }
    }

    if (empty($options)) {
        return $suggestions;
    }

    natcasesort($options);
    $options = array_values($options);

    $prefixes = [];
    foreach ($priorityPrefixes as $prefix) {
        $prefix = (string) $prefix;
        if ($prefix === '') {
            continue;
        }

        $prefixes[] = $prefix;
    }

    $prefixes = array_values(array_unique($prefixes));

    $matching = [];
    if (! empty($prefixes)) {
        foreach ($options as $filename) {
            foreach ($prefixes as $prefix) {
                if (stripos($filename, $prefix) === 0) {
                    $matching[] = $filename;
                    break;
                }
            }
        }

        $matching = array_values(array_unique($matching));

        if (! empty($matching)) {
            $options = array_merge($matching, array_values(array_diff($options, $matching)));
        }
    }

    $suggestions['options'] = $options;
    $suggestions['matching'] = $matching;

    return $suggestions;
}

function renderFilenameNote(array $matching, array $options, $prefix, $directoryExists, $folderPath) {
    if (! $directoryExists) {
        echo "<div class=\"form-note\">Folder " . escapeHtml($folderPath) . " is not available.</div>";
        return;
    }

    if (empty($options)) {
        echo "<div class=\"form-note\">No files found in " . escapeHtml($folderPath) . ".</div>";
        return;
    }

    if ($prefix !== '' && empty($matching)) {
        echo "<div class=\"form-note\">No files found in " . escapeHtml($folderPath) . " starting with " . escapeHtml($prefix) . ".</div>";
    }
}

$id = isset($_GET['id']) ? trim($_GET['id']) : '';
$successMessage = '';
$errorMessage = '';
$updateSuccess = false;
$formValues = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $id = isset($_POST['id']) ? trim($_POST['id']) : $id;
    $title = isset($_POST['title']) ? trim($_POST['title']) : '';
    $lyrics = isset($_POST['lyrics']) ? $_POST['lyrics'] : '';
    $lyricsShortInput = isset($_POST['lyrics_short']) ? $_POST['lyrics_short'] : '';
    $lyricsPagedInput = isset($_POST['lyrics_paged']) ? $_POST['lyrics_paged'] : '';
    $tabfilenameInput = isset($_POST['tabfilename']) ? trim($_POST['tabfilename']) : '';
    $mp3filenameInput = isset($_POST['mp3filename']) ? trim($_POST['mp3filename']) : '';
    $mp3filename2Input = isset($_POST['mp3filename2']) ? trim($_POST['mp3filename2']) : '';
    $authorInput = isset($_POST['author']) ? trim($_POST['author']) : '';
    $keywordsInput = isset($_POST['keywords']) ? trim($_POST['keywords']) : '';

    $lyrics_short = nullIfEmpty($lyricsShortInput);
    $lyrics_paged = nullIfEmpty($lyricsPagedInput);
    $tabfilename = nullIfEmpty($tabfilenameInput);
    $mp3filename = nullIfEmpty($mp3filenameInput);
    $mp3filename2 = nullIfEmpty($mp3filename2Input);
    $author = nullIfEmpty($authorInput);
    $keywords = nullIfEmpty($keywordsInput);

    $formValues = [
        'id' => $id,
        'title' => $title,
        'lyrics' => $lyrics,
        'lyrics_short' => $lyricsShortInput,
        'lyrics_paged' => $lyricsPagedInput,
        'tabfilename' => $tabfilenameInput,
        'mp3filename' => $mp3filenameInput,
        'mp3filename2' => $mp3filename2Input,
        'author' => $authorInput,
        'keywords' => $keywordsInput,
    ];

    if ($id === '') {
        $errorMessage = 'Missing song ID.';
    } else {
        $updateSql = "UPDATE songs SET title = ?, lyrics = ?, lyrics_short = ?, lyrics_paged = ?, tabfilename = ?, mp3filename = ?, mp3filename2 = ?, author = ?, keywords = ? WHERE id = ?";
        $updateStmt = $conn->prepare($updateSql);

        if (! $updateStmt) {
            error_log('Failed to prepare update statement: ' . $conn->error);
            $errorMessage = 'Unable to update the song at this time.';
        } else {
            $updateStmt->bind_param(
                'ssssssssss',
                $title,
                $lyrics,
                $lyrics_short,
                $lyrics_paged,
                $tabfilename,
                $mp3filename,
                $mp3filename2,
                $author,
                $keywords,
                $id
            );

            if ($updateStmt->execute()) {
                $updateSuccess = true;
                $successMessage = 'Song updated successfully.';
            } else {
                error_log('Failed to execute update statement: ' . $updateStmt->error);
                $errorMessage = 'Failed to update the song.';
            }

            $updateStmt->close();
        }
    }
}

$songFromDatabase = null;
if ($id !== '') {
    $songFromDatabase = fetchSong($conn, $id);
    if (! $songFromDatabase && $errorMessage === '') {
        $errorMessage = 'Song not found.';
    }
} elseif ($errorMessage === '') {
    $errorMessage = 'No song ID provided.';
}

if ($updateSuccess && $songFromDatabase) {
    $songData = $songFromDatabase;
} elseif ($formValues) {
    $songData = $formValues;
} else {
    $songData = $songFromDatabase;
}

$conn->close();

$songImagesDirectory = __DIR__ . '/../img/songs';
$audioSongsDirectory = __DIR__ . '/../audio/songs';
$songIdForSuggestions = isset($songData['id']) ? (string) $songData['id'] : (string) $id;

$tabFilenamePrefix = '';
$tabFilenamePrefixes = [];
if ($songIdForSuggestions !== '') {
    $tabFilenamePrefix = substr($songIdForSuggestions, 0, 4);
    if ($tabFilenamePrefix !== '') {
        $tabFilenamePrefixes[] = $tabFilenamePrefix;
    }
}

$tabSuggestions = buildFilenameSuggestions($songImagesDirectory, $tabFilenamePrefixes);
$tabFilenameOptions = $tabSuggestions['options'];
$matchingTabFilenames = $tabSuggestions['matching'];
$tabDirectoryExists = $tabSuggestions['directoryExists'];

$mp3FilenamePrefixes = [];
$mp3FilenamePrefix = '';
$trimmedSongId = $songIdForSuggestions !== '' ? ltrim($songIdForSuggestions, '0') : '';

if ($trimmedSongId !== '') {
    $mp3FilenamePrefix = $trimmedSongId;
    $mp3FilenamePrefixes[] = $trimmedSongId;
}

if ($songIdForSuggestions !== '' && $songIdForSuggestions !== $trimmedSongId) {
    $mp3FilenamePrefixes[] = $songIdForSuggestions;
}

$uniqueMp3Prefixes = [];
foreach ($mp3FilenamePrefixes as $prefix) {
    if ($prefix === '') {
        continue;
    }

    if (! in_array($prefix, $uniqueMp3Prefixes, true)) {
        $uniqueMp3Prefixes[] = $prefix;
    }
}

$mp3FilenamePrefixes = $uniqueMp3Prefixes;

if ($mp3FilenamePrefix === '' && ! empty($mp3FilenamePrefixes)) {
    $mp3FilenamePrefix = $mp3FilenamePrefixes[0];
}

$mp3Suggestions = buildFilenameSuggestions($audioSongsDirectory, $mp3FilenamePrefixes);
$mp3FilenameOptions = $mp3Suggestions['options'];
$matchingMp3Filenames = $mp3Suggestions['matching'];
$mp3DirectoryExists = $mp3Suggestions['directoryExists'];

$songIdValue = '';
if (is_array($songData) && array_key_exists('id', $songData)) {
    $songIdValue = (string) $songData['id'];
} else {
    $songIdValue = (string) $id;
}

?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Edit Song <?php echo escapeHtml($songIdValue); ?></title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f7f7f2;
            color: #333;
        }
        .edit-song-container {
            max-width: 960px;
            margin: 0 auto;
        }
        h1 {
            font-size: 1.75rem;
            margin-bottom: 20px;
        }
        .form-group {
            margin-bottom: 15px;
        }
        .form-group label {
            display: block;
            font-weight: 600;
            margin-bottom: 6px;
        }
        .form-group input[type="text"],
        .form-group textarea {
            width: 100%;
            padding: 10px;
            border: 1px solid #c5c5c5;
            border-radius: 4px;
            box-sizing: border-box;
            font-size: 1rem;
            font-family: inherit;
        }
        .form-group textarea {
            min-height: 120px;
            resize: vertical;
            line-height: 1.4;
        }
        .form-note {
            margin-top: 6px;
            font-size: 0.9rem;
            color: #555;
        }
        .short-textarea {
            min-height: 80px;
        }
        .readonly-input {
            background-color: #f0f0f0;
            color: #555;
        }
        .button-row {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 24px;
        }
        .button-row button {
            padding: 10px 18px;
            font-size: 1rem;
            border-radius: 4px;
            border: 1px solid transparent;
            cursor: pointer;
        }
        .button-row .delete-btn {
            background-color: #d9534f;
            border-color: #d43f3a;
            color: #fff;
        }
        .button-row .delete-btn:hover {
            background-color: #c9302c;
        }
        .button-row .save-btn {
            background-color: #4CAF50;
            border-color: #4CAF50;
            color: #fff;
        }
        .button-row .save-btn:hover {
            background-color: #45a049;
        }
        .button-row .cancel-btn {
            background-color: #f0f0f0;
            border-color: #ccc;
            color: #333;
        }
        .button-row .cancel-btn:hover {
            background-color: #e0e0e0;
        }
        .alert {
            padding: 12px 16px;
            border-radius: 4px;
            margin-bottom: 18px;
            border: 1px solid transparent;
        }
        .alert-success {
            background-color: #d4edda;
            border-color: #c3e6cb;
            color: #155724;
        }
        .alert-error {
            background-color: #f8d7da;
            border-color: #f5c6cb;
            color: #721c24;
        }
    </style>
</head>
<body>
    <div class="edit-song-container">
        <h1>Edit Song <?php echo escapeHtml($songIdValue); ?></h1>

        <?php if ($successMessage): ?>
            <div class="alert alert-success"><?php echo escapeHtml($successMessage); ?></div>
        <?php endif; ?>

        <?php if ($errorMessage): ?>
            <div class="alert alert-error"><?php echo escapeHtml($errorMessage); ?></div>
        <?php endif; ?>

        <?php if ($songData): ?>
            <form method="post" id="edit-song-form">
                <input type="hidden" name="id" value="<?php echo escapeHtml($songIdValue); ?>">

                <div class="form-group">
                    <label for="song-id">ID</label>
                    <input type="text" id="song-id" class="readonly-input" value="<?php echo escapeHtml($songIdValue); ?>" readonly>
                </div>

                <div class="form-group">
                    <label for="song-title">Title</label>
                    <input type="text" id="song-title" name="title" value="<?php echo escapeHtml($songData['title'] ?? ''); ?>">
                </div>

                <div class="form-group">
                    <label for="song-lyrics">Lyrics</label>
                    <textarea id="song-lyrics" name="lyrics" rows="12"><?php echo htmlspecialchars($songData['lyrics'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
                </div>

                <div class="form-group">
                    <label for="song-lyrics-short">Lyrics (Short)</label>
                    <textarea id="song-lyrics-short" name="lyrics_short" class="short-textarea" rows="6"><?php echo htmlspecialchars($songData['lyrics_short'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
                </div>

                <div class="form-group">
                    <label for="song-lyrics-paged">Lyrics (Paged)</label>
                    <textarea id="song-lyrics-paged" name="lyrics_paged" class="short-textarea" rows="6"><?php echo htmlspecialchars($songData['lyrics_paged'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
                </div>

                <div class="form-group">
                    <label for="tabfilename">Tab Filename</label>
                    <input type="text" id="tabfilename" name="tabfilename" list="tabfilename-options" value="<?php echo escapeHtml($songData['tabfilename'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingTabFilenames, $tabFilenameOptions, $tabFilenamePrefix, $tabDirectoryExists, 'img/songs/'); ?>
                </div>

                <datalist id="tabfilename-options">
                    <?php foreach ($tabFilenameOptions as $filenameOption): ?>
                        <option value="<?php echo escapeHtml($filenameOption); ?>"></option>
                    <?php endforeach; ?>
                </datalist>

                <div class="form-group">
                    <label for="mp3filename">MP3 Filename</label>
                    <input type="text" id="mp3filename" name="mp3filename" list="mp3filename-options" value="<?php echo escapeHtml($songData['mp3filename'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingMp3Filenames, $mp3FilenameOptions, $mp3FilenamePrefix, $mp3DirectoryExists, 'audio/songs/'); ?>
                </div>

                <div class="form-group">
                    <label for="mp3filename2">MP3 Filename 2</label>
                    <input type="text" id="mp3filename2" name="mp3filename2" list="mp3filename-options" value="<?php echo escapeHtml($songData['mp3filename2'] ?? ''); ?>">
                    <?php renderFilenameNote($matchingMp3Filenames, $mp3FilenameOptions, $mp3FilenamePrefix, $mp3DirectoryExists, 'audio/songs/'); ?>
                </div>

                <datalist id="mp3filename-options">
                    <?php foreach ($mp3FilenameOptions as $filenameOption): ?>
                        <option value="<?php echo escapeHtml($filenameOption); ?>"></option>
                    <?php endforeach; ?>
                </datalist>

                <div class="form-group">
                    <label for="song-author">Author(s)</label>
                    <input type="text" id="song-author" name="author" value="<?php echo escapeHtml($songData['author'] ?? ''); ?>">
                </div>

                <div class="form-group">
                    <label for="song-keywords">Keywords</label>
                    <input type="text" id="song-keywords" name="keywords" value="<?php echo escapeHtml($songData['keywords'] ?? ''); ?>">
                </div>

                <div class="button-row">
                    <button type="button" class="cancel-btn" id="cancel-edit">Cancel</button>
                    <?php if ($songData && $songIdValue !== ''): ?>
                        <button type="button" class="delete-btn" id="delete-song">Delete</button>
                    <?php endif; ?>
                    <button type="submit" class="save-btn">Save</button>
                </div>
            </form>
        <?php elseif (! $errorMessage): ?>
            <p>No song data available.</p>
        <?php endif; ?>
    </div>

    <script>
        (function() {
            var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
            var cancelButton = document.getElementById('cancel-edit');
            if (cancelButton) {
                cancelButton.addEventListener('click', function() {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({ type: 'closeEditModal' }, modalOrigin);
                    } else if (document.referrer) {
                        window.location.href = document.referrer;
                    } else {
                        window.location.href = 'index.html';
                    }
                });
            }

            var deleteButton = document.getElementById('delete-song');
            if (deleteButton) {
                var deleteUrl = <?php echo json_encode('deletesong.php?id=' . rawurlencode($songIdValue)); ?>;
                deleteButton.addEventListener('click', function() {
                    window.location.href = deleteUrl;
                });
            }
        })();
    </script>

    <?php if ($updateSuccess): ?>
        <script>
            (function() {
                var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
                if (window.parent && window.parent !== window) {
                    window.parent.postMessage({ type: 'songUpdated', songId: <?php echo json_encode($id); ?> }, modalOrigin);
                }
            })();
        </script>
    <?php endif; ?>
</body>
</html>
