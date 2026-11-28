<?php
function viewableChordMarkup(?string $markup): ?string {
	if ($markup === null) {
		return null;
	}
	$sheet = json_decode ( $markup );
	if (($sheet->enabled ?? null) === false || ! in_array ( $sheet->version ?? null, [1, 1.0], true )
			|| ! is_string ( $sheet->title ?? null ) || ! is_array ( $sheet->details ?? null ) || ! is_array ( $sheet->lines ?? null )) {
		return null;
	}
	foreach ( $sheet->details as $detail ) {
		if (! is_string ( $detail )) {
			return null;
		}
	}
	$hasChords = false;
	foreach ( $sheet->lines as $line ) {
		$kind = $line->kind ?? null;
		$aside = $line->aside ?? '';
		if (! in_array ( $kind, ['chords', 'lyrics', 'note', 'pitch'], true ) || ! is_array ( $line->runs ?? null ) || ! is_string ( $aside )) {
			return null;
		}
		$hasText = trim ( $aside ) !== '';
		foreach ( $line->runs as $run ) {
			if (! is_string ( $run->text ?? null )) {
				return null;
			}
			$hasText = $hasText || trim ( $run->text ) !== '';
		}
		$hasChords = $hasChords || ($kind === 'chords' && $hasText);
	}
	return $hasChords ? $markup : null;
}

// Both queries supply songs in ID order. Ignore rows without chord markup.
function chordMarkupRevision(iterable $songs): string {
	$hash = hash_init ( 'sha256' );
	foreach ( $songs as $song ) {
		$markup = trim ( $song ['chordmarkup'] ?? '' );
		if ($markup !== '') {
			hash_update ( $hash, $song ['id'] . "\0" . $markup . "\0" );
		}
	}
	return hash_final ( $hash );
}
