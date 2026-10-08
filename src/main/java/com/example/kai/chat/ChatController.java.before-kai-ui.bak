package com.example.kai.chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.config.ModelProvider;
import com.example.kai.config.Setup;
import com.example.kai.orchestrator.Extraction;
import com.example.kai.orchestrator.Finding;
import com.example.kai.orchestrator.Orchestrator;
import com.example.kai.orchestrator.Progress;
import com.example.kai.orchestrator.Proposal;
import com.example.kai.writer.ChangeWriter;
import com.example.kai.writer.History;

// The chat: where users work. (StartController's start page comes first and only handles
// settings.) Every message goes to the Orchestrator; agents stay behind it and never talk to the user.
@Controller
public class ChatController {

	// report:     set on bot replies that carry a scan result, otherwise null
	// extraction: set on the "changes found in the updated file" reply, which waits for the user
	//             to confirm; text = the changes in its box (as confirmed, once done)
	// applied:    the report was finalized, or the extraction confirmed, so it can't be done again
	// link:       "Open the report" address on Finalize replies, otherwise null
	public record Message(String sender, String text, Finding.Report report, Extraction extraction, boolean applied,
			String link) {

		Message(String sender, String text, Finding.Report report) {
			this(sender, text, report, null, false, null);
		}
	}

	private final Orchestrator orchestrator;
	private final Setup setup;
	private final ModelProvider models;
	private final History historyFolder; // finalized runs on disk (not the chat history)
	private final ExecutorService jobs = Executors.newVirtualThreadPerTaskExecutor(); // background scans

	public ChatController(Orchestrator orchestrator, Setup setup, ModelProvider models, History historyFolder) {
		this.orchestrator = orchestrator;
		this.setup = setup;
		this.models = models;
		this.historyFolder = historyFolder;
	}

	// A scan or extraction running in the background for one browser session; the page polls
	// /progress. result = the bot's reply, added to the chat when done.
	record Job(Progress progress, CompletableFuture<Message> result) {
	}

	private interface Step {
		Message run(Progress progress) throws IOException;
	}

	@GetMapping("/")
	public String chat(HttpSession session, Model model) {
		if (!started(session)) { // every browser session begins on the start page
			return "redirect:/start";
		}
		collect(session);
		Job job = (Job) session.getAttribute("job");
		model.addAttribute("messages", history(session));
		model.addAttribute("log", job == null ? null : job.progress().since(0)); // set = a scan is running
		model.addAttribute("targets", orchestrator.targets());
		model.addAttribute("folders", orchestrator.folders());
		model.addAttribute("backupDir", setup.properties().backupDir().toString());
		model.addAttribute("aiModel", models.settings().model());
		model.addAttribute("unfinished", unfinished(history(session))); // New chat asks first if set
		if (history(session).isEmpty()) { // empty chat: show the latest finalized changes
			List<History.Run> runs = historyFolder.list(setup.properties().backupDir());
			model.addAttribute("recent", runs.subList(0, Math.min(5, runs.size())));
			model.addAttribute("older", Math.max(0, runs.size() - 5));
		}
		return "chat"; // -> templates/chat.html
	}

	// "New chat": clear the conversation and start again with the same settings. Refused while a
	// scan runs, or its reply would land in the new chat. Finalized reports stay in the backup folder.
	@PostMapping("/new")
	public String newChat(HttpSession session) {
		collect(session);
		if (session.getAttribute("job") == null) {
			session.removeAttribute("history");
		}
		return "redirect:/";
	}

	// What New chat would throw away, as a question for the browser's confirm box; null = nothing
	private static String unfinished(List<Message> history) {
		long edits = history.stream().filter(m -> m.report() != null && !m.applied() && m.report().readyCount() > 0).count();
		long changes = history.stream().filter(m -> m.extraction() != null && !m.applied()).count();
		if (edits == 0 && changes == 0) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		if (edits > 0) {
			parts.add(edits + (edits == 1 ? " report has" : " reports have") + " proposed edits that were not written to your files");
		}
		if (changes > 0) {
			parts.add(changes + (changes == 1 ? " list of changes was" : " lists of changes were") + " not scanned yet");
		}
		return String.join(", and ", parts) + ". A new chat discards them. Start a new chat?";
	}

	// Starts the work and returns at once; the page shows the agents' progress until it's done.
	// No updated file: message is the change request, scan at once.
	// Updated file picked: message is the user's summary; the Extractor runs first and its
	// changes wait in the chat for the user to confirm (POST /confirm), which starts the scan.
	@PostMapping("/chat")
	public String send(@RequestParam String message, @RequestParam(defaultValue = "") String source, HttpSession session) {
		if (!started(session)) {
			return "redirect:/start";
		}
		collect(session);
		if (session.getAttribute("job") != null || message.isBlank()) {
			return "redirect:/";
		}
		List<Message> history = history(session);
		if (source.isEmpty()) {
			history.add(new Message("user", message, null));
			start(session, p -> reply(orchestrator.scan(message, null, p)));
			return "redirect:/";
		}
		Finding.Source s;
		try {
			s = orchestrator.source(source);
		}
		catch (IOException e) {
			history.add(new Message("error", e.getMessage(), null));
			return "redirect:/";
		}
		history.add(new Message("user", "Updated file: " + s.name() + "\n" + message, null));
		start(session, p -> {
			Extraction x = orchestrator.extract(message, s, p);
			return new Message("bot", x.text(), null, x, false, null);
		});
		return "redirect:/";
	}

	// "Scan other files" on the changes found in an updated file: the box text, maybe edited,
	// becomes the change request. The updated file itself is skipped.
	@PostMapping("/confirm")
	public String confirm(@RequestParam int index, @RequestParam String changes, HttpSession session) {
		if (!started(session)) {
			return "redirect:/start";
		}
		collect(session);
		List<Message> history = history(session);
		Message m = index >= 0 && index < history.size() ? history.get(index) : null;
		if (m == null || m.extraction() == null || m.applied() || changes.isBlank() || session.getAttribute("job") != null) {
			return "redirect:/";
		}
		Extraction x = m.extraction();
		history.set(index, new Message(m.sender(), changes.strip(), null, x, true, null));
		start(session, p -> reply(orchestrator.scan(x.instruction(changes), x.source(), p)));
		return "redirect:/";
	}

	private void start(HttpSession session, Step step) {
		Progress progress = new Progress();
		progress.add("Started");
		session.setAttribute("job", new Job(progress, CompletableFuture.supplyAsync(() -> {
			try {
				return step.run(progress);
			}
			catch (IOException e) {
				throw new CompletionException(e);
			}
		}, jobs)));
	}

	private static Message reply(Finding.Report report) {
		String summary = report.affectedCount() + " of " + report.findings().size() + " files affected"
				+ (report.readyCount() > 0 ? ". " + report.readyCount() + " proposed edits to review below" : "")
				+ (report.unappliedCount() > 0 ? ". " + report.unappliedCount() + " could not be edited automatically" : "")
				+ (report.manualCount() > 0 ? ". " + report.manualCount() + " need a manual update (docx/pptx/pdf)" : "")
				+ (report.errorCount() > 0 ? " (" + report.errorCount() + " could not be checked, see Error rows)" : "");
		return new Message("bot", summary, report);
	}

	// New log lines since index `since`; done = reload the page to see the report
	@GetMapping("/progress")
	@ResponseBody
	public Map<String, Object> progress(@RequestParam int since, HttpSession session) {
		Job job = (Job) session.getAttribute("job");
		return job == null ? Map.of("lines", List.of(), "done", true)
				: Map.of("lines", job.progress().since(since), "done", job.result().isDone());
	}

	// A finished job becomes the bot's chat message. Only request threads touch the history.
	private synchronized void collect(HttpSession session) {
		Job job = (Job) session.getAttribute("job");
		if (job == null || !job.result().isDone()) {
			return;
		}
		session.removeAttribute("job");
		try {
			history(session).add(job.result().join());
		}
		catch (CompletionException e) {
			// e.g. folder missing: show it in the chat instead of an error page
			Throwable c = e.getCause() == null ? e : e.getCause();
			history(session).add(new Message("error", c.getClass().getSimpleName() + ": " + c.getMessage(), null));
		}
	}

	// "Save edits" on one report: the textarea text and include tick of each proposal.
	// Fields are named text<i> / include<i>, i = the finding's index in the report.
	// Saved in the session only; nothing is written to disk.
	@PostMapping("/save")
	public String save(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
		Message m = reportMessage(history(session), report);
		if (m != null && !m.applied() && started(session)) {
			save(m.report(), form);
		}
		return "redirect:/#report-" + report;
	}

	// "Finalize": save the form first (so unsaved edits count), then report -> backup -> write.
	// The outcome is a new chat message with a link to the saved report.
	@PostMapping("/finalize")
	public String finalizeReport(@RequestParam int report, @RequestParam Map<String, String> form, HttpSession session) {
		List<Message> history = history(session);
		Message m = reportMessage(history, report);
		if (m == null || m.applied() || !started(session)) {
			return "redirect:/";
		}
		save(m.report(), form);
		ChangeWriter.Outcome o = orchestrator.apply(m.report());
		if (o.result() == ChangeWriter.Result.APPLIED) {
			history.set(report, new Message(m.sender(), m.text(), m.report(), null, true, null));
		}
		history.add(new Message(o.result() == ChangeWriter.Result.APPLIED ? "bot" : "error", o.message(), null, null, false,
				o.run() == null ? null : History.appHref(o.run())));
		return "redirect:/";
	}

	// A saved report, so the chat can link to it (browsers block file:// links from a web page).
	// run = the run's folder name in the history folder; History refuses anything else.
	@GetMapping("/report/{run}")
	public ResponseEntity<String> finalReport(@PathVariable String run) throws IOException {
		Path file = setup.ready() ? historyFolder.report(setup.properties().backupDir(), run) : null;
		if (file == null || !Files.isRegularFile(file)) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(Files.readString(file));
	}

	// Every finalized change, newest first: the same page as History.html in the history folder
	@GetMapping("/history")
	public String pastReports(HttpSession session, Model model) {
		if (!started(session)) {
			return "redirect:/start";
		}
		Path dir = setup.properties().backupDir();
		model.addAttribute("runs", historyFolder.list(dir));
		model.addAttribute("app", true);
		model.addAttribute("folder", dir.toString());
		return "history"; // -> templates/history.html
	}

	private static void save(Finding.Report report, Map<String, String> form) {
		List<Finding> findings = report.findings();
		for (int i = 0; i < findings.size(); i++) {
			Proposal p = findings.get(i).proposal();
			String text = form.get("text" + i);
			if (p != null && p.ready() && text != null) {
				p.save(text, form.containsKey("include" + i));
			}
		}
	}

	private static Message reportMessage(List<Message> history, int index) {
		return index >= 0 && index < history.size() && history.get(index).report() != null ? history.get(index) : null;
	}

	// Set by the start page's Start Kai. Kai must also be set up (it always is once someone started).
	static final String STARTED = "started";

	private boolean started(HttpSession session) {
		return session.getAttribute(STARTED) != null && setup.ready();
	}

	// Conversation is kept per browser session, so no database is needed
	@SuppressWarnings("unchecked")
	private List<Message> history(HttpSession session) {
		List<Message> history = (List<Message>) session.getAttribute("history");
		if (history == null) {
			history = new ArrayList<>();
			session.setAttribute("history", history);
		}
		return history;
	}
}
