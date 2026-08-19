package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.FieldType;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.noties.markwon.Markwon;

public final class CardShareManager {
    private static final int WIDTH = 1080;
    private static final int GAP = 28;
    private static final int BACKGROUND = Color.rgb(245, 248, 244);

    private CardShareManager() {
    }

    public static Intent buildShareIntent(Context context, ProfileDetails details, List<RecordEntity> records,
                                          Map<String, List<RecordFieldEntity>> fieldsByRecord,
                                          Map<String, List<String>> imagesByRecord)
            throws Exception {
        File file = createCardFile(context, details, records, fieldsByRecord, imagesByRecord);
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("image/png");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(share, context.getString(R.string.share_card_title));
    }

    public static File createCardFile(Context context, ProfileDetails details, List<RecordEntity> records,
                                      Map<String, List<RecordFieldEntity>> fieldsByRecord,
                                      Map<String, List<String>> imagesByRecord)
            throws Exception {
        Markwon markwon = Markwon.create(context);
        Bitmap top = createTopCardBitmap(context, details);

        List<Bitmap> recordBitmaps = new ArrayList<>();
        int totalHeight = top.getHeight();
        if (records != null) {
            for (RecordEntity record : records) {
                Bitmap recordBitmap = createRecordBitmap(context, markwon, record,
                        fieldsByRecord.get(record.id), imagesByRecord.get(record.id));
                recordBitmaps.add(recordBitmap);
                totalHeight += recordBitmap.getHeight() + GAP;
            }
        }

        Bitmap result = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(BACKGROUND);
        int y = 0;
        canvas.drawBitmap(top, 0, 0, null);
        y += top.getHeight();
        for (Bitmap recordBitmap : recordBitmaps) {
            y += GAP;
            canvas.drawBitmap(recordBitmap, 0, y, null);
            y += recordBitmap.getHeight();
        }

        File output = new File(context.getCacheDir(), "profile_long_card_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            result.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        return output;
    }

    private static Bitmap createTopCardBitmap(Context context, ProfileDetails details) {
        int height = topCardHeight(context, details);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(BACKGROUND);
        drawTopCardContent(canvas, context, details);
        return bitmap;
    }

    private static Bitmap createRecordBitmap(Context context, Markwon markwon, RecordEntity record,
                                             List<RecordFieldEntity> fields, List<String> images) {
        int height = recordBoxHeight(context, markwon, record, fields, images);
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        drawRecordBoxContent(canvas, context, markwon, record, fields, images, height);
        return bitmap;
    }

    private static void drawTopCardContent(Canvas canvas, Context context, ProfileDetails details) {
        Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.rgb(46, 125, 50));
        canvas.drawRoundRect(new RectF(0, 0, WIDTH, 220), 0, 0, headerPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(54f);
        titlePaint.setFakeBoldText(true);
        canvas.drawText(context.getString(R.string.share_card_title), 48, 140, titlePaint);

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.rgb(90, 90, 90));
        labelPaint.setTextSize(34f);
        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.rgb(20, 20, 20));
        valuePaint.setTextSize(40f);

        float y = 280f;
        y = drawLine(canvas, labelPaint, valuePaint, "ID", details.profile.id, y);
        y = drawLine(canvas, labelPaint, valuePaint, "Taxonomy", TaxonomyUtil.speciesDisplay(details.profile), y);
        for (ProfileCustomFieldEntity field : details.customFields) {
            String value = field.numericValue != null
                    ? String.valueOf(field.numericValue) + (field.unit == null ? "" : " " + field.unit)
                    : field.textValue;
            y = drawLine(canvas, labelPaint, valuePaint, field.fieldName, value, y);
        }
        if (details.profile.avatarUri != null && !details.profile.avatarUri.trim().isEmpty()) {
            Bitmap avatar = decodeBitmap(context, Uri.parse(details.profile.avatarUri), 300);
            if (avatar != null) {
                canvas.drawBitmap(avatar, null, new RectF(48, y + 24, 348, y + 324), null);
            }
        }
    }

    private static float drawLine(Canvas canvas, Paint label, Paint value, String name, String text, float y) {
        canvas.drawText(name == null ? "" : name, 48, y, label);
        StaticLayout layout = layoutValue(text == null ? "" : text, value);
        canvas.save();
        canvas.translate(360f, y + value.getFontMetrics().ascent);
        layout.draw(canvas);
        canvas.restore();
        return y + Math.max(74f, layout.getHeight() + 18f);
    }

    private static StaticLayout layoutValue(String text, Paint valuePaint) {
        TextPaint textPaint = new TextPaint(valuePaint);
        return StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, WIDTH - 360 - 48)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build();
    }

    private static int topCardHeight(Context context, ProfileDetails details) {
        Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valuePaint.setColor(Color.rgb(20, 20, 20));
        valuePaint.setTextSize(40f);

        float y = 280f;
        y += lineHeight(details.profile.id, valuePaint);
        y += lineHeight(TaxonomyUtil.speciesDisplay(details.profile), valuePaint);
        for (ProfileCustomFieldEntity field : details.customFields) {
            String value = field.numericValue != null
                    ? String.valueOf(field.numericValue) + (field.unit == null ? "" : " " + field.unit)
                    : field.textValue;
            y += lineHeight(value, valuePaint);
        }
        if (details.profile.avatarUri != null && !details.profile.avatarUri.trim().isEmpty()) {
            y += 350f;
        } else {
            y += 24f;
        }
        return (int) (y + 40f);
    }

    private static float lineHeight(String text, Paint valuePaint) {
        return Math.max(74f, layoutValue(text == null ? "" : text, valuePaint).getHeight() + 18f);
    }

    private static int recordBoxHeight(Context context, Markwon markwon, RecordEntity record,
                                       List<RecordFieldEntity> fields, List<String> images) {
        int fieldsHeight = fields == null ? 0 : fields.size() * 44;
        int metadataHeight = metadataLines(context, record).size() * 44;
        int notesHeight = markdownHeight(context, markwon, record.notesMarkdown, WIDTH - 128);
        int imagesHeight = images == null || images.isEmpty() ? 0 : 244;
        return 320 + fieldsHeight + metadataHeight + notesHeight + imagesHeight;
    }

    private static void drawRecordBoxContent(Canvas canvas, Context context, Markwon markwon,
                                             RecordEntity record, List<RecordFieldEntity> fields,
                                             List<String> images, int boxHeight) {
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(6f);
        borderPaint.setColor(typeColor(record.type));
        canvas.drawRoundRect(new RectF(24, 8, WIDTH - 24, boxHeight - 8), 18f, 18f, borderPaint);

        Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(30, 14, WIDTH - 30, boxHeight - 14), 14f, 14f, backgroundPaint);

        float x = 64f;
        float y = 56f;
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.rgb(25, 25, 25));
        titlePaint.setTextSize(40f);
        titlePaint.setFakeBoldText(true);
        String title = record.title == null || record.title.trim().isEmpty()
                ? typeLabel(context, record.type)
                : record.title;
        canvas.drawText(title, x, y, titlePaint);

        y += 54f;
        Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setColor(Color.rgb(80, 80, 80));
        metaPaint.setTextSize(31f);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String time = format.format(new Date(record.timestamp));
        canvas.drawText(typeLabel(context, record.type) + "  ·  " + time, x, y, metaPaint);

        y += 48f;
        String location = locationText(record);
        if (location.isEmpty()) {
            location = context.getString(R.string.label_none);
        }
        canvas.drawText(context.getString(R.string.label_location) + ": " + location, x, y, metaPaint);

        List<String> metadata = metadataLines(context, record);
        if (!metadata.isEmpty()) {
            y += 52f;
            for (String line : metadata) {
                canvas.drawText(line, x, y, metaPaint);
                y += 44f;
            }
        }

        if (fields != null && !fields.isEmpty()) {
            y += 52f;
            Paint fieldNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fieldNamePaint.setColor(Color.rgb(60, 60, 60));
            fieldNamePaint.setTextSize(31f);
            Paint fieldValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fieldValuePaint.setColor(Color.rgb(20, 20, 20));
            fieldValuePaint.setTextSize(31f);
            for (RecordFieldEntity field : fields) {
                String value;
                if (FieldType.NUMBER.equals(field.fieldType)) {
                    value = field.numericValue == null ? "" : String.valueOf(field.numericValue);
                    if (field.unit != null && !field.unit.trim().isEmpty()) {
                        value += " " + field.unit;
                    }
                } else {
                    value = field.textValue == null ? "" : field.textValue;
                }
                canvas.drawText(field.fieldName, x, y, fieldNamePaint);
                canvas.drawText(value, x + 320, y, fieldValuePaint);
                y += 44f;
            }
        }

        y += 44f;
        String notes = stripMarkdownImages(record.notesMarkdown);
        if (notes == null || notes.trim().isEmpty()) {
            notes = " ";
        }
        TextView notesView = buildMarkdownView(context, markwon, notes, (int) (WIDTH - x - 64), 34f);
        canvas.save();
        canvas.translate(x, y);
        notesView.draw(canvas);
        canvas.restore();
        y += notesView.getHeight();

        if (images != null && !images.isEmpty()) {
            y += 24f;
            int size = 180;
            int margin = 12;
            int count = Math.min(3, images.size());
            for (int i = 0; i < count; i++) {
                Bitmap image = decodeBitmap(context, Uri.parse(images.get(i)), size);
                if (image != null) {
                    canvas.drawBitmap(image, null, new RectF(x + i * (size + margin), y,
                            x + i * (size + margin) + size, y + size), null);
                }
            }
        }
    }

    private static int markdownHeight(Context context, Markwon markwon, String markdown, int width) {
        String text = stripMarkdownImages(markdown);
        if (text == null || text.trim().isEmpty()) {
            text = " ";
        }
        TextView view = buildMarkdownView(context, markwon, text, width, 34f);
        return Math.max(44, view.getMeasuredHeight());
    }

    private static TextView buildMarkdownView(Context context, Markwon markwon, String markdown,
                                              int width, float textSizePx) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
        markwon.setMarkdown(view, markdown);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        view.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = view.getMeasuredHeight();
        view.layout(0, 0, width, height);
        return view;
    }

    private static String stripMarkdownImages(String markdown) {
        if (markdown == null) {
            return "";
        }
        return markdown.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");
    }

    private static String locationText(RecordEntity record) {
        StringBuilder builder = new StringBuilder();
        if (record.locationName != null && !record.locationName.trim().isEmpty()) {
            builder.append(record.locationName);
        }
        if (record.latitude != null && record.longitude != null) {
            if (builder.length() > 0) builder.append("  ");
            builder.append(LocationHelper.formatDms(record.latitude, true))
                    .append(", ")
                    .append(LocationHelper.formatDms(record.longitude, false));
        }
        return builder.toString();
    }

    private static List<String> metadataLines(Context context, RecordEntity record) {
        List<String> lines = new ArrayList<>();
        if (RecordType.ESTABLISHMENT.equals(record.type)) {
            if (record.keeperName != null && !record.keeperName.trim().isEmpty()) {
                lines.add(context.getString(R.string.label_keeper) + ": " + record.keeperName);
            }
            lines.add(context.getString(R.string.label_establishment_source)
                    + ": " + establishmentSourceLabel(context, record.establishmentSource));
        } else if (RecordType.ARCHIVE.equals(record.type)) {
            lines.add(context.getString(R.string.label_archive_reason)
                    + ": " + archiveReasonLabel(context, record.archiveReason));
        } else if (RecordType.TRANSFER.equals(record.type)) {
            lines.add(context.getString(R.string.label_transfer_from_person)
                    + ": " + safeText(record.transferFromPerson));
            lines.add(context.getString(R.string.label_transfer_to_person)
                    + ": " + safeText(record.transferToPerson));
            lines.add(context.getString(R.string.label_transfer_from_place)
                    + ": " + safeText(record.transferFromPlace));
            lines.add(context.getString(R.string.label_transfer_to_place)
                    + ": " + safeText(record.transferToPlace));
        }
        return lines;
    }

    private static String establishmentSourceLabel(Context context, String source) {
        if ("WILD".equals(source)) {
            return context.getString(R.string.record_establishment_source_wild);
        }
        if ("PURCHASE".equals(source)) {
            return context.getString(R.string.record_establishment_source_purchase);
        }
        return context.getString(R.string.record_establishment_source_breed);
    }

    private static String archiveReasonLabel(Context context, String reason) {
        return "TRANSFER".equals(reason)
                ? context.getString(R.string.record_archive_transfer)
                : context.getString(R.string.record_archive_death);
    }

    private static String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "" : value.trim();
    }

    private static int typeColor(String type) {
        switch (type) {
            case RecordType.ESTABLISHMENT:
                return Color.parseColor("#BBDEFB");
            case RecordType.TRANSFER:
                return Color.parseColor("#E1BEE7");
            case RecordType.ARCHIVE:
                return Color.parseColor("#FFCDD2");
            default:
                return Color.parseColor("#A5D6A7");
        }
    }

    private static String typeLabel(Context context, String type) {
        switch (type) {
            case RecordType.ESTABLISHMENT:
                return context.getString(R.string.record_establishment);
            case RecordType.TRANSFER:
                return context.getString(R.string.record_transfer);
            case RecordType.ARCHIVE:
                return context.getString(R.string.record_archive);
            default:
                return context.getString(R.string.record_daily);
        }
    }

    private static Bitmap decodeBitmap(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if ("content".equals(uri.getScheme())) {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) return null;
                    BitmapFactory.decodeStream(input, null, options);
                }
            } else {
                BitmapFactory.decodeFile(uri.getPath(), options);
            }
            options.inSampleSize = calculateSampleSize(options, maxSize);
            options.inJustDecodeBounds = false;
            if ("content".equals(uri.getScheme())) {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) return null;
                    return BitmapFactory.decodeStream(input, null, options);
                }
            } else {
                return BitmapFactory.decodeFile(uri.getPath(), options);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int calculateSampleSize(BitmapFactory.Options options, int maxSize) {
        int sample = 1;
        int width = options.outWidth;
        int height = options.outHeight;
        while (width / 2 >= maxSize || height / 2 >= maxSize) {
            width /= 2;
            height /= 2;
            sample *= 2;
        }
        return Math.max(1, sample);
    }
}
