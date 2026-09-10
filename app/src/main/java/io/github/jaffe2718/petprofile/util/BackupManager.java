package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup format shared by the local ZIP and the OneDrive sync.
 *
 * <p>The backup is an unpacked tree: {@code data.json} plus {@code images/<name>} entries. Image
 * names are the app's unified content-based names and are <b>never renamed</b> — export, import,
 * ZIP and OneDrive round-trips all keep the exact same file name, which is what makes the
 * differential cloud sync (comparing name lists) cheap.
 */
public final class BackupManager {
    private static final String TAG = "PetProfileBackup";
    public static final String DATA_ENTRY = "data.json";
    public static final String IMAGE_PREFIX = "images/";
    private static final Gson GSON = new Gson();
    private static final Pattern URI_PATTERN = Pattern.compile("(?i)(content|file)://[^\\s)\\]}\"']+");
    private static final Pattern IMAGE_REF_PATTERN = Pattern.compile("images/[^\\s)\\]}\"',]+");

    private BackupManager() {
    }

    /** An unpacked backup tree. */
    public static final class Tree {
        /** The rewritten data.json bytes (image URIs are {@code images/<name>}). */
        public final byte[] dataJson;
        /** {@code images/<name>} -> local image Uri, in stable order. */
        public final Map<String, Uri> images;

        Tree(byte[] dataJson, Map<String, Uri> images) {
            this.dataJson = dataJson;
            this.images = images;
        }
    }

    // ----- tree -----

    /** Builds the unpacked tree. Image names are preserved (taken from the stored file names). */
    public static Tree buildTree(Context context, ExportBundle bundle) {
        ExportBundle copy = GSON.fromJson(GSON.toJson(bundle), ExportBundle.class);
        Map<String, String> seen = new HashMap<>();
        Map<String, Uri> images = new LinkedHashMap<>();

        for (ProfileEntity profile : copy.profiles) {
            profile.avatarUri = collect(profile.avatarUri, seen, images);
        }
        for (RecordEntity record : copy.records) {
            record.notesMarkdown = replaceInMarkdown(record.notesMarkdown, seen, images);
        }
        for (RecordImageEntity image : copy.recordImages) {
            image.uri = collect(image.uri, seen, images);
        }
        byte[] json = GSON.toJson(copy).getBytes(StandardCharsets.UTF_8);
        return new Tree(json, images);
    }

    /**
     * Restores a tree: writes the provided images to {@code files/images/<name>} and rewrites every
     * registered reference to its {@code file://} URI. Images may be omitted (an incremental cloud
     * restore imports data.json before the images arrive); see {@link #restoreUri}.
     */
    public static ExportBundle readTree(Context context, byte[] dataJson, Map<String, byte[]> images) throws Exception {
        ExportBundle bundle = GSON.fromJson(new String(dataJson, StandardCharsets.UTF_8), ExportBundle.class);
        File dir = new File(context.getFilesDir(), ImageStorage.IMAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create images directory.");
        }
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            writeImage(dir, stripPrefix(entry.getKey()), entry.getValue());
        }
        for (ProfileEntity profile : bundle.profiles) {
            profile.avatarUri = restoreUri(dir, profile.avatarUri);
        }
        for (RecordEntity record : bundle.records) {
            record.notesMarkdown = restoreMarkdown(dir, record.notesMarkdown);
        }
        for (RecordImageEntity image : bundle.recordImages) {
            image.uri = restoreUri(dir, image.uri);
        }
        return bundle;
    }

    /** All {@code images/<name>} references registered in a data.json payload. */
    public static Set<String> registeredImages(byte[] dataJson) {
        Set<String> refs = new LinkedHashSet<>();
        Matcher matcher = IMAGE_REF_PATTERN.matcher(new String(dataJson, StandardCharsets.UTF_8));
        while (matcher.find()) {
            refs.add(matcher.group());
        }
        return refs;
    }

    public static String stripPrefix(String key) {
        return key.startsWith(IMAGE_PREFIX) ? key.substring(IMAGE_PREFIX.length()) : key;
    }

    public static File imagesDir(Context context) {
        return new File(context.getFilesDir(), ImageStorage.IMAGE_DIR);
    }

    // ----- ZIP (local export / import) -----

    /**
     * Builds the ZIP. An image whose file cannot be read (a reference left behind by a partial cloud
     * restore, a file removed outside the app) is skipped rather than failing the whole export; the
     * data.json inside the archive still references it, so an import shows the record without that
     * image just like the device it came from.
     */
    public static byte[] createZipBytes(Context context, ExportBundle bundle) throws Exception {
        Tree tree = buildTree(context, bundle);
        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            int skipped = 0;
            for (Map.Entry<String, Uri> entry : tree.images.entrySet()) {
                InputStream input;
                try {
                    input = openStream(context, entry.getValue());
                } catch (Throwable t) {
                    skipped++;
                    Log.w(TAG, "skipping unreadable image in export: " + entry.getValue(), t);
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                try (InputStream in = input) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            if (skipped > 0) {
                Log.w(TAG, "exported with " + skipped + " missing image(s)");
            }
            zip.putNextEntry(new ZipEntry(DATA_ENTRY));
            zip.write(tree.dataJson);
            zip.closeEntry();
            zip.finish();
            return raw.toByteArray();
        }
    }

    public static void exportZip(Context context, ExportBundle bundle, Uri targetUri) throws Exception {
        byte[] zipBytes = createZipBytes(context, bundle);
        try (OutputStream raw = context.getContentResolver().openOutputStream(targetUri)) {
            if (raw == null) {
                throw new IOException("Unable to open output stream.");
            }
            raw.write(zipBytes);
        }
    }

    public static ExportBundle readZip(Context context, Uri sourceUri) throws Exception {
        try (InputStream raw = context.getContentResolver().openInputStream(sourceUri)) {
            if (raw == null) {
                throw new IOException("Unable to open input stream.");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = raw.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return readZipBytes(context, buffer.toByteArray());
        }
    }

    public static ExportBundle readZipBytes(Context context, byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = zip.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                entries.put(entry.getName(), buffer.toByteArray());
            }
        }
        byte[] json = entries.get(DATA_ENTRY);
        if (json == null) {
            throw new IOException("Missing data.json in archive.");
        }
        Map<String, byte[]> images = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().startsWith(IMAGE_PREFIX)) {
                images.put(entry.getKey(), entry.getValue());
            }
        }
        return readTree(context, json, images);
    }

    // ----- internals -----

    private static String collect(String uriText, Map<String, String> seen, Map<String, Uri> images) {
        if (uriText == null || uriText.trim().isEmpty()) {
            return uriText;
        }
        String existing = seen.get(uriText);
        if (existing != null) {
            return existing;
        }
        Uri uri = Uri.parse(uriText);
        if (!isReadable(uri)) {
            return uriText;
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) {
            return uriText;
        }
        String key = IMAGE_PREFIX + name;
        seen.put(uriText, key);
        images.put(key, uri);
        return key;
    }

    private static String replaceInMarkdown(String markdown, Map<String, String> seen, Map<String, Uri> images) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher matcher = URI_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = collect(matcher.group(), seen, images);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void writeImage(File dir, String name, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(new File(dir, name))) {
            output.write(data);
        }
    }

    /**
     * Rewrites a registered reference into a {@code file://} URI inside {@code files/images/}.
     *
     * <p>The file is <b>not</b> required to exist: a restore imports data.json first and stores the
     * images afterwards, so records legitimately reference images that are not on the device yet.
     * Writing the final URI right away makes such a reference self-healing — the image can be
     * fetched later and lands exactly where the reference already points, with no database update.
     */
    private static String restoreUri(File dir, String value) {
        if (value == null || !value.startsWith(IMAGE_PREFIX)) {
            return value;
        }
        return Uri.fromFile(new File(dir, stripPrefix(value))).toString();
    }

    private static String restoreMarkdown(File dir, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher matcher = IMAGE_REF_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = restoreUri(dir, matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static boolean isReadable(Uri uri) {
        String scheme = uri.getScheme();
        return "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    private static InputStream openStream(Context context, Uri uri) throws IOException {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileInputStream(uri.getPath());
        }
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("Unable to open stream for " + uri);
        }
        return input;
    }
}
