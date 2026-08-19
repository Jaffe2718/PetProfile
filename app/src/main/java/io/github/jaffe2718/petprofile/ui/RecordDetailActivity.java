package io.github.jaffe2718.petprofile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.FieldType;
import io.github.jaffe2718.petprofile.data.RecordDetails;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.LocationHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.noties.markwon.Markwon;
import io.noties.markwon.image.glide.GlideImagesPlugin;

public class RecordDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_RECORD_ID = "record_id";

    private PetRepository repository;
    private String profileId;
    private String recordId;

    private TextView titleTextView;
    private TextView typeTextView;
    private TextView timeTextView;
    private TextView locationTextView;
    private TextView metadataTextView;
    private View metadataCard;
    private LinearLayout attributesContainer;
    private TextView notesTextView;
    private LinearLayout imagesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_detail);
        repository = PetRepository.get(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);
        if (profileId == null || recordId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        titleTextView = findViewById(R.id.detailTitleTextView);
        typeTextView = findViewById(R.id.detailTypeTextView);
        timeTextView = findViewById(R.id.detailTimeTextView);
        locationTextView = findViewById(R.id.detailLocationTextView);
        metadataTextView = findViewById(R.id.detailMetadataTextView);
        metadataCard = findViewById(R.id.metadataCard);
        attributesContainer = findViewById(R.id.detailAttributesContainer);
        notesTextView = findViewById(R.id.detailNotesTextView);
        imagesContainer = findViewById(R.id.detailImagesContainer);

    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecord();
    }

    private void loadRecord() {
        repository.getRecordDetails(recordId, new Async.Result<RecordDetails>() {
            @Override
            public void onSuccess(RecordDetails value) {
                bind(value);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordDetailActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void bind(RecordDetails details) {
        titleTextView.setText(details.record.title == null || details.record.title.trim().isEmpty()
                ? typeLabel(details.record.type)
                : details.record.title);
        typeTextView.setText(typeLabel(details.record.type));
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        timeTextView.setText(format.format(new Date(details.record.timestamp)));

        StringBuilder location = new StringBuilder();
        if (details.record.locationName != null && !details.record.locationName.trim().isEmpty()) {
            location.append(details.record.locationName);
        }
        if (details.record.latitude != null && details.record.longitude != null) {
            if (location.length() > 0) location.append("  ");
            location.append(LocationHelper.formatDms(details.record.latitude, true))
                    .append(", ")
                    .append(LocationHelper.formatDms(details.record.longitude, false));
        }
        locationTextView.setText(location.length() == 0 ? getString(R.string.label_none) : location.toString());

        String metadata = buildMetadata(details);
        metadataTextView.setText(metadata);
        metadataCard.setVisibility(metadata.isEmpty() ? View.GONE : View.VISIBLE);
        attributesContainer.removeAllViews();
        for (RecordFieldEntity field : details.fields) {
            TextView fieldView = new TextView(this);
            fieldView.setPadding(0, 6, 0, 6);
            String value;
            if (FieldType.NUMBER.equals(field.fieldType)) {
                value = field.numericValue == null ? "" : String.valueOf(field.numericValue);
                if (field.unit != null && !field.unit.trim().isEmpty()) {
                    value += " " + field.unit;
                }
            } else {
                value = field.textValue == null ? "" : field.textValue;
            }
            fieldView.setText(field.fieldName + ": " + value);
            attributesContainer.addView(fieldView);
        }

        Markwon markwon = Markwon.builder(this)
                .usePlugin(GlideImagesPlugin.create(this))
                .build();
        markwon.setMarkdown(notesTextView, details.record.notesMarkdown == null ? "" : details.record.notesMarkdown);

        imagesContainer.removeAllViews();
        for (RecordImageEntity image : details.images) {
            ImageView imageView = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(180, 180);
            params.setMargins(0, 0, 12, 0);
            imageView.setLayoutParams(params);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setBackgroundResource(R.drawable.bg_thumb_rounded);
            imageView.setClipToOutline(true);
            Glide.with(this).load(image.uri).into(imageView);
            imagesContainer.addView(imageView);
        }
    }

    private String buildMetadata(RecordDetails details) {
        StringBuilder builder = new StringBuilder();
        if (RecordType.ESTABLISHMENT.equals(details.record.type)) {
            if (details.record.keeperName != null && !details.record.keeperName.trim().isEmpty()) {
                append(builder, getString(R.string.label_keeper) + ": " + details.record.keeperName);
            }
            append(builder, getString(R.string.record_establishment) + ": " + sourceLabel(details.record.establishmentSource));
        } else if (RecordType.ARCHIVE.equals(details.record.type)) {
            append(builder, getString(R.string.record_archive) + ": " + archiveReasonLabel(details.record.archiveReason));
        }
        if (RecordType.TRANSFER.equals(details.record.type)
                || (RecordType.ARCHIVE.equals(details.record.type) && "TRANSFER".equals(details.record.archiveReason))) {
            append(builder, getString(R.string.label_transfer_from_person) + ": " + safe(details.record.transferFromPerson));
            append(builder, getString(R.string.label_transfer_to_person) + ": " + safe(details.record.transferToPerson));
            append(builder, getString(R.string.label_transfer_from_place) + ": " + safe(details.record.transferFromPlace));
            append(builder, getString(R.string.label_transfer_to_place) + ": " + safe(details.record.transferToPlace));
        }
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(value);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? getString(R.string.label_none) : value;
    }

    private String sourceLabel(String source) {
        if ("WILD".equals(source)) return getString(R.string.record_establishment_source_wild);
        if ("PURCHASE".equals(source)) return getString(R.string.record_establishment_source_purchase);
        return getString(R.string.record_establishment_source_breed);
    }

    private String archiveReasonLabel(String reason) {
        return "TRANSFER".equals(reason)
                ? getString(R.string.record_archive_transfer)
                : getString(R.string.record_archive_death);
    }

    private String typeLabel(String type) {
        switch (type) {
            case RecordType.ESTABLISHMENT:
                return getString(R.string.record_establishment);
            case RecordType.DAILY:
                return getString(R.string.record_daily);
            case RecordType.TRANSFER:
                return getString(R.string.record_transfer);
            case RecordType.ARCHIVE:
                return getString(R.string.record_archive);
            default:
                return type;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_record_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_edit) {
            Intent intent = new Intent(this, RecordEditActivity.class);
            intent.putExtra(RecordEditActivity.EXTRA_PROFILE_ID, profileId);
            intent.putExtra(RecordEditActivity.EXTRA_RECORD_ID, recordId);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
