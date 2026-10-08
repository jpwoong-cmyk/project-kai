package com.example.kai.repository;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.example.kai.agent.scanner.ScannerAgent;
import com.example.kai.orchestrator.Progress;

/** Read a large file completely, calling the existing ScannerAgent with bounded context. */
public final class ChunkedScan {

    private static final int CHUNK_CHARS = 12_000;
    private static final int OVERLAP_CHARS = 240;

    public record Result(boolean affected, String reason, int sections) { }

    private ChunkedScan() { }

    public static Result assess(Reader reader, ScannerAgent scanner, String instruction,
                                String filename, Progress progress) throws IOException {
        char[] buffer = new char[CHUNK_CHARS];
        String overlap = "";
        int sections = 0;
        boolean affected = false;
        List<String> reasons = new ArrayList<>();
        int read;
        while ((read = reader.read(buffer)) != -1) {
            if (read == 0) continue;
            sections++;
            String section = overlap + new String(buffer, 0, read);
            ScannerAgent.Verdict verdict = scanner.assess(instruction,
                    filename + " [section " + sections + "]", section);
            if (verdict.affected()) {
                affected = true;
                // Keep the final report concise; continue scanning the complete file.
                if (reasons.size() < 3) reasons.add("Section " + sections + ": " + verdict.reason());
            }
            overlap = section.substring(Math.max(0, section.length() - OVERLAP_CHARS));
            if (sections % 20 == 0) {
                progress.add("Scanner: " + filename + ": checked " + sections + " sections");
            }
        }
        if (sections == 0) return new Result(false, "No readable text found in the document", 0);
        return new Result(affected,
                affected ? String.join("; ", reasons) + " (scanned " + sections + " sections; update manually)"
                         : "No changes required across " + sections + " sections",
                sections);
    }
}
