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
	•	Analyze/preview must not modify project files.
	•	Commit to git after each completed step.
	•	Do not introduce speculative abstractions.
	•	Keep CLI and MCP thin; both use the same refactoring engine.
	•	Run the full test suite before declaring a milestone complete.

