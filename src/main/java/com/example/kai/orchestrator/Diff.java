package com.example.kai.orchestrator;

import java.util.ArrayList;
import java.util.List;

// Line diff of original -> proposed for the review screen (longest common subsequence).
// Unchanged lines far from any change are folded into one "gap" line.
public final class Diff {

	// kind: same | del | add | gap (text = "12 unchanged lines")
	public record Line(String kind, String text) {
	}

	private static final int CONTEXT = 2; // unchanged lines shown around each change

	private Diff() {
	}

	public static List<Line> lines(String original, String proposed) {
		String[] a = original.split("\r?\n", -1);
		String[] b = proposed.split("\r?\n", -1);

		// Skip the common start and end, so the table only covers the changed middle
		int pre = 0;
		while (pre < a.length && pre < b.length && a[pre].equals(b[pre])) {
			pre++;
		}
		int suf = 0;
		while (suf < a.length - pre && suf < b.length - pre && a[a.length - 1 - suf].equals(b[b.length - 1 - suf])) {
			suf++;
		}
		int n = a.length - pre - suf;
		int m = b.length - pre - suf;
		int[][] lcs = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				lcs[i][j] = a[pre + i].equals(b[pre + j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
			}
		}

		List<Line> all = new ArrayList<>();
		for (int k = 0; k < pre; k++) {
			all.add(new Line("same", a[k]));
		}
		int i = 0;
		int j = 0;
		while (i < n || j < m) {
			if (i < n && j < m && a[pre + i].equals(b[pre + j])) {
				all.add(new Line("same", a[pre + i++]));
				j++;
			}
			else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
				all.add(new Line("del", a[pre + i++])); // removed lines before added ones
			}
			else {
				all.add(new Line("add", b[pre + j++]));
			}
		}
		for (int k = a.length - suf; k < a.length; k++) {
			all.add(new Line("same", a[k]));
		}
		return fold(all);
	}

	private static List<Line> fold(List<Line> all) {
		boolean[] keep = new boolean[all.size()];
		for (int k = 0; k < all.size(); k++) {
			if (!all.get(k).kind().equals("same")) {
				for (int c = Math.max(0, k - CONTEXT); c <= Math.min(all.size() - 1, k + CONTEXT); c++) {
					keep[c] = true;
				}
			}
		}
		List<Line> out = new ArrayList<>();
		int hidden = 0;
		for (int k = 0; k < all.size(); k++) {
			if (keep[k]) {
				if (hidden > 0) {
					out.add(gap(hidden));
					hidden = 0;
				}
				out.add(all.get(k));
			}
			else {
				hidden++;
			}
		}
		if (hidden > 0 && !out.isEmpty()) { // nothing changed at all -> empty list
			out.add(gap(hidden));
		}
		return out;
	}

	private static Line gap(int count) {
		return new Line("gap", count + (count == 1 ? " unchanged line" : " unchanged lines"));
	}
}
