package io.github.jaffe2718.petprofile.repository;

import android.content.Context;

import androidx.room.RoomDatabase;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.PetProfileApplication;
import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.FamilyGraph;
import io.github.jaffe2718.petprofile.data.KeeperInfo;
import io.github.jaffe2718.petprofile.data.NumericPoint;
import io.github.jaffe2718.petprofile.data.NumericSeries;
import io.github.jaffe2718.petprofile.data.FieldType;
import io.github.jaffe2718.petprofile.data.ProfileDetails;
import io.github.jaffe2718.petprofile.data.RecordDetails;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.dao.ProfileDao;
import io.github.jaffe2718.petprofile.data.dao.RecordDao;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileParentCrossRef;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.ImageStorage;
import io.github.jaffe2718.petprofile.util.IdUtil;
import io.github.jaffe2718.petprofile.util.KeeperInfoManager;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PetRepository {
    private static volatile PetRepository instance;
    private final AppDatabase database;
    private final Context context;

    private PetRepository(Context context) {
        this.context = context.getApplicationContext();
        database = AppDatabase.getInstance(context);
    }

    public static PetRepository get(Context context) {
        if (instance == null) {
            synchronized (PetRepository.class) {
                if (instance == null) {
                    instance = new PetRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void getAllProfiles(Async.Result<List<ProfileEntity>> callback) {
        Async.run(() -> {
            try {
                List<ProfileEntity> value = database.profileDao().getAllProfiles();
                Async.post(callback, value, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getAllProfileDetails(Async.Result<List<ProfileDetails>> callback) {
        Async.run(() -> {
            try {
                ProfileDao dao = database.profileDao();
                RecordDao recordDao = database.recordDao();
                List<ProfileEntity> profiles = dao.getAllProfiles();
                List<ProfileDetails> result = new ArrayList<>();
                for (ProfileEntity profile : profiles) {
                    String privateAvatar = ImageStorage.copyToPrivateStorage(context, profile.avatarUri);
                    if (!java.util.Objects.equals(privateAvatar, profile.avatarUri)) {
                        profile.avatarUri = privateAvatar;
                        dao.updateProfile(profile);
                    }
                    ProfileDetails details = new ProfileDetails();
                    details.profile = profile;
                    details.customFields.addAll(dao.getCustomFields(profile.id));
                    details.parentIds.addAll(dao.getParentIds(profile.id));
                    details.fatherId = dao.getParentIdByRole(profile.id, "FATHER");
                    details.motherId = dao.getParentIdByRole(profile.id, "MOTHER");
                    RecordEntity establishment = recordDao.getFirstByType(profile.id, RecordType.ESTABLISHMENT);
                    details.establishmentSource = establishment == null ? null : establishment.establishmentSource;
                    details.establishmentTimestamp = establishment == null ? null : establishment.timestamp;
                    details.lastRecordTimestamp = recordDao.getLatestTimestamp(profile.id);
                    result.add(details);
                }
                Async.post(callback, result, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getProfileDetails(String profileId, Async.Result<ProfileDetails> callback) {
        Async.run(() -> {
            try {
                ProfileDao dao = database.profileDao();
                ProfileDetails details = new ProfileDetails();
                details.profile = dao.getById(profileId);
                if (details.profile == null) {
                    throw new IllegalStateException("Profile not found: " + profileId);
                }
                String privateAvatar = ImageStorage.copyToPrivateStorage(context, details.profile.avatarUri);
                if (!java.util.Objects.equals(privateAvatar, details.profile.avatarUri)) {
                    details.profile.avatarUri = privateAvatar;
                    dao.updateProfile(details.profile);
                }
                details.customFields.addAll(dao.getCustomFields(profileId));
                details.parentIds.addAll(dao.getParentIds(profileId));
                details.fatherId = dao.getParentIdByRole(profileId, "FATHER");
                details.motherId = dao.getParentIdByRole(profileId, "MOTHER");
                RecordEntity establishment = database.recordDao().getFirstByType(profileId, RecordType.ESTABLISHMENT);
                details.establishmentSource = establishment == null ? null : establishment.establishmentSource;
                details.establishmentTimestamp = establishment == null ? null : establishment.timestamp;
                details.lastRecordTimestamp = database.recordDao().getLatestTimestamp(profileId);
                if (!details.parentIds.isEmpty()) {
                    details.parents.addAll(dao.getByIds(details.parentIds));
                }
                Async.post(callback, details, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getProfileById(String profileId, Async.Result<ProfileEntity> callback) {
        Async.run(() -> {
            try {
                ProfileEntity value = database.profileDao().getById(profileId);
                Async.post(callback, value, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getRecords(String profileId, Async.Result<List<RecordEntity>> callback) {
        Async.run(() -> {
            try {
                List<RecordEntity> value = database.recordDao().getRecordsForProfile(profileId);
                Async.post(callback, value, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getRecordImages(List<String> recordIds, Async.Result<Map<String, List<String>>> callback) {
        Async.run(() -> {
            try {
                Map<String, List<String>> result = new HashMap<>();
                if (recordIds == null || recordIds.isEmpty()) {
                    Async.post(callback, result, null);
                    return;
                }
                List<RecordImageEntity> images = database.recordDao().getImagesForRecords(recordIds);
                List<RecordImageEntity> privateImages = new ArrayList<>();
                for (RecordImageEntity image : images) {
                    if (image.recordId != null && image.uri != null && !image.uri.trim().isEmpty()) {
                        String privateUri = ImageStorage.copyToPrivateStorage(context, image.uri);
                        if (!java.util.Objects.equals(privateUri, image.uri)) {
                            image.uri = privateUri;
                            privateImages.add(image);
                        }
                        result.computeIfAbsent(image.recordId, ignored -> new ArrayList<>()).add(image.uri);
                    }
                }
                if (!privateImages.isEmpty()) {
                    database.recordDao().insertImages(privateImages);
                }
                Async.post(callback, result, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getRecordFields(List<String> recordIds, Async.Result<Map<String, List<RecordFieldEntity>>> callback) {
        Async.run(() -> {
            try {
                Map<String, List<RecordFieldEntity>> result = new HashMap<>();
                if (recordIds == null || recordIds.isEmpty()) {
                    Async.post(callback, result, null);
                    return;
                }
                List<RecordFieldEntity> fields = database.recordDao().getFieldsForRecords(recordIds);
                for (RecordFieldEntity field : fields) {
                    if (field.recordId != null) {
                        result.computeIfAbsent(field.recordId, ignored -> new ArrayList<>()).add(field);
                    }
                }
                Async.post(callback, result, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getRecordDetails(String recordId, Async.Result<RecordDetails> callback) {
        Async.run(() -> {
            try {
                RecordDao dao = database.recordDao();
                RecordDetails details = new RecordDetails();
                details.record = dao.getById(recordId);
                if (details.record == null) {
                    throw new IllegalStateException("Record not found: " + recordId);
                }
                String privateNotes = ImageStorage.copyMarkdownImages(context, details.record.notesMarkdown);
                if (!privateNotes.equals(details.record.notesMarkdown)) {
                    details.record.notesMarkdown = privateNotes;
                    dao.updateRecord(details.record);
                }
                details.fields.addAll(dao.getFields(recordId));
                details.images.addAll(dao.getImages(recordId));
                List<RecordImageEntity> privateImages = new ArrayList<>();
                for (RecordImageEntity image : details.images) {
                    String privateUri = ImageStorage.copyToPrivateStorage(context, image.uri);
                    if (!java.util.Objects.equals(privateUri, image.uri)) {
                        image.uri = privateUri;
                        privateImages.add(image);
                    }
                }
                if (!privateImages.isEmpty()) {
                    dao.insertImages(privateImages);
                }
                Async.post(callback, details, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void createProfile(
            ProfileEntity profile,
            List<ProfileCustomFieldEntity> customFields,
            String fatherId,
            String motherId,
            RecordEntity establishment,
            List<RecordFieldEntity> establishmentFields,
            List<RecordImageEntity> establishmentImages,
            Async.Result<String> callback
    ) {
        Async.run(() -> {
            try {
                if (profile == null || establishment == null) {
                    throw new IllegalArgumentException("Profile and establishment record are required.");
                }
                if (profile.id == null || profile.id.trim().isEmpty()) {
                    profile.id = IdUtil.timeBasedId();
                } else {
                    profile.id = IdUtil.normalizeId(profile.id);
                }
                long now = System.currentTimeMillis();
                profile.createdAt = establishment.timestamp;
                profile.updatedAt = now;
                establishment.id = IdUtil.randomId();
                establishment.profileId = profile.id;
                establishment.type = RecordType.ESTABLISHMENT;
                List<String> parentIds = nonEmptyParents(fatherId, motherId);

                database.runInTransaction(() -> {
                    ProfileDao profileDao = database.profileDao();
                    RecordDao recordDao = database.recordDao();
                    profileDao.insertProfile(profile);
                    validateParents(profile.id, parentIds);
                    validateParentGenders(fatherId, motherId);
                    replaceCustomFields(profileDao, profile.id, customFields);
                    replaceParents(profileDao, profile.id, fatherId, motherId);
                    recordDao.insertRecord(establishment);
                    replaceRecordFields(recordDao, establishment.id, establishmentFields);
                    replaceRecordImages(recordDao, establishment.id, establishmentImages);
                });
                Async.post(callback, profile.id, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void updateProfile(
            ProfileEntity profile,
            List<ProfileCustomFieldEntity> customFields,
            String fatherId,
            String motherId,
            Async.Result<String> callback
    ) {
        Async.run(() -> {
            try {
                List<String> parentIds = nonEmptyParents(fatherId, motherId);
                validateParents(profile.id, parentIds);
                validateParentGenders(fatherId, motherId);
                profile.id = IdUtil.normalizeId(profile.id);
                profile.updatedAt = System.currentTimeMillis();
                ProfileDao profileDao = database.profileDao();
                ProfileEntity previous = profileDao.getById(profile.id);
                database.runInTransaction(() -> {
                    profileDao.updateProfile(profile);
                    applyGenderRelationCleanup(profileDao, profile.id, previous == null ? null : previous.gender, profile.gender);
                    replaceCustomFields(profileDao, profile.id, customFields);
                    replaceParents(profileDao, profile.id, fatherId, motherId);
                });
                Async.post(callback, profile.id, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void deleteProfile(String profileId, Async.EmptyResult callback) {
        Async.run(() -> {
            try {
                database.profileDao().deleteById(profileId);
                Async.ui(callback::onSuccess);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    public void saveRecord(
            RecordEntity record,
            List<RecordFieldEntity> fields,
            List<RecordImageEntity> images,
            Async.Result<String> callback
    ) {
        Async.run(() -> {
            try {
                validateRecord(record);
                if (record.id == null || record.id.trim().isEmpty()) {
                    record.id = IdUtil.randomId();
                } else {
                    record.id = IdUtil.normalizeId(record.id);
                }
                database.runInTransaction(() -> {
                    RecordDao recordDao = database.recordDao();
                    ProfileDao profileDao = database.profileDao();
                    if (recordDao.getById(record.id) == null) {
                        recordDao.insertRecord(record);
                    } else {
                        recordDao.updateRecord(record);
                    }
                    replaceRecordFields(recordDao, record.id, fields);
                    replaceRecordImages(recordDao, record.id, images);
                    syncArchiveStatus(profileDao, recordDao, record.profileId);
                });
                Async.post(callback, record.id, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void deleteRecord(String recordId, Async.EmptyResult callback) {
        Async.run(() -> {
            try {
                RecordDao recordDao = database.recordDao();
                RecordEntity record = recordDao.getById(recordId);
                if (record == null) {
                    Async.ui(callback::onSuccess);
                    return;
                }
                if (RecordType.ESTABLISHMENT.equals(record.type)) {
                    throw new IllegalStateException("Establishment record cannot be deleted alone.");
                }
                recordDao.deleteById(recordId);
                syncArchiveStatus(database.profileDao(), recordDao, record.profileId);
                Async.ui(callback::onSuccess);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    public void getAncestorIds(String profileId, Async.Result<List<String>> callback) {
        Async.run(() -> {
            try {
                List<String> ancestors = collectAncestors(profileId);
                Async.post(callback, ancestors, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getDescendantIds(String profileId, Async.Result<List<String>> callback) {
        Async.run(() -> {
            try {
                List<String> descendants = collectDescendants(profileId);
                Async.post(callback, descendants, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getProfilesByIds(List<String> ids, Async.Result<List<ProfileEntity>> callback) {
        Async.run(() -> {
            try {
                List<ProfileEntity> value = database.profileDao().getByIds(ids);
                Async.post(callback, value, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getFamily(String profileId, Async.Result<FamilyGraph> callback) {
        Async.run(() -> {
            try {
                ProfileDao dao = database.profileDao();
                FamilyGraph graph = new FamilyGraph();
                graph.root = dao.getById(profileId);
                if (graph.root == null) {
                    throw new IllegalStateException("Profile not found: " + profileId);
                }
                List<String> ancestorIds = collectAncestors(profileId);
                List<String> descendantIds = collectDescendants(profileId);
                if (!ancestorIds.isEmpty()) {
                    graph.ancestors.addAll(dao.getByIds(ancestorIds));
                }
                if (!descendantIds.isEmpty()) {
                    graph.descendants.addAll(dao.getByIds(descendantIds));
                }
                Set<String> familyIds = new HashSet<>();
                familyIds.add(profileId);
                familyIds.addAll(ancestorIds);
                familyIds.addAll(descendantIds);
                for (String currentId : familyIds) {
                    List<String> parents = dao.getParentIds(currentId);
                    graph.parentIdsByChild.put(currentId, parents);
                    for (String parentId : parents) {
                        graph.childIdsByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(currentId);
                    }
                }
                Async.post(callback, graph, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void getNumericChartData(String profileId, Async.Result<List<NumericSeries>> callback) {
        Async.run(() -> {
            try {
                RecordDao dao = database.recordDao();
                List<RecordEntity> records = dao.getRecordsForProfileOldestFirst(profileId);
                List<String> recordIds = new ArrayList<>();
                Map<String, Long> timestampById = new HashMap<>();
                for (RecordEntity record : records) {
                    recordIds.add(record.id);
                    timestampById.put(record.id, record.timestamp);
                }
                List<RecordFieldEntity> allFields = recordIds.isEmpty()
                        ? new ArrayList<>()
                        : dao.getFieldsForRecords(recordIds);
                Map<String, NumericSeries> seriesByKey = new HashMap<>();
                for (RecordFieldEntity field : allFields) {
                    if (!FieldType.NUMBER.equals(field.fieldType) || field.numericValue == null) {
                        continue;
                    }
                    Long time = timestampById.get(field.recordId);
                    if (time == null) continue;
                    String key = field.fieldKey == null || field.fieldKey.trim().isEmpty()
                            ? field.fieldName
                            : field.fieldKey;
                    NumericSeries series = seriesByKey.get(key);
                    if (series == null) {
                        series = new NumericSeries();
                        series.fieldKey = key;
                        series.fieldName = field.fieldName;
                        series.unit = field.unit;
                        seriesByKey.put(key, series);
                    }
                    series.points.add(new NumericPoint(time, field.numericValue));
                }
                List<NumericSeries> result = new ArrayList<>(seriesByKey.values());
                for (NumericSeries series : result) {
                    series.points.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));
                }
                Async.post(callback, result, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void collectTransferBundle(String profileId, Async.Result<ExportBundle> callback) {
        Async.run(() -> {
            try {
                ProfileEntity profile = database.profileDao().getById(profileId);
                if (profile == null) {
                    throw new IllegalStateException("Profile not found.");
                }
                Set<String> ids = new HashSet<>();
                ids.add(profile.id);
                ids.addAll(collectAncestors(profile.id));
                List<String> descendants = collectDescendants(profile.id);
                ids.addAll(descendants);
                ExportBundle bundle = exportProfiles(ids);
                bundle.rootProfileId = profile.id;
                bundle.descendantIds.addAll(descendants);
                bundle.keeperInfo = KeeperInfoManager.load(context);
                Async.post(callback, bundle, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void exportAll(Async.Result<ExportBundle> callback) {
        Async.run(() -> {
            try {
                List<ProfileEntity> profiles = database.profileDao().getAllProfilesOldestFirst();
                Set<String> ids = new HashSet<>();
                for (ProfileEntity profile : profiles) {
                    ids.add(profile.id);
                }
                ExportBundle bundle = exportProfiles(ids);
                Async.post(callback, bundle, null);
            } catch (Throwable t) {
                Async.post(callback, null, t);
            }
        });
    }

    public void importBundle(ExportBundle bundle, Async.EmptyResult callback) {
        Async.run(() -> {
            try {
                if (bundle == null) {
                    throw new IllegalArgumentException("Empty bundle.");
                }
                database.runInTransaction(() -> {
                    ProfileDao profileDao = database.profileDao();
                    RecordDao recordDao = database.recordDao();
                    Set<String> profileIds = new HashSet<>();
                    for (ProfileEntity profile : bundle.profiles) {
                        profile.id = IdUtil.normalizeId(profile.id);
                        profileIds.add(profile.id);
                    }
                    for (String profileId : profileIds) {
                        profileDao.deleteById(profileId);
                    }
                    for (ProfileEntity profile : bundle.profiles) {
                        profileDao.insertProfile(profile);
                    }
                    for (ProfileCustomFieldEntity field : bundle.customFields) {
                        field.profileId = IdUtil.normalizeId(field.profileId);
                    }
                    if (!bundle.customFields.isEmpty()) {
                        profileDao.insertCustomFields(bundle.customFields);
                    }

                    for (ProfileParentCrossRef link : bundle.parentLinks) {
                        link.childId = IdUtil.normalizeId(link.childId);
                        link.parentId = IdUtil.normalizeId(link.parentId);
                    }
                    if (!bundle.parentLinks.isEmpty()) {
                        profileDao.insertParents(bundle.parentLinks);
                    }

                    for (RecordEntity record : bundle.records) {
                        record.id = IdUtil.normalizeId(record.id);
                        record.profileId = IdUtil.normalizeId(record.profileId);
                    }
                    if (!bundle.records.isEmpty()) {
                        recordDao.insertRecords(bundle.records);
                    }

                    for (RecordFieldEntity field : bundle.recordFields) {
                        field.recordId = IdUtil.normalizeId(field.recordId);
                    }
                    if (!bundle.recordFields.isEmpty()) {
                        recordDao.insertFields(bundle.recordFields);
                    }

                    for (RecordImageEntity image : bundle.recordImages) {
                        image.id = IdUtil.normalizeId(image.id);
                        image.recordId = IdUtil.normalizeId(image.recordId);
                    }
                    if (!bundle.recordImages.isEmpty()) {
                        recordDao.insertImages(bundle.recordImages);
                    }
                    for (String profileId : profileIds) {
                        syncArchiveStatus(profileDao, recordDao, profileId);
                    }
                });
                Async.ui(callback::onSuccess);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    public void importTransferBundle(ExportBundle bundle, Async.EmptyResult callback) {
        Async.run(() -> {
            try {
                if (bundle == null) {
                    throw new IllegalArgumentException("Empty bundle.");
                }
                String rootId = bundle.rootProfileId == null ? null : IdUtil.normalizeId(bundle.rootProfileId);
                KeeperInfo senderInfo = bundle.keeperInfo == null ? new KeeperInfo() : bundle.keeperInfo;
                KeeperInfo receiverInfo = KeeperInfoManager.load(context);
                Set<String> existingProfileIds = new HashSet<>();
                for (ProfileEntity existing : database.profileDao().getAllProfiles()) {
                    existingProfileIds.add(IdUtil.normalizeId(existing.id));
                }
                if (rootId != null) {
                    RecordEntity rootArchive = null;
                    for (RecordEntity record : bundle.records) {
                        if (rootId.equals(IdUtil.normalizeId(record.profileId))
                                && RecordType.ARCHIVE.equals(record.type)) {
                            rootArchive = record;
                            break;
                        }
                    }
                    if (rootArchive == null) {
                        appendTransferRecord(bundle, rootId, senderInfo, receiverInfo);
                    } else if ("TRANSFER".equals(rootArchive.archiveReason)) {
                        rootArchive.type = RecordType.TRANSFER;
                        rootArchive.archiveReason = null;
                    }
                }
                if (bundle.descendantIds != null) {
                    for (String descendantId : bundle.descendantIds) {
                        String normalized = IdUtil.normalizeId(descendantId);
                        if (!existingProfileIds.contains(normalized)
                                && !hasArchiveRecord(bundle, normalized)) {
                            appendDescendantArchiveRecord(bundle, normalized);
                        }
                    }
                }
                importBundle(bundle, callback);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    public void applyOutgoingTransfer(String profileId, KeeperInfo receiverInfo, Async.EmptyResult callback) {
        Async.run(() -> {
            try {
                String normalizedProfileId = IdUtil.normalizeId(profileId);
                RecordDao recordDao = database.recordDao();
                ProfileDao profileDao = database.profileDao();
                if (profileDao.getById(normalizedProfileId) == null) {
                    Async.ui(callback::onSuccess);
                    return;
                }
                if (recordDao.getFirstByType(normalizedProfileId, RecordType.ARCHIVE) != null) {
                    Async.ui(callback::onSuccess);
                    return;
                }
                KeeperInfo senderInfo = KeeperInfoManager.load(context);
                final KeeperInfo receiver = receiverInfo == null ? new KeeperInfo() : receiverInfo;
                RecordEntity record = new RecordEntity();
                record.id = IdUtil.randomId();
                record.profileId = normalizedProfileId;
                record.type = RecordType.ARCHIVE;
                record.archiveReason = "TRANSFER";
                record.title = context.getString(R.string.record_archive_transfer);
                Long latest = recordDao.getLatestTimestamp(normalizedProfileId);
                record.timestamp = latest == null ? System.currentTimeMillis() : Math.max(System.currentTimeMillis(), latest + 1);
                record.transferFromPerson = senderInfo.nickname;
                record.transferToPlace = senderInfo.homePlace;
                record.transferToPerson = receiver.nickname;
                record.transferFromPlace = receiver.homePlace;
                database.runInTransaction(() -> {
                    recordDao.insertRecord(record);
                    syncArchiveStatus(profileDao, recordDao, normalizedProfileId);
                });
                Async.ui(callback::onSuccess);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t));
            }
        });
    }

    private void appendTransferRecord(
            ExportBundle bundle,
            String profileId,
            KeeperInfo senderInfo,
            KeeperInfo receiverInfo
    ) {
        RecordEntity record = new RecordEntity();
        record.id = IdUtil.randomId();
        record.profileId = profileId;
        record.type = RecordType.TRANSFER;
        record.title = context.getString(R.string.record_transfer);
        record.timestamp = System.currentTimeMillis();
        if (senderInfo != null) {
            record.transferFromPerson = senderInfo.nickname;
            record.transferFromPlace = senderInfo.homePlace;
        }
        if (receiverInfo != null) {
            record.transferToPerson = receiverInfo.nickname;
            record.transferToPlace = receiverInfo.homePlace;
        }
        bundle.records.add(record);
    }

    private void appendDescendantArchiveRecord(ExportBundle bundle, String profileId) {
        RecordEntity record = new RecordEntity();
        record.id = IdUtil.randomId();
        record.profileId = profileId;
        record.type = RecordType.ARCHIVE;
        record.archiveReason = "TRANSFER";
        record.title = context.getString(R.string.record_archive_transfer);
        record.timestamp = System.currentTimeMillis();
        bundle.records.add(record);
    }

    private boolean hasArchiveRecord(ExportBundle bundle, String profileId) {
        for (RecordEntity record : bundle.records) {
            if (profileId.equals(IdUtil.normalizeId(record.profileId))
                    && RecordType.ARCHIVE.equals(record.type)) {
                return true;
            }
        }
        return false;
    }

    private ExportBundle exportProfiles(Set<String> profileIds) {
        ExportBundle bundle = new ExportBundle();
        if (profileIds.isEmpty()) {
            return bundle;
        }
        ProfileDao profileDao = database.profileDao();
        RecordDao recordDao = database.recordDao();
        List<String> ids = new ArrayList<>(profileIds);
        bundle.profiles.addAll(profileDao.getByIds(ids));
        for (ProfileEntity profile : bundle.profiles) {
            bundle.customFields.addAll(profileDao.getCustomFields(profile.id));
            bundle.parentLinks.addAll(profileDao.getParentLinks(profile.id));
            List<RecordEntity> records = recordDao.getRecordsForProfileOldestFirst(profile.id);
            bundle.records.addAll(records);
            List<String> recordIds = new ArrayList<>();
            for (RecordEntity record : records) {
                recordIds.add(record.id);
            }
            if (!recordIds.isEmpty()) {
                bundle.recordFields.addAll(recordDao.getFieldsForRecords(recordIds));
                bundle.recordImages.addAll(recordDao.getImagesForRecords(recordIds));
            }
        }
        return bundle;
    }

    private void validateParents(String childId, List<String> parentIds) {
        if (parentIds == null) {
            return;
        }
        Set<String> unique = new HashSet<>();
        ProfileDao dao = database.profileDao();
        ProfileEntity child = dao.getById(childId);
        if (child == null) {
            return;
        }
        for (String parentId : parentIds) {
            if (parentId == null || parentId.trim().isEmpty()) {
                continue;
            }
            parentId = IdUtil.normalizeId(parentId);
            if (!unique.add(parentId)) {
                continue;
            }
            if (parentId.equals(childId)) {
                throw new IllegalStateException("Self parent is not allowed.");
            }
            ProfileEntity parent = dao.getById(parentId);
            if (parent == null) {
                continue;
            }
            if (!TaxonomyUtil.sameMajorTaxonomy(child, parent)) {
                throw new IllegalStateException("Major taxonomy mismatch.");
            }
            if (parent.createdAt >= child.createdAt) {
                throw new IllegalStateException("Parent must be established before child.");
            }
            Set<String> descendants = new HashSet<>(collectDescendants(childId));
            if (descendants.contains(parentId)) {
                throw new IllegalStateException("Parent causes family tree cycle.");
            }
        }
    }

    private void validateParentGenders(String fatherId, String motherId) {
        ProfileDao dao = database.profileDao();
        if (fatherId != null && !fatherId.trim().isEmpty()) {
            ProfileEntity father = dao.getById(IdUtil.normalizeId(fatherId));
            if (father != null && !"MALE".equals(father.gender)) {
                throw new IllegalStateException("Father must be male.");
            }
        }
        if (motherId != null && !motherId.trim().isEmpty()) {
            ProfileEntity mother = dao.getById(IdUtil.normalizeId(motherId));
            if (mother != null && !"FEMALE".equals(mother.gender)) {
                throw new IllegalStateException("Mother must be female.");
            }
        }
    }

    private void applyGenderRelationCleanup(ProfileDao profileDao, String profileId, String oldGender, String newGender) {
        if (oldGender == null) {
            oldGender = "UNKNOWN";
        }
        if (newGender == null) {
            newGender = "UNKNOWN";
        }
        if (oldGender.equals(newGender)) {
            return;
        }
        if ("MALE".equals(newGender)) {
            profileDao.deleteParentLinksByRole(profileId, "MOTHER");
        } else if ("FEMALE".equals(newGender)) {
            profileDao.deleteParentLinksByRole(profileId, "FATHER");
        } else {
            profileDao.deleteParentLinksByParent(profileId);
        }
    }

    private void validateRecord(RecordEntity record) {
        if (record == null || record.profileId == null || record.type == null) {
            throw new IllegalArgumentException("Invalid record.");
        }
        RecordDao dao = database.recordDao();
        List<RecordEntity> others = new ArrayList<>(dao.getRecordsForProfile(record.profileId));
        others.removeIf(item -> item.id != null && item.id.equals(record.id));

        RecordEntity establishment = null;
        RecordEntity archive = null;
        Long minOther = null;
        Long maxOther = null;
        for (RecordEntity item : others) {
            if (RecordType.ESTABLISHMENT.equals(item.type)) {
                establishment = item;
            }
            if (RecordType.ARCHIVE.equals(item.type)) {
                archive = item;
            }
            minOther = min(minOther, item.timestamp);
            maxOther = max(maxOther, item.timestamp);
        }

        if (RecordType.ESTABLISHMENT.equals(record.type)) {
            if (establishment != null) {
                throw new IllegalStateException("Only one establishment record allowed.");
            }
            if (minOther != null && record.timestamp > minOther) {
                throw new IllegalStateException("Establishment record must be earliest.");
            }
        } else {
            if (establishment != null && record.timestamp < establishment.timestamp) {
                throw new IllegalStateException("Record cannot be earlier than establishment.");
            }
            if (archive != null && record.timestamp > archive.timestamp) {
                throw new IllegalStateException("Record cannot be later than archive.");
            }
        }

        if (RecordType.ARCHIVE.equals(record.type)) {
            if (archive != null) {
                throw new IllegalStateException("Only one archive record allowed.");
            }
            if (maxOther != null && record.timestamp < maxOther) {
                throw new IllegalStateException("Archive record must be latest.");
            }
        } else {
            if (archive != null && record.timestamp > archive.timestamp) {
                throw new IllegalStateException("Record cannot be later than archive.");
            }
        }
    }

    private void replaceCustomFields(ProfileDao dao, String profileId, List<ProfileCustomFieldEntity> fields) {
        dao.deleteCustomFields(profileId);
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<ProfileCustomFieldEntity> normalized = new ArrayList<>();
        int position = 0;
        for (ProfileCustomFieldEntity field : fields) {
            if (field == null || field.fieldName == null || field.fieldName.trim().isEmpty()) {
                continue;
            }
            field.profileId = profileId;
            field.fieldKey = safeKey(field.fieldKey, field.fieldName);
            field.fieldName = field.fieldName.trim();
            if (field.fieldType == null || field.fieldType.trim().isEmpty()) {
                field.fieldType = field.numericValue != null ? FieldType.NUMBER : FieldType.TEXT;
            }
            field.position = position++;
            normalized.add(field);
        }
        if (!normalized.isEmpty()) {
            dao.insertCustomFields(normalized);
        }
    }

    private void replaceParents(ProfileDao dao, String childId, String fatherId, String motherId) {
        dao.deleteParents(childId);
        List<ProfileParentCrossRef> links = new ArrayList<>();
        addParentLink(dao, links, childId, fatherId, "FATHER");
        addParentLink(dao, links, childId, motherId, "MOTHER");
        if (!links.isEmpty()) {
            dao.insertParents(links);
        }
    }

    private void addParentLink(ProfileDao dao, List<ProfileParentCrossRef> links, String childId, String parentId, String role) {
        if (parentId == null || parentId.trim().isEmpty()) {
            return;
        }
        String normalized = IdUtil.normalizeId(parentId);
        if (dao.getById(normalized) == null) {
            return;
        }
        ProfileParentCrossRef link = new ProfileParentCrossRef();
        link.childId = childId;
        link.parentId = normalized;
        link.role = role;
        links.add(link);
    }

    private List<String> nonEmptyParents(String fatherId, String motherId) {
        List<String> parents = new ArrayList<>();
        if (fatherId != null && !fatherId.trim().isEmpty()) {
            parents.add(IdUtil.normalizeId(fatherId));
        }
        if (motherId != null && !motherId.trim().isEmpty()) {
            parents.add(IdUtil.normalizeId(motherId));
        }
        return parents;
    }

    private void replaceRecordFields(RecordDao dao, String recordId, List<RecordFieldEntity> fields) {
        dao.deleteFields(recordId);
        if (fields == null || fields.isEmpty()) {
            return;
        }
        List<RecordFieldEntity> normalized = new ArrayList<>();
        int position = 0;
        for (RecordFieldEntity field : fields) {
            if (field == null || field.fieldName == null || field.fieldName.trim().isEmpty()) {
                continue;
            }
            field.recordId = recordId;
            field.fieldKey = safeKey(field.fieldKey, field.fieldName);
            field.fieldName = field.fieldName.trim();
            if (field.fieldType == null || field.fieldType.trim().isEmpty()) {
                field.fieldType = field.numericValue != null ? FieldType.NUMBER : FieldType.TAG;
            }
            field.position = position++;
            normalized.add(field);
        }
        if (!normalized.isEmpty()) {
            dao.insertFields(normalized);
        }
    }

    private void replaceRecordImages(RecordDao dao, String recordId, List<RecordImageEntity> images) {
        dao.deleteImages(recordId);
        if (images == null || images.isEmpty()) {
            return;
        }
        List<RecordImageEntity> normalized = new ArrayList<>();
        int position = 0;
        for (RecordImageEntity image : images) {
            if (image == null || image.uri == null || image.uri.trim().isEmpty()) {
                continue;
            }
            if (image.id == null || image.id.trim().isEmpty()) {
                image.id = IdUtil.randomId();
            } else {
                image.id = IdUtil.normalizeId(image.id);
            }
            image.recordId = recordId;
            image.position = position++;
            normalized.add(image);
        }
        if (!normalized.isEmpty()) {
            dao.insertImages(normalized);
        }
    }

    private void syncArchiveStatus(ProfileDao profileDao, RecordDao recordDao, String profileId) {
        ProfileEntity profile = profileDao.getById(profileId);
        if (profile == null) {
            return;
        }
        RecordEntity archive = recordDao.getFirstByType(profileId, RecordType.ARCHIVE);
        profile.archivedAt = archive == null ? null : archive.timestamp;
        profileDao.updateProfile(profile);
    }

    private String safeKey(String key, String fallback) {
        if (key != null && !key.trim().isEmpty()) {
            return key.trim().toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9_\\u4e00-\\u9fff]+", "_");
        }
        return fallback.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_\\u4e00-\\u9fff]+", "_");
    }

    private List<String> collectAncestors(String profileId) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(profileId);
        visited.add(profileId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> parents = database.profileDao().getParentIds(current);
            for (String parent : parents) {
                if (visited.add(parent)) {
                    result.add(parent);
                    queue.add(parent);
                }
            }
        }
        return result;
    }

    private List<String> collectDescendants(String profileId) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(profileId);
        visited.add(profileId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> children = database.profileDao().getChildIds(current);
            for (String child : children) {
                if (visited.add(child)) {
                    result.add(child);
                    queue.add(child);
                }
            }
        }
        return result;
    }

    private Long min(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private Long max(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }
}
