package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
