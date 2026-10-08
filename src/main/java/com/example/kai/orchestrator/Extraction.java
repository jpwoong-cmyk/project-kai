package com.example.kai.orchestrator;

import java.util.List;
import java.util.stream.Collectors;

// The Extractor's result for an updated file, shown in the chat for the user to confirm or edit
// before the other files are scanned. summary = what the user typed.
public record Extraction(Finding.Source source, String summary, List<String> changes, List<String> notFound) {

	// Prefilled text of the confirm box, one change per line
	public String text() {
		return changes.stream().map(c -> "- " + c).collect(Collectors.joining("\n"));
	}

	// The change request for the other files, from the confirmed (maybe edited) text
	public String instruction(String confirmed) {
		return "The document " + source.name() + " was updated and is now correct."
				+ " Other documents must be consistent with these changes:\n" + confirmed.strip();
	}
}
