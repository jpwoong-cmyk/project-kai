package com.example.kai.repository;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

// Repository contract. Defaults keep any future Box/SharePoint implementation compatible.
public interface DocumentRepository {

    String type();
    List<String> list(String location) throws IOException;
    String read(String location, String id) throws IOException;
    boolean canWrite(String id);
    void write(String location, String id, String content) throws IOException;
    byte[] readBytes(String location, String id) throws IOException;
    void writeBytes(String location, String id, byte[] content) throws IOException;

    // Stream extracted text instead of accumulating a 100 MB LLM prompt in memory.
    default Reader openText(String location, String id) throws IOException {
        return new StringReader(read(location, id));
    }

    // -1 means this adapter cannot provide the original byte count.
    default long fileSize(String location, String id) throws IOException {
        return -1;
    }

    record SkippedFile(String file, long bytes, String reason) { }

    // Report excluded files explicitly; adapters that do not implement it remain compatible.
    default List<SkippedFile> skipped(String location) throws IOException {
        return List.of();
    }
}
