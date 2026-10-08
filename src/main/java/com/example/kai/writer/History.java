package com.example.kai.writer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.stereotype.Component;

// The history folder (kai.backup-dir), organised for people:
//
//   <history folder>/
//     History.html                          every change, newest first (rewritten after each Finalize)
//     2026-10-08 14.35 Java 17 to 21/       one folder per Finalize: date, time, start of the request
//       Report.html                         before/after of every changed file
//       Original files/<scan folder>/...    the files exactly as they were before
//       .kai-run.properties                 for Kai: request, time, result (hidden)
//
// Folders from before this layout (20261008-143512/report.html) are listed too, by date only.
@Component
public class History {

	private static final Logger log = LoggerFactory.getLogger(History.class);

	public static final String INDEX = "History.html";
	public static final String REPORT = "Report.html";
	public static final String ORIGINALS = "Original files";
	static final String SUMMARY = ".kai-run.properties";

	private static final String OLD_REPORT = "report.html";
	private static final DateTimeFormatter OLD_NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern OLD_FOLDER = Pattern.compile("\\d{8}-\\d{6}");
	private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm");
	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

	// result: APPLIED, ROLLED_BACK, ROLLBACK_INCOMPLETE, NOT_WRITTEN, or UNFINISHED (Kai stopped half-way)
	// request / files: null for old-format folders
	public record Run(String folder, LocalDateTime time, String request, String result, Integer files) {

		public String when() {
			return time.format(SHOWN);
		}

		// The request's first line, shortened for a list; the folder name for older runs
		public String headline() {
			String first = request == null ? "" : request.strip().lines().findFirst().orElse("");
			return first.isEmpty() ? folder : first.length() > 120 ? first.substring(0, 117).strip() + "…" : first;
		}

		// What happened, in the chat's words
		public String outcome() {
			return switch (result) {
				case "APPLIED" -> files == null ? "Changed" : files + (files == 1 ? " file changed" : " files changed");
				case "ROLLED_BACK" -> "Rolled back, nothing changed";
				case "ROLLBACK_INCOMPLETE" -> "Rollback incomplete: see the report";
				case "NOT_WRITTEN" -> "Not written";
				case "UNFINISHED" -> "Unfinished: see the report";
				default -> ""; // older format: not recorded
			};
		}

		public boolean problem() {
			return result.equals("ROLLBACK_INCOMPLETE") || result.equals("UNFINISHED");
		}

		// Link from History.html in the same folder
		public String fileHref() {
			return encode(folder) + "/" + reportName();
		}

		// Link inside Kai
		public String appHref() {
			return History.appHref(folder);
		}

		String reportName() {
			return result.isEmpty() ? OLD_REPORT : REPORT;
		}
	}

	private final ITemplateEngine templates;

	public History(ITemplateEngine templates) {
		this.templates = templates;
	}

	public static String appHref(String folder) {
		return "/report/" + encode(folder);
	}

	// A new run folder: "2026-10-08 14.35 Java 17 to 21", "... (2)" if that name is taken
	static Path newRun(Path dir, LocalDateTime time, String request) {
		String base = time.format(NAME) + " " + title(request);
		Path p = dir.resolve(base);
		for (int n = 2; Files.exists(p); n++) {
			p = dir.resolve(base + " (" + n + ")");
		}
		return p;
	}

	// The start of the request, safe as a folder name on Windows and macOS
	static String title(String request) {
		String first = request == null ? "" : request.strip().lines().findFirst().orElse("");
		String t = first.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").strip();
		if (t.length() > 40) {
			int cut = t.lastIndexOf(' ', 40);
			t = t.substring(0, cut > 20 ? cut : 40).strip();
		}
		t = t.replaceAll("[. ]+$", ""); // Windows drops trailing dots and spaces
		return t.isEmpty() ? "Change" : t;
	}

	static void writeSummary(Path run, String request, LocalDateTime time, String result, int files) throws IOException {
		Properties p = new Properties();
		p.setProperty("request", request == null ? "" : request);
		p.setProperty("time", time.toString());
		p.setProperty("result", result);
		p.setProperty("files", String.valueOf(files));
		try (Writer w = Files.newBufferedWriter(run.resolve(SUMMARY), StandardCharsets.UTF_8)) {
			p.store(w, "Kai run summary, used by History.html and Past reports");
		}
	}

	// Every run in the history folder, newest first
	public List<Run> list(Path dir) {
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		List<Run> runs = new ArrayList<>();
		try (Stream<Path> children = Files.list(dir)) {
			for (Path p : children.filter(Files::isDirectory).toList()) {
				Run r = read(p);
				if (r != null) {
					runs.add(r);
				}
			}
		}
		catch (IOException e) {
			log.warn("HISTORY cannot list {}", dir, e);
		}
		runs.sort(Comparator.comparing(Run::time).reversed());
		return runs;
	}

	// The report of one run, or null. folder must be a run folder directly inside dir (no ../ tricks)
	public Path report(Path dir, String folder) {
		Path p = dir.resolve(folder).normalize();
		if (folder.isBlank() || !dir.normalize().equals(p.getParent())) {
			return null;
		}
		if (read(p) == null) {
			return null;
		}
		return Files.isRegularFile(p.resolve(REPORT)) ? p.resolve(REPORT) : p.resolve(OLD_REPORT);
	}

	// History.html: the same list, readable without Kai running. Never throws: it can be rebuilt.
	public void writeIndex(Path dir) {
		try {
			Context ctx = new Context(Locale.ENGLISH);
			ctx.setVariable("runs", list(dir));
			ctx.setVariable("app", false);
			ctx.setVariable("folder", dir.toString());
			Files.writeString(dir.resolve(INDEX), templates.process("history", ctx)); // templates/history.html
		}
		catch (Exception e) {
			log.warn("HISTORY cannot write {}", dir.resolve(INDEX), e);
		}
	}

	private static Run read(Path p) {
		String name = p.getFileName().toString();
		if (Files.isRegularFile(p.resolve(SUMMARY))) {
			Properties s = new Properties();
			try (Reader r = Files.newBufferedReader(p.resolve(SUMMARY), StandardCharsets.UTF_8)) {
				s.load(r);
				String files = s.getProperty("files", "");
				return new Run(name, LocalDateTime.parse(s.getProperty("time")), s.getProperty("request", ""),
						s.getProperty("result", "UNFINISHED"), files.matches("\\d+") ? Integer.valueOf(files) : null);
			}
			catch (IOException | RuntimeException e) {
				log.warn("HISTORY unreadable summary in {}", p, e);
			}
		}
		if (!Files.isRegularFile(p.resolve(REPORT)) && !Files.isRegularFile(p.resolve(OLD_REPORT))) {
			return null; // not a Kai run: leave it alone
		}
		// No summary. Decided by the name, not by report.html vs Report.html: macOS and Windows
		// ignore the case of file names, so both always "exist" there.
		boolean old = OLD_FOLDER.matcher(name).matches();
		return new Run(name, oldTime(p, name), null, old ? "" : "UNFINISHED", null);
	}

	private static LocalDateTime oldTime(Path p, String name) {
		try {
			return LocalDateTime.parse(name, OLD_NAME);
		}
		catch (DateTimeParseException e) {
			try {
				return LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZoneId.systemDefault());
			}
			catch (IOException io) {
				return LocalDateTime.MIN;
			}
		}
	}

	// One path segment for a link: spaces and other unsafe characters percent-encoded
	private static String encode(String segment) {
		try {
			return new URI(null, null, segment, null).getRawPath().replace("/", "%2F");
		}
		catch (URISyntaxException e) {
			return segment;
		}
	}
}
