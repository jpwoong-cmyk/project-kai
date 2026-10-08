package com.example.kai.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import com.example.kai.repository.DocumentRepository;

// What the start page works with: the folders, the history folder (kai.backup-dir) and the AI connection.
// Reads kai.properties fresh each time the page opens and checks each part separately, so the
// page can show every result next to its field. On Start it writes the values back and
// switches Kai to them. Until then Kai has no settings, and the chat sends the user here.
@Component
public class Setup {

	// The start page fields, as typed. scan: one entry per folder (one comma-separated line in the file)
	public record Form(List<String> scan, String backup, String url, String key, String model) {
	}

	// One folder row. path: full path; documents: how many files Kai would read (null if unknown)
	public record Folder(String entry, String path, Integer documents, String problem) {
	}

	// The Documents part. foldersProblem: about the list as a whole. general: only fixable in the file.
	public record Folders(List<Folder> folders, String foldersProblem, String backupPath, boolean backupExists,
			String backupProblem, List<String> general, boolean ok) {
	}

	// The AI part. models: what the service offers right now; empty while ok = it doesn't list them.
	public record Ai(String url, List<String> models, String problem, boolean ok) {
	}

	// Everything the start page needs when it opens. folders / ai: null = not checked yet (no file).
	// ready: all fine, so the page shows the summary with one Start button instead of the form.
	public record State(Form form, Folders folders, Ai ai, String modelProblem, String note, boolean ready) {
	}

	private static final Form BLANK = new Form(List.of(), "", "", "", "");
	private static final Pattern ENV = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

	private final Path file;
	private final ModelProvider models;
	private final DocumentRepository local;
	private volatile KaiProperties properties; // null until the user starts

	public Setup(ApplicationArguments args, ModelProvider models, List<DocumentRepository> repositories) {
		this.file = KaiConfig.locate(args.getSourceArgs());
		this.models = models;
		this.local = repositories.stream().filter(r -> r.type().equals("local")).findFirst().orElse(null);
	}

	public boolean ready() {
		return properties != null && models.ready();
	}

	public KaiProperties properties() {
		KaiProperties p = properties;
		if (p == null) {
			throw new IllegalStateException("Kai is not set up yet. Open the start page.");
		}
		return p;
	}

	// Start page opened: the values from kai.properties (blank if there is no file), all checked.
	// The model list is always asked from the service, never trusted from the file.
	public State state() {
		if (!Files.isRegularFile(file)) {
			return new State(BLANK, null, null, null, "There is no settings file yet. Fill in the fields below. "
					+ "Kai saves them in " + file + " when you start.", false);
		}
		List<String> unset = new ArrayList<>(); // ${NAME} lines whose variable doesn't exist here
		Form f;
		try {
			f = fromFile(unset);
		}
		catch (KaiConfig.Invalid e) {
			return new State(BLANK, new Folders(List.of(), null, null, false, null, List.of(e.getMessage()), false),
					null, null, null, false);
		}
		Folders folders = folders(f.scan(), f.backup());
		Ai ai = ai(f.url(), f.key());
		if (!ai.ok() && !unset.isEmpty()) {
			unset.add(ai.problem());
			ai = new Ai(ai.url(), ai.models(), String.join("\n", unset), false);
		}
		String modelProblem = ai.ok() ? modelProblem(ai.models(), f.model()) : null;
		return new State(f, folders, ai, modelProblem, null, folders.ok() && ai.ok() && modelProblem == null);
	}

	// The Documents part, for the folders and history folder as typed
	public Folders folders(List<String> scan, String backup) {
		Properties p;
		try {
			p = KaiConfig.read(file);
		}
		catch (KaiConfig.Invalid e) {
			return new Folders(List.of(), null, null, false, null, List.of(e.getMessage()), false);
		}
		p.setProperty(KaiConfig.SCAN, String.join(", ", scan));
		p.setProperty(KaiConfig.BACKUP, backup);
		KaiConfig.Inspection r = KaiConfig.inspect(file, p, false);
		List<Folder> rows = r.folders().stream()
				.map(c -> new Folder(c.entry(), c.path().toString(), c.problem() == null ? documents(c.path()) : null, c.problem()))
				.toList();
		Path b = r.backupDir();
		return new Folders(rows, r.foldersProblem(), b == null ? null : b.toString(), b != null && Files.isDirectory(b),
				r.backupProblem(), r.general(), r.ok());
	}

	// The AI part: can Kai reach the service with this key, and which models does it offer?
	public Ai ai(String url, String key) {
		try {
			return new Ai(ModelProvider.normalize(url), models.models(url, key), null, true);
		}
		catch (ModelProvider.Problem e) {
			return new Ai(ModelProvider.normalize(url), List.of(), e.getMessage(), false);
		}
	}

	// null = fine. Not checkable if the service doesn't list its models: Start Kai's test call decides.
	// The start page's script has the same rule (modelProblem in start.html): keep them in step.
	public static String modelProblem(List<String> listed, String model) {
		if (listed.isEmpty()) {
			return model == null || model.isBlank() ? "Enter the model name your Kai admin gave you." : null;
		}
		if (model == null || model.isBlank()) {
			return "Choose a model.";
		}
		return listed.contains(model) ? null
				: "\"" + model + "\" is not offered by this AI service. Choose one of the " + listed.size() + " models in the list.";
	}

	// "Start": check everything again (the model with one tiny call), save to kai.properties, switch.
	// null = done, otherwise why not (then nothing is saved or switched)
	public String proceed(Form f) {
		try {
			Properties p = KaiConfig.read(file);
			p.setProperty(KaiConfig.SCAN, String.join(", ", f.scan()));
			p.setProperty(KaiConfig.BACKUP, f.backup());
			KaiProperties kp = KaiConfig.check(file, p, true);
			String wrong = modelProblem(models.models(f.url(), f.key()), f.model()); // the list may have changed
			if (wrong != null) {
				return wrong;
			}
			ModelProvider.Settings s = new ModelProvider.Settings(f.url(), f.key(), f.model());
			ChatModel m = models.connect(s);
			save(f);
			properties = kp;
			models.use(s, m);
			return null;
		}
		catch (KaiConfig.Invalid | ModelProvider.Problem e) {
			return e.getMessage();
		}
		catch (IOException e) {
			return "Kai could not save its settings file " + file + " (" + e.getMessage() + ")";
		}
	}

	// How many files Kai would read in this folder (the same listing the scan uses)
	private Integer documents(Path dir) {
		try {
			return local == null ? null : local.list(dir.toString()).size();
		}
		catch (IOException | RuntimeException e) {
			return null;
		}
	}

	// Write only the fields that differ from the file, so e.g. a ${ICA_CODEX_KEY} line stays as it is
	private void save(Form f) throws IOException, KaiConfig.Invalid {
		Form old = Files.isRegularFile(file) ? fromFile(new ArrayList<>()) : BLANK;
		Map<String, String> changed = new LinkedHashMap<>();
		put(changed, KaiConfig.SCAN, String.join(", ", f.scan()), String.join(", ", old.scan()));
		put(changed, KaiConfig.BACKUP, f.backup(), old.backup());
		put(changed, ModelProvider.KEY, f.key(), old.key());
		put(changed, ModelProvider.URL, ModelProvider.normalize(f.url()), ModelProvider.normalize(old.url()));
		put(changed, ModelProvider.MODEL, f.model(), old.model());
		if (!changed.isEmpty()) {
			KaiConfig.write(file, changed);
		}
	}

	private static void put(Map<String, String> changed, String key, String now, String before) {
		if (!Objects.equals(now, before)) {
			changed.put(key, now);
		}
	}

	private Form fromFile(List<String> unset) throws KaiConfig.Invalid {
		Properties p = KaiConfig.read(file);
		return new Form(KaiConfig.split(raw(p, KaiConfig.SCAN)), raw(p, KaiConfig.BACKUP), resolve(p, ModelProvider.URL, unset),
				resolve(p, ModelProvider.KEY, unset), resolve(p, ModelProvider.MODEL, unset));
	}

	private static String raw(Properties p, String key) {
		return p.getProperty(key, "").trim();
	}

	// ${NAME} or ${NAME:default}: from the computer's environment, like Spring does
	private static String resolve(Properties p, String key, List<String> unset) {
		String v = raw(p, key);
		Matcher m = ENV.matcher(v);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String name = m.group(1).trim();
			String found = System.getenv(name) != null ? System.getenv(name) : System.getProperty(name, m.group(2));
			if (found == null) {
				unset.add("kai.properties says " + key + "=" + v + ", but " + name
						+ " is not set on this computer. Type the value in the field instead.");
				return "";
			}
			m.appendReplacement(out, Matcher.quoteReplacement(found));
		}
		m.appendTail(out);
		return out.toString().trim();
	}
}
