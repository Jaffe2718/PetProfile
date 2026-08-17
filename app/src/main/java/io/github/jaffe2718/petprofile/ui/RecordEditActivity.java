package io.github.jaffe2718.petprofile.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.FieldType;
import io.github.jaffe2718.petprofile.data.RecordDetails;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.LocationHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordEditActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_RECORD_ID = "record_id";
    private static final int REQUEST_IMAGES = 5301;
    private static final int REQUEST_MAP_PICK = 5401;

    private PetRepository repository;
    private String profileId;
    private String recordId;
    private RecordEntity existingRecord;
    private FieldEditorAdapter fieldAdapter;
    private final List<String> imageUris = new ArrayList<>();
    private String currentType = RecordType.DAILY;
    private long timestamp = System.currentTimeMillis();
    private String locationName;
    private Double latitude;
    private Double longitude;

    private RadioGroup typeRadioGroup;
    private Button timeButton;
    private Button locationButton;
    private LinearLayout establishmentExtraLayout;
    private RadioGroup establishmentSourceRadioGroup;
    private LinearLayout archiveExtraLayout;
    private RadioGroup archiveReasonRadioGroup;
    private LinearLayout transferExtraLayout;
    private EditText transferFromPersonEditText;
    private EditText transferToPersonEditText;
    private EditText transferFromPlaceEditText;
    private EditText transferToPlaceEditText;
    private EditText notesEditText;
    private LinearLayout imagesContainer;
    private boolean recordLoaded;
    private EditText titleEditText;
    private boolean recordIsLatest;
    private final List<RecordEntity> profileRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_edit);
        repository = PetRepository.get(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);
        recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);
        if (profileId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(recordId == null ? R.string.action_add_record : R.string.action_edit);

        bindViews();
        setupSpinners();
        setupFieldEditor();
        setupListeners();
        updateTimeButton();
        Button saveButton = findViewById(R.id.saveButton);
        recordLoaded = recordId == null;
        saveButton.setEnabled(recordLoaded);

        if (recordId == null) {
            findViewById(R.id.typeEstablishmentRadio).setEnabled(false);
            updateTypeUi();
            applyRecordTypeAvailability();
            prefillFieldsFromLatestRecord();
        } else {
            loadRecord();
        }
    }

    private void bindViews() {
        typeRadioGroup = findViewById(R.id.typeRadioGroup);
        timeButton = findViewById(R.id.timeButton);
        locationButton = findViewById(R.id.locationButton);
        establishmentExtraLayout = findViewById(R.id.establishmentExtraLayout);
        establishmentSourceRadioGroup = findViewById(R.id.establishmentSourceRadioGroup);
        archiveExtraLayout = findViewById(R.id.archiveExtraLayout);
        archiveReasonRadioGroup = findViewById(R.id.archiveReasonRadioGroup);
        transferExtraLayout = findViewById(R.id.transferExtraLayout);
        transferFromPersonEditText = findViewById(R.id.transferFromPersonEditText);
        transferToPersonEditText = findViewById(R.id.transferToPersonEditText);
        transferFromPlaceEditText = findViewById(R.id.transferFromPlaceEditText);
        transferToPlaceEditText = findViewById(R.id.transferToPlaceEditText);
        notesEditText = findViewById(R.id.notesEditText);
        imagesContainer = findViewById(R.id.imagesContainer);
        titleEditText = findViewById(R.id.titleEditText);
    }

    private void setupSpinners() {
    }

    private void setupFieldEditor() {
        RecyclerView recyclerView = findViewById(R.id.recordFieldsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fieldAdapter = new FieldEditorAdapter(FieldEditorAdapter.MODE_RECORD, position -> {
        });
        recyclerView.setAdapter(fieldAdapter);
    }

    private void prefillFieldsFromLatestRecord() {
        repository.getRecords(profileId, new Async.Result<List<RecordEntity>>() {
            @Override
            public void onSuccess(List<RecordEntity> value) {
                if (value == null || value.isEmpty()) {
                    return;
                }
                RecordEntity latest = value.get(0);
                repository.getRecordDetails(latest.id, new Async.Result<RecordDetails>() {
                    @Override
                    public void onSuccess(RecordDetails details) {
                        List<EditableField> fields = new ArrayList<>();
                        for (RecordFieldEntity entity : details.fields) {
                            fields.add(toEditableField(entity));
                        }
                        fieldAdapter.setFields(fields);
                    }

                    @Override
                    public void onError(Throwable error) {
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
            }
        });
    }

    private void setupListeners() {
        typeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            currentType = typeFromRadio(checkedId);
            updateTypeUi();
        });
        timeButton.setOnClickListener(v -> pickTime());
        findViewById(R.id.locationButton).setOnClickListener(v -> pickLocation());
        findViewById(R.id.addFieldButton).setOnClickListener(v ->
                fieldAdapter.addField(new EditableField("", FieldType.NUMBER)));
        findViewById(R.id.addImageButton).setOnClickListener(v -> pickImages());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());
        archiveReasonRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateTypeUi();
        });
    }

    private void loadRecord() {
        repository.getRecordDetails(recordId, new Async.Result<RecordDetails>() {
            @Override
            public void onSuccess(RecordDetails details) {
                existingRecord = details.record;
                currentType = existingRecord.type;
                timestamp = existingRecord.timestamp;
                titleEditText.setText(existingRecord.title);
                locationName = existingRecord.locationName;
                latitude = existingRecord.latitude;
                longitude = existingRecord.longitude;
                selectTypeRadio(currentType);
                updateTimeButton();
                locationButton.setText(locationName == null ? getString(R.string.action_pick_location) : locationName);
                notesEditText.setText(existingRecord.notesMarkdown);
                transferFromPersonEditText.setText(existingRecord.transferFromPerson);
                transferToPersonEditText.setText(existingRecord.transferToPerson);
                transferFromPlaceEditText.setText(existingRecord.transferFromPlace);
                transferToPlaceEditText.setText(existingRecord.transferToPlace);
                if (existingRecord.establishmentSource != null) {
                    selectEstablishmentSource(existingRecord.establishmentSource);
                }
                if (existingRecord.archiveReason != null) {
                    selectArchiveReason(existingRecord.archiveReason);
                }
                List<EditableField> fields = new ArrayList<>();
                for (RecordFieldEntity entity : details.fields) {
                    fields.add(toEditableField(entity));
                }
                fieldAdapter.setFields(fields);
                imageUris.clear();
                for (RecordImageEntity image : details.images) {
                    imageUris.add(image.uri);
                }
                renderImages();
                updateTypeUi();
                determineRecordPositionAndApplyAvailability();
                recordLoaded = true;
                findViewById(R.id.saveButton).setEnabled(true);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void determineRecordPositionAndApplyAvailability() {
        repository.getRecords(profileId, new Async.Result<List<RecordEntity>>() {
            @Override
            public void onSuccess(List<RecordEntity> value) {
                profileRecords.clear();
                if (value != null) {
                    profileRecords.addAll(value);
                }
                recordIsLatest = value != null && !value.isEmpty()
                        && recordId != null && recordId.equals(value.get(0).id);
                applyRecordTypeAvailability();
            }

            @Override
            public void onError(Throwable error) {
                profileRecords.clear();
                recordIsLatest = false;
                applyRecordTypeAvailability();
            }
        });
    }

    private void applyRecordTypeAvailability() {
        typeRadioGroup.setEnabled(true);
        if (recordId == null) {
            typeRadioGroup.findViewById(R.id.typeEstablishmentRadio).setEnabled(false);
            typeRadioGroup.findViewById(R.id.typeDailyRadio).setEnabled(true);
            typeRadioGroup.findViewById(R.id.typeTransferRadio).setEnabled(true);
            typeRadioGroup.findViewById(R.id.typeArchiveRadio).setEnabled(true);
            return;
        }
        boolean establishment = RecordType.ESTABLISHMENT.equals(currentType);
        if (establishment) {
            typeRadioGroup.findViewById(R.id.typeEstablishmentRadio).setEnabled(true);
            typeRadioGroup.findViewById(R.id.typeDailyRadio).setEnabled(false);
            typeRadioGroup.findViewById(R.id.typeTransferRadio).setEnabled(false);
            typeRadioGroup.findViewById(R.id.typeArchiveRadio).setEnabled(false);
            return;
        }
        typeRadioGroup.findViewById(R.id.typeEstablishmentRadio).setEnabled(false);
        typeRadioGroup.findViewById(R.id.typeDailyRadio).setEnabled(true);
        typeRadioGroup.findViewById(R.id.typeTransferRadio).setEnabled(true);
        typeRadioGroup.findViewById(R.id.typeArchiveRadio).setEnabled(recordIsLatest);
    }

    private EditableField toEditableField(RecordFieldEntity entity) {
        EditableField field = new EditableField();
        field.key = entity.fieldKey;
        field.name = entity.fieldName;
        field.type = entity.fieldType;
        field.unit = entity.unit;
        field.value = entity.numericValue != null
                ? String.valueOf(entity.numericValue)
                : entity.textValue;
        return field;
    }

    private String typeFromRadio(int checkedId) {
        if (checkedId == R.id.typeEstablishmentRadio) return RecordType.ESTABLISHMENT;
        if (checkedId == R.id.typeTransferRadio) return RecordType.TRANSFER;
        if (checkedId == R.id.typeArchiveRadio) return RecordType.ARCHIVE;
        return RecordType.DAILY;
    }

    private void selectTypeRadio(String type) {
        if (RecordType.ESTABLISHMENT.equals(type)) {
            typeRadioGroup.check(R.id.typeEstablishmentRadio);
        } else if (RecordType.TRANSFER.equals(type)) {
            typeRadioGroup.check(R.id.typeTransferRadio);
        } else if (RecordType.ARCHIVE.equals(type)) {
            typeRadioGroup.check(R.id.typeArchiveRadio);
        } else {
            typeRadioGroup.check(R.id.typeDailyRadio);
        }
    }

    private void selectEstablishmentSource(String source) {
        int id = "WILD".equals(source) ? R.id.establishmentSourceWildRadio
                : ("PURCHASE".equals(source) ? R.id.establishmentSourcePurchaseRadio
                : R.id.establishmentSourceBredRadio);
        establishmentSourceRadioGroup.check(id);
    }

    private void selectArchiveReason(String reason) {
        archiveReasonRadioGroup.check("TRANSFER".equals(reason) ? R.id.archiveTransferRadio : R.id.archiveDeathRadio);
    }

    private void updateTypeUi() {
        boolean establishment = RecordType.ESTABLISHMENT.equals(currentType);
        boolean transfer = RecordType.TRANSFER.equals(currentType);
        boolean archive = RecordType.ARCHIVE.equals(currentType);
        establishmentExtraLayout.setVisibility(establishment ? View.VISIBLE : View.GONE);
        archiveExtraLayout.setVisibility(archive ? View.VISIBLE : View.GONE);
        boolean showTransferFields = transfer
                || (archive && archiveReasonRadioGroup.getCheckedRadioButtonId() == R.id.archiveTransferRadio);
        transferExtraLayout.setVisibility(showTransferFields ? View.VISIBLE : View.GONE);
    }

    private void pickTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    Calendar date = Calendar.getInstance();
                    date.set(year, month, day);
                    new TimePickerDialog(
                            this,
                            (view2, hour, minute) -> {
                                date.set(Calendar.HOUR_OF_DAY, hour);
                                date.set(Calendar.MINUTE, minute);
                                long candidate = date.getTimeInMillis();
                                if (!isEstablishmentTimestampValid(candidate)) {
                                    Toast.makeText(RecordEditActivity.this,
                                            R.string.error_establishment_time_order,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }
                                timestamp = candidate;
                                updateTimeButton();
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                    ).show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateTimeButton() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        timeButton.setText(format.format(new Date(timestamp)));
    }

    private void pickLocation() {
        double[] coords = LocationHelper.lastKnownCoordinates(this);
        double initialLatitude = coords == null ? 35.0 : coords[0];
        double initialLongitude = coords == null ? 105.0 : coords[1];
        LocationHelper.openMapPicker(this, REQUEST_MAP_PICK, initialLatitude, initialLongitude);
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGES);
    }

    private void renderImages() {
        imagesContainer.removeAllViews();
        for (int i = 0; i < imageUris.size(); i++) {
            String uri = imageUris.get(i);
            ImageView image = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    120,
                    120
            );
            params.setMargins(0, 0, 12, 0);
            image.setLayoutParams(params);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(uri).into(image);
            final int index = i;
            image.setOnClickListener(v -> {
                imageUris.remove(index);
                renderImages();
            });
            imagesContainer.addView(image);
        }
    }

    private void save() {
        String title = titleEditText.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.error_title_required, Toast.LENGTH_SHORT).show();
            titleEditText.requestFocus();
            return;
        }
        if (!isEstablishmentTimestampValid(timestamp)) {
            Toast.makeText(this, R.string.error_establishment_time_order, Toast.LENGTH_LONG).show();
            return;
        }
        List<RecordFieldEntity> fields = buildFields();
        if (fields == null) return;

        RecordEntity record = existingRecord == null ? new RecordEntity() : existingRecord;
        record.profileId = profileId;
        record.title = title;
        record.type = currentType;
        record.timestamp = timestamp;
        record.locationName = locationName;
        record.latitude = latitude;
        record.longitude = longitude;
        record.notesMarkdown = notesEditText.getText().toString();
        record.establishmentSource = RecordType.ESTABLISHMENT.equals(currentType) ? establishmentSource() : null;
        record.archiveReason = RecordType.ARCHIVE.equals(currentType) ? archiveReason() : null;
        record.transferFromPerson = text(transferFromPersonEditText);
        record.transferToPerson = text(transferToPersonEditText);
        record.transferFromPlace = text(transferFromPlaceEditText);
        record.transferToPlace = text(transferToPlaceEditText);

        List<RecordImageEntity> images = new ArrayList<>();
        for (int i = 0; i < imageUris.size(); i++) {
            RecordImageEntity image = new RecordImageEntity();
            image.id = null;
            image.uri = imageUris.get(i);
            image.position = i;
            images.add(image);
        }

        repository.saveRecord(record, fields, images, new Async.Result<String>() {
            @Override
            public void onSuccess(String value) {
                Toast.makeText(RecordEditActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(RecordEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isEstablishmentTimestampValid(long candidate) {
        if (!RecordType.ESTABLISHMENT.equals(currentType) || recordId == null) {
            return true;
        }
        for (RecordEntity record : profileRecords) {
            if (record.id != null && record.id.equals(recordId)) {
                continue;
            }
            if (candidate > record.timestamp) {
                return false;
            }
        }
        return true;
    }

    private List<RecordFieldEntity> buildFields() {
        List<RecordFieldEntity> result = new ArrayList<>();
        for (EditableField field : fieldAdapter.getFields()) {
            if (field.name == null || field.name.trim().isEmpty()) continue;
            RecordFieldEntity entity = new RecordFieldEntity();
            entity.fieldKey = field.key;
            entity.fieldName = field.name;
            entity.fieldType = field.type;
            if (FieldType.NUMBER.equals(field.type)) {
                if (field.value != null && !field.value.trim().isEmpty()) {
                    try {
                        entity.numericValue = Double.parseDouble(field.value.trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, field.name + ": " + getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                        return null;
                    }
                }
                entity.unit = field.unit;
            } else {
                entity.textValue = field.value;
            }
            result.add(entity);
        }
        return result;
    }

    private String establishmentSource() {
        int checkedId = establishmentSourceRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.establishmentSourceWildRadio) return "WILD";
        if (checkedId == R.id.establishmentSourcePurchaseRadio) return "PURCHASE";
        return "BREED";
    }

    private String archiveReason() {
        return archiveReasonRadioGroup.getCheckedRadioButtonId() == R.id.archiveTransferRadio ? "TRANSFER" : "DEATH";
    }

    private String text(EditText editText) {
        return editText.getText().toString().trim();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGES && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null) {
                        persistImagePermission(uri);
                        addImageUri(uri);
                    }
                }
            } else if (data.getData() != null) {
                persistImagePermission(data.getData());
                addImageUri(data.getData());
            }
            renderImages();
        } else if (requestCode == REQUEST_MAP_PICK && resultCode == RESULT_OK && data != null) {
            final double pickedLatitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LATITUDE, 0.0);
            final double pickedLongitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LONGITUDE, 0.0);
            latitude = pickedLatitude;
            longitude = pickedLongitude;
            LocationHelper.resolveAddress(this, pickedLatitude, pickedLongitude, new LocationHelper.Callback() {
                @Override
                public void onResult(LocationHelper.LocationResult result) {
                    locationName = result.name;
                    locationButton.setText(result.name);
                }

                @Override
                public void onError(String message) {
                    locationName = LocationHelper.formatDms(pickedLatitude, true)
                            + ", " + LocationHelper.formatDms(pickedLongitude, false);
                    locationButton.setText(locationName);
                }
            });
        }
    }

    private void addImageUri(Uri uri) {
        String text = uri.toString();
        imageUris.add(text);
        String markdown = "\n![image](" + text + ")";
        notesEditText.append(markdown);
    }

    private void persistImagePermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationHelper.REQUEST_LOCATION
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pickLocation();
        }
    }
}
