package com.example.kai.orchestrator;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// What the agents are doing right now, one line per step. Written by the scan threads,
// read by the chat page every second while a scan runs.
public final class Progress {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final List<String> lines = new ArrayList<>();

	public synchronized void add(String line) {
		lines.add(LocalTime.now().format(TIME) + "  " + line);
	}

	// Lines from index n on, so the page only fetches what it hasn't shown yet
	public synchronized List<String> since(int n) {
		return List.copyOf(lines.subList(Math.max(0, Math.min(n, lines.size())), lines.size()));
	}
}
