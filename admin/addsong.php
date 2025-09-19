<?php
header('Content-Type: text/html; charset=UTF-8');

require_once __DIR__ . '/../db_config.php';

function escapeHtml($value)
{
    return htmlspecialchars((string) ($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

$formValues = [
    'id' => '',
    'title' => '',
    'lyrics' => '',
];

$errorMessage = '';
$insertSuccess = false;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $formValues['id'] = isset($_POST['id']) ? trim($_POST['id']) : '';
    $formValues['title'] = isset($_POST['title']) ? trim($_POST['title']) : '';
    $lyricsInput = isset($_POST['lyrics']) ? $_POST['lyrics'] : '';
    $formValues['lyrics'] = $lyricsInput;

    if ($formValues['id'] === '' || $formValues['title'] === '' || trim($lyricsInput) === '') {
        $errorMessage = 'Please fill in all required fields.';
    } else {
        $insertSql = 'INSERT INTO songs (id, title, lyrics) VALUES (?, ?, ?)';
        $insertStmt = $conn->prepare($insertSql);

        if (! $insertStmt) {
            error_log('Failed to prepare insert statement: ' . $conn->error);
            $errorMessage = 'Unable to prepare the song for saving.';
        } else {
            $insertStmt->bind_param('sss', $formValues['id'], $formValues['title'], $lyricsInput);

            if ($insertStmt->execute()) {
                $insertSuccess = true;
            } else {
                if ($insertStmt->errno === 1062) {
                    $errorMessage = 'A song with this ID already exists.';
                } else {
                    error_log('Failed to execute insert statement: ' . $insertStmt->error);
                    $errorMessage = 'Failed to save the song.';
                }
            }

            $insertStmt->close();
        }
    }
}

if (isset($conn) && $conn instanceof mysqli) {
    $conn->close();
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Add New Song</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f7f7f2;
            color: #333;
        }
        .add-song-container {
            max-width: 720px;
            margin: 0 auto;
        }
        h1 {
            font-size: 1.75rem;
            margin-bottom: 16px;
        }
        .form-group {
            margin-bottom: 15px;
        }
        .form-group label {
            display: block;
            font-weight: 600;
            margin-bottom: 6px;
        }
        .required-indicator {
            color: #d9534f;
            margin-left: 4px;
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
            min-height: 200px;
            resize: vertical;
            line-height: 1.4;
        }
        .form-note {
            font-size: 0.9rem;
            color: #555;
            margin-top: 4px;
        }
        .button-row {
            display: flex;
            justify-content: flex-end;
            gap: 10px;
            margin-top: 20px;
        }
        .button-row button {
            padding: 10px 18px;
            font-size: 1rem;
            border-radius: 4px;
            border: 1px solid transparent;
            cursor: pointer;
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
        .alert-error {
            background-color: #f8d7da;
            border-color: #f5c6cb;
            color: #721c24;
        }
    </style>
</head>
<body>
    <div class="add-song-container">
        <h1>Add New Song</h1>
        <p class="form-note">Fields marked with <span class="required-indicator">*</span> are required.</p>

        <?php if ($errorMessage): ?>
            <div class="alert alert-error"><?php echo escapeHtml($errorMessage); ?></div>
        <?php endif; ?>

        <form method="post">
            <div class="form-group">
                <label for="song-id">Song ID <span class="required-indicator">*</span></label>
                <input type="text" id="song-id" name="id" value="<?php echo escapeHtml($formValues['id']); ?>" required>
            </div>

            <div class="form-group">
                <label for="song-title">Title <span class="required-indicator">*</span></label>
                <input type="text" id="song-title" name="title" value="<?php echo escapeHtml($formValues['title']); ?>" required>
            </div>

            <div class="form-group">
                <label for="song-lyrics">Lyrics <span class="required-indicator">*</span></label>
                <textarea id="song-lyrics" name="lyrics" required><?php echo escapeHtml($formValues['lyrics']); ?></textarea>
            </div>

            <div class="button-row">
                <button type="button" class="cancel-btn" id="cancel-add">Cancel</button>
                <button type="submit" class="save-btn">Save</button>
            </div>
        </form>
    </div>

    <script>
        (function() {
            var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
            var cancelButton = document.getElementById('cancel-add');
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

            var idInput = document.getElementById('song-id');
            if (idInput) {
                idInput.focus();
                idInput.select();
            }
        })();
    </script>

    <?php if ($insertSuccess): ?>
        <script>
            (function() {
                var modalOrigin = window.location.origin || (window.location.protocol + '//' + window.location.host);
                if (window.parent && window.parent !== window) {
                    window.parent.postMessage({ type: 'songInserted', songId: <?php echo json_encode($formValues['id']); ?> }, modalOrigin);
                } else {
                    window.location.href = <?php echo json_encode('/admin/editsong.php?id=' . rawurlencode($formValues['id'])); ?>;
                }
            })();
        </script>
    <?php endif; ?>
</body>
</html>
