package com.example.kai.chat;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.example.kai.config.Setup;

// The start page: every browser session begins here. Existing settings and validation stay unchanged.
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

    // Local folder picker. The Windows path is selected by PowerShell/WinForms, not Java AWT.
    // This avoids Spring or a previously initialized JVM marking Java graphics as headless.
    @PostMapping("/start/browse")
    @ResponseBody
    public Map<String, String> browse(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!"127.0.0.1".equals(remote) && !"::1".equals(remote)
                && !"0:0:0:0:0:0:0:1".equals(remote)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !"same-origin".equals(fetchSite) && !"none".equals(fetchSite)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String origin = request.getHeader("Origin");
        if (origin != null) {
            String expected = request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 80 && "http".equals(request.getScheme()))
                    || (request.getServerPort() == 443 && "https".equals(request.getScheme()))
                    ? "" : ":" + request.getServerPort());
            if (!origin.equalsIgnoreCase(expected)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return browseWindows();
        }

        if (GraphicsEnvironment.isHeadless()) {
            return Map.of("error", "No graphical desktop is available on the computer running Kai.");
        }
        AtomicReference<String> selection = new AtomicReference<>("");
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select folder to scan");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setMultiSelectionEnabled(false);
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    selection.set(chooser.getSelectedFile().getAbsolutePath());
                }
            });
            return Map.of("path", selection.get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Folder selection interrupted.");
        } catch (InvocationTargetException ex) {
            return Map.of("error", "Could not open folder picker.");
        }
    }

    private static Map<String, String> browseWindows() {
        // Use the Windows Vista+ Explorer folder picker (IFileDialog with FOS_PICKFOLDERS).
        // FolderBrowserDialog used by Windows PowerShell 5.1 can display the older XP-style tree.
        // The script is constant: no paths or commands from the browser are interpolated.
        String script = """
                $ErrorActionPreference = 'Stop'
                Add-Type -TypeDefinition @'
                using System;
                using System.Runtime.InteropServices;

                [ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"),
                 InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface IKaiFileDialog {
                    [PreserveSig] int Show(IntPtr owner);
                    void SetFileTypes(uint count, IntPtr filters);
                    void SetFileTypeIndex(uint index);
                    void GetFileTypeIndex(out uint index);
                    void Advise(IntPtr events, out uint cookie);
                    void Unadvise(uint cookie);
                    void SetOptions(uint options);
                    void GetOptions(out uint options);
                    void SetDefaultFolder(IntPtr item);
                    void SetFolder(IntPtr item);
                    void GetFolder(out IntPtr item);
                    void GetCurrentSelection(out IntPtr item);
                    void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
                    void GetFileName(out IntPtr name);
                    void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
                    void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
                    void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
                    void GetResult(out IKaiShellItem item);
                }

                [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"),
                 InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
                public interface IKaiShellItem {
                    void BindToHandler(IntPtr context, ref Guid handler, ref Guid iid, out IntPtr result);
                    void GetParent(out IKaiShellItem parent);
                    void GetDisplayName(uint type, out IntPtr name);
                }

                public static class KaiNativeFolderPicker {
                    public static string Choose() {
                        // CLSID_FileOpenDialog, FOS_PICKFOLDERS and FOS_FORCEFILESYSTEM.
                        Type type = Type.GetTypeFromCLSID(new Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7"));
                        IKaiFileDialog dialog = (IKaiFileDialog)Activator.CreateInstance(type);
                        try {
                            uint options;
                            dialog.GetOptions(out options);
                            dialog.SetOptions(options | 0x20u | 0x40u | 0x800u);
                            dialog.SetTitle("Select a folder for KAI");
                            int result = dialog.Show(IntPtr.Zero);
                            if (result == unchecked((int)0x800704C7)) return ""; // User cancelled.
                            if (result != 0) Marshal.ThrowExceptionForHR(result);

                            IKaiShellItem item;
                            dialog.GetResult(out item);
                            try {
                                IntPtr path;
                                item.GetDisplayName(0x80058000u, out path); // SIGDN_FILESYSPATH
                                try { return Marshal.PtrToStringUni(path) ?? ""; }
                                finally { Marshal.FreeCoTaskMem(path); }
                            }
                            finally { Marshal.ReleaseComObject(item); }
                        }
                        finally { Marshal.ReleaseComObject(dialog); }
                    }
                }
                '@
                $folder = [KaiNativeFolderPicker]::Choose()
                [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
                if ($folder) { [Console]::Out.Write($folder) }
                """;
        try {
            // EncodedCommand safely transports the multiline script without shell quoting issues.
            String encoded = Base64.getEncoder().encodeToString(
                    script.getBytes(StandardCharsets.UTF_16LE));
            Process picker = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                    "-EncodedCommand", encoded).redirectErrorStream(true).start();
            String result = new String(picker.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = picker.waitFor();
            if (exit != 0) {
                return Map.of("error", "Windows folder chooser failed. "
                        + (result.isEmpty() ? "Check whether PowerShell is permitted." : result));
            }
            return Map.of("path", result); // Empty = cancel; the field remains unchanged.
        } catch (IOException ex) {
            return Map.of("error", "Could not start the Windows folder chooser: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Folder selection interrupted.");
        }
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
        String problem = job != null && !job.result().isDone()
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
