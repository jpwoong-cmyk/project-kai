package com.example.kai.orchestrator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.example.kai.agent.editor.EditorAgent;
import com.example.kai.agent.extractor.ExtractorAgent;
import com.example.kai.agent.reviewer.ReviewerAgent;
import com.example.kai.agent.scanner.ScannerAgent;
import com.example.kai.config.KaiProperties;
import com.example.kai.config.Setup;
import com.example.kai.config.KaiProperties.Target;
import com.example.kai.repository.DocumentRepository;
import com.example.kai.writer.ChangeWriter;

// The only thing the chat talks to. Plain Java: decides which agent runs when
// and passes results between them. Agents never talk to the user directly.
// Optional first step, when the user picked an updated file: ExtractorAgent -> user confirms.
// Per file: ScannerAgent -> (affected + editable) EditorAgent -> ReviewerAgent.
// Finalize: ChangeWriter (no LLM) writes the ticked proposals.
@Service
public class Orchestrator {

	private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

	private final Map<String, DocumentRepository> repositories = new TreeMap<>();
	private final ExtractorAgent extractor;
	private final ScannerAgent scanner;
	private final EditorAgent editor;
	private final ReviewerAgent reviewer;
	private final ChangeWriter writer;
	private final Setup setup;

	// Spring injects every DocumentRepository bean, so new adapters register themselves
	public Orchestrator(List<DocumentRepository> repositories, ExtractorAgent extractor, ScannerAgent scanner,
			EditorAgent editor, ReviewerAgent reviewer, ChangeWriter writer, Setup setup) {
		repositories.forEach(r -> this.repositories.put(r.type(), r));
		this.extractor = extractor;
		this.scanner = scanner;
		this.editor = editor;
		this.reviewer = reviewer;
		this.writer = writer;
		this.setup = setup;
	}

	// Where change requests are checked, from kai.scan.* (as set on the start page)
	public List<Target> targets() {
		return setup.properties().targets();
	}

	// One scan location and its files, for the chat's "Updated file" list
	public record Folder(Target target, List<String> files) {
	}

	// Every file in every location, in kai.properties order. A location that can't be listed
	// stays in with no files, so a position always matches targets().
	public List<Folder> folders() {
		List<Folder> folders = new ArrayList<>();
		for (Target target : targets()) {
			List<String> files;
			try {
				files = repository(target.type()).list(target.location());
			}
			catch (Exception e) {
				files = List.of();
			}
			folders.add(new Folder(target, files));
		}
		return folders;
	}

	// The updated file from the chat's list: "<location position>:<file>". Only files Kai would
	// scan are accepted, so the value can't point anywhere else.
	public Finding.Source source(String key) throws IOException {
		int colon = key.indexOf(':');
		List<Target> targets = targets();
		int i = key.matches("\\d{1,4}:.+") ? Integer.parseInt(key.substring(0, colon)) : -1;
		String file = key.substring(colon + 1);
		if (i < 0 || i >= targets.size() || !repository(targets.get(i).type()).list(targets.get(i).location()).contains(file)) {
			throw new IOException("The updated file is no longer in the scan folders: " + file);
		}
		return new Finding.Source(targets.get(i), file);
	}

	// Step 1 for an updated file: what changed in it, guided by the user's summary
	public Extraction extract(String summary, Finding.Source source, Progress progress) throws IOException {
		Target target = source.target();
		progress.add("Extractor: reading " + source.name());
		String content = repository(target.type()).read(target.location(), source.file());
		ExtractorAgent.Changes c = extractor.extract(summary, source.file(), content);
		progress.add("Extractor: found " + c.changes().size() + " changes"
				+ (c.notFound().isEmpty() ? "" : ", " + c.notFound().size() + " not found in the file"));
		return new Extraction(source, summary, c.changes(), c.notFound());
	}

	// Handles up to kai.scan.parallel files at the same time, across all targets. Each file runs
	// scan -> edit -> review in one task, so edits for one file overlap with scans of others.
	// The report keeps the target and file-list order. Each step is added to progress for the chat page.
	// source = the updated file the change came from (skipped: it is already right), or null.
	public Finding.Report scan(String instruction, Finding.Source source, Progress progress) throws IOException {
		KaiProperties properties = setup.properties(); // one set of settings from start to end
		List<Future<Finding>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(properties.parallel())) { // close() waits for all
			for (Target target : properties.targets()) {
				DocumentRepository repo = repository(target.type());
				List<String> ids = repo.list(target.location());
				progress.add("Found " + ids.size() + " files in " + target.entry());
				for (String id : ids) {
					if (source != null && source.target().equals(target) && source.file().equals(id)) {
						progress.add("Skipping " + source.name() + ": it is the updated file");
						continue;
					}
					futures.add(pool.submit(() -> check(target, repo, instruction, id, progress)));
				}
			}
			progress.add("Checking " + futures.size() + " files, up to " + properties.parallel() + " at a time");
		}
		List<Finding> findings = futures.stream().map(Future::resultNow).toList();
		Finding.Report report = new Finding.Report(instruction, source, properties.targets(), findings);
		progress.add("Done: " + report.affectedCount() + " of " + findings.size() + " files affected");
		return report;
	}

	// Finalize: report -> backup -> write the ticked files -> restore all on any failure
	public ChangeWriter.Outcome apply(Finding.Report report) {
		return writer.apply(report, this::repository);
	}

	// Never throws: one bad file or model call must not stop the scan, and must not look like "not affected"
	private Finding check(Target target, DocumentRepository repo, String instruction, String id, Progress progress) {
		boolean editable = repo.canWrite(id);
		String name = target.entry() + "/" + id;
		try {
			String content = repo.read(target.location(), id);
			progress.add("Scanner: checking " + name);
			ScannerAgent.Verdict v = scanner.assess(instruction, id, content);
			if (!v.affected()) {
				progress.add("Scanner: " + name + " needs no change");
				return new Finding(target, id, Finding.Status.NOT_AFFECTED, v.reason(), editable, null);
			}
			progress.add("Scanner: " + name + " must change" + (editable ? "" : " (update by hand)"));
			Proposal proposal = editable ? propose(instruction, id, name, content, progress) : null;
			return new Finding(target, id, Finding.Status.AFFECTED, v.reason(), editable, proposal);
		}
		catch (Exception e) {
			log.warn("SCANNER {} {} -> ERROR", target.label(), id, e);
			progress.add("Scanner: could not check " + name + ": " + message(e));
			return new Finding(target, id, Finding.Status.ERROR, message(e), editable, null);
		}
	}

	// Editor -> apply (one automatic retry if a passage doesn't match) -> Reviewer. Never throws.
	private Proposal propose(String instruction, String id, String name, String content, Progress progress) {
		String proposed;
		try {
			progress.add("Editor: drafting edits for " + name);
			try {
				proposed = Patch.apply(content, editor.propose(instruction, id, content, null));
			}
			catch (Patch.NoMatch first) {
				log.info("EDITOR {} -> retry:\n{}", id, first.getMessage());
				progress.add("Editor: an edit did not match " + name + ", retrying once");
				proposed = Patch.apply(content, editor.propose(instruction, id, content, first.getMessage()));
			}
		}
		catch (Patch.NoMatch e) {
			log.warn("EDITOR {} -> could not apply:\n{}", id, e.getMessage());
			progress.add("Editor: could not apply edits to " + name);
			return Proposal.failed(content, "Kai's edits did not match the file text, so nothing was proposed.\n" + e.getMessage());
		}
		catch (Exception e) {
			log.warn("EDITOR {} -> ERROR", id, e);
			progress.add("Editor: failed on " + name + ": " + message(e));
			return Proposal.failed(content, "The editor failed: " + message(e));
		}
		// A reviewer failure keeps the proposal: the note says it wasn't checked
		try {
			progress.add("Reviewer: checking the edits for " + name);
			ReviewerAgent.Review r = reviewer.review(instruction, id, content, proposed);
			progress.add("Reviewer: " + name + (r.ok() ? " looks right" : " please check"));
			return Proposal.ready(content, proposed, r.ok(), r.note());
		}
		catch (Exception e) {
			log.warn("REVIEWER {} -> ERROR", id, e);
			progress.add("Reviewer: could not check " + name + ": " + message(e));
			return Proposal.ready(content, proposed, null, "The reviewer could not check this proposal: " + message(e));
		}
	}

	private static String message(Exception e) {
		return e.getClass().getSimpleName() + ": " + e.getMessage();
	}

	private DocumentRepository repository(String type) {
		DocumentRepository repo = repositories.get(type);
		if (repo == null) {
			throw new IllegalArgumentException("Unknown repository '" + type + "', available: " + repositories.keySet());
		}
		return repo;
	}
}
