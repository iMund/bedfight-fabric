package br.com.tavares.bedfight.arena;

/** Thrown for a domain validation failure while capturing/updating a map (origin mismatch, selection too large, etc). */
public final class MapCaptureException extends Exception {
	public MapCaptureException(String message) {
		super(message);
	}
}
