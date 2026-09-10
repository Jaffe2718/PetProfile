package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
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
    private static final int IMAGE_GAP = 24;
    private static final int RECORD_IMAGE_MAX_WIDTH = WIDTH - 64 - 64;
    private static final int BACKGROUND = Color.rgb(245, 248, 244);

    // Passport card: the first block of the shared long image, styled after the profile list item.
    private static final int CARD_MARGIN = 24;
    private static final float CARD_RADIUS = 28f;
    private static final float CARD_PADDING = 36f;
    /** Heavier than the 6px frame of the record cards below, so the profile card reads as the header. */
    private static final float PROFILE_BORDER = 9f;
    /** Email-style rule across the top of the whole sheet, in the card's tone. */
    private static final float TOP_RULE_HEIGHT = 5f;
    /** The header is blank, then the rule bisects it, then the title, each with its own padding. */
    private static final float HEADER_TOP_PADDING = 44f;
    private static final float TITLE_TOP_PADDING = 72f;
    private static final float TITLE_BOTTOM_PADDING = 51f;
    private static final float TITLE_TEXT_SIZE = 82f;
    private static final float AVATAR_SIZE = 240f;
    private static final float AVATAR_RADIUS = 48f;
    private static final float AVATAR_TEXT_GAP = 32f;
    private static final float ATTRIBUTE_NAME_WEIGHT = 0.42f;
    private static final float ATTRIBUTE_COLUMN_GAP = 16f;

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
        RectF card = new RectF();
        layoutTopCard(context, details, null, card);

        // One tone for the whole header: the card's own background is the gender tint, while the
        // frame and the rule above the title are a saturated shade of that same hue (never grey, and
        // independent of the archived state).
        int tone = genderToneColor(context, details);
        int accent = toneBorder(tone);

        // Inset exactly like the card, so the rule and the card share the same left/right edges.
        Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rulePaint.setColor(accent);
        canvas.drawRect(CARD_MARGIN, HEADER_TOP_PADDING,
                WIDTH - CARD_MARGIN, HEADER_TOP_PADDING + TOP_RULE_HEIGHT, rulePaint);

        Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardPaint.setColor(tone);
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, cardPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(PROFILE_BORDER);
        borderPaint.setColor(accent);
        canvas.drawRoundRect(card, CARD_RADIUS, CARD_RADIUS, borderPaint);

        layoutTopCard(context, details, canvas, null);
    }

    /**
     * Walks the header top-down with a single cursor. With {@code canvas == null} it only advances
     * that cursor to measure; otherwise it paints. Both modes share every position, so the measured
     * height and the painted content can never disagree — which is what keeps the stacked cards
     * below from overlapping.
     *
     * <p>The title sits outside the card, under the sheet's header rule; the card itself starts below
     * the title.
     *
     * @param cardOut optional; receives the rectangle of the card itself
     * @return the height of the whole header block, including the margin under it
     */
    private static float layoutTopCard(Context context, ProfileDetails details, Canvas canvas, RectF cardOut) {
        float contentLeft = CARD_MARGIN + CARD_PADDING;
        float contentRight = WIDTH - CARD_MARGIN - CARD_PADDING;

        Paint titlePaint = textPaint(TITLE_TEXT_SIZE, context.getColor(R.color.text_primary), true);
        Paint namePaint = textPaint(46f, context.getColor(R.color.text_primary), true);
        Paint idPaint = textPaint(34f, context.getColor(R.color.text_secondary), false);
        Paint taxonomyPaint = textPaint(36f, context.getColor(R.color.text_primary), false);
        Paint metaPaint = textPaint(34f, context.getColor(R.color.text_secondary), false);
        Paint sectionPaint = textPaint(36f, context.getColor(R.color.primary), true);
        Paint headerPaint = textPaint(32f, context.getColor(R.color.text_primary), true);
        Paint bodyPaint = textPaint(32f, context.getColor(R.color.text_primary), false);

        // Blank header, the rule that bisects it, blank space, then the title above the card.
        float y = HEADER_TOP_PADDING + TOP_RULE_HEIGHT + TITLE_TOP_PADDING;
        String title = context.getString(R.string.share_card_title);
        if (canvas != null) {
            canvas.drawText(title, (WIDTH - titlePaint.measureText(title)) / 2f,
                    y - titlePaint.getFontMetrics().ascent, titlePaint);
        }
        y += lineHeightOf(titlePaint) + TITLE_BOTTOM_PADDING;

        float cardTop = y;
        y += CARD_PADDING;

        // Avatar and the identity column beside it.
        float columnLeft = contentLeft + AVATAR_SIZE + AVATAR_TEXT_GAP;
        float columnWidth = contentRight - columnLeft;
        List<StaticLayout> column = new ArrayList<>();
        ProfileCustomFieldEntity nicknameField = findNicknameField(details.customFields);
        String nickname = nicknameField == null || nicknameField.textValue == null
                ? "" : nicknameField.textValue.trim();
        if (!nickname.isEmpty()) {
            column.add(layoutText(nickname, namePaint, columnWidth));
        }
        String id = details.profile.id == null ? "" : details.profile.id.trim();
        if (!id.isEmpty()) {
            column.add(layoutText("ID  " + id, idPaint, columnWidth));
        }
        String taxonomy = TaxonomyUtil.speciesDisplay(details.profile);
        if (taxonomy != null && !taxonomy.trim().isEmpty()) {
            column.add(layoutText(taxonomy, taxonomyPaint, columnWidth));
        }
        String meta = genderAndSource(context, details);
        if (!meta.isEmpty()) {
            column.add(layoutText(meta, metaPaint, columnWidth));
        }

        float columnHeight = 0f;
        for (StaticLayout line : column) {
            columnHeight += line.getHeight();
        }
        float rowHeight = Math.max(AVATAR_SIZE, columnHeight);
        if (canvas != null) {
            drawAvatar(canvas, context, details,
                    new RectF(contentLeft, y, contentLeft + AVATAR_SIZE, y + AVATAR_SIZE));
            float columnTop = y + Math.max(0f, (rowHeight - columnHeight) / 2f);
            canvas.save();
            canvas.translate(columnLeft, columnTop);
            for (StaticLayout line : column) {
                line.draw(canvas);
                canvas.translate(0f, line.getHeight());
            }
            canvas.restore();
        }
        y += rowHeight + 32f;

        // Profile attributes, laid out like the expandable table on the profile list.
        List<ProfileCustomFieldEntity> attributes = new ArrayList<>();
        if (details.customFields != null) {
            for (ProfileCustomFieldEntity field : details.customFields) {
                if (field != nicknameField) {
                    attributes.add(field);
                }
            }
        }
        if (!attributes.isEmpty()) {
            String section = context.getString(R.string.label_profile_attributes) + " (" + attributes.size() + ")";
            if (canvas != null) {
                canvas.drawText(section, contentLeft, y - sectionPaint.getFontMetrics().ascent, sectionPaint);
            }
            y += lineHeightOf(sectionPaint) + 18f;

            float nameWidth = (contentRight - contentLeft) * ATTRIBUTE_NAME_WEIGHT;
            float valueX = contentLeft + nameWidth + ATTRIBUTE_COLUMN_GAP;
            float valueWidth = contentRight - valueX;
            if (canvas != null) {
                canvas.drawText(context.getString(R.string.label_field_name), contentLeft,
                        y - headerPaint.getFontMetrics().ascent, headerPaint);
                canvas.drawText(context.getString(R.string.label_value), valueX,
                        y - headerPaint.getFontMetrics().ascent, headerPaint);
            }
            y += lineHeightOf(headerPaint) + 10f;

            for (ProfileCustomFieldEntity field : attributes) {
                StaticLayout nameLayout = layoutText(field.fieldName == null ? "" : field.fieldName,
                        bodyPaint, nameWidth);
                StaticLayout valueLayout = layoutText(fieldValue(field), bodyPaint, valueWidth);
                if (canvas != null) {
                    canvas.save();
                    canvas.translate(contentLeft, y);
                    nameLayout.draw(canvas);
                    canvas.restore();
                    canvas.save();
                    canvas.translate(valueX, y);
                    valueLayout.draw(canvas);
                    canvas.restore();
                }
                y += Math.max(nameLayout.getHeight(), valueLayout.getHeight()) + 14f;
            }
            y -= 14f;
        }

        if (cardOut != null) {
            cardOut.set(CARD_MARGIN, cardTop, WIDTH - CARD_MARGIN, y + CARD_PADDING);
        }
        return y + CARD_PADDING + CARD_MARGIN;
    }

    private static Paint textPaint(float size, int color, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setFakeBoldText(bold);
        return paint;
    }

    private static StaticLayout layoutText(String text, Paint paint, float width) {
        String value = text == null ? "" : text;
        return StaticLayout.Builder
                .obtain(value, 0, value.length(), new TextPaint(paint), Math.max(1, (int) width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
    }

    private static float lineHeightOf(Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    /** Gender tint used by the card background, the profile list and nothing else: never grey. */
    private static int genderToneColor(Context context, ProfileDetails details) {
        String gender = details.profile.gender;
        if ("MALE".equals(gender)) {
            return context.getColor(R.color.profile_male_bg);
        }
        if ("FEMALE".equals(gender)) {
            return context.getColor(R.color.profile_female_bg);
        }
        return context.getColor(R.color.profile_unknown_bg);
    }

    /**
     * A saturated shade of the same hue, used for the card frame and the header rule. Scaling the
     * channels down (the obvious "darken") would drain the colour and read as grey, so the hue is
     * kept and the saturation raised instead.
     */
    private static int toneBorder(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.28f, Math.min(1f, hsv[1] * 2.6f));
        hsv[2] = Math.max(0.35f, hsv[2] * 0.86f);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    /** Gender symbol and establishment source, joined the way the profile list shows them. */
    private static String genderAndSource(Context context, ProfileDetails details) {
        List<String> parts = new ArrayList<>();
        String gender = details.profile.gender;
        if ("MALE".equals(gender)) {
            parts.add("♂");
        } else if ("FEMALE".equals(gender)) {
            parts.add("♀");
        }
        String source = sourceLabel(context, details.establishmentSource);
        if (source != null) {
            parts.add(source);
        }
        return String.join(" · ", parts);
    }

    private static String sourceLabel(Context context, String source) {
        if ("WILD".equals(source)) {
            return context.getString(R.string.record_establishment_source_wild);
        }
        if ("PURCHASE".equals(source)) {
            return context.getString(R.string.record_establishment_source_purchase);
        }
        if ("BREED".equals(source)) {
            return context.getString(R.string.record_establishment_source_breed);
        }
        return null;
    }

    /** A centre-cropped avatar in a rounded square, with the shared avatar background behind it. */
    private static void drawAvatar(Canvas canvas, Context context, ProfileDetails details, RectF box) {
        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(context.getColor(R.color.avatar_background));
        canvas.drawRoundRect(box, AVATAR_RADIUS, AVATAR_RADIUS, background);
        if (details.profile.avatarUri == null || details.profile.avatarUri.trim().isEmpty()) {
            return;
        }
        Bitmap avatar = decodeBitmap(context, Uri.parse(details.profile.avatarUri), (int) AVATAR_SIZE);
        if (avatar == null) {
            return;
        }
        int side = Math.min(avatar.getWidth(), avatar.getHeight());
        if (side <= 0) {
            return;
        }
        int srcLeft = (avatar.getWidth() - side) / 2;
        int srcTop = (avatar.getHeight() - side) / 2;
        Rect src = new Rect(srcLeft, srcTop, srcLeft + side, srcTop + side);
        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(box, AVATAR_RADIUS, AVATAR_RADIUS, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(avatar, src, box, null);
        canvas.restore();
    }

    private static ProfileCustomFieldEntity findNicknameField(List<ProfileCustomFieldEntity> fields) {
        if (fields == null) {
            return null;
        }
        for (ProfileCustomFieldEntity field : fields) {
            if (TaxonomyUtil.isNickname(field)) {
                return field;
            }
        }
        return null;
    }

    private static String fieldValue(ProfileCustomFieldEntity field) {
        if (field.numericValue != null) {
            return FieldValueUtil.formatNumeric(field.numericValue, field.unit);
        }
        return field.textValue == null ? "" : field.textValue;
    }

    private static int topCardHeight(Context context, ProfileDetails details) {
        return (int) Math.ceil(layoutTopCard(context, details, null, null));
    }

    private static int recordBoxHeight(Context context, Markwon markwon, RecordEntity record,
                                       List<RecordFieldEntity> fields, List<String> images) {
        int fieldsHeight = fields == null ? 0 : fields.size() * 44;
        int metadataHeight = metadataLines(context, record).size() * 44;
        int notesHeight = markdownHeight(context, markwon, record.notesMarkdown, WIDTH - 128);
        int imagesHeight = imagesLayoutHeight(context, images);
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
                    value = FieldValueUtil.formatNumeric(field.numericValue, field.unit);
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
            y += IMAGE_GAP;
            int count = Math.min(3, images.size());
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    y += IMAGE_GAP;
                }
                Bitmap image = decodeBitmap(context, Uri.parse(images.get(i)), RECORD_IMAGE_MAX_WIDTH);
                if (image != null) {
                    int drawWidth = Math.min(RECORD_IMAGE_MAX_WIDTH, image.getWidth());
                    int drawHeight = Math.round(drawWidth * (float) image.getHeight() / image.getWidth());
                    canvas.drawBitmap(image, null, new RectF(x, y, x + drawWidth, y + drawHeight), null);
                    y += drawHeight;
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

    private static int imagesLayoutHeight(Context context, List<String> images) {
        if (images == null || images.isEmpty()) {
            return 0;
        }
        int count = Math.min(3, images.size());
        int total = IMAGE_GAP;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                total += IMAGE_GAP;
            }
            total += scaledImageHeight(context, images.get(i));
        }
        return total;
    }

    private static int scaledImageHeight(Context context, String uri) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        decodeBounds(context, Uri.parse(uri), options);
        int originalWidth = options.outWidth;
        int originalHeight = options.outHeight;
        if (originalWidth <= 0 || originalHeight <= 0) {
            return 0;
        }
        int sample = calculateSampleSize(options, RECORD_IMAGE_MAX_WIDTH);
        int decodedWidth = Math.max(1, (originalWidth + sample - 1) / sample);
        int decodedHeight = Math.max(1, (originalHeight + sample - 1) / sample);
        int drawWidth = Math.min(RECORD_IMAGE_MAX_WIDTH, decodedWidth);
        return Math.round(drawWidth * (float) decodedHeight / decodedWidth);
    }

    private static void decodeBounds(Context context, Uri uri, BitmapFactory.Options options) {
        options.inJustDecodeBounds = true;
        if ("content".equals(uri.getScheme())) {
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input != null) {
                    BitmapFactory.decodeStream(input, null, options);
                }
            } catch (Exception ignored) {
            }
        } else {
            BitmapFactory.decodeFile(uri.getPath(), options);
        }
    }

    private static Bitmap decodeBitmap(Context context, Uri uri, int maxSize) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            decodeBounds(context, uri, options);
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
