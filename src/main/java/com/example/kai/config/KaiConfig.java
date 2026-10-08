package com.example.kai.config;

import java.io.Console;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.example.kai.config.KaiProperties.Target;

// Reads, checks and writes kai.properties. Nothing here stops Kai: problems are messages for the
// start page, where the user fixes them. Where the file is looked for:
//   1. --config=<path> program argument, if given (for developers)
//   2. otherwise: the working directory (IntelliJ default: the project root)
// Relative paths inside the file start from the file's own folder, not the working
// directory, so they mean the same thing wherever Kai is started from.
public final class KaiConfig {

	public static final String FILE = "kai.properties";
	public static final String SCAN = "kai.scan.local";
	public static final String BACKUP = "kai.backup-dir";

	private static final Set<String> KEYS = Set.of(BACKUP, SCAN, "kai.scan.box", "kai.scan.parallel");

	public static class Invalid extends Exception {

		Invalid(String message) {
			super(message);
		}
	}

	private KaiConfig() {
	}

	public static Path locate(String[] args) {
		for (String arg : args) {
			if (arg.startsWith("--config=")) {
				return Path.of(arg.substring("--config=".length())).toAbsolutePath().normalize();
			}
		}
		return Path.of(FILE).toAbsolutePath();
	}

	// The file's settings as typed. Empty if the file does not exist.
	// A \ is kept as typed, so Windows paths can be pasted as-is (C:\Team\Docs).
	// Normal .properties files treat \ as an escape, so every one is doubled before parsing.
	public static Properties read(Path file) throws Invalid {
		Properties p = new Properties();
		if (!Files.isRegularFile(file)) {
			return p;
		}
		try {
			p.load(new StringReader(Files.readString(file).replace("\\", "\\\\")));
			return p;
		}
		catch (IOException | IllegalArgumentException e) {
			throw new Invalid("Kai could not read its settings file:\n  " + file + "\n" + e.getMessage());
		}
	}

	// The copy of kai.properties that Spring reads (server.port etc.), with every \ doubled
	// like in read(), else e.g. C:\\users would fail as a bad \\u escape. null = no usable file.
	public static Path springCopy(Path file) {
		try {
			if (!Files.isRegularFile(file)) {
				return null;
			}
			Path copy = Files.createTempFile("kai-", ".properties");
			copy.toFile().deleteOnExit();
			Files.writeString(copy, Files.readString(file).replace("\\", "\\\\"));
			return copy;
		}
		catch (IOException e) {
			return null; // the start page shows the read error
		}
	}

	// Program arguments for Spring: also load the copy for the spring.* / server.* keys
	// (additional-location: its keys override application.properties). --config stays, the start page reads it.
	public static String[] springArgs(String[] args, Path springCopy) {
		return springCopy == null ? args
				: Stream.concat(Arrays.stream(args), Stream.of("--spring.config.additional-location=file:"
						+ springCopy.toString().replace('\\', '/'))).toArray(String[]::new);
	}

	// One folder to scan: the value as typed, the full path, and what is wrong with it (null = fine)
	public record FolderCheck(String entry, Path path, String problem) {
	}

	// The kai.* settings, checked field by field so the start page can show each problem next to
	// its field. foldersProblem: about the list as a whole. general: lines only fixable in the file.
	// properties: the checked result, null if anything is wrong.
	public record Inspection(List<FolderCheck> folders, String foldersProblem, Path backupDir, String backupProblem,
			List<String> general, KaiProperties properties) {

		public boolean ok() {
			return properties != null;
		}

		public String message() {
			List<String> all = new ArrayList<>(general);
			folders.stream().filter(f -> f.problem() != null).map(f -> f.entry() + ": " + f.problem()).forEach(all::add);
			if (foldersProblem != null) {
				all.add(foldersProblem);
			}
			if (backupProblem != null) {
				all.add("History folder: " + backupProblem);
			}
			return String.join("\n", all);
		}
	}

	public static KaiProperties check(Path file, Properties p, boolean create) throws Invalid {
		Inspection r = inspect(file, p, create);
		if (!r.ok()) {
			throw new Invalid(r.message());
		}
		return r.properties();
	}

	// Checks the kai.* settings. create = make the history folder (kai.backup-dir; only on Start Kai)
	public static Inspection inspect(Path file, Properties p, boolean create) {
		Path home = file.getParent();
		List<String> general = new ArrayList<>();

		// Typos like "kai.scan.locl" would otherwise be silently ignored
		for (String key : new TreeSet<>(p.stringPropertyNames())) {
			if (key.startsWith("kai.") && !KEYS.contains(key)) {
				general.add("kai.properties has an unknown setting \"" + key + "\". Check the spelling in the file. Known settings: "
						+ String.join(", ", new TreeSet<>(KEYS)));
			}
		}

		Path backupDir = null;
		String backupProblem = null;
		String backup = value(p, BACKUP);
		if (backup == null) {
			backupProblem = "Enter a history folder, for example ~/Kai history";
		}
		else {
			backupDir = path(home, backup);
			if (Files.exists(backupDir) && !Files.isDirectory(backupDir)) {
				backupProblem = "This is a file, not a folder.";
			}
			else if (create) {
				try {
					Files.createDirectories(backupDir);
				}
				catch (IOException e) {
					backupProblem = "Kai cannot create this folder (" + e.getMessage() + ")";
				}
			}
		}

		List<FolderCheck> folders = new ArrayList<>();
		List<Target> targets = new ArrayList<>();
		List<String> local = list(p, SCAN);
		List<String> box = list(p, "kai.scan.box");
		for (String folder : local) {
			Path dir = path(home, folder);
			String problem = Files.isDirectory(dir) ? null : Files.exists(dir) ? "This is a file, not a folder." : "Folder not found.";
			folders.add(new FolderCheck(folder, dir, problem));
			if (problem == null) {
				targets.add(new Target("local", dir.toString(), folder));
			}
		}
		// Else the history (reports and original files) would be scanned and edited as if it were documents
		for (Target t : targets) {
			if (backupProblem == null && backupDir != null && backupDir.startsWith(Path.of(t.location()))) {
				backupProblem = "This is inside a folder Kai scans (" + t.entry() + "). Choose a folder outside it, for example ~/Kai history";
			}
		}
		if (!box.isEmpty()) {
			// Placeholder: the Box adapter is not built yet
			general.add("kai.scan.box: Box folders are not supported yet. Put a # in front of that line in kai.properties.");
		}
		String foldersProblem = local.isEmpty() && box.isEmpty() ? "Add at least one folder to scan." : null;

		int parallel = 4;
		String par = value(p, "kai.scan.parallel");
		if (par != null) {
			try {
				parallel = Integer.parseInt(par);
			}
			catch (NumberFormatException e) {
				parallel = 0;
			}
			if (parallel < 1) {
				general.add("kai.scan.parallel in kai.properties must be a whole number of 1 or more, but is \"" + par + "\"");
			}
		}

		boolean ok = general.isEmpty() && foldersProblem == null && backupProblem == null
				&& folders.stream().allMatch(f -> f.problem() == null);
		return new Inspection(List.copyOf(folders), foldersProblem, backupDir, backupProblem, List.copyOf(general),
				ok ? new KaiProperties(backupDir, List.copyOf(targets), parallel) : null);
	}

	// Replace the lines for these keys (comments and every other line stay as they are); add any
	// that are missing. Creates the file if it does not exist. Values are written as typed:
	// read() takes a \ literally.
	public static void write(Path file, Map<String, String> values) throws IOException {
		String text = Files.exists(file) ? Files.readString(file)
				: "# Kai settings. Written by Kai's start page; you can also edit it by hand.\n";
		String nl = text.contains("\r\n") ? "\r\n" : "\n";
		List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
		if (lines.getLast().isEmpty()) {
			lines.removeLast(); // the final newline; added back below
		}
		for (var v : values.entrySet()) {
			String line = v.getKey() + "=" + v.getValue().replaceAll("[\r\n]", " ").trim();
			Pattern key = Pattern.compile("^\\s*" + Pattern.quote(v.getKey()) + "\\s*[=:].*");
			boolean found = false;
			for (int i = 0; i < lines.size(); i++) {
				if (key.matcher(lines.get(i)).matches()) {
					lines.set(i, line);
					found = true;
				}
			}
			if (!found) {
				lines.add(line);
			}
		}
		Files.writeString(file, String.join(nl, lines) + nl);
	}

	// Print the reason and stop. Only used when Spring itself cannot start (e.g. port in use):
	// settings problems never stop Kai, the start page shows them. In a real terminal (not the IDE console), wait for Enter first
	// so the message stays on screen if the window would otherwise close.
	public static void exit(String message) {
		System.err.println();
		System.err.println(message);
		System.err.println();
		Console console = System.console();
		if (console != null && console.isTerminal()) {
			System.err.println("Press Enter to close this window.");
			console.readLine();
		}
		System.exit(1);
	}

	private static Path path(Path home, String value) {
		// Windows "Copy as path" adds quotes: "C:\Team\Docs"
		String v = value.length() > 1 && value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1).trim() : value;
		v = v.startsWith("~/") ? System.getProperty("user.home") + v.substring(1) : v;
		return home.resolve(v).toAbsolutePath().normalize();
	}

	private static String value(Properties p, String key) {
		String v = p.getProperty(key);
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static List<String> list(Properties p, String key) {
		return split(value(p, key));
	}

	// In the file, several folders go on one line, separated by commas: "a, b ,c" -> [a, b, c]
	public static List<String> split(String v) {
		return v == null ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}
}
