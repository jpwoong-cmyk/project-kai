package com.example.kai.orchestrator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.example.kai.agent.editor.EditorAgent.Edit;

// Applies the Editor's passage edits to a file. No guessing: every original passage must
// appear exactly once, and passages must not overlap; otherwise nothing is applied.
// Text outside the passages stays byte-for-byte identical.
final class Patch {

	static class NoMatch extends Exception {

		NoMatch(String problems) {
			super(problems);
		}
	}

	private record Spot(int start, int end, String replacement) {
	}

	private Patch() {
	}

	static String apply(String content, List<Edit> edits) throws NoMatch {
		if (edits.isEmpty()) {
			throw new NoMatch("The editor proposed no edits.");
		}
		// The model writes \n; a Windows file has \r\n
		boolean crlf = content.contains("\r\n");
		List<String> problems = new ArrayList<>();
		List<Spot> spots = new ArrayList<>();
		for (Edit e : edits) {
			String from = eol(e.original(), crlf);
			if (from.isBlank()) {
				problems.add("- An edit has an empty original passage.");
				continue;
			}
			int at = content.indexOf(from);
			if (at < 0) {
				problems.add("- Not found in the file: \"" + preview(e.original()) + "\"");
			}
			else if (content.indexOf(from, at + 1) >= 0) {
				problems.add("- Found more than once, add surrounding text to make it unique: \"" + preview(e.original()) + "\"");
			}
			else {
				spots.add(new Spot(at, at + from.length(), eol(e.replacement(), crlf)));
			}
		}
		spots.sort(Comparator.comparingInt(Spot::start));
		for (int i = 1; i < spots.size(); i++) {
			if (spots.get(i).start() < spots.get(i - 1).end()) {
				problems.add("- Two edits overlap; merge them into one passage.");
			}
		}
		if (!problems.isEmpty()) {
			throw new NoMatch(String.join("\n", problems));
		}

		StringBuilder out = new StringBuilder();
		int pos = 0;
		for (Spot s : spots) {
			out.append(content, pos, s.start()).append(s.replacement());
			pos = s.end();
		}
		return out.append(content.substring(pos)).toString();
	}

	private static String eol(String text, boolean crlf) {
		String t = text == null ? "" : text.replace("\r\n", "\n");
		return crlf ? t.replace("\n", "\r\n") : t;
	}

	private static String preview(String text) {
		String t = text.strip().replaceAll("\\s+", " ");
		return t.length() <= 120 ? t : t.substring(0, 120) + "…";
	}
}
