package vn.edu.epu.eoffice_be.security.pki;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** JVM coordination still used by legacy revocation readers and writers. */
public final class PkiFileSupport {
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();
    private PkiFileSupport() { }

    @FunctionalInterface
    public interface IOCallable<T> { T call() throws IOException; }

    public static <T> T withJvmLock(Path lockFile, IOCallable<T> operation) throws IOException {
        ReentrantLock mutex = LOCKS.computeIfAbsent(lockFile.toAbsolutePath().normalize(), ignored -> new ReentrantLock());
        mutex.lock();
        try { return operation.call(); }
        finally { mutex.unlock(); }
    }

}
