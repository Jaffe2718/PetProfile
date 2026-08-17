package io.github.jaffe2718.petprofile.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.LocationHelper;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ProfileEditActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profile_id";
    private static final int REQUEST_AVATAR = 5201;
    private static final int REQUEST_ESTABLISHMENT_IMAGES = 5202;
    private static final int REQUEST_MAP_PICK = 5401;

    private PetRepository repository;
    private String profileId;
    private ProfileEntity existingProfile;
    private String avatarUri;
    private final Map<String, ProfileEntity> availableProfileMap = new HashMap<>();
    private final Map<String, ProfileDetails> availableDetailsMap = new HashMap<>();
    private final List<String> establishmentImageUris = new ArrayList<>();
    private List<ProfileEntity> availableProfiles = new ArrayList<>();
    private final List<ProfileEntity> fatherCandidates = new ArrayList<>();
    private final List<ProfileEntity> motherCandidates = new ArrayList<>();
    private final Set<String> descendantIds = new HashSet<>();
    private boolean parentCandidatesReady;
    private String fatherId;
    private String motherId;
    private FieldEditorAdapter fieldAdapter;
    private FieldEditorAdapter establishmentFieldAdapter;
    private long establishmentTimestamp = System.currentTimeMillis();
    private String establishmentLocationName;
    private Double establishmentLatitude;
    private Double establishmentLongitude;
    private boolean profileLoaded;

    private EditText kingdomEditText;
    private EditText phylumEditText;
    private EditText classEditText;
    private EditText orderEditText;
    private EditText familyEditText;
    private EditText genusEditText;
    private EditText speciesEditText;
    private EditText subspeciesEditText;
    private EditText nicknameEditText;
    private Spinner genderSpinner;
    private android.widget.LinearLayout taxonomyContainer;
    private boolean taxonomyExpanded;
    private TextView profileIdTextView;
    private ImageView avatarPreview;
    private Spinner fatherSpinner;
    private Spinner motherSpinner;
    private RadioGroup establishmentSourceRadioGroup;
    private Button establishmentTimeButton;
    private Button establishmentLocationButton;
    private EditText establishmentNotesEditText;
    private EditText establishmentTitleEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);
        repository = PetRepository.get(this);
        profileId = getIntent().getStringExtra(EXTRA_PROFILE_ID);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(profileId == null ? R.string.action_add_profile : R.string.action_edit);

        bindViews();
        setupToolbar();
        setupSpinners();
        setupFieldEditor();
        setupButtons();
        updateTimeButton();
        Button saveButton = findViewById(R.id.saveButton);
        profileLoaded = profileId == null;
        saveButton.setEnabled(profileLoaded);

        if (profileId == null) {
            profileIdTextView.setText(R.string.label_none);
            setEstablishmentVisibility(true);
        } else {
            setEstablishmentVisibility(false);
            loadProfile();
        }
        loadAvailableProfiles();
    }

    private void bindViews() {
        kingdomEditText = findViewById(R.id.kingdomEditText);
        phylumEditText = findViewById(R.id.phylumEditText);
        classEditText = findViewById(R.id.classEditText);
        orderEditText = findViewById(R.id.orderEditText);
        familyEditText = findViewById(R.id.familyEditText);
        genusEditText = findViewById(R.id.genusEditText);
        speciesEditText = findViewById(R.id.speciesEditText);
        subspeciesEditText = findViewById(R.id.subspeciesEditText);
        nicknameEditText = findViewById(R.id.nicknameEditText);
        genderSpinner = findViewById(R.id.genderSpinner);
        taxonomyContainer = findViewById(R.id.taxonomyContainer);
        profileIdTextView = findViewById(R.id.profileIdTextView);
        avatarPreview = findViewById(R.id.avatarPreview);
        fatherSpinner = findViewById(R.id.fatherSpinner);
        motherSpinner = findViewById(R.id.motherSpinner);
        establishmentSourceRadioGroup = findViewById(R.id.establishmentSourceRadioGroup);
        establishmentTimeButton = findViewById(R.id.establishmentTimeButton);
        establishmentLocationButton = findViewById(R.id.establishmentLocationButton);
        establishmentNotesEditText = findViewById(R.id.establishmentNotesEditText);
        establishmentTitleEditText = findViewById(R.id.establishmentTitleEditText);
    }

    private void setupSpinners() {
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.gender_unknown),
                        getString(R.string.gender_female),
                        getString(R.string.gender_male)
                }
        );
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(genderAdapter);
        genderSpinner.setSelection(0);

    }

    private void setupFieldEditor() {
        RecyclerView recyclerView = findViewById(R.id.profileFieldsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fieldAdapter = new FieldEditorAdapter(FieldEditorAdapter.MODE_PROFILE, position -> {
        });
        recyclerView.setAdapter(fieldAdapter);

        RecyclerView establishmentRecyclerView = findViewById(R.id.establishmentFieldsRecyclerView);
        establishmentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        establishmentFieldAdapter = new FieldEditorAdapter(FieldEditorAdapter.MODE_RECORD, position -> {
        });
        establishmentRecyclerView.setAdapter(establishmentFieldAdapter);
    }

    private void setupButtons() {
        avatarPreview.setOnClickListener(v -> pickAvatar());
        findViewById(R.id.taxonomyToggleButton).setOnClickListener(v -> {
            taxonomyExpanded = !taxonomyExpanded;
            taxonomyContainer.setVisibility(taxonomyExpanded ? View.VISIBLE : View.GONE);
        });
        findViewById(R.id.addFieldButton).setOnClickListener(v ->
                fieldAdapter.addField(new EditableField("", FieldType.TEXT)));
        findViewById(R.id.establishmentTimeButton).setOnClickListener(v -> pickEstablishmentTime());
        findViewById(R.id.establishmentLocationButton).setOnClickListener(v -> pickLocation());
        findViewById(R.id.addEstablishmentFieldButton).setOnClickListener(v ->
                establishmentFieldAdapter.addField(new EditableField("", FieldType.NUMBER)));
        findViewById(R.id.establishmentImageButton).setOnClickListener(v -> pickEstablishmentImages());
        findViewById(R.id.saveButton).setOnClickListener(v -> save());
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.menu_profile_edit);
        MenuItem recordsItem = toolbar.getMenu().findItem(R.id.action_records);
        recordsItem.setVisible(profileId != null);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_records && profileId != null) {
                Intent intent = new Intent(ProfileEditActivity.this, RecordListActivity.class);
                intent.putExtra(RecordListActivity.EXTRA_PROFILE_ID, profileId);
                startActivity(intent);
                return true;
            }
            return false;
        });
    }

    private void loadAvailableProfiles() {
        repository.getAllProfileDetails(new Async.Result<List<ProfileDetails>>() {
            @Override
            public void onSuccess(List<ProfileDetails> value) {
                availableProfiles.clear();
                availableProfileMap.clear();
                availableDetailsMap.clear();
                for (ProfileDetails details : value) {
                    ProfileEntity profile = details.profile;
                    availableProfiles.add(profile);
                    availableProfileMap.put(profile.id, profile);
                    availableDetailsMap.put(profile.id, details);
                }
                prepareParentCandidates();
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(ProfileEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void prepareParentCandidates() {
        if (profileId == null) {
            descendantIds.clear();
            setupParentSpinners();
            return;
        }
        repository.getDescendantIds(profileId, new Async.Result<List<String>>() {
            @Override
            public void onSuccess(List<String> value) {
                descendantIds.clear();
                if (value != null) {
                    descendantIds.addAll(value);
                }
                setupParentSpinners();
            }

            @Override
            public void onError(Throwable error) {
                descendantIds.clear();
                setupParentSpinners();
            }
        });
    }

    private void loadProfile() {
        repository.getProfileDetails(profileId, new Async.Result<ProfileDetails>() {
            @Override
            public void onSuccess(ProfileDetails details) {
                existingProfile = details.profile;
                profileIdTextView.setText(existingProfile.id);
                avatarUri = existingProfile.avatarUri;
                kingdomEditText.setText(existingProfile.kingdom);
                phylumEditText.setText(existingProfile.phylum);
                classEditText.setText(existingProfile.taxClass);
                orderEditText.setText(existingProfile.taxOrder);
                familyEditText.setText(existingProfile.family);
                genusEditText.setText(existingProfile.genus);
                speciesEditText.setText(existingProfile.species);
                subspeciesEditText.setText(existingProfile.subspecies);
                String nickname = "";
                List<EditableField> fields = new ArrayList<>();
                for (ProfileCustomFieldEntity entity : details.customFields) {
                    if (isNicknameField(entity)) {
                        nickname = entity.textValue == null ? "" : entity.textValue;
                        continue;
                    }
                    fields.add(toEditableField(entity));
                }
                nicknameEditText.setText(nickname);
                setGenderSelection(existingProfile.gender);
                fatherId = details.fatherId;
                motherId = details.motherId;
                fieldAdapter.setFields(fields);
                updateParentSelections();
                showAvatar();
                profileLoaded = true;
                findViewById(R.id.saveButton).setEnabled(true);
            }

            @Override
            public void onError(Throwable error) {
                Toast.makeText(ProfileEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private EditableField toEditableField(ProfileCustomFieldEntity entity) {
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

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_AVATAR);
    }

    private void pickEstablishmentImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_ESTABLISHMENT_IMAGES);
    }

    private void appendEstablishmentImage(Uri uri) {
        persistImagePermission(uri);
        String text = uri.toString();
        establishmentImageUris.add(text);
        establishmentNotesEditText.append("\n![image](" + text + ")");
    }

    private void persistImagePermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void setupParentSpinners() {
        fatherCandidates.clear();
        motherCandidates.clear();
        ProfileEntity reference = currentTaxonomyReference();
        long childCreatedAt = currentChildCreationTime();
        for (ProfileEntity profile : availableProfiles) {
            if (profile.id.equals(profileId) || descendantIds.contains(profile.id)) {
                continue;
            }
            if (profile.createdAt >= childCreatedAt) {
                continue;
            }
            if (!TaxonomyUtil.sameMajorTaxonomy(reference, profile)) {
                continue;
            }
            if ("MALE".equals(profile.gender)) {
                fatherCandidates.add(profile);
            }
            if ("FEMALE".equals(profile.gender)) {
                motherCandidates.add(profile);
            }
        }
        List<String> fatherLabels = new ArrayList<>();
        List<String> motherLabels = new ArrayList<>();
        fatherLabels.add("");
        motherLabels.add("");
        for (ProfileEntity profile : fatherCandidates) {
            fatherLabels.add(parentLabel(profile));
        }
        for (ProfileEntity profile : motherCandidates) {
            motherLabels.add(parentLabel(profile));
        }

        ArrayAdapter<String> fatherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fatherLabels);
        fatherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fatherSpinner.setAdapter(fatherAdapter);
        fatherSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                fatherId = parentIdAtPosition(fatherCandidates, position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                fatherId = null;
            }
        });

        ArrayAdapter<String> motherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, motherLabels);
        motherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        motherSpinner.setAdapter(motherAdapter);
        motherSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                motherId = parentIdAtPosition(motherCandidates, position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                motherId = null;
            }
        });
        parentCandidatesReady = true;
        updateParentSelections();
    }

    private String parentLabel(ProfileEntity profile) {
        ProfileDetails details = availableDetailsMap.get(profile.id);
        String nickname = details == null ? "" : TaxonomyUtil.displayName(details.profile, details.customFields);
        String species = TaxonomyUtil.speciesDisplay(profile);
        StringBuilder label = new StringBuilder();
        if (!nickname.isEmpty() && !nickname.equals(profile.id)) {
            label.append(nickname);
        }
        if (!species.isEmpty()) {
            if (label.length() > 0) label.append(" - ");
            label.append(species);
        }
        if (label.length() > 0) label.append(" ");
        label.append("(").append(profile.id).append(")");
        return label.toString();
    }

    private ProfileEntity currentTaxonomyReference() {
        ProfileEntity reference = new ProfileEntity();
        reference.kingdom = text(kingdomEditText);
        reference.phylum = text(phylumEditText);
        reference.taxClass = text(classEditText);
        reference.taxOrder = text(orderEditText);
        reference.family = text(familyEditText);
        return reference;
    }

    private long currentChildCreationTime() {
        if (existingProfile != null) {
            return existingProfile.createdAt;
        }
        return establishmentTimestamp;
    }

    private String parentIdAtPosition(List<ProfileEntity> candidates, int position) {
        if (position <= 0 || position > candidates.size()) {
            return null;
        }
        return candidates.get(position - 1).id;
    }

    private void updateParentSelections() {
        if (!parentCandidatesReady) {
            return;
        }
        fatherSpinner.setSelection(indexOfParent(fatherCandidates, fatherId));
        motherSpinner.setSelection(indexOfParent(motherCandidates, motherId));
    }

    private int indexOfParent(List<ProfileEntity> candidates, String parentId) {
        if (parentId == null) {
            return 0;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).id.equals(parentId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void pickEstablishmentTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(establishmentTimestamp);
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
                                establishmentTimestamp = date.getTimeInMillis();
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
        updateTimeButton();
    }

    private void updateTimeButton() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        establishmentTimeButton.setText(format.format(new Date(establishmentTimestamp)));
    }

    private void pickLocation() {
        double[] coords = LocationHelper.lastKnownCoordinates(this);
        double initialLatitude = coords == null ? 35.0 : coords[0];
        double initialLongitude = coords == null ? 105.0 : coords[1];
        LocationHelper.openMapPicker(this, REQUEST_MAP_PICK, initialLatitude, initialLongitude);
    }

    private void save() {
        String nickname = text(nicknameEditText);
        if (nickname.isEmpty()) {
            Toast.makeText(this, R.string.error_nickname_required, Toast.LENGTH_SHORT).show();
            nicknameEditText.requestFocus();
            return;
        }
        List<ProfileCustomFieldEntity> customFields = buildCustomFields();
        if (customFields == null) return;
        customFields.add(0, buildNicknameField(nickname));

        ProfileEntity profile = existingProfile == null ? new ProfileEntity() : existingProfile;
        profile.kingdom = text(kingdomEditText);
        profile.phylum = text(phylumEditText);
        profile.taxClass = text(classEditText);
        profile.taxOrder = text(orderEditText);
        profile.family = text(familyEditText);
        profile.genus = text(genusEditText);
        profile.species = text(speciesEditText);
        profile.subspecies = text(subspeciesEditText);
        profile.gender = selectedGenderValue();
        profile.avatarUri = avatarUri;
        if (fatherId != null && fatherId.equals(motherId)) {
            Toast.makeText(this, R.string.error_parent_duplicate, Toast.LENGTH_SHORT).show();
            return;
        }

        if (profileId == null) {
            String title = text(establishmentTitleEditText);
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.error_title_required, Toast.LENGTH_SHORT).show();
                establishmentTitleEditText.requestFocus();
                return;
            }
            RecordEntity establishment = new RecordEntity();
            establishment.type = RecordType.ESTABLISHMENT;
            establishment.title = title;
            establishment.timestamp = establishmentTimestamp;
            establishment.locationName = establishmentLocationName;
            establishment.latitude = establishmentLatitude;
            establishment.longitude = establishmentLongitude;
            establishment.notesMarkdown = text(establishmentNotesEditText);
            establishment.establishmentSource = sourceValue();
            List<RecordFieldEntity> establishmentFields = buildEstablishmentFields();
            if (establishmentFields == null) {
                return;
            }
            repository.createProfile(
                    profile,
                    customFields,
                    fatherId,
                    motherId,
                    establishment,
                    establishmentFields,
                    buildEstablishmentImages(),
                    new Async.Result<String>() {
                        @Override
                        public void onSuccess(String value) {
                            Toast.makeText(ProfileEditActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(Throwable error) {
                            Toast.makeText(ProfileEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
            );
        } else {
            repository.updateProfile(profile, customFields, fatherId, motherId, new Async.Result<String>() {
                @Override
                public void onSuccess(String value) {
                    Toast.makeText(ProfileEditActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(Throwable error) {
                    Toast.makeText(ProfileEditActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private List<RecordImageEntity> buildEstablishmentImages() {
        List<RecordImageEntity> images = new ArrayList<>();
        for (int i = 0; i < establishmentImageUris.size(); i++) {
            RecordImageEntity image = new RecordImageEntity();
            image.id = null;
            image.uri = establishmentImageUris.get(i);
            image.position = i;
            images.add(image);
        }
        return images;
    }

    private List<RecordFieldEntity> buildEstablishmentFields() {
        List<RecordFieldEntity> result = new ArrayList<>();
        for (EditableField field : establishmentFieldAdapter.getFields()) {
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

    private List<ProfileCustomFieldEntity> buildCustomFields() {
        List<ProfileCustomFieldEntity> result = new ArrayList<>();
        for (EditableField field : fieldAdapter.getFields()) {
            if (field.name == null || field.name.trim().isEmpty()) continue;
            if ("nickname".equalsIgnoreCase(field.key)
                    || "nickname".equalsIgnoreCase(field.name)
                    || "昵称".equals(field.name)
                    || "暱稱".equals(field.name)) {
                continue;
            }
            ProfileCustomFieldEntity entity = new ProfileCustomFieldEntity();
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

    private void setGenderSelection(String gender) {
        if ("FEMALE".equals(gender)) {
            genderSpinner.setSelection(1);
        } else if ("MALE".equals(gender)) {
            genderSpinner.setSelection(2);
        } else {
            genderSpinner.setSelection(0);
        }
    }

    private String selectedGenderValue() {
        int position = genderSpinner.getSelectedItemPosition();
        if (position == 1) {
            return "FEMALE";
        }
        if (position == 2) {
            return "MALE";
        }
        return "UNKNOWN";
    }

    private ProfileCustomFieldEntity buildNicknameField(String nickname) {
        ProfileCustomFieldEntity entity = new ProfileCustomFieldEntity();
        entity.fieldKey = "nickname";
        entity.fieldName = "nickname";
        entity.fieldType = FieldType.TEXT;
        entity.textValue = nickname;
        entity.position = 0;
        return entity;
    }

    private boolean isNicknameField(ProfileCustomFieldEntity entity) {
        return "nickname".equalsIgnoreCase(entity.fieldKey)
                || "nickname".equalsIgnoreCase(entity.fieldName)
                || "昵称".equals(entity.fieldName)
                || "暱稱".equals(entity.fieldName);
    }

    private String sourceValue() {
        int checkedId = establishmentSourceRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.establishmentSourceWildRadio) {
            return "WILD";
        }
        if (checkedId == R.id.establishmentSourcePurchaseRadio) {
            return "PURCHASE";
        }
        return "BREED";
    }

    private void setEstablishmentVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        findViewById(R.id.establishmentHeaderTextView).setVisibility(visibility);
        findViewById(R.id.establishmentTitleEditText).setVisibility(visibility);
        establishmentSourceRadioGroup.setVisibility(visibility);
        establishmentTimeButton.setVisibility(visibility);
        findViewById(R.id.establishmentLocationButton).setVisibility(visibility);
        findViewById(R.id.establishmentFieldsHeaderTextView).setVisibility(visibility);
        findViewById(R.id.establishmentFieldsRecyclerView).setVisibility(visibility);
        findViewById(R.id.addEstablishmentFieldButton).setVisibility(visibility);
        findViewById(R.id.establishmentImageButton).setVisibility(visibility);
        establishmentNotesEditText.setVisibility(visibility);
        View notesParent = (View) establishmentNotesEditText.getParent();
        if (notesParent != null) {
            notesParent.setVisibility(visibility);
        }
        View titleParent = (View) establishmentTitleEditText.getParent();
        if (titleParent != null) {
            titleParent.setVisibility(visibility);
        }
    }

    private void showAvatar() {
        if (avatarUri == null || avatarUri.trim().isEmpty()) {
            avatarPreview.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            Glide.with(this).load(avatarUri).into(avatarPreview);
        }
    }

    private String text(EditText editText) {
        return editText.getText().toString().trim();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AVATAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
            avatarUri = uri.toString();
            showAvatar();
        } else if (requestCode == REQUEST_ESTABLISHMENT_IMAGES && resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null) {
                        appendEstablishmentImage(uri);
                    }
                }
            } else if (data.getData() != null) {
                appendEstablishmentImage(data.getData());
            }
        } else if (requestCode == REQUEST_MAP_PICK && resultCode == RESULT_OK && data != null) {
            final double pickedLatitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LATITUDE, 0.0);
            final double pickedLongitude = data.getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LONGITUDE, 0.0);
            establishmentLatitude = pickedLatitude;
            establishmentLongitude = pickedLongitude;
            LocationHelper.resolveAddress(this, pickedLatitude, pickedLongitude, new LocationHelper.Callback() {
                @Override
                public void onResult(LocationHelper.LocationResult result) {
                    establishmentLocationName = result.name;
                    establishmentLocationButton.setText(result.name);
                }

                @Override
                public void onError(String message) {
                    establishmentLocationName = LocationHelper.formatDms(pickedLatitude, true)
                            + ", " + LocationHelper.formatDms(pickedLongitude, false);
                    establishmentLocationButton.setText(establishmentLocationName);
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LocationHelper.REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pickLocation();
            }
        }
    }
}
