<?php
header('Content-Type: text/html; charset=UTF-8');

require_once __DIR__ . '/../db_config.php';

function escapeHtml($value)
{
    return htmlspecialchars((string) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function fetchSong($conn, $songId)
{
    $sql = 'SELECT id, title, lyrics FROM songs WHERE id = ?';
    $stmt = $conn->prepare($sql);

    if (! $stmt) {
        error_log('Failed to prepare statement for fetching song: ' . $conn->error);
        return null;
    }

    $stmt->bind_param('s', $songId);
    if (! $stmt->execute()) {
        error_log('Failed to execute fetch statement: ' . $stmt->error);
        $stmt->close();
        return null;
    }

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

$id = isset($_GET['id']) ? trim($_GET['id']) : '';
$errorMessage = '';
$successMessage = '';
$deleteCompleted = false;
$confirmDeleteChecked = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $id = isset($_POST['id']) ? trim($_POST['id']) : $id;
    $confirmDeleteChecked = isset($_POST['confirm_delete']);
}

$songData = null;
if ($id !== '') {
    $songData = fetchSong($conn, $id);
    if (! $songData && $_SERVER['REQUEST_METHOD'] !== 'POST') {
        $errorMessage = 'Song not found.';
    }
} elseif ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $errorMessage = 'No song ID provided.';
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if ($id === '') {
        $errorMessage = 'Missing song ID.';
    } elseif (! $confirmDeleteChecked) {
        $errorMessage = 'Please confirm the deletion by checking the checkbox.';
    } elseif (! $songData) {
        $errorMessage = 'Song not found.';
    } else {
        $deleteStmt = $conn->prepare('DELETE FROM songs WHERE id = ?');

        if (! $deleteStmt) {
            error_log('Failed to prepare delete statement: ' . $conn->error);
            $errorMessage = 'Unable to delete the song at this time.';
        } else {
            $deleteStmt->bind_param('s', $id);

            if ($deleteStmt->execute()) {
                if ($deleteStmt->affected_rows > 0) {
                    $deleteCompleted = true;
                    $successMessage = 'Song deleted successfully.';
                } else {
                    $errorMessage = 'Song not found or already deleted.';
                }
            } else {
                error_log('Failed to execute delete statement: ' . $deleteStmt->error);
                $errorMessage = 'Failed to delete the song.';
            }

            $deleteStmt->close();
        }
    }
}

if (isset($conn) && $conn instanceof mysqli) {
    $conn->close();
}

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
    <title>Delete Song <?php echo escapeHtml($songIdValue); ?></title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f7f7f2;
            color: #333;
        }
        .delete-song-container {
            max-width: 720px;
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
            min-height: 160px;
            resize: vertical;
            line-height: 1.4;
        }
        .readonly-input {
            background-color: #f0f0f0;
            color: #555;
        }
        .checkbox-group {
            margin-top: 20px;
            margin-bottom: 20px;
        }
        .checkbox-label {
            display: flex;
            align-items: center;
            gap: 10px;
            font-size: 1rem;
        }
        .checkbox-label input[type="checkbox"] {
            width: 18px;
            height: 18px;
        }
        .button-row {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 10px;
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
        .button-row .delete-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }
        .button-row .delete-btn:not(:disabled):hover {
            background-color: #c9302c;
        }
        .button-row .cancel-btn {
            background-color: #f0f0f0;
            border-color: #ccc;
            color: #333;
        }
        .button-row .cancel-btn:hover {
            background-color: #e0e0e0;
        }
        .button-row .close-btn {
            background-color: #4CAF50;
            border-color: #4CAF50;
            color: #fff;
        }
        .button-row .close-btn:hover {
            background-color: #45a049;
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
    <div class="delete-song-container">
        <h1>Delete Song <?php echo escapeHtml($songIdValue); ?></h1>

        <?php if ($successMessage): ?>
            <div class="alert alert-success"><?php echo escapeHtml($successMessage); ?></div>
        <?php endif; ?>

        <?php if ($errorMessage): ?>
            <div class="alert alert-error"><?php echo escapeHtml($errorMessage); ?></div>
        <?php endif; ?>

        <?php if ($songData): ?>
            <div class="song-details">
                <div class="form-group">
                    <label for="song-id">ID</label>
                    <input type="text" id="song-id" class="readonly-input" value="<?php echo escapeHtml($songData['id']); ?>" readonly>
                </div>

                <div class="form-group">
                    <label for="song-title">Title</label>
                    <input type="text" id="song-title" class="readonly-input" value="<?php echo escapeHtml($songData['title'] ?? ''); ?>" readonly>
                </div>

                <div class="form-group">
                    <label for="song-lyrics">Lyrics</label>
                    <textarea id="song-lyrics" class="readonly-input" readonly><?php echo htmlspecialchars($songData['lyrics'] ?? '', ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); ?></textarea>
                </div>
            </div>
        <?php endif; ?>

        <?php if ($songData && ! $deleteCompleted): ?>
            <form method="post" class="delete-form">
                <input type="hidden" name="id" value="<?php echo escapeHtml($songData['id']); ?>">

                <div class="checkbox-group">
                    <label class="checkbox-label" for="confirm-delete-checkbox">
                        <input type="checkbox" name="confirm_delete" id="confirm-delete-checkbox" <?php echo $confirmDeleteChecked ? 'checked' : ''; ?>>
                        <span>I understand that this action will permanently delete this song.</span>
                    </label>
                </div>

                <div class="button-row">
                    <button type="button" class="cancel-btn" id="cancel-delete">Cancel</button>
                    <button type="submit" class="delete-btn" id="confirm-delete-btn" <?php echo $confirmDeleteChecked ? '' : 'disabled'; ?>>Delete</button>
                </div>
            </form>
        <?php elseif (! $songData && ! $errorMessage): ?>
            <p>No song data available.</p>
        <?php endif; ?>

        <?php if ($deleteCompleted || (! $songData && $errorMessage)): ?>
            <div class="button-row">
                <button type="button" class="close-btn" id="close-delete">Close</button>
            </div>
        <?php endif; ?>
    </div>

    <script>
        (function() {
            var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
            var confirmCheckbox = document.getElementById('confirm-delete-checkbox');
            var deleteButton = document.getElementById('confirm-delete-btn');

            if (confirmCheckbox && deleteButton) {
                deleteButton.disabled = !confirmCheckbox.checked;
                confirmCheckbox.addEventListener('change', function() {
                    deleteButton.disabled = !confirmCheckbox.checked;
                });
            }

            var cancelButton = document.getElementById('cancel-delete');
            if (cancelButton) {
                var editUrl = <?php echo json_encode('editsong.php?id=' . rawurlencode($songIdValue)); ?>;
                cancelButton.addEventListener('click', function() {
                    if (editUrl) {
                        window.location.href = editUrl;
                    } else if (document.referrer) {
                        window.location.href = document.referrer;
                    } else {
                        window.location.href = 'index.html';
                    }
                });
            }

            var closeButton = document.getElementById('close-delete');
            if (closeButton) {
                closeButton.addEventListener('click', function() {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({ type: 'closeEditModal' }, modalOrigin);
                    } else if (document.referrer) {
                        window.location.href = document.referrer;
                    } else {
                        window.location.href = 'index.html';
                    }
                });
            }
        })();
    </script>

    <?php if ($deleteCompleted): ?>
        <script>
            (function() {
                var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
                if (window.parent && window.parent !== window) {
                    window.parent.postMessage({ type: 'songDeleted', songId: <?php echo json_encode($songIdValue); ?> }, modalOrigin);
                }
            })();
        </script>
    <?php endif; ?>
</body>
</html>
