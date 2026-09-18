package dev.mcp.refactor.mcp;

/**
 * Entry point for the MCP server process.
 * Reads JSON-RPC messages from stdin, writes responses to stdout.
 */
public class McpMain {

    public static void main(String[] args) throws InterruptedException {
        RefactoringServer.build();
        // Stdio transport runs on background threads; keep the JVM alive.
        Thread.currentThread().join();
    }
}
