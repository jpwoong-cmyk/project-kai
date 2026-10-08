# Kai

Kai reads your team's documents and tells you which ones need updating when something
changes. You type the change in plain words, for example *"We now require Java 21 instead
of 17"*. Kai lists every document that mentions it, says why, and proposes the edit.

Kai reads Word (.docx), PowerPoint (.pptx), PDF and text files. It never changes a file
without your approval, and it backs up every file before it changes it.

You do not need to install anything. Kai brings everything it needs with it.

## What is in the folder

| File | What it is |
|---|---|
| `kai.exe` (Windows) or `kai` (Mac) | The Kai program |
| `kai.properties` | Kai's settings: which folders to check, where to keep the history of changes, and the AI connection |
| `README.md` | This guide |

Keep these files together in **one folder**. Kai looks for `kai.properties` next to itself.

When Kai starts for the first time, it adds a folder called `.kai-runtime` (100 MB or more) with
the parts it needs to run. It is hidden on a Mac. Leave it alone. Kai manages it by itself.

Put the Kai folder somewhere on your own computer, for example in `Documents`. Do not put it in
a Box Drive, OneDrive or other synced folder, or `.kai-runtime` gets uploaded too.

## 1. Unzip Kai

- **Windows:** right-click `kai-windows.zip` → **Extract All…**, and pick a folder, for
  example `Documents\Kai`. Do not start Kai from inside the zip. It cannot find its settings there.
- **Mac:** double-click `kai-macos.zip`, then move the `macos` folder where you like, for
  example to `Documents` (you can rename it `Kai`).

## 2. Set up kai.properties

**You can skip this step.** When you open Kai in the browser, its **start page** shows the
folders, the history folder and the AI connection from `kai.properties` (empty fields if there is
nothing yet), checks each one as you fill it in, and shows any problem under its field. When
everything works, click **Start Kai**. Kai saves the values into `kai.properties`.
This section is for editing the file by hand instead.

Open `kai.properties` in a plain text editor: **Notepad** on Windows (right-click → **Open with**
→ **Notepad**), **TextEdit** on Mac (right-click → **Open With** → **TextEdit**). Do not use Word.

A few rules for the whole file:

- A line that starts with `#` is a note. Kai ignores it. To switch a setting on, remove the `#`.
- Each setting is one line: `name=value`. Do not change the part before `=`.
- **Relative paths** such as `./docs` start from the folder where `kai.properties` is.
  `~/` means your home folder, for example `~/Documents/Runbooks`.
- **Windows paths** can be pasted as they are, for example from right-click → **Copy as path**.
  `C:\Team\Docs` and `"C:\Team\Docs"` both work.
- **Mac paths:** right-click the folder in Finder, hold **Option**, and choose **Copy "…" as Pathname**.

Below is every setting, in the order you will find it in the file.

### Folders to check (required)

```
kai.scan.local=./docs
```

The folder or folders with your documents. Kai also checks every folder inside them. To check
several folders, separate them with commas:

```
kai.scan.local=~/Documents/Runbooks, C:\Team\Docs
```

On the start page, each folder has its own field instead: click **+ Add folder** for another one.

**Box Drive** and other synced folders (OneDrive, shared drives) work like any other folder.
Example on a Mac: `kai.scan.local=~/Library/CloudStorage/Box-Box/Team Docs`.
Files that are only in the cloud and not downloaded to your computer are skipped.

Leave the `# kai.scan.box=…` line as it is (with the `#`). Box web links are not supported yet.

### History folder (required)

```
kai.backup-dir=~/Kai history
```

Every time you click **Finalize**, Kai keeps a report of the change here, together with the
original files from before it. Kai creates the folder if it is not there yet. It must **not**
be inside one of the folders you check. (The setting is still called `backup-dir`.)

```
Kai history/                                  (your history folder)
  History.html                                ← open this: every change, newest first
  2026-10-08 14.35 We now require Java 21/    ← one folder per Finalize: date, time, request
    Report.html                               ← before and after of every changed file
    Original files/                           ← the files exactly as they were before
      sampleDocs/app-onboarding-runbook.md
```

Open **History.html** to see every change, even when Kai is not running. To get a file back
by hand, copy it from **Original files**. Older changes, from before this layout, keep their
folder names (for example `20261008-143512`) and are listed in History.html too.

### Speed

```
kai.scan.parallel=4
```

How many files Kai checks at the same time. Leave it at `4`. If many files show
**Could not check** with "429" or "rate limit", lower it to `2` or `1`.

### AI connection (ask your Kai admin)

```
spring.ai.openai.api-key=${ICA_CODEX_KEY}
spring.ai.openai.base-url=${ICA_BASE_URL_V1}
spring.ai.openai.chat.model=gpt-5.6-terra
```

Your Kai admin tells you what to put here. You can also type them on the start page: once the
address and key work, the **Model** list shows the models you can use. Kai saves only values that
work, into these lines. To get back to the start page later, click **Change settings** next to
*AI model* at the top of the Kai page.

- `api-key` is your key for the AI service. Keep it private, like a password, and do not share
  your `kai.properties` with others once your key is in it.
- `base-url` is the address of the AI service. It ends with `/v1`; Kai adds it if it is missing.
- `chat.model` is the AI model's name.
- `${...}` means "take the value from this computer's settings". If your admin set that up for
  you, leave those lines as they are. Otherwise replace the whole `${...}` with the value, for
  example `spring.ai.openai.api-key=sk-abc123`.

### Web address (only if needed)

```
# server.port=8080
```

Kai opens a page at **http://localhost:8080**. Only if Kai says that it cannot start because
the port is in use: remove the `#` and change `8080` to another number, such as `8090`.
The address then becomes http://localhost:8090.

**Save the file** when you are done (keep the name exactly `kai.properties`).

## 3. Start Kai

### Windows

Double-click `kai.exe`. A black window opens.

The first time, Windows may show **"Windows protected your PC"**. Click **More info** →
**Run anyway**. This happens because Kai is an in-house program, not one from a store.

### Mac

Double-click `kai`. A Terminal window opens.

The first time, the Mac may say **"kai" cannot be opened** or **"Apple could not verify…"**.
Click **Done** (or **OK**), then open **System Settings** → **Privacy & Security**, scroll down,
and click **Open Anyway** next to the message about `kai`. On older Macs: right-click `kai` →
**Open** → **Open**. You only have to do this once.

### Both

The **first start takes longer** (up to a minute) because Kai unpacks itself into a folder
called `.kai-runtime` next to the program. Later starts are faster. Kai then shows:

```
Kai is running. Open http://localhost:8080 in your browser.
Keep this window open while you use Kai. Close it to stop Kai.
```

Open **http://localhost:8080** in your browser (Chrome, Edge, Safari, …). You always see the
**start page** first. If your settings already work, it shows a short **Ready to scan** summary
(your folders with their number of documents, the history folder and the AI model): click
**Start Kai**. Otherwise it shows the settings with each problem under its field. Kai checks a
field when you leave it (or press Enter), and **Start Kai** stays greyed out until everything
works. To change settings later, click **Edit settings** on the summary, or **Change settings**
next to *AI model* on the Kai page.

**Keep the window open** while you use Kai. To stop Kai, close the window (or press
**Ctrl+C** in it). On a Mac the window then says *[Process completed]*, and you can close it.

## 4. Use Kai

Type the change in the box at the bottom and click **Scan documents**. You can write one
sentence or paste release notes several paragraphs long. **Enter** starts a new line. Only
the button sends.

**Already updated one document?** Pick it under **Updated file** above the box, type a short
summary of what changed (for example "support email changed, Java now 21") and click **Find
changes**. Kai reads that file and lists the changes it found, plus anything in your summary
it could not find in the file. Fix the list if Kai got something wrong, then click **Scan
other files**. Every other file is checked against that list, and the updated file itself is
never changed. A summary is required: it tells Kai which changes matter.

While Kai works, a live log shows what it is doing for each file. The results appear by
themselves when the scan is done. You can refresh the page in the meantime. One scan runs at a time.

The results are grouped by **Location**: each folder from `kai.scan.local`, with how many of
its files are affected. Each file gets one of these results:

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
- **Edit:** opens the proposed text so you can change it. Click **Save edits** and the red and
  green view updates.
- **Include this file:** untick it to leave that file as it is.

Saving edits does **not** change your files yet.

When you are happy, click **Finalize** and confirm. Kai then:

1. Checks that no file changed since the scan. If one did, Kai writes nothing and asks you to
   scan again, so it never overwrites someone else's newer work.
2. Saves a report of every change (before and after) and a copy of the original files, in a
   new folder in your history folder.
3. Changes the ticked files. If changing any file fails, Kai puts **all** of them back from the
   copies and says **Rolled back**, so your files are exactly as before.

The reply has an **Open the report** link. Every report also stays reachable later: click
**Past reports** at the top of the Kai page, look under **Recent changes** on an empty chat, or
open **History.html** in your history folder.

To start again with an empty page, click **New chat** at the top. Your settings stay as they
are. If a report still has edits you have not written with **Finalize**, or changes found in an
updated file are still waiting for **Scan other files**, Kai asks first, because a new chat
discards them. Finalized reports are not affected: they stay in the history folder and under
**Past reports**.

## If something goes wrong

Problems with the settings never stop Kai. They appear in red on the **start page**, under the
field they belong to. Fix the field and leave it (or press Enter) to check it again. Nothing is
saved until you click **Start Kai**, and Start Kai first tests the chosen model, so it only saves
settings that work. A few problems (marked *in kai.properties* below) can only be fixed
in the file: fix it, save it, and reload the start page in the browser.

| Message | What to do |
|---|---|
| *There is no settings file yet* | Normal the first time, or if `kai.properties` is not next to `kai.exe` / `kai`. Fill in the fields; **Start Kai** creates the file. If you do have one, check its name: Windows sometimes saves it as `kai.properties.txt` (in File Explorer turn on **View** → **Show** → **File name extensions**) |
| *Folder to scan not found* | Check the path. Copy it again from File Explorer or Finder |
| *Enter a history folder* | Type a history folder, for example `~/Kai history` |
| *This is inside a folder Kai scans* (history folder) | Choose a history folder outside your document folders, for example `~/Kai history` |
| *Folders to scan: add at least one folder* | Type at least one folder |
| *Box folders are not supported yet* (in kai.properties) | Put a `#` in front of the `kai.scan.box` line |
| *Unknown setting* (in kai.properties) | A setting name is misspelled. Compare it with the list in the message |
| *kai.scan.parallel … must be a whole number* (in kai.properties) | Use a number such as `4`, without spaces or letters |
| *… is not set on this computer* | `kai.properties` takes the value from a `${...}` computer setting that does not exist here. Type the value in the field instead |
| *The AI key is not set* / *The AI address is not set* | Fill in the address and key, with the values from your Kai admin |
| *The AI address must start with https://* | Check the address with your Kai admin |
| *"…" is not offered by this AI service* | The model in `kai.properties` is not available (any more). Choose one from the **Model** list and click **Start Kai** |
| *Enter the model name your Kai admin gave you* | This AI service does not list its models. Type the name, and click **Start Kai** |
| *A scan is still running* | Wait until the scan has finished, then click **Start Kai** again |
| *The AI service refused the key* | The key is wrong or has expired. Ask your Kai admin for a new one |
| *The AI service does not know this address or model* | Check the address and the model name with your Kai admin |
| *Kai cannot reach the AI service* | Check that you are online, and on the company VPN if you need one |
| *Kai could not start: unpacking Java failed* | Kai needs to write into its own folder. Move the Kai folder somewhere you can save files, such as `Documents` (not `Program Files`, and not a read-only shared drive). Also check that your disk is not full |
| *Kai could not start. The reason is shown above.* | Often the port is in use ("Port 8080 was already in use"). Kai may already be running in another window. Use that one, or close it. Otherwise change `server.port` (see above) |
| Many files show **Could not check** | If you see "429" or "rate limit", set `kai.scan.parallel=1`. Otherwise click **Change settings** next to *AI model*: the start page tests the AI connection again |

## Updating Kai

When you get a new version, replace only `kai.exe` / `kai`. Keep your own `kai.properties`
and your history folder.

Close Kai before you replace it. The new version replaces its `.kai-runtime` folder by itself
the first time it starts.

To remove Kai completely, delete the Kai folder. That removes everything, including
`.kai-runtime`. Keep your history folder if it is somewhere else and you still need it.
