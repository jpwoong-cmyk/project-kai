# Kai

Kai reads your team's documents and tells you which ones need updating when something
changes. You describe the change in plain words, for example *"We now require Java 21
instead of 17"*, or you paste a few paragraphs of release notes. Kai then checks every
document in the folders you chose, lists the ones that mention the change, explains why,
and proposes the edit.

What Kai can do:

- **Find affected documents.** Kai reads Word (.docx), PowerPoint (.pptx), PDF and text
  files (.md, .txt and similar) in one or more folders, including Box Drive and other synced folders.
- **Propose the edits.** For text files Kai writes the new wording and shows it as a
  before/after view (red = removed, green = added). A second AI check reviews each edit and
  tells you if something looks off.
- **Let you decide.** You can change any proposed text, or leave a file out. Nothing is
  written until you click **Finalize**.
- **Keep you safe.** Before changing anything, Kai backs up the original files and saves a
  report of every change. If a single file fails to save, Kai puts **all** files back as they were.

Word, PowerPoint and PDF files are listed when they need a change, but you update those
yourself. Kai only edits text files for now.

This file has two parts:

1. **For users** (below): what you need, how to set Kai up, and how to use it. No
   technical knowledge needed.
2. **For developers** (at the bottom): how the project is built, its structure, and how
   each way of running Kai is made.

---

# Part 1: For users

## Two ways to get Kai

Your Kai admin gives you Kai in one of two forms. Both work the same once Kai is running.
The only difference is what you need on your computer and how you start it.

| | **A. Kai program** (recommended) | **B. Kai jar** |
|---|---|---|
| What you get | `kai.exe` (Windows) or `kai` (Mac), plus `kai.properties` | `kai.jar`, plus `kai.properties` |
| What you need to install | Nothing | Java 25 |
| How you start it | Double-click the program | Type one command in a terminal |
| Size | About 200 MB | About 150 MB, plus Java |
| Best for | Most people | People who already have Java, or use Linux |

**Why two ways?** Kai is written in Java, a language that needs a helper program (the "Java
runtime") to run. Option **B** is Kai on its own, so you have to install Java first. Option
**A** is the same Kai with its own private copy of Java packed inside, so you just
double-click it. Option A is bigger, but there is nothing to install and nothing to type.

In both cases `kai.properties` is Kai's settings file. It tells Kai which folders to check
and where to keep the history of changes. It must be in the **same folder** as the program.

## 1. Put Kai in a folder

Make a folder on your own computer, for example `Documents\Kai` (Windows) or
`Documents/Kai` (Mac), and put the files from your admin in it.

- **Option A, Windows:** right-click `kai-windows.zip` → **Extract All…** and pick that
  folder. Do not start Kai from inside the zip. It cannot find its settings there.
- **Option A, Mac:** double-click `kai-macos.zip`, then move the `macos` folder where you
  like (you can rename it to `Kai`).
- **Option B:** copy `kai.jar` and `kai.properties` into the folder.

Do not put the Kai folder inside Box Drive, OneDrive or another synced folder. Option A
unpacks about 200 MB of its own files next to itself (in a folder called `.kai-runtime`),
and you do not want that uploaded.

## 2. Set up kai.properties

**You can skip this step.** Kai's **start page** (the first page in the browser) shows the
folders, the history folder and the AI connection from `kai.properties` (empty fields if there is
nothing yet), checks each one as you fill it in, and lists the AI models. When everything works,
click **Start Kai**: Kai saves the values into `kai.properties`. This section is for editing the
file by hand instead.

Open `kai.properties` in a plain text editor: **Notepad** on Windows (right-click → **Open
with** → **Notepad**), **TextEdit** on Mac (right-click → **Open With** → **TextEdit**).
Do not use Word.

A few rules for the whole file:

- A line that starts with `#` is a note. Kai ignores it. To switch a setting on, remove the `#`.
- Each setting is one line: `name=value`. Do not change the part before `=`.
- Paths such as `./docs` start from the folder where `kai.properties` is. `~/` means your
  home folder, for example `~/Documents/Runbooks`.
- **Windows:** paste folder paths as they are, for example from right-click → **Copy as
  path**. `C:\Team\Docs` and `"C:\Team\Docs"` both work.
- **Mac:** right-click the folder in Finder, hold **Option**, and choose **Copy "…" as Pathname**.

The settings, in the order you find them in the file:

| Setting | What to put there |
|---|---|
| `kai.scan.local` | **Required.** The folder or folders with your documents. Kai also checks every folder inside them. Separate several folders with commas: `kai.scan.local=~/Documents/Runbooks, C:\Team\Docs`. (On the start page each folder has its own field: **+ Add folder**.) Box Drive folders work like any other folder, for example `~/Library/CloudStorage/Box-Box/Team Docs` on a Mac. Files that are only in the cloud and not downloaded are skipped |
| `# kai.scan.box` | Leave it as it is, with the `#`. Box web links are not supported yet |
| `kai.backup-dir` | **Required.** The **history folder**: one folder per Finalize with its `Report.html` and `Original files`, plus `History.html` listing them all. Kai creates it. It must **not** be inside a folder you check, for example `kai.backup-dir=~/kai-backups` |
| `kai.scan.parallel` | How many files Kai checks at the same time. Leave it at `4`. Lower it to `2` or `1` if you see "429" or "rate limit" |
| `spring.ai.openai.…` | The AI connection: key, address and model. **Your Kai admin tells you what to put here.** You can also type them on the start page, which tests them, lists the models, and saves working values into these lines. Keep the key private, like a password. `${...}` means "take the value from this computer's settings"; if your admin did not set that up, replace the whole `${...}` with the value |
| `# server.port=8080` | Only if Kai says the port is in use: remove the `#` and change `8080` to another number, such as `8090` |

**Save the file**, and keep the name exactly `kai.properties`.

## 3. Start Kai

### Option A: the Kai program

**Windows:** double-click `kai.exe`. A black window opens. The first time, Windows may show
**"Windows protected your PC"**. Click **More info** → **Run anyway**. This happens because
Kai is an in-house program, not one from a store.

**Mac:** double-click `kai`. A Terminal window opens. The first time, the Mac may say
**"kai" cannot be opened** or **"Apple could not verify…"**. Click **Done**, then open
**System Settings** → **Privacy & Security**, scroll down and click **Open Anyway** next to
the message about `kai`. On older Macs: right-click `kai` → **Open** → **Open**. You only
do this once.

The **first start takes longer** (up to a minute), because Kai unpacks its own copy of Java.
Later starts are faster.

### Option B: the Kai jar

1. Install **Java 25** if you do not have it yet (ask your admin, or download "Temurin 25"
   from adoptium.net). To check, open a terminal and type `java -version`. It should say `25`.
2. Open a terminal in the Kai folder. **Windows:** open the folder in File Explorer, click
   the address bar, type `cmd` and press Enter. **Mac:** right-click the folder in Finder →
   **New Terminal at Folder** (or open Terminal and type `cd ` followed by the folder path).
3. Type this and press Enter:

```
java -jar kai.jar
```

### Both options

Kai shows:

```
Kai is running. Open http://localhost:8080 in your browser.
Keep this window open while you use Kai. Close it to stop Kai.
```

Open **http://localhost:8080** in your browser (Chrome, Edge, Safari, …). You always see the
**start page** first. If your settings already work, it shows a short **Ready to scan** summary
(folders with their number of documents, history folder, AI model): click **Start Kai**. Otherwise
it shows the settings with each problem under its field. Kai checks a field when you leave it
(or press Enter). **Start Kai** is greyed out until everything works. Click **Edit settings** on
the summary, or **Change settings** next to *AI model* on the Kai page, to change them later.

**Keep the window open** while you use Kai. To stop Kai, close the window, or press
**Ctrl+C** in it.

## 4. Use Kai

Type the change in the box at the bottom and click **Scan documents**. You can write one
sentence or paste release notes several paragraphs long. **Enter** starts a new line; only
the button sends.

**Already updated one document?** Pick it under **Updated file** above the box, type a short
summary of what changed (for example "support email changed, Java now 21") and click **Find
changes**. Kai reads that file and lists the changes it found, plus anything in your summary
it could not find in the file. Fix the list if Kai got something wrong, then click **Scan
other files**. Every other file is checked against that list, and the updated file itself is
never changed. A summary is required: it tells Kai which changes matter.

While Kai works, a live log shows what it is doing for each file. The results appear by
themselves when the scan is done. You can refresh the page in the meantime. One scan runs
at a time.

The results are grouped by **Location**: each folder from `kai.scan.local`, with how many
of its files are affected (hover to see the full path). Each file gets one of these results:

| Result | Meaning |
|---|---|
| **Must change** | The file mentions the change. Kai shows its proposed edit below it |
| **Could not apply** | The file must change, but Kai could not place its edit in the text. Update it yourself |
| **Update by hand** | The file mentions the change, but it is a Word, PowerPoint or PDF file. Open it and change it yourself |
| **Could not check** | Kai could not read the file or reach the AI. Check this file yourself, or try again |
| **No change needed** | Click to see the list. These files do not need updating |

Under each **Must change** file you see:

- **What changes:** removed lines in red (−), new lines in green (+). Unchanged parts are folded.
- **The reviewer's note:** "looks right", or what to check.
- **Edit:** opens the proposed text so you can change it. Click **Save edits** and the
  red and green view updates.
- **Include this file:** untick it to leave that file as it is.

Saving edits does **not** change your files yet.

When you are happy, click **Finalize** and confirm. Kai then:

1. Checks that no file changed since the scan. If one did, Kai writes nothing and asks you
   to scan again, so it never overwrites someone else's newer work.
2. Saves a report of every change (before and after) and a copy of the original files, in
   a new folder in your history folder (layout below).
3. Changes the ticked files. If changing any file fails, Kai puts **all** of them back from
   the copies and says **Rolled back**, so your files are exactly as before.

The reply has an **Open the report** link. Every report also stays reachable later: click
**Past reports** at the top of the Kai page, look under **Recent changes** on an empty chat,
or open **History.html** in your history folder, which works even when Kai is not running:

```
Kai history/                                  (your history folder)
  History.html                                ← open this: every change, newest first
  2026-10-08 14.35 We now require Java 21/    ← one folder per Finalize: date, time, request
    Report.html                               ← before and after of every changed file
    Original files/                           ← the files exactly as they were before
      sampleDocs/app-onboarding-runbook.md
```

To get a file back by hand, copy it from **Original files**. Older changes, from before this
layout, keep their folder names (for example `20261008-143512`) and are listed too.

To start again with an empty page, click **New chat** at the top. Your settings stay as they
are. If a report still has edits you have not written with **Finalize**, or changes found in an
updated file are still waiting for **Scan other files**, Kai asks first, because a new chat
discards them. Finalized reports are not affected: they stay in the history folder and under
**Past reports**.

## If something goes wrong

Problems with the settings never stop Kai. They appear in red on the **start page**, under the
field they belong to. Fix the field and leave it (or press Enter) to check it again. Nothing is
saved until **Start Kai**, and Start Kai first tests the chosen model, so it only saves settings
that work. Problems marked *(in kai.properties)* can only be fixed in the file: fix it,
save it, and reload the start page.

| Message | What to do |
|---|---|
| *There is no settings file yet* | Normal the first time: fill in the fields, and **Start Kai** creates `kai.properties`. If you do have one, put it in the same folder as Kai (with option B, also start Kai from that folder) and check the name: Windows sometimes saves it as `kai.properties.txt` (File Explorer → **View** → **Show** → **File name extensions**) |
| *Folder to scan not found* | Check the path. Copy it again from File Explorer or Finder |
| *Enter a history folder* | Type a history folder, for example `~/Kai history` |
| *This is inside a folder Kai scans* (history folder) | Choose a history folder outside your document folders, for example `~/Kai history` |
| *Folders to scan: add at least one folder* | Type at least one folder |
| *Box folders are not supported yet* (in kai.properties) | Put a `#` in front of the `kai.scan.box` line |
| *Unknown setting* (in kai.properties) | A setting name is misspelled. Compare it with the list in the message |
| *kai.scan.parallel … must be a whole number* (in kai.properties) | Use a number such as `4`, without spaces or letters |
| *… is not set on this computer* | `kai.properties` takes the value from a `${...}` computer setting that does not exist here. Type the value in the field instead |
| *The AI key is not set* / *The AI address is not set* | Fill in the values from your Kai admin |
| *The AI address must start with https://* | Check the address with your Kai admin. A missing `/v1` at the end is added automatically |
| *"…" is not offered by this AI service* | The model in `kai.properties` is not available (any more). Choose one from the **Model** list and click **Start Kai** |
| *Enter the model name your Kai admin gave you* | This AI service does not list its models. Type the name, and click **Start Kai** |
| *A scan is still running* | Wait until the scan has finished, then click **Start Kai** again |
| *The AI service refused the key* | The key is wrong or has expired. Ask your Kai admin for a new one |
| *The AI service does not know this address or model* | Check the address and the model name with your Kai admin |
| *Kai cannot reach the AI service* | Check that you are online, and on the company VPN if you need one |
| *Kai could not start: unpacking Java failed* (option A) | Kai needs to write into its own folder. Move it somewhere you can save files, such as `Documents` (not `Program Files` or a read-only shared drive), and check that your disk is not full |
| *java is not recognized* / *command not found: java* (option B) | Java is not installed. Install Java 25, or ask your admin for option A |
| *UnsupportedClassVersionError* (option B) | Your Java is too old. Install Java 25 |
| *Kai could not start. The reason is shown above.* | Often "Port 8080 was already in use": Kai may already be running in another window. Use that one, or close it, or change `server.port` |
| Many files show **Could not check** | If you see "429" or "rate limit", set `kai.scan.parallel=1`. Otherwise click **Change settings**: the start page tests the AI connection again |

## Updating or removing Kai

When you get a new version, replace only the program (`kai.exe`, `kai` or `kai.jar`). Keep
your own `kai.properties` and your history folder. Close Kai before you replace it. With
option A, the new version cleans up the old `.kai-runtime` files by itself.

To remove Kai completely, delete the Kai folder. Keep your history folder if it is somewhere
else and you still need it.

---

# Part 2: For developers

## What it is

Kai is a Spring Boot 4.1 web app (Java 25, Thymeleaf, Spring AI 2.0.1 with the OpenAI
module; `ModelProvider` builds the chat model in code, it is not auto-configured) that runs
locally on the user's machine and talks to an OpenAI-compatible gateway. Each browser session opens on a start page (`/start`) for the settings, then the
user works in one chat page at `/`. Behind it, a plain-Java orchestrator runs three LLM
agents per file, plus an Extractor first when the user picked an updated file:

```
StartController (/start)  ── settings: folders, history folder, AI address/key/model
ChatController (UI at /)  ── where users work; agents never have a UI
      │
      ▼
Orchestrator  ── plain Java; fixed pool of kai.scan.parallel threads, one task per file
  ├─ ExtractorAgent "What changed in the updated file?"      -> Changes(changes, notFound), user confirms
  ├─ ScannerAgent   "Is this file affected? why?"            -> Verdict(affected, reason)
  ├─ EditorAgent    "Which passages change, and to what?"    -> Edits[(original, replacement)]
  └─ ReviewerAgent  "Does the edit do only what was asked?"  -> Review(ok, note)
      │
      ▼
DocumentRepository (adapter: type / list / read / write)  ── LocalFileRepository today
ChangeWriter ── stale check → report → byte copy of originals → write → roll back all on failure
History      ── the history folder: run folders, .kai-run.properties, History.html, /history
```

Documents are read through Apache Tika (docx, pptx, pdf) or as plain text. Only text files
are writable (`DocumentRepository.canWrite`). Edits are passage replacements that must
match exactly once (`Patch`), so untouched parts of a file stay byte-identical. See
`PLAN.md` for the full design and stage plan.

## Project structure

```
kai/
├── pom.xml                      Spring Boot 4.1.1, Spring AI 2.0.1, Tika 4.0.0; enforcer: JDK 25 only
├── kai.properties               dev settings (scans ./sampleDocs); also the template shipped to users
├── PLAN.md                      architecture, flow and stage plan
├── src/main/java/com/example/kai/
│   ├── KaiApplication.java      main: starts Spring (with kai.properties if any), prints the URL
│   ├── chat/                    StartController: the start page (/start + its JSON: /start/state, /folders, /ai, /proceed)
│   │                            ChatController: the chat (/), scan job, /confirm, /progress, /save, /finalize, /report, /new
│   ├── orchestrator/            Orchestrator, Finding (+ Report, Source), Extraction, Proposal, Patch, Diff, Progress
│   ├── agent/
│   │   ├── extractor/           ExtractorAgent
│   │   ├── scanner/             ScannerAgent
│   │   ├── editor/              EditorAgent
│   │   └── reviewer/            ReviewerAgent
│   ├── repository/              DocumentRepository + LocalFileRepository (Tika for Office/PDF)
│   ├── writer/                  ChangeWriter: stale check, report, originals, write, rollback;
│   │                            History: run folder names, summaries, History.html, listing
│   └── config/                  KaiConfig (finds, reads, checks and writes kai.properties),
│                                KaiProperties (checked kai.* values), Setup (holds them; start-page logic),
│                                ModelProvider (the AI connection: lists models, tests, builds the client)
├── src/main/resources/
│   ├── application.properties   built-in defaults; kai.properties overrides them
│   └── templates/               start.html (start page), chat.html (the UI), final-report.html (saved report)
├── sampleDocs/                  demo documents (git-ignored)
└── packaging/                   mechanism 2: one executable per OS
    ├── build.sh                 builds dist/ from an input folder (jar + JREs + kai.properties + README)
    ├── launcher/main.go         Go launcher that embeds the Java runtime and kai.jar
    ├── README.md                user guide shipped inside the packages
    ├── build/                   your build input folder (git-ignored)
    └── dist/                    output: windows/, macos/, kai-windows.zip, kai-macos.zip (git-ignored)
```

## Requirements

- JDK 25. The build fails on any other version (enforcer rule in `pom.xml`).
- An OpenAI-compatible AI service: address and key, typed on the start page, or in
  `kai.properties` (directly or as `${ICA_CODEX_KEY}` / `${ICA_BASE_URL_V1}` environment
  variables). The address must end in `/v1` (the OpenAI Java SDK only appends
  `chat/completions`); Kai adds it if missing.
- For mechanism 2 only: a Mac, Go (`brew install go`), and a Java 25 runtime for each target OS.

## Run from the IDE or command line

IntelliJ: run `KaiApplication` with no arguments. The working directory is the project
root, so it picks up `./kai.properties`, which scans `./sampleDocs`. To use another
settings file, add the program argument `--config=/path/to/kai.properties`.

```
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments=--config=/path/to/kai.properties
```

## How kai.properties is found and loaded

Both mechanisms below depend on this. Nothing about it stops Kai: `KaiApplication.main` only
starts Spring and prints the URL. Every browser session begins on the **start page**
(`/start`); the chat (`/`) redirects there until the user clicks **Start Kai** in that session.

Where the file is (`KaiConfig.locate`):

1. `--config=<path>`, if given. The packaged launcher always passes this, pointing next to the
   executable. It is also passed on to Spring, where `Setup` reads it from `ApplicationArguments`.
2. Otherwise `./kai.properties` in the working directory (IDE, `java -jar`, `spring-boot:run`).

Relative paths in the file are resolved against the file's own folder, not the working
directory. Backslashes are kept literally (so Windows paths paste as-is) and surrounding
quotes are stripped; as a side effect, `\` escapes and line continuations do not work in
this file. If the file exists, a copy (with `\` doubled) is passed to Spring as
`--spring.config.additional-location`, so `server.port` and other `spring.*` keys in it
override `application.properties`.

The start page (`StartController` → `Setup`). `start.html` is static; its script talks JSON:

- **`GET /start/state`:** reads the file fresh (blank fields and nothing checked if there is no
  file). The AI values have `${NAME}` / `${NAME:default}` resolved from the environment; an
  unset variable becomes a blank field plus a message. Then it runs both checks below, and
  `ready` is true if everything passes: the page then shows the **Ready to scan** summary
  instead of the form.
- **`POST /start/folders`:** `KaiConfig.inspect` with the form's folders in place of the file's
  (the history folder is not created yet). The result is per field: each folder's full path,
  problem, and document count (`LocalFileRepository.list`, the same listing the scan uses),
  the history folder's, a problem for the list as a whole, and `general` problems only fixable in
  the file (unknown `kai.*` keys, `kai.scan.box`, `kai.scan.parallel`). Called when a folder or
  history field loses focus, on Enter, and when a row is added or removed.
- **`POST /start/ai`:** `GET <base-url>/models` with the key (a 404/405 or non-JSON answer
  means "type the model name"). Called when the address or key field loses focus. The model
  list always comes from the service: the dropdown holds only what it lists, and a model from
  `kai.properties` that it doesn't list is not preselected and is flagged under the field
  (`Setup.modelProblem`, mirrored in the page's script so picking a model needs no request).
- **`POST /start/proceed`** (**Start Kai**, enabled only when the last checks all passed and
  nothing was edited since): checks again (now creating the history folder), lists the models
  once more, builds an `OpenAiChatModel` and makes one tiny call with the chosen model (429
  counts as working), writes the fields that differ from the file (so a `${ICA_CODEX_KEY}`
  line stays as it is unless the key was changed; comments kept; the file is created if
  missing), and only then switches `Setup` and `ModelProvider` to the new values and marks the
  session as started. Refused while the session's scan is running, so one scan uses one set of
  settings. On failure nothing is saved or switched.

| Key | Default | Rule |
|---|---|---|
| `kai.scan.local` | | Comma-separated folders; each must exist. Needed unless another target is set |
| `kai.scan.box` | | Placeholder for a Box adapter; setting it is an error for now |
| `kai.backup-dir` | **required** | The history folder. Created on Start Kai if missing; must not be inside a scanned folder |
| `kai.scan.parallel` | `4` | Whole number, 1 or more (file only, not on the start page) |
| any other `kai.*` | | Error: "Unknown setting" (catches typos) |
| `spring.ai.openai.api-key`, `.base-url`, `.chat.model` | | The AI connection; read and written by the start page |

The agents never hold a `ChatClient`: each call asks `ModelProvider.client(system)`.
Orchestrator and ChangeWriter read `Setup.properties()` (the scan takes one snapshot).
Spring AI's own auto-configured models are switched off in `application.properties`
(`spring.ai.model.*=none`). OpenAI-compatible services only, for now. A black-holed AI
address can take a few minutes to fail on Start Kai (60 s timeout, SDK retries 3 times);
listing models gives up after 10 s (connect) / 30 s (read).

## The history folder (`kai.backup-dir`)

`ChangeWriter` and `History` organise it for people; the layout is in Part 1, step 4.

- **Run folder name:** `yyyy-MM-dd HH.mm ` + the request's first line, with `\ / : * ? " < > |`
  and control characters removed, cut at a word near 40 characters, trailing dots and spaces
  dropped (Windows). `" (2)"`, `" (3)"`… if the name is taken.
- **`Original files/<scan folder name>/<file>`:** exact bytes, mirroring the scanned folders.
  Two scan folders with the same name (case-insensitive) get `" (2)"`.
- **`.kai-run.properties`:** `request`, `time`, `result`, `files`. Written as `UNFINISHED`
  before anything is written, then `APPLIED`, `ROLLED_BACK`, `ROLLBACK_INCOMPLETE` or
  `NOT_WRITTEN`. A plain properties file, so no JSON library is involved.
- **`History.html`:** `templates/history.html`, rewritten after every Finalize (best effort;
  the list can always be rebuilt from the run folders). The same template is served at
  `/history` with links to `/report/<folder>`; on disk it links relatively. The empty chat
  shows the newest five as **Recent changes**.
- **Older folders** (`yyyyMMdd-HHmmss/report.html`, no summary) are listed by date only and
  never moved. Folders that are not Kai runs are ignored.
- **`GET /report/<folder>`** only serves a direct child of the history folder that is a run.

## Two ways to ship Kai

| | **Mechanism 1: jar** | **Mechanism 2: packaged executable** |
|---|---|---|
| Artifact | `kai.jar` (Spring Boot fat jar) | `kai.exe` / `kai` (Go binary with JRE + jar embedded) |
| User needs | JDK/JRE 25 on the PATH | Nothing |
| Start | `java -jar kai.jar` from the folder with `kai.properties` | Double-click; finds `kai.properties` next to itself |
| Config lookup | `--config=` or `./kai.properties` (working directory) | Launcher passes `--config=<exe folder>/kai.properties` |
| Platforms | Any OS with Java 25 (incl. Linux) | Windows x64, macOS arm64 or x64 (one build per OS) |
| Size | ~150 MB | ~195 MB per OS (compressed runtime + jar) |
| Build | `./mvnw package` | `./mvnw package` + 2 JREs + `packaging/build.sh` |
| Signing prompts | None | SmartScreen (Windows), Gatekeeper (macOS) on first start |

Both run the **same jar**: mechanism 2 is only a wrapper around mechanism 1. There is no
second code path to test.

### Mechanism 1: run as a jar

**Why.** It is the simplest artifact a Spring Boot app can have: one file, one command,
works on every OS, and it is exactly what you run in development. It suits developers,
testers, Linux users, and machines where Java 25 is already managed by IT.

**How it works.** `spring-boot-maven-plugin` repackages the build into an executable fat
jar: your classes plus every dependency (Spring, Tomcat, Spring AI, Tika, POI, PDFBox)
under `BOOT-INF/lib`, with a Spring Boot loader as `Main-Class`. `java -jar` starts the
loader, which starts `KaiApplication.main`. Since there is no `--config`, `KaiConfig` looks
for `./kai.properties` in the **working directory**, which is why users must start it from
the Kai folder. Started anywhere else, the start page says there is no settings file yet and
**Start Kai** would create a new one in that directory.

**How to build and ship it.**

```
./mvnw package                       # -> target/kai-0.0.1-SNAPSHOT.jar
cp target/kai-0.0.1-SNAPSHOT.jar dist/kai.jar
cp kai.properties dist/              # point kai.scan.local / kai.backup-dir at user folders first
```

Give the user `kai.jar` and `kai.properties` in one folder. They run:

```
java -jar kai.jar                                  # uses ./kai.properties
java -jar kai.jar --config=/path/to/kai.properties # or an explicit file
```

**Trade-off.** Users must install the right Java (25; older Java fails with
`UnsupportedClassVersionError`) and use a terminal. For non-technical users that is the
main source of setup trouble, which is what mechanism 2 removes.

### Mechanism 2: package as an executable with kai.properties

**Why.** End users are non-technical. They should download one zip, edit one settings file,
and double-click, with no Java install, no PATH, and no terminal commands. GraalVM native
image was tried and dropped: Tika, POI, PDFBox and the OpenAI SDK rely on reflection and
would need a lot of untested native configuration. `jpackage` was not a fit either: it
produces an app folder or installer per OS and has to run on that OS. A small Go launcher gives a
single file per OS, can be cross-built for Windows and macOS from one Mac, and runs the
unchanged jar on a normal JVM.

**How it works at runtime** (`packaging/launcher/main.go`):

1. The binary contains `payload.tar.gz` (a Java 25 runtime as `jre/` plus `kai.jar`) via
   `go:embed`.
2. On start it unpacks the payload to `<exe folder>/.kai-runtime/<first 6 bytes of the
   payload's sha256>/`. It unpacks into a temp folder (`unpack-*`) and renames it at the end,
   so a half-finished unpack (power cut, two starts at once) is never used. If the folder
   already exists, it skips unpacking, so later starts are fast.
3. After a fresh unpack it deletes the folders of older versions (renamed to `trash-*`
   first; on Windows a still-running old version keeps its files locked, so it is left for
   the next start). Users never have to clean up.
4. It runs `<runtime>/jre/bin/java -jar <runtime>/kai.jar --config=<exe folder>/kai.properties`
   (unless the user passed their own `--config=`), with stdin/stdout/stderr attached, so
   Kai's messages and "press Enter" prompts appear in the same window. Ctrl+C reaches Java,
   which shuts down cleanly; the launcher exits with Java's exit code.

Why the exe folder and not a user cache folder: deleting the Kai folder removes everything.
The leading dot hides `.kai-runtime` in Finder, and `LocalFileRepository` skips hidden
paths, so Kai never scans its own runtime even if the folder is inside a scan target.

**How to build it** (on a Mac):

1. Build the jar: `./mvnw package`.
2. Get a Java 25 runtime for each target: a Windows x64 one and a macOS one (arm64 by
   default). The simplest is the Temurin 25 **JRE** download for each OS from adoptium.net,
   unzipped. You can make them smaller with `jlink` (only the modules Kai needs), but a
   jlinked runtime must be built from the target OS's JDK `jmods`, with the same JDK version.
3. Make an input folder (for example `packaging/build/`, which is git-ignored) with exactly:

```
packaging/build/
├── kai-0.0.1-SNAPSHOT.jar   exactly one *.jar (renamed to kai.jar inside the payload)
├── winjre/                  Windows runtime, must contain bin/java.exe
├── macjre/                  macOS runtime, must contain bin/java
├── kai.properties           the settings template users get
└── README.md                exactly one README* (copy of packaging/README.md)
```

4. Run:

```
packaging/build.sh packaging/build                 # Apple Silicon macjre (default)
MAC_ARCH=amd64 packaging/build.sh packaging/build  # if macjre is an Intel runtime
```

For each OS, `build.sh` tars `jre/` + `kai.jar` into `launcher/payload.tar.gz`
(`tar -h` turns symlinks into plain files, `COPYFILE_DISABLE` keeps out macOS `._` files),
cross-compiles the launcher with `GOOS`/`GOARCH` and `CGO_ENABLED=0`, copies
`kai.properties` and the README next to it, and zips the folder (the zip keeps the Mac
executable bit). The output is:

```
packaging/dist/
├── windows/   kai.exe, kai.properties, README.md
├── macos/     kai, kai.properties, README.md
├── kai-windows.zip
└── kai-macos.zip
```

Ship the zip for the user's OS. Each build has a new payload hash, so a new version
unpacks into a fresh folder and never mixes files with the old one.

**Things to know.**

- The binaries are not signed. Windows SmartScreen and macOS Gatekeeper warn on first
  start; `packaging/README.md` tells users how to get past that. Signing (and notarizing on
  macOS) would remove the prompts but needs a code-signing certificate and Apple developer account.
- The first start unpacks about 200 MB, so it takes up to a minute. The Kai folder must be
  writable (not `Program Files`, not a read-only share) and should not be in a synced folder.
- Windows is x64 only. For Linux, use mechanism 1 (or add a `build linux amd64 ...` line
  and a Linux runtime to `build.sh`).
- The JRE inside must be Java 25, the same version the jar is built for.

## Extending

To add a document source (for example Box): write a `@Component implements
DocumentRepository` whose `type()` is `box`, then turn the `kai.scan.box` check in
`KaiConfig.inspect` into a `Target("box", url, entry)`, and add its field to the start page
(`Setup.Form`, `start.html`). Spring injects every repository into the
`Orchestrator`, and backup and rollback go through the same interface, so they work for
the new source too.
