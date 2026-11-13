package com.emcc.admin.app.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runs the bundled Spring Boot backend as a child process, so the packaged desktop
 * app is a single double-click experience — no separate server to start by hand.
 * The backend jar and its env file are bundled as classpath resources (see
 * src/main/resources/backend/) and extracted to a temp directory at launch.
 *
 * <p>A packaged (jpackage) app's own bundled runtime deliberately has no {@code
 * bin/java} — jpackage strips it since the app is meant to be launched only via its
 * own native stub. So spawning a second Java process needs a separate, complete
 * Java runtime: when the app is packaged, scripts/package-*.sh bundles one (built
 * with jlink, which — unlike jpackage — always keeps bin/java) as {@code
 * /backend/jre.zip}. In dev mode (running via {@code mvn javafx:run}) that resource
 * isn't present, so this falls back to the JVM already running the app, which does
 * have a full bin/java.
 */
public class BackendLauncher {

    private static final String BACKEND_JAR_RESOURCE = "/backend/emcc-admin-backend.jar";
    private static final String ENV_RESOURCE = "/backend/backend.env";
    private static final String JRE_ZIP_RESOURCE = "/backend/jre.zip";
    private static final int PORT = 8081;

    private Process process;

    /**
     * Starts the backend if nothing is already listening on its port (a dev instance
     * started separately via run.sh counts as "already running" and is left alone).
     */
    public void start() throws IOException {
        if (isPortOpen(PORT, 300)) {
            return;
        }

        Path javaBin = resolveJavaExecutable();
        Path jarPath = extractResource(BACKEND_JAR_RESOURCE, "emcc-admin-backend", ".jar");
        Map<String, String> env = loadEnv(ENV_RESOURCE);

        List<String> command = new ArrayList<>();
        command.add(javaBin.toAbsolutePath().toString());
        command.add("-jar");
        command.add(jarPath.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(env);
        File logFile = jarPath.resolveSibling("backend.log").toFile();
        builder.redirectOutput(logFile);
        builder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

        process = builder.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "backend-shutdown"));
    }

    /** Blocks the calling (background) thread until the backend accepts connections, or the timeout elapses. */
    public boolean awaitReady(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen(PORT, 400)) return true;
            if (process != null && !process.isAlive()) return false;
            sleep(400);
        }
        return false;
    }

    public void stop() {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Java runtime resolution ─────────────────────────────────────────────────

    /**
     * Resolves the java executable to launch the backend with: the bundled jlink
     * runtime if this is a packaged build, or the currently-running JVM's own
     * bin/java in dev mode.
     */
    private Path resolveJavaExecutable() throws IOException {
        URL jreZipUrl = BackendLauncher.class.getResource(JRE_ZIP_RESOURCE);
        if (jreZipUrl == null) {
            String bin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            return Path.of(isWindows() ? bin + ".exe" : bin);
        }

        Path jreDir = appSupportDir().resolve("jre");
        Path javaBin = jreDir.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        Path fingerprintFile = jreDir.resolve(".fingerprint");
        String fingerprint = String.valueOf(jreZipUrl.openConnection().getContentLengthLong());

        boolean upToDate = Files.exists(javaBin) && Files.exists(fingerprintFile)
                && fingerprint.equals(readFirstLine(fingerprintFile));
        if (!upToDate) {
            if (Files.exists(jreDir)) deleteRecursively(jreDir);
            extractZip(JRE_ZIP_RESOURCE, jreDir);
            makeBinariesExecutable(jreDir);
            Files.writeString(fingerprintFile, fingerprint);
        }
        return javaBin;
    }

    /** Per-user, persists across launches so the ~80MB bundled JRE is only extracted once. */
    private static Path appSupportDir() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Path.of(appData != null ? appData : home, "EMCC Admin");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "EMCC Admin");
        }
        return Path.of(home, ".emcc-admin");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ── Process helpers ──────────────────────────────────────────────────────────

    private static boolean isPortOpen(int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Path extractResource(String resourcePath, String prefix, String suffix) throws IOException {
        Path tempDir = Files.createTempDirectory("emcc-admin-backend");
        Path target = tempDir.resolve(prefix + suffix);
        try (InputStream in = BackendLauncher.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Bundled backend resource not found on classpath: " + resourcePath);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static Map<String, String> loadEnv(String resourcePath) throws IOException {
        Map<String, String> env = new HashMap<>();
        try (InputStream in = BackendLauncher.class.getResourceAsStream(resourcePath)) {
            if (in == null) return env;
            Properties props = new Properties();
            props.load(in);
            for (String name : props.stringPropertyNames()) {
                env.put(name, props.getProperty(name));
            }
        }
        return env;
    }

    // ── Zip extraction ───────────────────────────────────────────────────────────

    private static void extractZip(String resourcePath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream raw = BackendLauncher.class.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                throw new IOException("Bundled resource not found on classpath: " + resourcePath);
            }
            try (ZipInputStream zis = new ZipInputStream(raw)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path resolved = targetDir.resolve(entry.getName()).normalize();
                    if (!resolved.startsWith(targetDir)) {
                        throw new IOException("Zip entry escapes target directory: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(resolved);
                    } else {
                        Files.createDirectories(resolved.getParent());
                        Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
        }
    }

    /** Zip doesn't preserve Unix executable bits, so bin/* needs them restored by hand after extraction. */
    private static void makeBinariesExecutable(Path jreDir) throws IOException {
        Path binDir = jreDir.resolve("bin");
        if (Files.isDirectory(binDir)) {
            try (var files = Files.list(binDir)) {
                files.forEach(p -> p.toFile().setExecutable(true));
            }
        }
        Path jspawnhelper = jreDir.resolve("lib").resolve("jspawnhelper");
        if (Files.exists(jspawnhelper)) {
            jspawnhelper.toFile().setExecutable(true);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static String readFirstLine(Path file) {
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
