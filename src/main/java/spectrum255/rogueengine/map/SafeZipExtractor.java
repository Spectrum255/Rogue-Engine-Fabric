package spectrum255.rogueengine.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import spectrum255.rogueengine.persistence.AtomicFiles;

public final class SafeZipExtractor {
	private static final int MAX_ENTRIES = 20_000;
	private static final long MAX_DECLARED_BYTES = 2L * 1024 * 1024 * 1024;
	private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
	private static final long MAX_EXTRACTED_BYTES = 512L * 1024 * 1024;
	private static final int MAX_COMPRESSION_RATIO = 256;

	private SafeZipExtractor() {
	}

	public static void extract(Path zip, Path targetRoot, Set<String> allowedFiles) throws IOException {
		Objects.requireNonNull(zip, "zip");
		Objects.requireNonNull(targetRoot, "targetRoot");
		Objects.requireNonNull(allowedFiles, "allowedFiles");
		Path normalizedRoot = targetRoot.toAbsolutePath().normalize();
		Set<String> allowed = new HashSet<>(allowedFiles);
		List<Path> written = new ArrayList<>();
		long declaredTotal = 0L;
		long extractedTotal = 0L;
		int entries = 0;
		try (ZipFile archive = new ZipFile(zip.toFile())) {
			var enumeration = archive.getEntries();
			while (enumeration.hasMoreElements()) {
				ZipArchiveEntry entry = enumeration.nextElement();
				if (++entries > MAX_ENTRIES) {
					throw new IOException("ZIP entry count exceeds the allowed limit");
				}
				String name = validateName(entry);
				long size = entry.getSize();
				if (size < 0 || size > MAX_ENTRY_BYTES) {
					throw new IOException("ZIP entry has an invalid or oversized declared size: " + name);
				}
				declaredTotal += size;
				if (declaredTotal > MAX_DECLARED_BYTES) {
					throw new IOException("ZIP declared size exceeds the allowed limit");
				}
				long compressed = entry.getCompressedSize();
				if (compressed > 0 && size / compressed > MAX_COMPRESSION_RATIO) {
					throw new IOException("ZIP entry compression ratio exceeds the allowed limit: " + name);
				}
			}
			for (String name : allowed) {
				ZipArchiveEntry entry = archive.getEntry(name);
				if (entry == null || entry.isDirectory()) {
					continue;
				}
				Path target = normalizedRoot.resolve(name);
				AtomicFiles.verifyManagedPath(normalizedRoot, target);
				Files.createDirectories(target.getParent());
				long copied = 0L;
				try (InputStream entryInput = archive.getInputStream(entry);
						OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW,
								StandardOpenOption.WRITE)) {
					byte[] buffer = new byte[64 * 1024];
					int read;
					while ((read = entryInput.read(buffer)) != -1) {
						copied += read;
						if (copied > entry.getSize() || copied > MAX_ENTRY_BYTES
								|| extractedTotal + copied > MAX_EXTRACTED_BYTES) {
							throw new IOException("ZIP extracted size exceeds the allowed limit: " + name);
						}
						output.write(buffer, 0, read);
					}
				}
				extractedTotal += copied;
				written.add(target);
			}
		} catch (IOException exception) {
			for (Path path : written) {
				try {
					Files.deleteIfExists(path);
				} catch (IOException cleanup) {
					exception.addSuppressed(cleanup);
				}
			}
			throw exception;
		}
	}

	private static String validateName(ZipArchiveEntry entry) throws IOException {
		String name = entry.getName();
		if (name == null || name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
				|| name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
			throw new IOException("Unsafe ZIP entry name");
		}
		if ((entry.getUnixMode() & 0170000) == 0120000) {
			throw new IOException("Symbolic links are not allowed in ZIP imports");
		}
		String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		for (String segment : normalized.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
				throw new IOException("Unsafe ZIP entry path: " + name);
			}
		}
		return normalized;
	}
}
