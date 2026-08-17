package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class BackupManager {
    private static final Gson GSON = new Gson();
    private static final Pattern URI_PATTERN = Pattern.compile("(?i)(content|file)://[^\\s)\\]}\"']+");

    private BackupManager() {
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

    public static byte[] createZipBytes(Context context, ExportBundle bundle) throws Exception {
        ExportBundle copy = GSON.fromJson(GSON.toJson(bundle), ExportBundle.class);
        Map<String, String> originalToZip = new LinkedHashMap<>();
        Map<String, Uri> zipToUri = new LinkedHashMap<>();

        for (ProfileEntity profile : copy.profiles) {
            profile.avatarUri = collectAndReplace(context, profile.avatarUri, originalToZip, zipToUri);
        }
        for (RecordEntity record : copy.records) {
            record.notesMarkdown = replaceUrisInMarkdown(context, record.notesMarkdown, originalToZip, zipToUri);
        }
        for (RecordImageEntity image : copy.recordImages) {
            image.uri = collectAndReplace(context, image.uri, originalToZip, zipToUri);
        }

        try (ByteArrayOutputStream raw = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            for (Map.Entry<String, String> entry : originalToZip.entrySet()) {
                String uriText = entry.getKey();
                String zipPath = entry.getValue();
                Uri source = Uri.parse(uriText);
                zip.putNextEntry(new ZipEntry(zipPath));
                try (InputStream input = openStream(context, source)) {
                    if (input == null) {
                        continue;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }

            zip.putNextEntry(new ZipEntry("data.json"));
            byte[] json = GSON.toJson(copy).getBytes(StandardCharsets.UTF_8);
            zip.write(json);
            zip.closeEntry();
            zip.finish();
            return raw.toByteArray();
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
        byte[] jsonBytes = entries.get("data.json");
        if (jsonBytes == null) {
            throw new IOException("Missing data.json in archive.");
        }
        ExportBundle bundle = GSON.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), ExportBundle.class);
        File importRoot = new File(context.getFilesDir(), "imported");
        if (!importRoot.exists() && !importRoot.mkdirs()) {
            throw new IOException("Unable to create import directory.");
        }

        for (ProfileEntity profile : bundle.profiles) {
            profile.avatarUri = restoreUri(context, importRoot, profile.avatarUri, entries);
        }
        for (RecordEntity record : bundle.records) {
            record.notesMarkdown = restoreUrisInMarkdown(context, importRoot, record.notesMarkdown, entries);
        }
        for (RecordImageEntity image : bundle.recordImages) {
            image.uri = restoreUri(context, importRoot, image.uri, entries);
        }
        return bundle;
    }

    private static String collectAndReplace(
            Context context,
            String uriText,
            Map<String, String> originalToZip,
            Map<String, Uri> zipToUri
    ) {
        if (uriText == null || uriText.trim().isEmpty()) {
            return uriText;
        }
        String existing = originalToZip.get(uriText);
        if (existing != null) {
            return existing;
        }
        Uri uri = Uri.parse(uriText);
        if (!isReadable(uri)) {
            return uriText;
        }
        String extension = guessExtension(context, uri);
        String zipPath = "images/" + (originalToZip.size() + 1) + extension;
        originalToZip.put(uriText, zipPath);
        zipToUri.put(zipPath, uri);
        return zipPath;
    }

    private static String replaceUrisInMarkdown(
            Context context,
            String markdown,
            Map<String, String> originalToZip,
            Map<String, Uri> zipToUri
    ) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher matcher = URI_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = collectAndReplace(context, matcher.group(), originalToZip, zipToUri);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String restoreUri(
            Context context,
            File importRoot,
            String value,
            Map<String, byte[]> entries
    ) throws IOException {
        if (value == null || !value.startsWith("images/")) {
            return value;
        }
        byte[] data = entries.get(value);
        if (data == null) {
            return value;
        }
        String safeName = value.replace("images/", "img_").replaceAll("[^a-zA-Z0-9._-]", "_");
        File file = new File(importRoot, safeName);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
        }
        return Uri.fromFile(file).toString();
    }

    private static String restoreUrisInMarkdown(
            Context context,
            File importRoot,
            String markdown,
            Map<String, byte[]> entries
    ) throws IOException {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher matcher = Pattern.compile("images/[^\\s)\\]}\"']+").matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = restoreUri(context, importRoot, matcher.group(), entries);
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

    private static String guessExtension(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime == null ? "" : mime);
        if (ext != null && !ext.isEmpty()) {
            return "." + ext;
        }
        String path = uri.getLastPathSegment();
        if (path != null && path.contains(".")) {
            String suffix = path.substring(path.lastIndexOf('.'));
            if (suffix.length() <= 8) {
                return suffix;
            }
        }
        return ".jpg";
    }
}
