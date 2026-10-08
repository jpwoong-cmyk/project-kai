package com.example.kai.orchestrator;

import java.util.List;

import com.example.kai.config.KaiProperties.Target;

// One document's result. target = which kai.scan.* folder it came from.
// editable = false for formats Kai reads but never rewrites (docx, pptx, pdf).
// proposal = Editor + Reviewer result; only for affected, editable files, otherwise null.
public record Finding(Target target, String file, Status status, String reason, boolean editable, Proposal proposal) {

	// ERROR = the agent could not decide (timeout, bad model, unreadable file).
	// Never shown as "No", so a failure can't hide an affected file.
	public enum Status {
		AFFECTED, NOT_AFFECTED, ERROR
	}

	public boolean affected() {
		return status == Status.AFFECTED;
	}

	// Will be written on Finalize: a ready proposal, ticked, that differs from the original
	public boolean selected() {
		return proposal != null && proposal.ready() && proposal.include() && !proposal.proposed().equals(proposal.original());
	}

	// Affected, but Kai can't rewrite it: the user has to update it by hand
	public boolean manual() {
		return affected() && !editable;
	}

	// The updated file a change came from: never scanned or edited, it is the source of truth
	public record Source(Target target, String file) {

		public String name() {
			return target.entry() + "/" + file;
		}
	}

	// The whole run, kept in the chat history. source = the updated file it was checked against, or null
	public record Report(String instruction, Source source, List<Target> targets, List<Finding> findings) {

		// One kai.scan.* entry and its files, so the report shows the blast radius per location
		public record Group(Target target, List<Finding> findings) {

			// Affected or could not check: shown as rows. The rest go in one folded "No change" row.
			public List<Finding> attention() {
				return findings.stream().filter(f -> f.status() != Status.NOT_AFFECTED).toList();
			}

			public List<Finding> unchanged() {
				return findings.stream().filter(f -> f.status() == Status.NOT_AFFECTED).toList();
			}

			public long affectedCount() {
				return findings.stream().filter(Finding::affected).count();
			}

			// Table rows this group takes, for the rowspan of its location cell
			public int rows() {
				int rows = 0;
				for (Finding f : attention()) {
					rows += f.proposal() == null ? 1 : 2;
				}
				return Math.max(1, rows + (unchanged().isEmpty() ? 0 : 1));
			}
		}

		// In kai.properties order; a location with no files still gets a group
		public List<Group> groups() {
			return targets.stream().map(t -> new Group(t, findings.stream().filter(f -> f.target().equals(t)).toList())).toList();
		}

		public long affectedCount() {
			return count(Status.AFFECTED);
		}

		public long errorCount() {
			return count(Status.ERROR);
		}

		// Proposals the user can review (not "Could not apply")
		public long readyCount() {
			return findings.stream().filter(f -> f.proposal() != null && f.proposal().ready()).count();
		}

		public long unappliedCount() {
			return findings.stream().filter(f -> f.proposal() != null && !f.proposal().ready()).count();
		}

		public long manualCount() {
			return findings.stream().filter(Finding::manual).count();
		}

		private long count(Status status) {
			return findings.stream().filter(f -> f.status() == status).count();
		}
	}
}
