package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: launches the MCP fat-jar as a subprocess and drives the
 * JSON-RPC initialize → tools/list handshake over stdio.
 */
class McpServerSmokeIT {

    @Test
    void mcp_server_responds_to_initialize_and_lists_rename_tool() throws Exception {
        Path fatJar = Path.of(System.getProperty("fat.jar"));

        List<String> command = new ArrayList<>();
        command.add("java");
        String jacocoArgs = System.getProperty("jacoco.args");
        if (jacocoArgs != null && !jacocoArgs.isBlank()) {
            for (String token : jacocoArgs.trim().split("\\s+")) {
                command.add(forwardJacocoDestfile(token));
            }
        }
        command.add("-jar");
        command.add(fatJar.toString());
        Process proc = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            var writer = new PrintWriter(
                    new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8), true);
            var reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));

            writer.println("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"smoke\",\"version\":\"0\"}}}");
            String initReply = reader.readLine();
            assertThat(initReply).contains("\"result\"").contains("serverInfo");
            assertThat(initReply).contains("\"version\":\"" + System.getProperty("project.version") + "\"");

            // required before sending further requests
            writer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");

            writer.println("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String toolsReply = reader.readLine();
            assertThat(toolsReply).contains("\"tools\"").contains("rename");
        } finally {
            proc.destroy();
            proc.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static String forwardJacocoDestfile(String agentToken) {
        if (System.getProperty("jacoco.destfile") == null) {
            return agentToken;
        }
        int idx = agentToken.indexOf("destfile=");
        if (idx < 0) {
            return agentToken;
        }
        int end = agentToken.indexOf(',', idx);
        return agentToken.substring(0, idx) + "destfile=" + System.getProperty("jacoco.destfile")
                + (end >= 0 ? agentToken.substring(end) : "");
    }
}
