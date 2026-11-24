-- Minimal schema for the tested endpoints, with fictional song and author data.
CREATE TABLE songs (
    id VARCHAR(32) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    lyrics TEXT NOT NULL,
    lyrics_short TEXT,
    lyrics_paged TEXT,
    author TEXT,
    keywords TEXT,
    chordmarkup TEXT,
    tabfilename VARCHAR(255),
    mp3filename VARCHAR(255),
    mp3filename2 VARCHAR(255)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meaning (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    meaning TEXT NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE song_meaning (
    song_id VARCHAR(32) NOT NULL,
    meaning_id INT NOT NULL,
    PRIMARY KEY (song_id, meaning_id),
    FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
    FOREIGN KEY (meaning_id) REFERENCES meaning(id) ON DELETE CASCADE
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO songs (id, title, lyrics, lyrics_short, lyrics_paged, author, keywords, chordmarkup, tabfilename, mp3filename, mp3filename2)
VALUES
    ('0012a', 'Morning River', CONCAT('Morning |river', CHAR(10), 'Carry me home'), 'Carry me home', 'Morning river|Carry me home',
     'Ada Example [example.test], Ben Example', 'water', '[Am]', 'river.jpg', 'river.mp3', 'river-alternate.mp3'),
    ('0011', 'Evening Song', 'Morning river', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    ('0010', 'Morning Light', 'Warm skies', NULL, NULL, NULL, 'river', NULL, NULL, NULL, NULL),
    ('1000', 'Morning Sky', 'Bright stars', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    ('X001', 'Hidden Rehearsal', 'Practice song', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    ('Y001', 'Local Rehearsal', 'A local song', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL);

INSERT INTO meaning (id, title, meaning)
VALUES (17, 'Context', 'A fictional translation'), (18, 'Pronunciation', 'Sing gently');

INSERT INTO song_meaning (song_id, meaning_id)
VALUES ('0012a', 17), ('0012a', 18), ('X001', 18);
