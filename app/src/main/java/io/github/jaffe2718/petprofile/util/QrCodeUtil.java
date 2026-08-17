package io.github.jaffe2718.petprofile.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.LanTransferPayload;
import io.github.jaffe2718.petprofile.data.QrTransferPayload;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class QrCodeUtil {
    private static final Gson GSON = new Gson();
    private static final String FULL_TYPE = "PETPROFILE_TRANSFER_V1";
    private static final String REF_TYPE = "PETPROFILE_TRANSFER_REF_V1";
    private static final int MAX_QR_TEXT = 2800;

    private QrCodeUtil() {
    }

    public static Bitmap encode(String content, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
        );
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    public static String decode(Bitmap bitmap) throws Exception {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        RGBLuminanceSource source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        return new MultiFormatReader().decode(binaryBitmap).getText();
    }

    public static String buildTransferQrText(ExportBundle original) throws Exception {
        ExportBundle compact = GSON.fromJson(GSON.toJson(original), ExportBundle.class);
        for (RecordEntity record : compact.records) {
            record.notesMarkdown = stripLocalImages(record.notesMarkdown);
        }
        compact.recordImages.clear();
        for (io.github.jaffe2718.petprofile.data.entity.ProfileEntity profile : compact.profiles) {
            profile.avatarUri = null;
        }

        QrTransferPayload full = new QrTransferPayload();
        full.type = FULL_TYPE;
        full.profileId = compact.rootProfileId == null ? firstProfileId(compact) : compact.rootProfileId;
        full.data = gzipBase64(GSON.toJson(compact));
        if (full.data.length() <= MAX_QR_TEXT) {
            return GSON.toJson(full);
        }
        QrTransferPayload reference = new QrTransferPayload();
        reference.type = REF_TYPE;
        reference.profileId = full.profileId;
        return GSON.toJson(reference);
    }

    public static ExportBundle parseTransferQrText(String text) throws Exception {
        QrTransferPayload payload = GSON.fromJson(text, QrTransferPayload.class);
        if (payload == null || payload.type == null) {
            throw new IllegalArgumentException("Invalid QR payload.");
        }
        if (REF_TYPE.equals(payload.type) || payload.data == null || payload.data.isEmpty()) {
            return null;
        }
        if (!FULL_TYPE.equals(payload.type)) {
            throw new IllegalArgumentException("Unknown QR payload type.");
        }
        String json = gunzipBase64(payload.data);
        return GSON.fromJson(json, ExportBundle.class);
    }

    public static String buildLanTransferQrText(LanTransferPayload payload) {
        return GSON.toJson(payload);
    }

    public static LanTransferPayload parseLanTransferPayload(String text) {
        try {
            LanTransferPayload payload = GSON.fromJson(text, LanTransferPayload.class);
            if (payload != null && "PETPROFILE_LAN_V1".equals(payload.type)) {
                return payload;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String firstProfileId(ExportBundle bundle) {
        if (bundle.profiles.isEmpty()) {
            return "";
        }
        return bundle.profiles.get(0).id;
    }

    private static String stripLocalImages(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown.replaceAll("!\\[[^]]*]\\((content|file)://[^)]*\\)", "![image]");
    }

    private static String gzipBase64(String value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private static String gunzipBase64(String value) throws Exception {
        byte[] compressed = Base64.getUrlDecoder().decode(value);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
