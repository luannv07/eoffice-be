package vn.edu.epu.eoffice_be.security.pki;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** File operations shared by CA initialization and first-sign user provisioning. */
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

    public static <T> T withLock(Path lockFile, IOCallable<T> operation) throws IOException {
        Path path = lockFile.toAbsolutePath().normalize();
        return withJvmLock(path, () -> {
            ensurePrivateDirectory(path.getParent());
            if (Files.isSymbolicLink(path)) throw new IOException("Không sử dụng liên kết cho khóa PKI");
            try (FileChannel channel = FileChannel.open(path,
                    Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), privateAttributes(path))) {
                setPrivatePermissions(path);
                try (FileLock ignored = channel.lock()) { return operation.call(); }
            }
        });
    }

    public static void ensurePrivateDirectory(Path directory) throws IOException {
        if (directory == null) throw new IOException("Thiếu thư mục lưu PKI");
        assertNoSymbolicLinks(directory);
        if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectories(directory);
        }
        setPrivatePermissions(directory);
    }

    public static void assertNoSymbolicLinks(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path current = normalized.getRoot();
        for (Path component : normalized) {
            current = current.resolve(component);
            try {
                BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink()) throw new IOException("Không sử dụng liên kết cho nơi lưu PKI");
            } catch (NoSuchFileException e) {
                break;
            }
        }
    }

    public static Path createTemp(Path target) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        ensurePrivateDirectory(normalized.getParent());
        Path temporary = Files.createTempFile(normalized.getParent(), "." + normalized.getFileName() + ".", ".tmp", privateAttributes(normalized));
        setPrivatePermissions(temporary);
        return temporary;
    }

    /** CREATE_NEW is intentional: no target is ever replaced, even on partial failure. */
    public static void publishNew(Path temporary, Path target) throws IOException {
        try (FileChannel input = FileChannel.open(temporary, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(target,
                     Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), privateAttributes(target))) {
            setPrivatePermissions(target);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (input.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) output.write(buffer);
                buffer.clear();
            }
            output.force(true);
        }
    }

    public static void setPrivatePermissions(Path path) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(Files.isDirectory(path) ? "rwx------" : "rw-------"));
        } else {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (view != null) {
                AclEntry.Builder entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                        .setPrincipal(view.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class));
                if (Files.isDirectory(path)) entry.setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT);
                view.setAcl(List.of(entry.build()));
            }
        }
    }

    private static FileAttribute<?>[] privateAttributes(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }
}
