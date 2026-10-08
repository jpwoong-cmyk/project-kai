package com.example.kai.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.stereotype.Component;

import com.example.kai.config.KaiProperties.Target;
import com.example.kai.config.Setup;
import com.example.kai.orchestrator.Finding;
import com.example.kai.repository.DocumentRepository;

// Finalize (no LLM). Order matters, so a failure at any step leaves your files as they were:
//   1. stale check: a file changed on disk since the scan -> refuse, nothing written
//   2. report:      <history>/<run>/Report.html (before/after of every ticked file)
//   3. backup:      <history>/<run>/Original files/<scan folder>/<file>, exact bytes
//   4. write:       every ticked file
//   5. rollback:    any write fails -> restore ALL ticked files from the backup
// Then the run's summary and <history>/History.html are updated (see History for the layout).
// Reads and writes documents only through DocumentRepository, so this works for any adapter.
@Component
public class ChangeWriter {

	private static final Logger log = LoggerFactory.getLogger(ChangeWriter.class);

	public enum Result {
		APPLIED, ROLLED_BACK, NOT_WRITTEN
	}

	// run = the run's folder name in the history folder, or null if no report was saved
	public record Outcome(Result result, String message, String run) {
	}

	// One file in the report: backup = where its original is kept
	public record Row(Finding finding, String backup) {
	}

	private final ITemplateEngine templates;
	private final Setup setup;
	private final History history;

	public ChangeWriter(ITemplateEngine templates, Setup setup, History history) {
		this.templates = templates;
		this.setup = setup;
		this.history = history;
	}

	// Never throws: every problem becomes an Outcome the chat can show
	public Outcome apply(Finding.Report report, Function<String, DocumentRepository> repos) {
		List<Finding> selected = report.findings().stream().filter(Finding::selected).toList();
		if (selected.isEmpty()) {
			return new Outcome(Result.NOT_WRITTEN, "Nothing to write: no ticked file has changes.", null);
		}

		// 1. Stale check: the file must still be exactly what the proposal was made from
		List<String> stale = new ArrayList<>();
		for (Finding f : selected) {
			try {
				if (!repos.apply(f.target().type()).read(f.target().location(), f.file()).equals(f.proposal().original())) {
					stale.add(name(f));
				}
			}
			catch (Exception e) {
				stale.add(name(f) + " (cannot read it: " + e.getMessage() + ")");
			}
		}
		if (!stale.isEmpty()) {
			return new Outcome(Result.NOT_WRITTEN, "Nothing was written. These files changed after the scan, so Kai will not overwrite them:\n  - "
					+ String.join("\n  - ", stale) + "\nScan again to get fresh proposals.", null);
		}

		Path home = setup.properties().backupDir();
		LocalDateTime time = LocalDateTime.now();
		String request = request(report);
		Path dir = History.newRun(home, time, request);
		String run = dir.getFileName().toString();
		Map<Target, String> labels = labels(report.targets());
		List<Row> rows = selected.stream().map(f -> new Row(f, dir.resolve(History.ORIGINALS).resolve(labels.getOrDefault(f.target(), "Other"))
				.resolve(f.file()).toString())).toList();

		// 2. Report, 3. Backup. A failure here: nothing has been written yet
		try {
			saveReport(dir, report, rows, "Not written yet");
			History.writeSummary(dir, request, time, "UNFINISHED", rows.size()); // until the outcome is known
			for (Row r : rows) {
				Finding f = r.finding();
				Path backup = Path.of(r.backup());
				Files.createDirectories(backup.getParent());
				Files.write(backup, repos.apply(f.target().type()).readBytes(f.target().location(), f.file()));
			}
		}
		catch (Exception e) {
			log.warn("WRITER {} -> backup failed", run, e);
			Outcome o = new Outcome(Result.NOT_WRITTEN, "Nothing was written: Kai could not save the report or the original files in "
					+ dir + " (" + e.getMessage() + ")", null);
			summary(dir, report, time, "NOT_WRITTEN", rows.size());
			return o;
		}

		// 4. Write
		String failure = null;
		for (Row r : rows) {
			Finding f = r.finding();
			try {
				repos.apply(f.target().type()).write(f.target().location(), f.file(), f.proposal().proposed());
				log.info("WRITER {} wrote {}", run, name(f));
			}
			catch (Exception e) {
				log.warn("WRITER {} -> write failed: {}", run, name(f), e);
				failure = name(f) + ": " + e.getMessage();
				break;
			}
		}
		if (failure == null) {
			return finish(dir, report, rows, time, "APPLIED", new Outcome(Result.APPLIED, "Applied: " + rows.size()
					+ (rows.size() == 1 ? " file" : " files") + " changed. The original files are kept in " + dir.resolve(History.ORIGINALS), run));
		}

		// 5. Rollback: restore every ticked file, also those not written yet (restoring them is harmless)
		List<String> notRestored = new ArrayList<>();
		for (Row r : rows) {
			Finding f = r.finding();
			try {
				repos.apply(f.target().type()).writeBytes(f.target().location(), f.file(), Files.readAllBytes(Path.of(r.backup())));
			}
			catch (Exception e) {
				log.error("WRITER {} -> RESTORE FAILED: {}", run, name(f), e);
				notRestored.add(name(f) + "  <-  " + r.backup());
			}
		}
		String message = notRestored.isEmpty()
				? "Rolled back: writing " + failure + "\nAll " + rows.size() + " files were restored from the backup, so nothing changed."
				: "Rollback INCOMPLETE: writing " + failure + "\nThese files could not be restored. Copy them back by hand from the backup:\n  - "
						+ String.join("\n  - ", notRestored);
		return finish(dir, report, rows, time, notRestored.isEmpty() ? "ROLLED_BACK" : "ROLLBACK_INCOMPLETE",
				new Outcome(Result.ROLLED_BACK, message, run));
	}

	// The report is saved again with the outcome; if that fails, the first copy is still there
	private Outcome finish(Path dir, Finding.Report report, List<Row> rows, LocalDateTime time, String result, Outcome outcome) {
		try {
			saveReport(dir, report, rows, outcome.message());
		}
		catch (Exception e) {
			log.warn("WRITER {} -> could not update the report", dir, e);
		}
		summary(dir, report, time, result, rows.size());
		return outcome;
	}

	// The run's summary, then History.html. Best effort: the files are already safe either way.
	private void summary(Path dir, Finding.Report report, LocalDateTime time, String result, int files) {
		try {
			if (Files.isDirectory(dir)) {
				History.writeSummary(dir, request(report), time, result, files);
			}
		}
		catch (Exception e) {
			log.warn("WRITER {} -> could not save the summary", dir, e);
		}
		history.writeIndex(dir.getParent());
	}

	private void saveReport(Path dir, Finding.Report report, List<Row> rows, String outcome) throws IOException {
		Context ctx = new Context(Locale.ENGLISH);
		ctx.setVariable("report", report);
		ctx.setVariable("rows", rows);
		ctx.setVariable("outcome", outcome);
		ctx.setVariable("run", dir.getFileName().toString());
		ctx.setVariable("notWritten", report.findings().stream().filter(f -> f.affected() && !f.selected()).toList());
		Files.createDirectories(dir);
		Files.writeString(dir.resolve(History.REPORT), templates.process("final-report", ctx)); // templates/final-report.html
	}

	// Folder under "Original files" for each scan folder: its own name, "name (2)" if two share it
	private static Map<Target, String> labels(List<Target> targets) {
		Map<Target, String> labels = new HashMap<>();
		Set<String> used = new HashSet<>();
		for (Target t : targets) {
			Path name = Path.of(t.location()).getFileName();
			String base = name == null ? "Drive" : name.toString();
			String label = base;
			for (int n = 2; !used.add(label.toLowerCase(Locale.ROOT)); n++) { // macOS/Windows ignore case
				label = base + " (" + n + ")";
			}
			labels.put(t, label);
		}
		return labels;
	}

	// What the run is called in the history: the change request as typed, or for a change taken
	// from an updated file "Changes from <file>" plus the confirmed changes (not Kai's wording)
	private static String request(Finding.Report report) {
		String i = report.instruction();
		if (report.source() == null) {
			return i;
		}
		int nl = i.indexOf('\n');
		return "Changes from " + report.source().name() + (nl < 0 ? "" : "\n" + i.substring(nl + 1).strip());
	}

	private static String name(Finding f) {
		return f.target().entry() + "/" + f.file();
	}
}
