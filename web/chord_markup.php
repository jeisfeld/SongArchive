<?php
function chordMarkupReleased(): bool {
	return defined ( 'SONGARCHIVE_CHORD_MARKUP_RELEASED' ) && SONGARCHIVE_CHORD_MARKUP_RELEASED === true;
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
