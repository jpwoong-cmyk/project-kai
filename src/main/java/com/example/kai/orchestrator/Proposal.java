package com.example.kai.orchestrator;

import java.util.List;

// The proposed new content of one editable, affected file, plus the reviewer's advice.
// Kept in the HTTP session; the user can edit the text and untick it until Finalize.
// Nothing here touches disk.
public final class Proposal {

	private final String original;
	private final String error; // set = "Could not apply": no proposed text
	private final Boolean reviewOk; // null = the reviewer could not check it
	private final String review;
	private String proposed;
	private boolean include = true;
	private boolean edited; // changed by the user after the review

	private Proposal(String original, String proposed, String error, Boolean reviewOk, String review) {
		this.original = original;
		this.proposed = proposed;
		this.error = error;
		this.reviewOk = reviewOk;
		this.review = review;
	}

	static Proposal ready(String original, String proposed, Boolean reviewOk, String review) {
		return new Proposal(original, proposed, null, reviewOk, review);
	}

	static Proposal failed(String original, String error) {
		return new Proposal(original, null, error, null, null);
	}

	// From the review screen. Browsers send textarea lines as \r\n: keep the file's own line endings.
	public void save(String text, boolean include) {
		String t = original.contains("\r\n") ? text : text.replace("\r\n", "\n");
		edited |= !t.equals(proposed);
		proposed = t;
		this.include = include;
	}

	public List<Diff.Line> diff() {
		return Diff.lines(original, proposed);
	}

	public boolean ready() {
		return error == null;
	}

	public String original() {
		return original;
	}

	public String proposed() {
		return proposed;
	}

	public String error() {
		return error;
	}

	public Boolean reviewOk() {
		return reviewOk;
	}

	public String review() {
		return review;
	}

	public boolean include() {
		return include;
	}

	public boolean edited() {
		return edited;
	}
}
