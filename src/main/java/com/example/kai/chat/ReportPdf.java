package com.example.kai.chat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/** Renders a finalized, already-saved HTML report as a printable PDF. No scan is run. */
public final class ReportPdf {
    private ReportPdf() {}

    private static final String PRINT_CSS = """
        @page { size: A4; margin: 17mm 14mm; }
        body { font-family: sans-serif; font-size: 10pt; line-height: 1.4; color: #17252b; margin: 0; }
        h1 { font-family: sans-serif; font-size: 22pt; margin: 0 0 8pt; }
        h2 { font-family: sans-serif; font-size: 15pt; margin: 20pt 0 8pt; color: #1f5c6b; }
        h3 { font-family: monospace; font-size: 10pt; margin: 0 0 6pt; }
        .meta { color: #5f6f76; font-size: 9pt; margin: 2pt 0 10pt; }
        .request, .outcome { border: 1px solid #dde4e7; padding: 10pt; margin: 10pt 0; white-space: pre-wrap; }
        .outcome { font-weight: bold; }
        section.file { border: 1px solid #dde4e7; margin: 0 0 12pt; padding: 10pt; }
        section.file > p { font-size: 9pt; margin: 0 0 7pt; }
        section.file > p.warn { color: #8c520c; }
        code { font-family: monospace; font-size: 9pt; overflow-wrap: break-word; }
        .diff { border: 1px solid #dde4e7; font-family: monospace; font-size: 8pt; }
        .diff > div { white-space: pre-wrap; overflow-wrap: anywhere; padding: 2pt 6pt; }
        .diff .del { background: #fdecea; }
        .diff .del:before { content: '- '; }
        .diff .add { background: #e6f4ea; }
        .diff .add:before { content: '+ '; }
        .diff .gap { color: #5f6f76; background: #f6f8f9; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #dde4e7; text-align: left; vertical-align: top; padding: 6pt; font-size: 9pt; }
        th { background: #f6f8f9; }
        .report-toolbar, .kai-back-dialog, script, button { display: none; }
        """;

    public static byte[] render(String reportHtml) throws IOException {
        Document doc = Jsoup.parse(reportHtml);
        // Strip browser-only actions and use explicit print colors supported by PDF renderer.
        doc.select("script, dialog, .report-toolbar, style, link[rel=stylesheet]").remove();
        doc.selectFirst("html").attr("xmlns", "http://www.w3.org/1999/xhtml");
        doc.head().appendElement("style").appendText(PRINT_CSS);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        doc.outputSettings().prettyPrint(false);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(doc.outerHtml(), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IOException("Could not render finalized KAI report as PDF", ex);
        }
    }
}
