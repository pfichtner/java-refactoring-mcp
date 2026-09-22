package com.github.pfichtner.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import picocli.CommandLine;

class CliTestBed {

    private final Path root;
    private final StringWriter out;
    private final CommandLine cli;

    CliTestBed(Path root) {
        this.root = root;
        this.out = new StringWriter();
        CommandLine cmd = new CommandLine(new Main());
        PrintWriter pw = new PrintWriter(out, true);
        cmd.setOut(pw);
        cmd.setErr(pw);
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        this.cli = cmd;
    }

    /** Temp-dir copy of the fixture: project root dir, or single fixture file. */
    Path root() { return root; }

    /** Captured stdout+stderr of all CLI invocations in this test. */
    StringWriter out() { return out; }

    /** Pre-configured CLI ready to execute subcommands. */
    CommandLine cli() { return cli; }
}
