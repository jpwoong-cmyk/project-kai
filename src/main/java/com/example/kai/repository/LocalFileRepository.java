package com.example.kai.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

/** Documents beneath selected local folders; all size thresholds are measured in bytes. */
@Component
public class LocalFileRepository implements DocumentRepository {

    private static final Set<String> TEXT = Set.of("md", "txt", "properties", "yml", "yaml", "json", "xml");
    private static final Set<String> OFFICE = Set.of("docx", "pptx", "pdf", "doc", "ppt");

    // Decimal megabytes, consistent with the old 10_000_000-byte limit.
    public static final long MAX_FILE_BYTES = 100_000_000L;
    // The old 50 KB / 50,000-character small-document behaviour remains for non-scanner callers.
    private static final int MAX_PREVIEW_CHARS = 50_000;
    private final Tika tika = new Tika();

    public LocalFileRepository() {
        tika.setMaxStringLength(MAX_PREVIEW_CHARS);
    }

    @Override
    public String type() { return "local"; }

    @Override
    public List<String> list(String location) throws IOException {
        Path root = root(location);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> !isHidden(root.relativize(p)))
                    .filter(LocalFileRepository::supported)
                    .sorted()
                    .map(p -> root.relativize(p).toString())
                    .toList();
        }
    }

    @Override
    public List<SkippedFile> skipped(String location) throws IOException {
        Path root = root(location);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> !isHidden(root.relativize(p)))
                    .filter(p -> !supported(p))
                    .sorted()
                    .map(p -> {
                        long bytes = p.toFile().length();
                        String reason = !isAccepted(extension(p))
                                ? "Unsupported file type" : "Exceeds 100 MB limit";
                        return new SkippedFile(root.relativize(p).toString(), bytes, reason);
                    })
                    .toList();
        }
    }

    @Override
    public String read(String location, String id) throws IOException {
        Path file = resolve(location, id);
        if (canWrite(id)) return Files.readString(file);
        try (InputStream in = Files.newInputStream(file)) {
            return tika.parseToString(in);
        } catch (TikaException e) {
            throw new IOException("Cannot extract text from " + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Reader openText(String location, String id) throws IOException {
        Path file = resolve(location, id);
        if (!supported(file)) throw new IOException("Document is excluded (unsupported or exceeds 100 MB): " + id);
        // No 50,000 character truncation in the scanner path. Tika's parse(Path)
        // exposes a streaming Reader rather than a capped parseToString().
        return canWrite(id) ? Files.newBufferedReader(file) : tika.parse(file);
    }

    @Override
    public long fileSize(String location, String id) throws IOException {
        return Files.size(resolve(location, id));
    }

    @Override
    public boolean canWrite(String id) { return TEXT.contains(extension(Path.of(id))); }

    @Override
    public void write(String location, String id, String content) throws IOException {
        if (!canWrite(id)) throw new IOException(id + " is read-only in Kai (update it by hand)");
        Files.writeString(resolve(location, id), content);
    }

    @Override
    public byte[] readBytes(String location, String id) throws IOException {
        return Files.readAllBytes(resolve(location, id));
    }

    @Override
    public void writeBytes(String location, String id, byte[] content) throws IOException {
        if (!canWrite(id)) throw new IOException(id + " is read-only in Kai (update it by hand)");
        Files.write(resolve(location, id), content);
    }

    private static boolean supported(Path p) {
        return isAccepted(extension(p)) && p.toFile().length() <= MAX_FILE_BYTES;
    }
    private static boolean isAccepted(String ext) { return TEXT.contains(ext) || OFFICE.contains(ext); }
    private static Path root(String location) { return Path.of(location).toAbsolutePath().normalize(); }

    private static Path resolve(String location, String id) {
        Path root = root(location);
        Path file = root.resolve(id).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Outside " + root + ": " + id);
        return file;
    }

    private static String extension(Path p) {
        String name = p.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isHidden(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".") || part.toString().startsWith("~$")) return true;
        }
        return false;
    }
}
