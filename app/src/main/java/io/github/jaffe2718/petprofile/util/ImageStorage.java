package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageStorage {
    private static final String IMAGE_DIR = "images";
    private static final Pattern URI_PATTERN = Pattern.compile("(?i)(content|file)://[^\\s)\\]}\"']+");

    private ImageStorage() {
    }

    public static String copyToPrivateStorage(Context context, String uriText) {
        if (uriText == null || uriText.trim().isEmpty()) {
            return uriText;
        }
        Uri uri = Uri.parse(uriText);
        String scheme = uri.getScheme();
        if ("file".equalsIgnoreCase(scheme) && uri.getPath() != null) {
            File privateRoot = context.getFilesDir();
            File candidate = new File(uri.getPath());
            if (candidate.getAbsolutePath().startsWith(privateRoot.getAbsolutePath())) {
                return uriText;
            }
        }
        if (!"content".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) {
            return uriText;
        }
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return uriText;
            }
            File dir = new File(context.getFilesDir(), IMAGE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return uriText;
            }
            String extension = guessExtension(context, uri);
            File out = new File(dir, IdUtil.timeBasedId() + extension);
            try (FileOutputStream output = new FileOutputStream(out)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            return Uri.fromFile(out).toString();
        } catch (Exception ignored) {
            return uriText;
        }
    }

    /**
     * Saves raw bytes into app-private storage under {@code files/images/} using the same
     * time-based naming rule as {@link #copyToPrivateStorage}. Returns a {@code file://} URI, or
     * {@code null} on failure.
     */
    public static String saveBytes(Context context, byte[] data, String extension) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            File dir = new File(context.getFilesDir(), IMAGE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            String ext = normalizeExtension(extension);
            File out = new File(dir, IdUtil.timeBasedId() + ext);
            try (FileOutputStream output = new FileOutputStream(out)) {
                output.write(data);
            }
            return Uri.fromFile(out).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return ".jpg";
        }
        String ext = extension.trim();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        return ext;
    }

    private static final Pattern BASE64_MARKDOWN_IMAGE =
            Pattern.compile("(?i)data:image/([a-z0-9+.-]+);base64,([A-Za-z0-9+/=]+)");

    /**
     * Rewrites inline {@code data:image/...;base64,....} images in a Markdown string into the
     * private {@code file://} URIs after decoding and storing them, so an agent can embed an image
     * it holds on the computer directly into a record's notes.
     */
    public static String rewriteMarkdownBase64Images(Context context, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        try {
            Matcher matcher = BASE64_MARKDOWN_IMAGE.matcher(markdown);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String mime = matcher.group(1);
                String data = matcher.group(2);
                byte[] bytes = Base64.getDecoder().decode(data);
                String ext = extensionFromMime(mime);
                String uri = saveBytes(context, bytes, ext);
                if (uri != null) {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(uri));
                } else {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        } catch (Exception ignored) {
            return markdown;
        }
    }

    public static String extensionFromMime(String mime) {
        if (mime == null) {
            return null;
        }
        if (mime.contains("png")) {
            return ".png";
        }
        if (mime.contains("jpeg") || mime.contains("jpg")) {
            return ".jpg";
        }
        if (mime.contains("gif")) {
            return ".gif";
        }
        if (mime.contains("webp")) {
            return ".webp";
        }
        if (mime.contains("bmp")) {
            return ".bmp";
        }
        return ".jpg";
    }

    public static String copyMarkdownImages(Context context, String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher matcher = URI_PATTERN.matcher(markdown);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = copyToPrivateStorage(context, matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String guessExtension(Context context, Uri uri) {
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
