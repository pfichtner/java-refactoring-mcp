# Releasing java-refactoring-mcp

A release is a git tag `vX.Y.Z` pushed to GitHub. The `release` workflow in
`.github/workflows/release.yml` then builds the project, derives the Maven
version from the tag, and publishes the runnable fat jars as GitHub Release
assets — no manual upload, no Maven Central account.

Releases live at <https://github.com/pfichtner/java-refactoring-mcp/releases>.
Reported artifacts per release:

| Asset | What it is |
|-------|------------|
| `refactor-mcp-<version>-fat.jar` | MCP server — what coding agents run (`java -jar …`) |
| `refactor-cli-<version>-fat.jar` | CLI (`java-refactor`) |
| `*-fat.jar.sha256` | Checksums for both jars |

## How to cut a release

1. **Make sure `main` is green.** Run the full suite:
   ```bash
   mvn --batch-mode verify
   ```

2. **Choose the next version** (`X.Y.Z`, SemVer) — for example `0.1.0`.

3. **Bump the version in all module poms** (parent + `refactor-core` +
   `refactor-cli` + `refactor-mcp`) and check the result:
   ```bash
   mvn versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false
   git diff --stat     # four poms, no .orig backup files
   ```

4. **Refresh the version-embedded jar names in the docs/configs.**
   Several files hard-code the versioned jar name of the externally published
   artifacts (`refactor-mcp-<version>-fat.jar`). Keep them in sync so the
   README and the MCP configs point at the released jar:
   `README.md`, `REFERENCE.md`, `.claude/settings.json`, and everything under
   `gilded-rose-demo/` (`opencode.json`, `README.md`, `scripts/`).
   ```bash
   grep -rn -- '-fat.jar' README.md REFERENCE.md .claude gilded-rose-demo
   ```

5. **Build and sanity-check the artifacts:**
   ```bash
   mvn package -DskipTests
   ls refactor-mcp/target/refactor-mcp-X.Y.Z-fat.jar refactor-cli/target/refactor-cli-X.Y.Z-fat.jar
   ```

6. **Commit and tag.**
   ```bash
   git add -A
   git commit -m "chore(release): prepare X.Y.Z"
   git tag -a vX.Y.Z -m "java-refactoring-mcp X.Y.Z"
   ```

7. **Push.** Pushing the tag triggers the release workflow.
   ```bash
   git push origin main --tags
   ```

8. **Verify.** Watch Actions → `release` workflow for the `vX.Y.Z` run, then
   check the release page. Verify a downloaded jar against its checksum:
   ```bash
   curl -sL \
     -o refactor-mcp-X.Y.Z-fat.jar \
     https://github.com/pfichtner/java-refactoring-mcp/releases/download/vX.Y.Z/refactor-mcp-X.Y.Z-fat.jar
   curl -sL \
     -o refactor-mcp-X.Y.Z-fat.jar.sha256 \
     https://github.com/pfichtner/java-refactoring-mcp/releases/download/vX.Y.Z/refactor-mcp-X.Y.Z-fat.jar.sha256
   sha256sum -c refactor-mcp-X.Y.Z-fat.jar.sha256
   ```

9. **Bump version after release.** For the next feature cycle, follow step 3
   with the next version (e.g. `0.1.1`). `main` carries the released version
   until then — this project does not keep an intermediate `-SNAPSHOT` on
   `main` between releases.

## What the release workflow does

1. `actions/checkout` the tagged commit.
2. Set the Maven version from the tag: `vX.Y.Z` → poms at `X.Y.Z`
   (`mvn versions:set`), so the tag is always consistent with the artifact it
   produces.
3. `mvn --batch-mode verify` — full test suite gates the release.
4. `sha256sum` both fat jars.
5. Upload `refactor-mcp` and `refactor-cli` fat jars + checksums as release
   assets via `softprops/action-gh-release`.

The `GITHUB_TOKEN` is scoped to `contents: write`, which is all the release
needs.

## Why not the Maven Release Plugin?

`maven-release-plugin` is built for publishing to a Maven repository
(`release:perform` — deploy). This project's distribution channel is GitHub
Releases built by CI, so the plugin would add an `<scm>` section and a deploy
step we do not need. The `versions-maven-plugin` + tag + workflow covers the
same ground with fewer moving parts.