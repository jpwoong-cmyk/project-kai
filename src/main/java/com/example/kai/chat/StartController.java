package com.example.kai.chat;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.config.Setup;

// The start page: every browser session begins here. The page itself is static; its script asks
// these endpoints for the state from kai.properties and re-checks each part as the user edits it.
// Start saves the values to kai.properties and opens the chat.
@Controller
public class StartController {

	private final Setup setup;

	public StartController(Setup setup) {
		this.setup = setup;
	}

	@GetMapping("/start")
	public String start() {
		return "start"; // -> templates/start.html
	}

	// What kai.properties says, already checked (blank fields and nothing checked if there is no file)
	@GetMapping("/start/state")
	@ResponseBody
	public Setup.State state() {
		return setup.state();
	}

	public record FoldersRequest(List<String> scan, String backup) {
	}

	@PostMapping("/start/folders")
	@ResponseBody
	public Setup.Folders folders(@RequestBody FoldersRequest r) {
		return setup.folders(clean(r.scan()), trim(r.backup()));
	}

	public record AiRequest(String url, String key) {
	}

	@PostMapping("/start/ai")
	@ResponseBody
	public Setup.Ai ai(@RequestBody AiRequest r) {
		return setup.ai(trim(r.url()), trim(r.key()));
	}

	// "Start Kai". problem: null = saved and switched, the page opens the chat
	@PostMapping("/start/proceed")
	@ResponseBody
	public Map<String, Object> proceed(@RequestBody Setup.Form f, HttpSession session) {
		Setup.Form form = new Setup.Form(clean(f.scan()), trim(f.backup()), trim(f.url()), trim(f.key()), trim(f.model()));
		ChatController.Job job = (ChatController.Job) session.getAttribute("job");
		String problem = job != null && !job.result().isDone() // a scan uses one set of settings from start to end
				? "A scan is still running. Wait until it has finished, then click Start Kai again."
				: setup.proceed(form);
		if (problem == null) {
			session.setAttribute(ChatController.STARTED, true);
			return Map.of("ok", true);
		}
		return Map.of("ok", false, "problem", problem);
	}

	// One entry per folder row; empty rows are dropped
	private static List<String> clean(List<String> scan) {
		return scan == null ? List.of() : scan.stream().map(StartController::trim).filter(s -> !s.isEmpty()).toList();
	}

	private static String trim(String s) {
		return s == null ? "" : s.trim();
	}
}
