package storage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Publish completed exports without replacing an existing file. */
public final class ExportFilePublisher {
    private ExportFilePublisher() {
        // This utility should never be instantiated!
    }

    @FunctionalInterface
    public interface Writer {
        void write(OutputStream stream) throws IOException;
    }

    /**
     * A file publisher that ensure every file exist uniquely under concurrency.
     * @param requested Unvalidated, raw path.
     * @param renameOnCollision Collision indicator. False results in checked exception.
     * @param writer Writer with write method.
     * @return Destination of the file in path.
     * @throws IOException Note that the temporary writing file will be destroyed when caught.
     */
    public static Path publish(Path requested, boolean renameOnCollision, Writer writer) throws IOException {
        Path target = requested.toAbsolutePath().normalize();
        Path directory = target.getParent();
        Path fileName = target.getFileName();// NPE if target is the root directory
        if (directory == null || fileName == null) throw new IllegalArgumentException("Not a file path: " + requested);

        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".export-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                writer.write(stream);
            }
            String name = target.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String extension = dot > 0 ? name.substring(dot) : "";
            for (long suffix = 0; ; suffix++) {
                Path candidate = suffix == 0 ? target : directory.resolve(base + "-" + suffix + extension);
                try {
                    // No REPLACE_EXISTING or ATOMIC_MOVE: existing destinations must never be replaced.
                    return Files.move(temporary, candidate);
                } catch (FileAlreadyExistsException collision) {
                    if (!renameOnCollision) throw collision;
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
