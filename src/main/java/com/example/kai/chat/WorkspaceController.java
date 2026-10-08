package com.example.kai.chat;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.kai.config.ModelProvider;
import com.example.kai.config.Setup;

/** Local workspace settings. Never modifies scanner, editor or report code. */
@Controller
public class WorkspaceController {

    private final Setup setup;
    private final ModelProvider models;

    public WorkspaceController(Setup setup, ModelProvider models) {
        this.setup = setup;
        this.models = models;
    }

    public record FolderRequest(List<String> scan, String backup) { }
    public record ModelRequest(String model) { }

    @GetMapping("/workspace/models")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> offeredModels(HttpSession session) {
        if (!active(session)) return problem(HttpStatus.FORBIDDEN, "Start KAI first.");
        try {
            ModelProvider.Settings current = models.settings();
            return ResponseEntity.ok(Map.of("ok", true,
                    "current", current.model(),
                    "models", models.models(current.url(), current.key())));
        } catch (ModelProvider.Problem ex) {
            return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/workspace/folders")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> folders(@RequestBody FolderRequest request, HttpSession session) {
        ResponseEntity<Map<String, Object>> blocked = canChange(session);
        if (blocked != null) return blocked;
        List<String> folders = request.scan() == null ? List.of() :
                request.scan().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        String backup = request.backup() == null ? "" : request.backup().trim();
        if (folders.isEmpty()) return problem(HttpStatus.BAD_REQUEST, "Choose at least one scan folder.");
        String issue = setup.updateWorkspaceFolders(folders, backup);
        return issue == null ? ResponseEntity.ok(Map.of("ok", true))
                : problem(HttpStatus.BAD_REQUEST, issue);
    }

    @PostMapping("/workspace/model")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> selectModel(@RequestBody ModelRequest request, HttpSession session) {
        ResponseEntity<Map<String, Object>> blocked = canChange(session);
        if (blocked != null) return blocked;
        String issue = setup.updateWorkspaceModel(request.model());
        return issue == null ? ResponseEntity.ok(Map.of("ok", true))
                : problem(HttpStatus.BAD_REQUEST, issue);
    }

    private static boolean active(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(ChatController.STARTED));
    }

    private static ResponseEntity<Map<String, Object>> canChange(HttpSession session) {
        if (!active(session)) return problem(HttpStatus.FORBIDDEN, "Start KAI first.");
        if (session.getAttribute("job") != null)
            return problem(HttpStatus.CONFLICT, "Wait until the current scan finishes before changing workspace settings.");
        return null;
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String issue) {
        return ResponseEntity.status(status).body(Map.of("ok", false, "problem", issue));
    }
}
