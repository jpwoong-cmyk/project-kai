# Kai — hackathon plan

Input a change request → agents find affected files and propose edits → **you review
and edit the proposals** → final HTML report → copy of the originals → write → auto-rollback on failure.

## Flow

```
[Scan] ──> [Review & edit] ──> [Finalize] ──────────────────────────────┐
 agents     you: tick/untick     1. final report  <history>/<run>/Report.html
 propose    files, edit the      2. originals     <history>/<run>/Original files/<scan folder>/...
            proposed text,       3. write all selected files
            save as often as     4. any write fails -> restore ALL from backup
            you like             5. chat reply: Applied / Rolled back
```

Nothing touches disk until you click **Finalize**. Until then, proposals and your
edits live in the HTTP session.

## Architecture

```
StartController (/start)  ── start page, first in every browser session: settings only
ChatController (UI: /)  ── where users work; agents never have a UI
      │
      ▼
Orchestrator  ── plain Java, decides the order and passes results between agents.
                 Agents have no UI and never talk to the user.
  ├─ ExtractorAgent "What changed in the updated file?"      (optional, once, before the scan)
  ├─ ScannerAgent   "Is this file affected? why?"            (1 LLM call per file)
  ├─ EditorAgent    "Which passages change, and to what?"    (affected text files only)
  └─ ReviewerAgent  "Does the edit do only what was asked?"  (advice shown to you)
      │
      ▼
DocumentRepository (adapter interface: type / list / read / write)
  ├─ LocalFileRepository   folder on disk              ← MVP
  ├─ SharePointRepository  Graph API                   (future)
  └─ OneDriveRepository    Graph API                   (future)

ChangeWriter ── report → backup → write → rollback-on-failure (no LLM)
               runs are kept in the history folder (kai.backup-dir), with History.html; it reads/writes
               only via DocumentRepository, so rollback works for any adapter
```

Adding a repository = one `@Component implements DocumentRepository` in `repository/`.
Spring injects all of them into the Orchestrator; each `kai.scan.<type>` key in kai.properties
(e.g. `kai.scan.local`) is a target scanned by the adapter whose `type()` matches.

## Project structure

```
src/main/java/com/example/kai/
├── KaiApplication.java
├── chat/          StartController: start page (/start, settings); ChatController: the chat (/ and /chat)
├── orchestrator/  Orchestrator, Finding (+ Report), Proposal, Patch (applies edits), Diff (line diff)
├── agent/
│   ├── extractor/ ExtractorAgent: changes found in an updated file (stage 6)
│   ├── scanner/   ScannerAgent: stage 1 stub, stage 2 LLM
│   ├── editor/    EditorAgent: passage edits (original -> replacement)
│   └── reviewer/  ReviewerAgent: ok + note on the edited result
├── repository/    DocumentRepository + all adapters (LocalFileRepository; later SharePoint, OneDrive)
├── writer/        ChangeWriter: stale check, report, originals, write, rollback; History: the history folder
└── config/        KaiConfig: finds, reads, checks, writes kai.properties; KaiProperties: checked kai.* values;
                   Setup: holds them, start-page logic; ModelProvider: the AI connection
src/main/resources/templates/start.html, chat.html
kai.properties     user settings; lives next to kai.jar (or project root in the IDE)
README.md          how to run (users first, developers at the bottom)
sampleDocs/        10 cloud-onboarding test documents (git-ignored)
```

## Sample documents (`sampleDocs/`)

The scanner reads every format. Only text files (md, txt, yml, …) can be rewritten by Kai;
docx, pptx and pdf are read with Apache Tika and marked **Update by hand** when affected.

| File | Topic | Acme Portal | Java 17 | Port 8080 | support@ |
|---|---|:-:|:-:|:-:|:-:|
| README.md | Onboarding overview | ✓ | ✓ | | ✓ |
| landing-zone-setup.md | Accounts, OUs, regions | ✓ | | | |
| app-onboarding-runbook.md | Container deploy steps | | ✓ `temurin:17` | ✓ | |
| tagging-and-cost-policy.md | Tags and budgets | | | | |
| iam-access-onboarding.docx | SSO roles, access requests | ✓ | | | ✓ |
| network-vpc-guide.docx | Subnets, security groups | | | ✓ | |
| security-baseline.pdf | Patching, encryption | | ✓ `JDK 17` | | |
| dr-backup-policy.pdf | RPO/RTO tiers | | | | |
| onboarding-checklist.pptx | 5-step checklist deck | ✓ | ✓ | ✓ | |
| cloud-101-training.pptx | New-joiner training | | (says "Java" only) | | |

Expected scan results (LLM may vary slightly):

| Change request | Affected | Of which by hand |
|---|---|---|
| Rename the product from Acme Portal to Kai Hub | 4 | 2 (docx, pptx) |
| We now require Java 21 instead of 17 | 4 | 2 (pdf, pptx) |
| Change the application port from 8080 to 9090 | 3 | 2 (docx, pptx) |
| Support email is now help@kaihub.example | 2 | 1 (docx) |

`tagging-and-cost-policy.md`, `dr-backup-policy.pdf` and `cloud-101-training.pptx` are true
negatives. The training deck mentions Java as a supported language, so it checks that the
scanner doesn't flag a file just for naming a related topic.

## Configuration

`kai.properties` never stops Kai. Every browser session opens on the **start page**, which reads
the file fresh (blank fields if it is missing), checks each setting as the user edits it (with
each problem under its field), always lists the models from the AI service, and saves working
values back on **Start Kai** (creating the file if needed). Where it is looked for:
`--config=<path>` → `./kai.properties` in the working directory. Relative paths start from
the file's folder. Full rules and run instructions: `README.md`.

| Key | Default | Purpose |
|---|---|---|
| `kai.scan.local` | | Folders to scan, comma-separated. Each must exist |
| `kai.scan.box` | | Box **file** shared links, comma-separated (stage 2b). Folder links need an API token: not supported yet |
| `kai.scan.parallel` | `4` | Files checked at the same time. Lower it on 429 rate-limit errors. Must be ≥ 1 |
| `kai.backup-dir` | **required** | The history folder: a folder per Finalize (`Report.html`, `Original files/`), plus `History.html`. Created if missing. Must not be inside a scanned folder |
| `spring.ai.openai.api-key`, `.base-url`, `.chat.model` | | The AI connection (OpenAI-compatible); set on the start page |
| `server.port`, other `spring.*` | from application.properties | Any Spring Boot key |

Unknown `kai.*` keys are errors, so typos don't get silently ignored.

Build: `./mvnw package` → `packaging/build/kai-0.0.1-SNAPSHOT.jar`.

## Stages (review each before moving on)

| # | Stage | LLM? | Done when |
|---|-------|------|-----------|
| 1 | Skeleton: sample docs, `DocumentRepository` + `LocalFileRepository`, orchestrator, **stub** scanner, report table | No | Typing a change request in the chat at `/` returns a table of every file with affected yes/no from a keyword stub ✅ built |
| 2 | ScannerAgent: real LLM, structured output `Verdict(affected, reason)`; per-file Error status | Yes | On `sampleDocs/`, the four requests above give the expected counts; docx/pptx/pdf are read (Tika) and shown as "Update by hand"; bad model → "Could not check" rows, never "No change" ✅ built |
| 2b | **Box (cloud) + local together**: `BoxRepository` reads Box files by public shared link, read-only | No (scan uses LLM as before) | Two sample docs moved from `sampleDocs/` to Box: the Java 21 request still finds 4 affected / 2 by hand, Box rows show their source and open in Box. Bad or folder link → plain startup message. ✅ Done instead through a Box Drive folder in `kai.scan.local` (see below) |
| 3 | EditorAgent + ReviewerAgent → **review in the chat**: the bot reply shows, per file: a **diff** of original vs proposed, an Edit toggle (editable textarea of the proposed text), include checkbox, reviewer note. "Save edits" button | Yes | You can change a proposal, save, reload, and still see your edit (diff updated). Editor + Reviewer run in parallel. Nothing is written to disk ✅ built |
| 4 | **Finalize**: write final HTML report (before/after for each selected file) → back up originals → write → if any write fails, restore every file from the backup | No | Happy path: files changed and report saved. Forced failure: every file identical to before, and the chat says "Rolled back" ✅ built (forced-failure test deferred, see below) |
| 4b | **docx/pptx write-back** (after stage 4, decided 2026-10-01): Apache POI, edits only inside one paragraph; anything else stays "Could not apply" / "Update by hand". PDF stays by hand | No (edits come from the Editor as now) | Sample docx/pptx edited, open in Word/PowerPoint without a repair prompt, rollback restores them byte-for-byte |
| 6 | **Updated file as the change** (2026-10-08): pick the updated file + type a summary → ExtractorAgent lists the changes → user confirms/edits → scan other files (updated file skipped) | Yes | See "Stage 6" below ✅ built (uncompiled) |
| 5 | Demo polish: "simulate failure" checkbox (fails on the last file) to show rollback live, agent log panel (✅ built early: live progress log), clearer errors | No | 2-minute demo runs cleanly |

Stage 2b outcome (2026-10-01): **done through Box Drive, no Box code.** The hackathon has no
access to Box or SharePoint through developer tokens or APIs: no developer account, and public
links need a login. Box Drive shows Box as a local folder, so it goes in `kai.scan.local` like
any other folder, e.g. `/Users/<you>/Library/CloudStorage/Box-Box/kaitest`. Kai reads it
locally. Only files that Box Drive shows in the folder (`ls`) are scanned. `kai.scan.box` stays
a placeholder that is rejected at startup. The plan below is parked.

Parked plan (shared links, needs links that download without login):

- **Spike first (5 min, on your machine):** confirm a public file link downloads without
  login: `curl -L -o test.pdf https://app.box.com/shared/static/<id>` (same `<id>` as the
  `https://app.box.com/s/<id>` link). If it doesn't, stop and get a developer token instead.
- **Config:** `kai.scan.box=<link>, <link>` takes **file** shared links (`/s/<id>` or
  `/shared/static/<id>`), comma-separated like `kai.scan.local`. Links must be shared with
  "People with the link" and allow download.
- **Startup check (KaiConfig):** each link must be https on a box.com host and actually
  download; otherwise a plain message ("This Box link can't be downloaded: is it a folder, or
  is download turned off?"). Folder links can't be listed without an API token, so they are
  reported as "not supported yet — share the files one by one".
- **`BoxRepository`** (`type()` = `box`): `list(link)` returns the file name (from the
  download's `Content-Disposition`); `read()` downloads with `java.net.http.HttpClient` and
  extracts text with Tika, same size limits as local; `canWrite()` = false.
- **Read-only:** affected Box files show as "Update by hand", with the file name linking to the
  Box page. Writing back to Box (and rollback through Box) is a later stage; it needs a token
  with edit rights.
- **Report:** local and Box files in one table; each row shows its source (`local` / `box`).
- **Later, with a token:** folder links via the Box API (`/2.0/shared_items` + folder items),
  then write-back. Same adapter, no changes elsewhere.

Stage 3 requirements (built 2026-10-01):

- **Parallel:** Editor + Reviewer run per file in parallel, in the same `kai.scan.parallel`
  pool as the scan (Reviewer runs after the Editor for the same file). A failure on one file
  shows as an error on that file only and never blocks the others.
- **Diff, not two boxes:** each file shows a line diff of original → proposed (removed lines
  red, added lines green). "Edit" opens the proposed text in a textarea; after "Save edits"
  the diff is recalculated from your edited text.
- Only editable (text) files get a proposal; affected docx/pptx/pdf stay "Update by hand".
- **Passage edits, any size:** the Editor returns a list of `(original passage → new passage)`
  edits, not the whole file. A passage can be a sentence, a paragraph or a whole section, and
  the new passage can be fully rewritten, longer, or add new paragraphs (anchored to a
  neighbouring passage). Changes are not limited to word swaps: e.g. a feature release that
  changes how other features work → the affected sections are rewritten properly. Everything
  not in an edit stays byte-for-byte identical, so the diff shows only real changes. If the
  whole document needs rewriting, that is one edit whose passage is the entire file.
- **Applying edits:** Kai applies an edit only if its original passage appears **exactly once**
  in the file. If any edit can't be placed, Kai retries the Editor **once automatically**,
  telling it which passages didn't match. Still failing → that file shows "Could not apply"
  (no guessing); other files are unaffected.
- **Reviewer** checks the edited result against the change request: does it do what was
  asked, nothing unrelated, and was anything affected missed?
- **Multi-line request box:** the chat input becomes a textarea, so users can paste release
  notes or a feature description several paragraphs long. Enter only adds a new line (never
  sends); the only way to send is clicking **Scan documents**. No keyboard shortcut.

How stage 3 is built:

- `Orchestrator.check` runs scan → `propose` in one pool task per file, so edits overlap with
  other files' scans. `propose` = `EditorAgent` → `Patch.apply` → on `NoMatch`, one retry with
  the problems listed → `ReviewerAgent`. A reviewer failure keeps the proposal ("Not reviewed").
- `Patch.apply` locates every original in the *unchanged* file (exactly once, no overlaps),
  then splices the replacements in. Edits written with `\n` are matched against `\r\n` files.
- `Proposal` (mutable, in the session) holds original, proposed, include tick, edited flag and
  the review. `Diff` is a plain LCS line diff with 2 context lines; the rest is folded.
- The report table is grouped by target (`Report.groups()`): a Location column (the
  `kai.scan.*` entry as typed, `Target.entry`, plus "N of M affected") spans its files' rows,
  followed by one folded "No change needed" row per location.
- `POST /save` takes `report=<message index>`, `text<i>` and `include<i>` (i = finding index).
  Textarea `\r\n` is turned back into `\n` unless the file uses `\r\n`.

How stage 4 is built (2026-10-01):

- **Finalize** button next to Save edits (same form, `formaction=/finalize`), so unsaved edits
  count. Browser confirm first. `ChatController` → `Orchestrator.apply` → `ChangeWriter.apply`.
- Selected = ready proposal, ticked, and different from the original (`Finding.selected()`).
- Order: stale check (current file text must equal the scanned original, else refuse ALL,
  nothing written) → `Report.html` (status "Not written yet") → byte copies via
  `DocumentRepository.readBytes` to `<history>/<run>/Original files/<scan folder>/<file>` →
  write → on the first failure, restore every selected file with `writeBytes`. The report is
  saved again with the outcome. Run folder = `yyyy-MM-dd HH.mm <start of the request>` (was
  `yyyyMMdd-HHmmss` before 2026-10-08); `.kai-run.properties` and `History.html` are updated last.
- The chat reply links to `GET /report/<run folder>`, which serves the saved report (browsers block
  `file://` links from a web page). An applied report hides its buttons; after a rollback
  you can Finalize again.
- `KaiConfig` rejects a history folder inside a scanned folder, else reports and originals would be scanned.
- Forced-failure test: deferred to stage 5's "simulate failure" checkbox (user, 2026-10-01).

Live progress log (added after stage 4, 2026-10-01): `POST /chat` starts the scan on a virtual
thread and returns at once; the session holds a `Job(Progress, CompletableFuture<Report>)`.
The Orchestrator adds one line per agent step to `Progress`. The page polls
`GET /progress?since=n` every second and reloads when done; `GET /` turns a finished job into
the bot message (only request threads touch the history). One scan at a time per session.

Stage 4b notes (not started; text files only until then): read docx/pptx with POI per paragraph
(not Tika) so passages match what is written back; replace only the text pieces (runs) inside
the match so the rest keeps its formatting; cover body + tables (docx) and shapes + tables
(pptx), headers/footers/notes later; for Office files the Edit toggle edits each replacement
passage, not the whole text. Needs byte-level read/write in `DocumentRepository`, which
stage 4's backup should already provide. Estimate ~1 day (docx ~½ day, pptx +2–3 h).

Stage 4 also detects **stale files**: if a file changed on disk after the scan,
Finalize refuses to overwrite it, so your review can't silently clobber newer content.

Stage 6: updated file as the change (built 2026-10-08, uncompiled):

- **Why:** users often update one document first and want the rest to follow. There is no
  old version to diff (no snapshots, by choice), so the user's summary says what matters.
- **UI:** optional **Updated file** dropdown above the chat box (default "None", one group per
  `kai.scan.*` location). Picked → the box is the summary (required), the button reads
  **Find changes**. "None" → exactly the old flow.
- **Flow:** `POST /chat` with `source=<location position>:<file>` → `Orchestrator.source` (only
  files Kai would scan are accepted) → job runs `Orchestrator.extract` → `ExtractorAgent`
  returns `Changes(changes, notFound)` → bot message with an editable list (one change per
  line) and the not-found items as a warning. **Scan other files** (`POST /confirm`) makes
  the edited list the change request (`Extraction.instruction`) and runs the normal
  `scan(instruction, source, progress)`, which skips the updated file. Report and saved
  report show "Checked against <file>".
- **Why the pause:** the extracted list steers every proposed edit, so a misread is cheaper
  to fix here than after the whole scan. One click.
- Size: the updated file is read once, with the usual `LocalFileRepository` limits.

## Demo script (draft)

1. *"Rename the product from Acme Portal to Kai Hub"*: easy, keyword-level.
2. *"We now require Java 21 instead of 17"*: the LLM catches `Java 17`, `JDK 17`
   (in a PDF) and `temurin:17`, but skips the training deck that only says "Java".
3. In review, hand-edit one proposal (e.g. change "Temurin" to "any JDK 21"), untick one file.
4. Finalize → open the report → show files changed.
4b. Updated file: edit the support email in `README.md` by hand, pick it under Updated file,
   type "support email changed", confirm the list → the docx shows "Update by hand".
5. Run again with "simulate failure" → show the chat says "Rolled back" and the files are unchanged.
