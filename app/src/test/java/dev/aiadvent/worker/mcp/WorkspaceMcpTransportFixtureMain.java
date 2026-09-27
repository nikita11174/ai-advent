package dev.aiadvent.worker.mcp;

import java.io.IOException;

public final class WorkspaceMcpTransportFixtureMain {
    private WorkspaceMcpTransportFixtureMain() { }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "large-stderr" -> {
                byte[] chunk = new byte[4096];
                java.util.Arrays.fill(chunk, (byte) 'e');
                for (int i = 0; i < 512; i++) System.err.write(chunk);
                System.err.println();
                System.err.flush();
                WorkspaceMcpServerMain.main(new String[0]);
            }
            case "oversize" -> {
                byte[] chunk = new byte[4096];
                java.util.Arrays.fill(chunk, (byte) 'x');
                for (int i = 0; i < 32; i++) System.out.write(chunk);
                System.out.flush();
                awaitEof();
            }
            case "malformed" -> {
                System.out.println("not-json");
                System.out.flush();
                awaitEof();
            }
            case "drain-after-eof" -> {
                awaitEof();
                byte[] chunk = new byte[4096];
                java.util.Arrays.fill(chunk, (byte) 'x');
                for (int i = 0; i < 512; i++) System.out.write(chunk);
                System.out.flush();
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private static void awaitEof() throws IOException {
        while (System.in.read() != -1) { }
    }
}
