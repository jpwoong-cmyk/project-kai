package com.example.kai.repository;

import java.io.IOException;
import java.util.List;

// Adapter for a place documents live. The orchestrator only talks to this interface,
// so adding SharePoint / OneDrive later = one new @Component implementing it.
//   location: where to look (local: a folder path; SharePoint: e.g. a site/library URL)
//   id:       a document inside that location (local: relative path)
public interface DocumentRepository {

	String type(); // matches kai.scan.<type> in kai.properties, e.g. "local"

	List<String> list(String location) throws IOException;

	String read(String location, String id) throws IOException; // plain text, also for docx/pptx/pdf

	// false = Kai can read it but not rewrite it (e.g. docx/pptx/pdf): the user updates it by hand
	boolean canWrite(String id);

	void write(String location, String id, String content) throws IOException;

	// Exact bytes, for backup and rollback: a restore must give back the identical file
	byte[] readBytes(String location, String id) throws IOException;

	void writeBytes(String location, String id, byte[] content) throws IOException;
}
