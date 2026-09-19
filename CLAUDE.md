AGENTS.md

# Development Rules

	•	Work one milestone at a time.
	•	Never implement future milestones without explicit instruction.
	•	Prefer JDT semantic APIs over custom Java analysis.
	•	Never use regexes for semantic refactoring.
	•	Every refactoring requires golden-master tests.
	•	Every bug becomes a regression fixture.
	•	Never blindly regenerate approval files.
	•	Inspect approval diffs before accepting them.
	•	In approval tests, assert structure only — not content. Keep: result map size, which paths are present, exception type and message fragment. Remove: assertTrue/assertFalse on source string contents (the approved file is the spec).
	•	Analyze/preview must not modify project files.
	•	Commit to git after each completed step.
	•	Do not introduce speculative abstractions.
	•	Keep CLI and MCP thin; both use the same refactoring engine.
	•	Run the full test suite before declaring a milestone complete.
	•	Update README.md when a milestone completes — move items from Planned to What Works, add usage examples for new commands or tools.
	•	Once M5 (MCP) is complete, use the running MCP server for all refactorings done on this codebase itself — dogfood the tool as far as possible instead of editing Java files by hand.

