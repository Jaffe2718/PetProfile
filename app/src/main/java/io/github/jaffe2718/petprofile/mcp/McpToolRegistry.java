package io.github.jaffe2718.petprofile.mcp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.data.FieldType;
import io.github.jaffe2718.petprofile.data.KeeperInfo;
import io.github.jaffe2718.petprofile.data.RecordType;
import io.github.jaffe2718.petprofile.data.dao.ProfileDao;
import io.github.jaffe2718.petprofile.data.dao.RecordDao;
import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.repository.PetRepository;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.BackupManager;
import io.github.jaffe2718.petprofile.util.IdUtil;
import io.github.jaffe2718.petprofile.util.ImageStorage;
import io.github.jaffe2718.petprofile.util.KeeperInfoManager;
import io.github.jaffe2718.petprofile.util.OneDriveBackupManager;
import io.github.jaffe2718.petprofile.util.RoutineNotifier;
import io.github.jaffe2718.petprofile.util.RoutineScheduler;
import io.github.jaffe2718.petprofile.util.RoutineTodoMath;
import io.github.jaffe2718.petprofile.util.TaxonomyUtil;

/**
 * Registry of the MCP tools exposed by the PetProfile server. Each tool maps a JSON-RPC
 * {@code tools/call} to the Room data layer (reads) or to {@link PetRepository} (writes that must
 * honour the app's record-order / parent / archive invariants).
 */
public final class McpToolRegistry {
    private final Context context;
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public McpToolRegistry(Context context) {
        this.context = context.getApplicationContext();
        registerAll();
    }

    private void registerAll() {
        register(tool("list_profiles",
                "List all profiles (individuals).",
                obj("status", str("ACTIVE or ARCHIVED or ALL (default ALL)")),
                this::listProfiles));
        register(tool("search_profiles",
                "Search profiles by taxonomy, gender, status, or keyword.",
                obj("keyword", str("match species or nickname"),
                        "gender", str("MALE or FEMALE or UNKNOWN"),
                        "status", str("ACTIVE or ARCHIVED")),
                this::searchProfiles));
        register(tool("get_profile",
                "Get full details of a single profile including custom fields and parents.",
                obj("profileId", str("required")),
                this::getProfile));
        register(tool("get_profile_family",
                "Get the pedigree (ancestors/descendants) of a profile.",
                obj("profileId", str("required"),
                        "direction", str("ANCESTORS, DESCENDANTS or BOTH (default BOTH)"),
                        "depth", num("max depth, optional")),
                this::getProfileFamily));
        register(tool("list_records",
                "List records for a profile (or all), optionally filtered by type/date range.",
                obj("profileId", str("optional"),
                        "type", str("ESTABLISHMENT, DAILY, TRANSFER or ARCHIVE"),
                        "from", num("millis, optional"),
                        "to", num("millis, optional")),
                this::listRecords));
        register(tool("get_record",
                "Get a single record with its custom fields and images.",
                obj("recordId", str("required")),
                this::getRecord));
        register(tool("get_record_timeseries",
                "Get numeric values of a custom field over time for charting.",
                obj("profileId", str("required"),
                        "fieldKey", str("required")),
                this::getRecordTimeseries));
        register(tool("list_routines",
                "List routine reminders for a profile (or all).",
                obj("profileId", str("optional")),
                this::listRoutines));
        register(tool("get_daily_todo",
                "Get today's todo items derived from the routine reminders.",
                obj("profileId", str("optional")),
                this::getDailyTodo));
        register(tool("get_keeper_info",
                "Get the keeper (breeder) nickname and home place.",
                obj(),
                this::getKeeperInfo));
        register(tool("get_stats",
                "Get overall counts: profiles, records, routines, archived.",
                obj(),
                this::getStats));
        register(tool("export_json",
                "Export the whole database (or a single profile tree) as JSON for sharing.",
                obj("profileId", str("optional")),
                this::exportJson));
        register(tool("get_app_version",
                "Get the installed application version (versionName/versionCode) and server info.",
                obj(),
                this::getAppVersion));

        register(writeTool("create_profile",
                "Create a new profile with an establishment record. For the avatar, base64-encode the file with a short local script (e.g. base64 -w0) and pass avatarData (with avatarExtension), rather than inlining large raw payloads in the conversation.",
                obj("taxonomy", obj("kingdom", str(), "phylum", str(), "taxClass", str(),
                        "taxOrder", str(), "family", str(), "genus", str(), "species", str(), "subspecies", str()),
                        "gender", str("MALE/FEMALE/UNKNOWN"),
                        "avatarUri", str("optional"),
                        "avatarData", str("base64 image, optional"),
                        "avatarExtension", str("optional, e.g. png/jpg"),
                        "nickname", str("optional"),
                        "fatherId", str("optional"), "motherId", str("optional"),
                        "establishedAt", num("millis"),
                        "source", str("ESTABLISHMENT reason: BREED/WILD/BUY")),
                this::createProfile));
        register(writeTool("update_profile",
                "Update an existing profile. Omitted fields keep their current value. To change the avatar, pass avatarUri or base64 avatarData (with avatarExtension, produced by a short local script).",
                obj("profileId", str("required"),
                        "taxonomy", obj("kingdom", str(), "phylum", str(), "taxClass", str(),
                                "taxOrder", str(), "family", str(), "genus", str(), "species", str(), "subspecies", str()),
                        "gender", str("MALE/FEMALE/UNKNOWN"),
                        "avatarUri", str("optional"),
                        "avatarData", str("base64 image, optional"),
                        "avatarExtension", str("optional, e.g. png/jpg"),
                        "nickname", str("optional"),
                        "fatherId", str("optional"), "motherId", str("optional")),
                this::updateProfile));
        register(writeTool("delete_profile",
                "Delete a profile and all its records/fields/images/routines.",
                obj("profileId", str("required")),
                this::deleteProfile));
        register(writeTool("set_profile_parents",
                "Set the father/mother of a profile (validates gender, taxonomy and cycles).",
                obj("profileId", str("required"),
                        "fatherId", str("optional"), "motherId", str("optional")),
                this::setProfileParents));
        register(writeTool("set_profile_custom_fields",
                "Replace the custom fields of a profile.",
                obj("profileId", str("required"),
                        "fields", arr() ),
                this::setProfileCustomFields));
        register(writeTool("create_record",
                "Create a record (DAILY/TRANSFER/ARCHIVE) on a profile. images[] entries are a content/file uri or base64 data (with extension/mimeType); base64-encode local files with a short script and pass data, or embed ![alt](data:image/...;base64,....) in notesMarkdown, instead of inlining huge payloads.",
                obj("profileId", str("required"),
                        "title", str("required"),
                        "type", str("DAILY, TRANSFER or ARCHIVE"),
                        "timestamp", num("millis"),
                        "locationName", str(), "latitude", num(), "longitude", num(),
                        "notesMarkdown", str(),
                        "keeperName", str(),
                        "archiveReason", str(), "transferFromPerson", str(), "transferToPerson", str(),
                        "transferFromPlace", str(), "transferToPlace", str(),
                        "fields", arr(),
                        "images", arr()),
                this::createRecord));
        register(writeTool("update_record",
                "Update an existing record. Omitted fields keep their current value. images[] can add/replace (imagesMode) or removeImages removes by id (read ids via get_record); base64-encode local files with a short script and pass data, or embed ![alt](data:image/...;base64,....) in notesMarkdown, instead of inlining huge payloads.",
                obj("recordId", str("required"),
                        "title", str(), "timestamp", num(),
                        "locationName", str(), "latitude", num(), "longitude", num(),
                        "notesMarkdown", str(), "keeperName", str(),
                        "archiveReason", str(), "transferFromPerson", str(), "transferToPerson", str(),
                        "transferFromPlace", str(), "transferToPlace", str(),
                        "fields", arr(),
                        "images", arr(),
                        "imagesMode", str("append or replace (default replace)"),
                        "removeImages", arr("image ids to remove; read them via get_record")),
                this::updateRecord));
        register(writeTool("delete_record",
                "Delete a non-establishment record.",
                obj("recordId", str("required")),
                this::deleteRecord));
        register(writeTool("create_routine",
                "Create a routine reminder on a profile.",
                obj("profileId", str("required"),
                        "title", str("required"),
                        "type", str("WEEKLY or ONCE (default WEEKLY)"),
                        "weekdays", arr("Sun=0..Sat=6 integers"),
                        "hour", num(), "minute", num(), "second", num(),
                        "policy", str("SKIP or CARRY (default SKIP)"),
                        "onceAt", num("millis, for ONCE"),
                        "details", str()),
                this::createRoutine));
        register(writeTool("update_routine",
                "Update a routine reminder. Omitted fields keep their current value.",
                obj("routineId", str("required"),
                        "title", str(), "type", str("WEEKLY or ONCE"),
                        "weekdays", arr("Sun=0..Sat=6 integers"),
                        "hour", num(), "minute", num(), "second", num(),
                        "policy", str("SKIP or CARRY"), "onceAt", num("millis"),
                        "enabled", bool(), "details", str()),
                this::updateRoutine));
        register(writeTool("delete_routine",
                "Delete a routine reminder.",
                obj("routineId", str("required")),
                this::deleteRoutine));
        register(writeTool("complete_routine",
                "Mark a routine occurrence completed or not completed. Defaults to completed (true).",
                obj("routineId", str("required"),
                        "completed", bool("true = mark done (default), false = mark not done")),
                this::completeRoutine));
        register(writeTool("save_keeper_info",
                "Save the keeper (breeder) nickname and home place.",
                obj("nickname", str(), "homePlace", str(), "latitude", num(), "longitude", num()),
                this::saveKeeperInfo));
        register(writeTool("import_zip",
                "Import a backup ZIP, given by \"uri\" (content:/file:) or \"data\" (base64), restoring the whole database. To import a large local ZIP, base64-encode and POST it with a short script rather than inlining the payload here.",
                obj("uri", str("optional"), "data", str("base64 ZIP, optional")),
                this::importZip));
        register(tool("export_zip",
                "Export a backup ZIP. Pass targetUri to write it on the device, or omit to receive base64 \"data\". To save the ZIP on the computer without flooding the conversation, have a local script call this endpoint and decode the returned base64 to a file.",
                obj("profileId", str("optional, single profile tree"),
                        "targetUri", str("optional, Android path to write to")),
                this::exportZip));

        // OneDrive cloud backup: upload/download require the user to have signed in to OneDrive
        // on the device (Keeper Info). No login tool is exposed here.
        register(tool("is_onedrive_connected",
                "Check whether the user has signed in to OneDrive. If false, ask the user to sign in on the device (饲养者信息 → 登录 OneDrive).",
                obj(),
                this::isOneDriveConnected));
        register(writeTool("upload_to_onedrive",
                "Back up the whole database to OneDrive. Requires the user to be signed in to OneDrive. Returns immediately; poll get_onedrive_result for the outcome.",
                obj(),
                this::uploadOneDrive));
        register(writeTool("download_onedrive",
                "Restore from the OneDrive backup. Incremental and resumable: data.json is imported first, then only the missing/changed images are transferred (unchanged files are skipped by name + size + SHA-1), so a partial run is simply completed by the next call. The backup wins wherever it and the device both have the same entry, while records, routines, fields, images and parent links that exist only locally (added after the last upload) are merged back instead of being dropped; an image that has not arrived yet is stored as an empty reference and renders as nothing. Returns immediately; poll get_onedrive_result for the outcome.",
                obj(),
                this::downloadOneDrive));
        register(tool("get_onedrive_result",
                "Get the outcome of the most recent upload_to_onedrive / download_onedrive call.",
                obj(),
                this::getOneDriveResult));
    }

    // ----- MCP list / call -----

    public JsonArray toMcpList() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            array.add(tool.toJson());
        }
        return array;
    }

    public JsonObject call(JsonObject request) {
        JsonObject params = request.has("params") && !request.get("params").isJsonNull()
                ? request.getAsJsonObject("params") : new JsonObject();
        JsonObject arguments = params.has("arguments") && !params.get("arguments").isJsonNull()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        String name = params.has("name") && !params.get("name").isJsonNull()
                ? params.get("name").getAsString() : null;
        McpTool tool = name == null ? null : tools.get(name);
        if (tool == null) {
            return toolCallError("Unknown tool: " + name);
        }
        try {
            JsonElement data = tool.handler.handle(arguments, context);
            if (tool.write) {
                notifyDataChanged();
            }
            JsonObject result = new JsonObject();
            JsonArray content = new JsonArray();
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", data.toString());
            content.add(text);
            result.add("content", content);
            result.addProperty("isError", false);
            return result;
        } catch (McpToolException e) {
            return toolCallError(e.getMessage());
        } catch (Throwable t) {
            return toolCallError("Tool failed: " + t.getMessage());
        }
    }

    private static JsonObject toolCallError(String message) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", message);
        content.add(text);
        result.add("content", content);
        result.addProperty("isError", true);
        return result;
    }

    /**
     * Fires a package-scoped broadcast after a successful write so that foreground screens reload
     * their data. The broadcast is received only by receivers registered while an activity is in
     * the foreground (onStart..onStop), so background refresh never happens.
     */
    private void notifyDataChanged() {
        try {
            Intent intent = new Intent(McpServer.ACTION_DATA_CHANGED);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    // ----- tool helpers -----

    private interface ToolHandler {
        JsonElement handle(JsonObject args, Context context) throws McpToolException;
    }

    private static final class McpTool {
        final String name;
        final String description;
        final JsonObject inputSchema;
        final ToolHandler handler;
        final boolean write;

        McpTool(String name, String description, JsonObject inputSchema, ToolHandler handler, boolean write) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.handler = handler;
            this.write = write;
        }

        JsonObject toJson() {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", name);
            tool.addProperty("description", description);
            tool.add("inputSchema", inputSchema);
            return tool;
        }
    }

    private static final class McpToolException extends Exception {
        McpToolException(String message) {
            super(message);
        }
    }

    private void register(McpTool tool) {
        tools.put(tool.name, tool);
    }

    private McpTool tool(String name, String description, JsonObject inputSchema, ToolHandler handler) {
        return new McpTool(name, description, inputSchema, handler, false);
    }

    private McpTool writeTool(String name, String description, JsonObject inputSchema, ToolHandler handler) {
        return new McpTool(name, description, inputSchema, handler, true);
    }

    // ----- JSON schema builders -----

    private static JsonObject schemaProperty(String type, String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        if (description != null) {
            property.addProperty("description", description);
        }
        return property;
    }

    private static JsonObject obj(Object... keyValues) {
        JsonObject properties = new JsonObject();
        String key = null;
        for (Object item : keyValues) {
            if (key == null) {
                key = (String) item;
            } else {
                if (item instanceof JsonObject) {
                    properties.add(key, (JsonObject) item);
                } else if (item instanceof JsonArray) {
                    properties.add(key, (JsonArray) item);
                } else if (item instanceof String) {
                    properties.add(key, schemaProperty("string", (String) item));
                } else if (item instanceof Boolean) {
                    properties.add(key, schemaProperty("boolean", null));
                } else {
                    properties.add(key, schemaProperty("string", null));
                }
                key = null;
            }
        }
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        schema.add("required", required);
        return schema;
    }

    private static JsonObject arr() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "object");
        schema.add("items", items);
        return schema;
    }

    private static JsonObject arr(String description) {
        JsonObject schema = arr();
        schema.addProperty("description", description);
        return schema;
    }

    private static JsonObject str(String description) {
        return schemaProperty("string", description);
    }

    private static JsonObject str() {
        return schemaProperty("string", null);
    }

    private static JsonObject num(String description) {
        return schemaProperty("number", description);
    }

    private static JsonObject num() {
        return schemaProperty("number", null);
    }

    private static JsonObject bool(String description) {
        return schemaProperty("boolean", description);
    }

    private static JsonObject bool() {
        return schemaProperty("boolean", null);
    }

    // ----- param readers -----

    private static String str(JsonObject object, String key) {
        JsonElement el = object.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return el.getAsString();
    }

    private static Long lng(JsonObject object, String key) {
        JsonElement el = object.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsLong();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double dbl(JsonObject object, String key) {
        JsonElement el = object.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsDouble();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Boolean bool(JsonObject object, String key) {
        JsonElement el = object.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        try {
            return el.getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ----- READ handlers -----

    private JsonElement listProfiles(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        List<ProfileEntity> profiles = dao.getAllProfilesOldestFirst();
        String status = str(args, "status");
        JsonArray array = new JsonArray();
        for (ProfileEntity profile : profiles) {
            if ("ACTIVE".equalsIgnoreCase(status) && profile.isArchived()) {
                continue;
            }
            if ("ARCHIVED".equalsIgnoreCase(status) && !profile.isArchived()) {
                continue;
            }
            array.add(profileJson(dao, ctx, profile, false));
        }
        return array;
    }

    private JsonElement searchProfiles(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        List<ProfileEntity> profiles = dao.getAllProfilesOldestFirst();
        String keyword = str(args, "keyword");
        String gender = str(args, "gender");
        String status = str(args, "status");
        String wantsKingdom = str(args, "kingdom");
        String wantsFamily = str(args, "family");
        String wantsGenus = str(args, "genus");
        String wantsSpecies = str(args, "species");
        JsonArray array = new JsonArray();
        for (ProfileEntity profile : profiles) {
            if ("ACTIVE".equalsIgnoreCase(status) && profile.isArchived()) {
                continue;
            }
            if ("ARCHIVED".equalsIgnoreCase(status) && !profile.isArchived()) {
                continue;
            }
            if (gender != null && !gender.trim().isEmpty()
                    && !gender.equalsIgnoreCase(profile.gender)) {
                continue;
            }
            if (wantsKingdom != null && !wantsKingdom.trim().isEmpty()
                    && !wantsKingdom.trim().equalsIgnoreCase(profile.kingdom)) {
                continue;
            }
            if (wantsFamily != null && !wantsFamily.trim().isEmpty()
                    && !wantsFamily.trim().equalsIgnoreCase(profile.family)) {
                continue;
            }
            if (wantsGenus != null && !wantsGenus.trim().isEmpty()
                    && !wantsGenus.trim().equalsIgnoreCase(profile.genus)) {
                continue;
            }
            if (wantsSpecies != null && !wantsSpecies.trim().isEmpty()
                    && !wantsSpecies.trim().equalsIgnoreCase(profile.species)) {
                continue;
            }
            if (keyword != null && !keyword.trim().isEmpty()) {
                String nickname = nickname(dao, profile);
                String display = TaxonomyUtil.speciesDisplay(profile);
                if (!contains(display, keyword) && !contains(nickname, keyword)
                        && !contains(profile.id, keyword)) {
                    continue;
                }
            }
            array.add(profileJson(dao, ctx, profile, false));
        }
        return array;
    }

    private JsonElement getProfile(JsonObject args, Context ctx) throws McpToolException {
        String profileId = str(args, "profileId");
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        ProfileEntity profile = requireProfile(dao, profileId);
        return profileJson(dao, ctx, profile, true);
    }

    private JsonElement getProfileFamily(JsonObject args, Context ctx) throws McpToolException {
        String profileId = str(args, "profileId");
        String direction = str(args, "direction");
        Long depth = lng(args, "depth");
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        ProfileEntity root = requireProfile(dao, profileId);
        boolean includeAncestors = direction == null || "BOTH".equalsIgnoreCase(direction)
                || "ANCESTORS".equalsIgnoreCase(direction);
        boolean includeDescendants = direction == null || "BOTH".equalsIgnoreCase(direction)
                || "DESCENDANTS".equalsIgnoreCase(direction);

        JsonObject result = new JsonObject();
        result.add("root", basicProfile(root));
        JsonArray nodes = new JsonArray();
        nodes.add(basicProfile(root));
        JsonArray edges = new JsonArray();
        java.util.Set<String> visited = new java.util.HashSet<>();
        visited.add(profileId);
        int maxDepth = depth == null ? Integer.MAX_VALUE : depth.intValue();

        if (includeAncestors) {
            collectFamily(dao, ctx, profileId, true, 0, maxDepth, visited, nodes, edges);
        }
        if (includeDescendants) {
            collectFamily(dao, ctx, profileId, false, 0, maxDepth, visited, nodes, edges);
        }
        result.add("nodes", nodes);
        result.add("edges", edges);
        return result;
    }

    private void collectFamily(ProfileDao dao, Context ctx, String id, boolean ancestors,
                               int level, int maxDepth, java.util.Set<String> visited,
                               JsonArray nodes, JsonArray edges) {
        if (level >= maxDepth) {
            return;
        }
        List<String> related = ancestors ? dao.getParentIds(id) : dao.getChildIds(id);
        for (String relatedId : related) {
            ProfileEntity entity = dao.getById(relatedId);
            if (entity == null) {
                continue;
            }
            JsonObject edge = new JsonObject();
            if (ancestors) {
                edge.addProperty("from", relatedId);
                edge.addProperty("to", id);
            } else {
                edge.addProperty("from", id);
                edge.addProperty("to", relatedId);
            }
            edges.add(edge);
            if (visited.add(relatedId)) {
                nodes.add(basicProfile(entity));
                collectFamily(dao, ctx, relatedId, ancestors, level + 1, maxDepth, visited, nodes, edges);
            }
        }
    }

    private JsonElement listRecords(JsonObject args, Context ctx) throws McpToolException {
        RecordDao dao = AppDatabase.getInstance(ctx).recordDao();
        ProfileDao profileDao = AppDatabase.getInstance(ctx).profileDao();
        String profileId = str(args, "profileId");
        String type = str(args, "type");
        Long from = lng(args, "from");
        Long to = lng(args, "to");
        List<RecordEntity> records = profileId == null || profileId.trim().isEmpty()
                ? allRecords(dao) : dao.getRecordsForProfileOldestFirst(profileId);
        JsonArray array = new JsonArray();
        for (RecordEntity record : records) {
            if (type != null && !type.trim().isEmpty() && !type.equals(record.type)) {
                continue;
            }
            if (from != null && record.timestamp < from) {
                continue;
            }
            if (to != null && record.timestamp > to) {
                continue;
            }
            array.add(recordJson(dao, record));
        }
        return array;
    }

    private List<RecordEntity> allRecords(RecordDao dao) {
        List<RecordEntity> all = new ArrayList<>();
        for (ProfileEntity profile : AppDatabase.getInstance(context).profileDao().getAllProfiles()) {
            all.addAll(dao.getRecordsForProfileOldestFirst(profile.id));
        }
        return all;
    }

    private JsonElement getRecord(JsonObject args, Context ctx) throws McpToolException {
        RecordDao dao = AppDatabase.getInstance(ctx).recordDao();
        String recordId = str(args, "recordId");
        RecordEntity record = dao.getById(recordId);
        if (record == null) {
            throw new McpToolException("Record not found: " + recordId);
        }
        JsonObject result = recordJson(dao, record);
        JsonArray fields = new JsonArray();
        for (RecordFieldEntity field : dao.getFields(recordId)) {
            fields.add(recordFieldJson(field));
        }
        JsonArray images = new JsonArray();
        for (RecordImageEntity image : dao.getImages(recordId)) {
            JsonObject img = new JsonObject();
            img.addProperty("id", image.id);
            img.addProperty("uri", image.uri);
            images.add(img);
        }
        result.add("fields", fields);
        result.add("images", images);
        return result;
    }

    private JsonElement getRecordTimeseries(JsonObject args, Context ctx) throws McpToolException {
        String profileId = str(args, "profileId");
        String fieldKey = str(args, "fieldKey");
        if (fieldKey == null || fieldKey.trim().isEmpty()) {
            throw new McpToolException("fieldKey is required");
        }
        RecordDao dao = AppDatabase.getInstance(ctx).recordDao();
        List<RecordEntity> records = dao.getRecordsForProfileOldestFirst(profileId);
        List<String> recordIds = new ArrayList<>();
        Map<String, Long> timestampById = new LinkedHashMap<>();
        for (RecordEntity record : records) {
            recordIds.add(record.id);
            timestampById.put(record.id, record.timestamp);
        }
        JsonArray array = new JsonArray();
        if (recordIds.isEmpty()) {
            return array;
        }
        List<RecordFieldEntity> fields = dao.getFieldsForRecords(recordIds);
        for (RecordFieldEntity field : fields) {
            if (!FieldType.NUMBER.equals(field.fieldType) || field.numericValue == null) {
                continue;
            }
            if (!fieldKey.equals(field.fieldKey)) {
                continue;
            }
            Long time = timestampById.get(field.recordId);
            if (time == null) {
                continue;
            }
            JsonObject point = new JsonObject();
            point.addProperty("timestamp", time);
            point.addProperty("value", field.numericValue);
            if (field.unit != null) {
                point.addProperty("unit", field.unit);
            }
            array.add(point);
        }
        return array;
    }

    private JsonElement listRoutines(JsonObject args, Context ctx) throws McpToolException {
        String profileId = str(args, "profileId");
        List<RoutineEntity> routines = profileId == null || profileId.trim().isEmpty()
                ? AppDatabase.getInstance(ctx).routineDao().getAllRoutines()
                : AppDatabase.getInstance(ctx).routineDao().getRoutinesForProfile(profileId);
        JsonArray array = new JsonArray();
        for (RoutineEntity routine : routines) {
            array.add(routineJson(routine));
        }
        return array;
    }

    private JsonElement getDailyTodo(JsonObject args, Context ctx) throws McpToolException {
        String profileId = str(args, "profileId");
        long now = System.currentTimeMillis();
        long todayStart = RoutineTodoMath.startOfToday();
        List<RoutineEntity> routines = profileId == null || profileId.trim().isEmpty()
                ? AppDatabase.getInstance(ctx).routineDao().getAllRoutines()
                : AppDatabase.getInstance(ctx).routineDao().getRoutinesForProfile(profileId);
        JsonArray array = new JsonArray();
        for (RoutineEntity routine : routines) {
            if (!routine.enabled) {
                continue;
            }
            ProfileEntity profile = AppDatabase.getInstance(ctx).profileDao().getById(routine.profileId);
            if (profile == null || profile.isArchived()) {
                continue;
            }
            Long dueToday = RoutineTodoMath.computeDueToday(routine, now);
            if (!RoutineTodoMath.isRelevant(routine, dueToday, now, todayStart)) {
                continue;
            }
            JsonObject item = new JsonObject();
            item.addProperty("routineId", routine.id);
            item.addProperty("profileId", profile.id);
            item.addProperty("title", routine.title);
            if (routine.details != null) {
                item.addProperty("details", routine.details);
            }
            Long dueTime = dueToday != null ? dueToday : routine.lastInteractionTime;
            if (dueTime != 0) {
                item.addProperty("dueAt", dueTime);
            }
            item.addProperty("done", routine.completed);
            item.addProperty("policy", routine.policy);
            array.add(item);
        }
        return array;
    }

    private JsonElement getKeeperInfo(JsonObject args, Context ctx) {
        KeeperInfo info = KeeperInfoManager.load(ctx);
        JsonObject result = new JsonObject();
        result.addProperty("nickname", info.nickname);
        result.addProperty("homePlace", info.homePlace);
        if (info.latitude != null) {
            result.addProperty("latitude", info.latitude);
        }
        if (info.longitude != null) {
            result.addProperty("longitude", info.longitude);
        }
        return result;
    }

    private JsonElement getStats(JsonObject args, Context ctx) {
        ProfileDao profileDao = AppDatabase.getInstance(ctx).profileDao();
        RecordDao recordDao = AppDatabase.getInstance(ctx).recordDao();
        List<ProfileEntity> profiles = profileDao.getAllProfiles();
        int archived = 0;
        int records = 0;
        for (ProfileEntity profile : profiles) {
            if (profile.isArchived()) {
                archived++;
            }
            records += recordDao.getRecordsForProfile(profile.id).size();
        }
        JsonObject result = new JsonObject();
        result.addProperty("profiles", profiles.size());
        result.addProperty("archived", archived);
        result.addProperty("records", records);
        result.addProperty("routines", AppDatabase.getInstance(ctx).routineDao().getAllRoutines().size());
        result.addProperty("keeperNickname", KeeperInfoManager.load(ctx).nickname);
        return result;
    }

    private JsonElement exportJson(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        List<ProfileEntity> profiles = dao.getAllProfilesOldestFirst();
        if (str(args, "profileId") != null) {
            List<ProfileEntity> filtered = new ArrayList<>();
            filtered.add(requireProfile(dao, str(args, "profileId")));
            profiles = filtered;
        }
        JsonArray array = new JsonArray();
        for (ProfileEntity profile : profiles) {
            array.add(profileJson(dao, ctx, profile, true));
        }
        JsonObject root = new JsonObject();
        root.addProperty("app", "PetProfile");
        root.addProperty("schemaVersion", 1);
        KeeperInfo keeper = KeeperInfoManager.load(ctx);
        JsonObject keeperJson = new JsonObject();
        keeperJson.addProperty("nickname", keeper.nickname);
        keeperJson.addProperty("homePlace", keeper.homePlace);
        root.add("keeperInfo", keeperJson);
        root.add("profiles", array);
        return root;
    }

    private JsonElement getAppVersion(JsonObject args, Context ctx) {
        JsonObject result = new JsonObject();
        String versionName = "unknown";
        long versionCode = 0;
        try {
            android.content.pm.PackageInfo info = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            versionName = info.versionName == null ? "unknown" : info.versionName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = info.getLongVersionCode();
            } else {
                versionCode = info.versionCode;
            }
        } catch (Exception ignored) {
        }
        result.addProperty("versionName", versionName);
        result.addProperty("versionCode", versionCode);
        result.addProperty("serverName", "PetProfile MCP Server");
        result.addProperty("protocolVersion", "2025-06-18");
        result.addProperty("toolCount", tools.size());
        return result;
    }

    // ----- WRITE handlers -----

    private JsonElement createProfile(JsonObject args, Context ctx) throws McpToolException {
        ProfileEntity profile = new ProfileEntity();
        JsonObject taxonomy = args.has("taxonomy") && !args.get("taxonomy").isJsonNull()
                ? args.getAsJsonObject("taxonomy") : new JsonObject();
        profile.id = str(args, "id");
        profile.kingdom = nz(str(taxonomy, "kingdom"));
        profile.phylum = nz(str(taxonomy, "phylum"));
        profile.taxClass = nz(str(taxonomy, "taxClass"));
        profile.taxOrder = nz(str(taxonomy, "taxOrder"));
        profile.family = nz(str(taxonomy, "family"));
        profile.genus = nz(str(taxonomy, "genus"));
        profile.species = nz(str(taxonomy, "species"));
        profile.subspecies = nz(str(taxonomy, "subspecies"));
        String gender = str(args, "gender");
        profile.gender = gender == null || gender.trim().isEmpty() ? "UNKNOWN" : gender.toUpperCase(java.util.Locale.ROOT);
        String avatarUri = str(args, "avatarUri");
        String avatarData = str(args, "avatarData");
        if (avatarData != null && !avatarData.trim().isEmpty()) {
            String privateUri = decodeImageToPrivate(ctx, avatarData, str(args, "avatarExtension"), null);
            if (privateUri != null) {
                avatarUri = privateUri;
            }
        }
        profile.avatarUri = avatarUri;
        profile.updatedAt = System.currentTimeMillis();

        List<ProfileCustomFieldEntity> customFields = new ArrayList<>();
        String nickname = str(args, "nickname");
        if (nickname != null && !nickname.trim().isEmpty()) {
            customFields.add(nicknameField(nickname));
        }

        RecordEntity establishment = new RecordEntity();
        establishment.title = "Establishment";
        establishment.type = RecordType.ESTABLISHMENT;
        Long establishedAt = lng(args, "establishedAt");
        establishment.timestamp = establishedAt == null ? System.currentTimeMillis() : establishedAt;
        establishment.establishmentSource = str(args, "source");
        establishment.keeperName = str(args, "keeperName") == null ? KeeperInfoManager.load(ctx).nickname : str(args, "keeperName");

        String fatherId = str(args, "fatherId");
        String motherId = str(args, "motherId");
        try {
            String id = PetRepository.get(ctx).createProfileSync(profile, customFields, fatherId, motherId,
                    establishment, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement updateProfile(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        String id = str(args, "profileId");
        ProfileEntity profile = requireProfile(dao, id);
        JsonObject taxonomy = args.has("taxonomy") && !args.get("taxonomy").isJsonNull()
                ? args.getAsJsonObject("taxonomy") : new JsonObject();
        if (taxonomy.size() > 0) {
            if (taxonomy.has("kingdom")) profile.kingdom = nz(str(taxonomy, "kingdom"));
            if (taxonomy.has("phylum")) profile.phylum = nz(str(taxonomy, "phylum"));
            if (taxonomy.has("taxClass")) profile.taxClass = nz(str(taxonomy, "taxClass"));
            if (taxonomy.has("taxOrder")) profile.taxOrder = nz(str(taxonomy, "taxOrder"));
            if (taxonomy.has("family")) profile.family = nz(str(taxonomy, "family"));
            if (taxonomy.has("genus")) profile.genus = nz(str(taxonomy, "genus"));
            if (taxonomy.has("species")) profile.species = nz(str(taxonomy, "species"));
            if (taxonomy.has("subspecies")) profile.subspecies = nz(str(taxonomy, "subspecies"));
        }
        if (args.has("gender")) {
            String gender = str(args, "gender");
            profile.gender = gender == null || gender.trim().isEmpty() ? "UNKNOWN" : gender.toUpperCase(java.util.Locale.ROOT);
        }
        if (args.has("avatarUri")) {
            profile.avatarUri = str(args, "avatarUri");
        }
        String avatarData = str(args, "avatarData");
        if (avatarData != null && !avatarData.trim().isEmpty()) {
            String privateUri = decodeImageToPrivate(ctx, avatarData, str(args, "avatarExtension"), null);
            if (privateUri != null) {
                profile.avatarUri = privateUri;
            }
        }
        List<ProfileCustomFieldEntity> customFields = new ArrayList<>(dao.getCustomFields(id));
        if (args.has("nickname")) {
            String nickname = str(args, "nickname");
            customFields.removeIf(f -> TaxonomyUtil.isNickname(f));
            if (nickname != null && !nickname.trim().isEmpty()) {
                customFields.add(nicknameField(nickname));
            }
        }
        String fatherId = str(args, "fatherId");
        String motherId = str(args, "motherId");
        try {
            String updatedId = PetRepository.get(ctx).updateProfileSync(profile, customFields, fatherId, motherId, null);
            JsonObject result = new JsonObject();
            result.addProperty("id", updatedId);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement deleteProfile(JsonObject args, Context ctx) throws McpToolException {
        String id = str(args, "profileId");
        PetRepository.get(ctx).deleteProfileSync(id);
        JsonObject result = new JsonObject();
        result.addProperty("deleted", true);
        result.addProperty("id", id);
        return result;
    }

    private JsonElement setProfileParents(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        String id = str(args, "profileId");
        ProfileEntity profile = requireProfile(dao, id);
        String fatherId = str(args, "fatherId");
        String motherId = str(args, "motherId");
        List<ProfileCustomFieldEntity> customFields = new ArrayList<>(dao.getCustomFields(id));
        try {
            String updatedId = PetRepository.get(ctx).updateProfileSync(profile, customFields, fatherId, motherId, null);
            JsonObject result = new JsonObject();
            result.addProperty("id", updatedId);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement setProfileCustomFields(JsonObject args, Context ctx) throws McpToolException {
        ProfileDao dao = AppDatabase.getInstance(ctx).profileDao();
        String id = str(args, "profileId");
        ProfileEntity profile = requireProfile(dao, id);
        List<ProfileCustomFieldEntity> customFields = parseProfileCustomFields(args);
        try {
            String updatedId = PetRepository.get(ctx).updateProfileSync(profile, customFields, null, null, null);
            JsonObject result = new JsonObject();
            result.addProperty("id", updatedId);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement createRecord(JsonObject args, Context ctx) throws McpToolException {
        RecordEntity record = new RecordEntity();
        record.profileId = str(args, "profileId");
        record.title = nz(str(args, "title"));
        record.type = nz(str(args, "type"));
        record.timestamp = lng(args, "timestamp") == null ? System.currentTimeMillis() : lng(args, "timestamp");
        record.locationName = str(args, "locationName");
        record.latitude = dbl(args, "latitude");
        record.longitude = dbl(args, "longitude");
        record.notesMarkdown = rewriteMarkdown(ctx, nz(str(args, "notesMarkdown")));
        record.keeperName = str(args, "keeperName");
        record.archiveReason = str(args, "archiveReason");
        record.transferFromPerson = str(args, "transferFromPerson");
        record.transferToPerson = str(args, "transferToPerson");
        record.transferFromPlace = str(args, "transferFromPlace");
        record.transferToPlace = str(args, "transferToPlace");
        List<RecordFieldEntity> fields = parseRecordFields(args);
        try {
            String id = PetRepository.get(ctx).saveRecordSync(record, fields, buildRecordImages(ctx, args));
            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement updateRecord(JsonObject args, Context ctx) throws McpToolException {
        RecordDao dao = AppDatabase.getInstance(ctx).recordDao();
        String id = str(args, "recordId");
        RecordEntity record = dao.getById(id);
        if (record == null) {
            throw new McpToolException("Record not found: " + id);
        }
        if (args.has("title")) record.title = nz(str(args, "title"));
        if (args.has("timestamp")) record.timestamp = lng(args, "timestamp");
        if (args.has("locationName")) record.locationName = str(args, "locationName");
        if (args.has("latitude")) record.latitude = dbl(args, "latitude");
        if (args.has("longitude")) record.longitude = dbl(args, "longitude");
        if (args.has("notesMarkdown")) record.notesMarkdown = rewriteMarkdown(ctx, nz(str(args, "notesMarkdown")));
        if (args.has("keeperName")) record.keeperName = str(args, "keeperName");
        if (args.has("archiveReason")) record.archiveReason = str(args, "archiveReason");
        if (args.has("transferFromPerson")) record.transferFromPerson = str(args, "transferFromPerson");
        if (args.has("transferToPerson")) record.transferToPerson = str(args, "transferToPerson");
        if (args.has("transferFromPlace")) record.transferFromPlace = str(args, "transferFromPlace");
        if (args.has("transferToPlace")) record.transferToPlace = str(args, "transferToPlace");
        List<RecordFieldEntity> fields = args.has("fields") ? parseRecordFields(args) : new ArrayList<>(dao.getFields(id));
        List<RecordImageEntity> images;
        if (args.has("images")) {
            List<RecordImageEntity> newImages = buildRecordImages(ctx, args);
            if ("append".equalsIgnoreCase(str(args, "imagesMode"))) {
                images = new ArrayList<>(dao.getImages(id));
                images.addAll(newImages);
            } else {
                images = newImages;
            }
        } else {
            images = new ArrayList<>(dao.getImages(id));
        }
        JsonElement removeEl = args.get("removeImages");
        if (removeEl != null && removeEl.isJsonArray()) {
            java.util.Set<String> removeIds = new java.util.HashSet<>();
            for (JsonElement r : removeEl.getAsJsonArray()) {
                if (r.isJsonPrimitive()) {
                    removeIds.add(r.getAsString());
                }
            }
            images.removeIf(img -> img.id != null && removeIds.contains(img.id));
        }
        try {
            String savedId = PetRepository.get(ctx).saveRecordSync(record, fields, images);
            JsonObject result = new JsonObject();
            result.addProperty("id", savedId);
            return result;
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    private JsonElement deleteRecord(JsonObject args, Context ctx) throws McpToolException {
        String id = str(args, "recordId");
        try {
            PetRepository.get(ctx).deleteRecordSync(id);
        } catch (RuntimeException e) {
            throw new McpToolException(e.getMessage());
        }
        JsonObject result = new JsonObject();
        result.addProperty("deleted", true);
        result.addProperty("id", id);
        return result;
    }

    private JsonElement createRoutine(JsonObject args, Context ctx) throws McpToolException {
        RoutineEntity routine = routineFromArgs(args, new RoutineEntity());
        routine.id = IdUtil.randomId();
        routine.profileId = str(args, "profileId");
        routine.title = nz(str(args, "title"));
        routine.type = args.has("type") ? nz(str(args, "type")) : RoutineEntity.TYPE_WEEKLY;
        routine.policy = args.has("policy") ? nz(str(args, "policy")) : RoutineEntity.POLICY_SKIP;
        routine.details = nz(str(args, "details"));
        Long onceAt = lng(args, "onceAt");
        if (RoutineEntity.TYPE_ONCE.equals(routine.type)) {
            routine.onceAt = onceAt == null ? System.currentTimeMillis() : onceAt;
        }
        List<RoutineEntity> routines = AppDatabase.getInstance(ctx).routineDao().getRoutinesForProfile(routine.profileId);
        routine.position = routines.size();
        java.util.List<RoutineEntity> single = new ArrayList<>();
        single.add(routine);
        AppDatabase.getInstance(ctx).routineDao().insertAll(single);
        RoutineScheduler.scheduleAll(ctx);
        JsonObject result = new JsonObject();
        result.addProperty("id", routine.id);
        return result;
    }

    private JsonElement updateRoutine(JsonObject args, Context ctx) throws McpToolException {
        String id = str(args, "routineId");
        RoutineEntity routine = AppDatabase.getInstance(ctx).routineDao().getById(id);
        if (routine == null) {
            throw new McpToolException("Routine not found: " + id);
        }
        routineFromArgs(args, routine);
        if (args.has("enabled")) {
            Boolean enabled = bool(args, "enabled");
            routine.enabled = enabled != null && enabled;
        }
        AppDatabase.getInstance(ctx).routineDao().update(routine);
        RoutineScheduler.scheduleAll(ctx);
        RoutineNotifier.syncNow(ctx);
        JsonObject result = new JsonObject();
        result.addProperty("id", routine.id);
        return result;
    }

    private JsonElement deleteRoutine(JsonObject args, Context ctx) throws McpToolException {
        String id = str(args, "routineId");
        AppDatabase.getInstance(ctx).routineDao().deleteById(id);
        RoutineScheduler.scheduleAll(ctx);
        RoutineNotifier.syncNow(ctx);
        JsonObject result = new JsonObject();
        result.addProperty("deleted", true);
        result.addProperty("id", id);
        return result;
    }

    private JsonElement completeRoutine(JsonObject args, Context ctx) throws McpToolException {
        String id = str(args, "routineId");
        RoutineEntity routine = AppDatabase.getInstance(ctx).routineDao().getById(id);
        if (routine == null) {
            throw new McpToolException("Routine not found: " + id);
        }
        Boolean completed = bool(args, "completed");
        routine.completed = completed == null || completed;
        routine.lastInteractionTime = System.currentTimeMillis();
        AppDatabase.getInstance(ctx).routineDao().update(routine);
        RoutineNotifier.syncNow(ctx);
        JsonObject result = new JsonObject();
        result.addProperty("id", routine.id);
        result.addProperty("completed", routine.completed);
        return result;
    }

    private JsonElement saveKeeperInfo(JsonObject args, Context ctx) {
        KeeperInfo info = new KeeperInfo();
        info.nickname = nz(str(args, "nickname"));
        info.homePlace = nz(str(args, "homePlace"));
        info.latitude = dbl(args, "latitude");
        info.longitude = dbl(args, "longitude");
        KeeperInfoManager.save(ctx, info);
        JsonObject result = new JsonObject();
        result.addProperty("nickname", info.nickname);
        result.addProperty("homePlace", info.homePlace);
        return result;
    }

    /**
     * Parses the optional {@code images} array of a {@code create_record}/{@code update_record}
     * call. Each image is either a content/file {@code uri} or base64 {@code data} (with optional
     * extension/mimeType); base64 bytes are decoded into app-private storage here, while
     * {@code recordId}/{@code position} are filled in by the repository when the record is saved.
     */
    private List<RecordImageEntity> buildRecordImages(Context ctx, JsonObject args) {
        List<RecordImageEntity> result = new ArrayList<>();
        JsonElement imagesEl = args == null ? null : args.get("images");
        if (imagesEl == null || !imagesEl.isJsonArray()) {
            return result;
        }
        int position = 0;
        for (JsonElement item : imagesEl.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject img = item.getAsJsonObject();
            String uri = str(img, "uri");
            String data = str(img, "data");
            String privateUri;
            if (data != null && !data.trim().isEmpty()) {
                privateUri = decodeImageToPrivate(ctx, data, str(img, "extension"), str(img, "mimeType"));
            } else if (uri != null && !uri.trim().isEmpty()) {
                privateUri = ImageStorage.copyToPrivateStorage(ctx, uri);
            } else {
                continue;
            }
            if (privateUri == null) {
                continue;
            }
            RecordImageEntity entity = new RecordImageEntity();
            entity.id = IdUtil.randomId();
            entity.uri = privateUri;
            entity.position = position++;
            result.add(entity);
        }
        return result;
    }

    private JsonElement importZip(JsonObject args, Context ctx) throws McpToolException {
        String uri = str(args, "uri");
        String data = str(args, "data");
        try {
            ExportBundle bundle;
            if (data != null && !data.trim().isEmpty()) {
                byte[] bytes = java.util.Base64.getDecoder().decode(data);
                java.io.File tmp = new java.io.File(ctx.getFilesDir(), "tmp_import_" + IdUtil.timeBasedId() + ".zip");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                    out.write(bytes);
                }
                try {
                    bundle = BackupManager.readZip(ctx, Uri.fromFile(tmp));
                } finally {
                    tmp.delete();
                }
            } else if (uri != null && !uri.trim().isEmpty()) {
                bundle = BackupManager.readZip(ctx, Uri.parse(uri));
            } else {
                throw new McpToolException("uri or data (base64) is required");
            }
            PetRepository.get(ctx).importBundleSync(bundle);
            JsonObject result = new JsonObject();
            result.addProperty("imported", true);
            result.addProperty("profiles", bundle.profiles.size());
            result.addProperty("records", bundle.records.size());
            result.addProperty("routines", bundle.routines.size());
            result.addProperty("recordImages", bundle.recordImages.size());
            return result;
        } catch (Exception e) {
            throw new McpToolException("Import failed: " + e.getMessage());
        }
    }

    private JsonElement exportZip(JsonObject args, Context ctx) throws McpToolException {
        String targetUri = str(args, "targetUri");
        try {
            ExportBundle bundle;
            String profileId = str(args, "profileId");
            if (profileId != null && !profileId.trim().isEmpty()) {
                bundle = PetRepository.get(ctx).exportSingleProfileSync(profileId);
            } else {
                bundle = PetRepository.get(ctx).exportAllSync();
            }
            byte[] zipBytes = BackupManager.createZipBytes(ctx, bundle);
            JsonObject result = new JsonObject();
            result.addProperty("size", zipBytes.length);
            if (targetUri != null && !targetUri.trim().isEmpty()) {
                BackupManager.exportZip(ctx, bundle, Uri.parse(targetUri));
                result.addProperty("writtenTo", targetUri);
            } else {
                result.addProperty("data", java.util.Base64.getEncoder().encodeToString(zipBytes));
                result.addProperty("filename", "pet-profile-backup.zip");
            }
            return result;
        } catch (Exception e) {
            throw new McpToolException("Export failed: " + e.getMessage());
        }
    }

    private JsonElement isOneDriveConnected(JsonObject args, Context ctx) {
        JsonObject result = new JsonObject();
        result.addProperty("signedIn", OneDriveBackupManager.isSignedIn(ctx));
        String account = OneDriveBackupManager.getAccountName(ctx);
        if (account != null) {
            result.addProperty("account", account);
        }
        return result;
    }

    private JsonElement uploadOneDrive(JsonObject args, Context ctx) throws McpToolException {
        requireOneDriveSignedIn(ctx);
        requireNotBusy();
        OneDriveBackupManager.setCloudBusy(true);
        OneDriveBackupManager.setLastResult(null);
        PetRepository.get(ctx).exportAll(new Async.Result<ExportBundle>() {
            @Override
            public void onSuccess(ExportBundle bundle) {
                OneDriveBackupManager.upload(ctx, bundle, noop());
            }

            @Override
            public void onError(Throwable error) {
                OneDriveBackupManager.setCloudBusy(false);
                OneDriveBackupManager.setLastResult(error.getMessage());
            }
        });
        JsonObject result = new JsonObject();
        result.addProperty("status", "started");
        result.addProperty("message", "上传已开始，调用 get_onedrive_result 查看结果");
        return result;
    }

    private JsonElement downloadOneDrive(JsonObject args, Context ctx) throws McpToolException {
        requireOneDriveSignedIn(ctx);
        requireNotBusy();
        OneDriveBackupManager.setCloudBusy(true);
        OneDriveBackupManager.setLastResult(null);
        OneDriveBackupManager.download(ctx, noop());
        JsonObject result = new JsonObject();
        result.addProperty("status", "started");
        result.addProperty("message", "恢复已开始，调用 get_onedrive_result 查看结果");
        return result;
    }

    private void requireNotBusy() throws McpToolException {
        if (OneDriveBackupManager.isCloudBusy()) {
            throw new McpToolException("已有上传/下载任务进行中，请稍后重试");
        }
    }

    private JsonElement getOneDriveResult(JsonObject args, Context ctx) {
        JsonObject result = new JsonObject();
        String r = OneDriveBackupManager.getLastResult();
        result.addProperty("result", r == null ? "pending" : r);
        return result;
    }

    private void requireOneDriveSignedIn(Context ctx) throws McpToolException {
        if (!OneDriveBackupManager.isSignedIn(ctx)) {
            throw new McpToolException("未登录 OneDrive，请先在 App「饲养者信息」→「登录 OneDrive」登录");
        }
    }

    private OneDriveBackupManager.Callback noop() {
        return new OneDriveBackupManager.Callback() {
            @Override
            public void onSuccess(String message) {
                OneDriveBackupManager.setCloudBusy(false);
            }

            @Override
            public void onError(String message) {
                OneDriveBackupManager.setCloudBusy(false);
            }
        };
    }

    /** Decodes a base64 image and stores it in app-private storage; returns a file URI or null. */
    private String decodeImageToPrivate(Context ctx, String base64, String extension, String mimeType) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            String ext = extension;
            if (ext == null || ext.trim().isEmpty()) {
                ext = ImageStorage.extensionFromMime(mimeType);
            }
            return ImageStorage.saveBytes(ctx, bytes, ext);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Rewrites inline base64 images in Markdown into private file:// URIs (storing the bytes). */
    private String rewriteMarkdown(Context ctx, String markdown) {
        try {
            return ImageStorage.rewriteMarkdownBase64Images(ctx, markdown);
        } catch (Exception ignored) {
            return markdown;
        }
    }

    // ----- parsers / builders -----

    private RoutineEntity routineFromArgs(JsonObject args, RoutineEntity routine) {
        if (args.has("title")) routine.title = nz(str(args, "title"));
        if (args.has("type")) routine.type = nz(str(args, "type"));
        if (args.has("policy")) routine.policy = nz(str(args, "policy"));
        if (args.has("details")) routine.details = nz(str(args, "details"));
        if (args.has("hour")) routine.hour = intOf(lng(args, "hour"));
        if (args.has("minute")) routine.minute = intOf(lng(args, "minute"));
        if (args.has("second")) routine.second = intOf(lng(args, "second"));
        if (args.has("weekdays")) {
            JsonElement el = args.get("weekdays");
            if (el != null && el.isJsonArray()) {
                routine.weekdays = weekdaysString(el.getAsJsonArray());
            } else if (el != null && !el.isJsonNull()) {
                routine.weekdays = el.getAsString();
            }
        }
        if (args.has("onceAt")) {
            routine.onceAt = lng(args, "onceAt");
        }
        return routine;
    }

    private static String weekdaysString(JsonArray array) {
        StringBuilder builder = new StringBuilder();
        for (JsonElement el : array) {
            if (el.isJsonPrimitive()) {
                if (builder.length() > 0) {
                    builder.append(',');
                }
                builder.append(el.getAsString());
            }
        }
        return builder.toString();
    }

    private static int intOf(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private List<RecordFieldEntity> parseRecordFields(JsonObject args) {
        List<RecordFieldEntity> fields = new ArrayList<>();
        JsonElement el = args.get("fields");
        if (el == null || !el.isJsonArray()) {
            return fields;
        }
        int position = 0;
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject fieldJson = item.getAsJsonObject();
            RecordFieldEntity field = new RecordFieldEntity();
            field.fieldKey = nz(str(fieldJson, "key"));
            field.fieldName = nz(str(fieldJson, "name"));
            field.fieldType = nz(str(fieldJson, "type"));
            if (field.fieldType.isEmpty()) {
                field.fieldType = fieldJson.has("value") && fieldJson.get("value").isJsonPrimitive()
                        && fieldJson.get("value").getAsJsonPrimitive().isNumber()
                        ? FieldType.NUMBER : FieldType.TEXT;
            }
            field.numericValue = dbl(fieldJson, "value");
            if (field.numericValue == null && fieldJson.has("numberValue")) {
                field.numericValue = dbl(fieldJson, "numberValue");
            }
            boolean isNumberField = FieldType.NUMBER.equals(field.fieldType);
            field.textValue = isNumberField ? null : str(fieldJson, "value");
            if (field.textValue == null && !isNumberField) {
                field.textValue = str(fieldJson, "textValue");
            }
            field.unit = str(fieldJson, "unit");
            field.position = position++;
            fields.add(field);
        }
        return fields;
    }

    private List<ProfileCustomFieldEntity> parseProfileCustomFields(JsonObject args) {
        List<ProfileCustomFieldEntity> fields = new ArrayList<>();
        JsonElement el = args.get("fields");
        if (el == null || !el.isJsonArray()) {
            return fields;
        }
        int position = 0;
        for (JsonElement item : el.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject fieldJson = item.getAsJsonObject();
            ProfileCustomFieldEntity field = new ProfileCustomFieldEntity();
            field.fieldKey = nz(str(fieldJson, "key"));
            field.fieldName = nz(str(fieldJson, "name"));
            field.fieldType = nz(str(fieldJson, "type"));
            if (field.fieldType.isEmpty()) {
                field.fieldType = fieldJson.has("value") && fieldJson.get("value").isJsonPrimitive()
                        && fieldJson.get("value").getAsJsonPrimitive().isNumber()
                        ? FieldType.NUMBER : FieldType.TEXT;
            }
            field.numericValue = dbl(fieldJson, "value");
            if (field.numericValue == null && fieldJson.has("numberValue")) {
                field.numericValue = dbl(fieldJson, "numberValue");
            }
            boolean isNumberField = FieldType.NUMBER.equals(field.fieldType);
            field.textValue = isNumberField ? null : str(fieldJson, "value");
            if (field.textValue == null && !isNumberField) {
                field.textValue = str(fieldJson, "textValue");
            }
            field.unit = str(fieldJson, "unit");
            field.position = position++;
            fields.add(field);
        }
        return fields;
    }

    private static ProfileCustomFieldEntity nicknameField(String nickname) {
        ProfileCustomFieldEntity field = new ProfileCustomFieldEntity();
        field.fieldKey = "nickname";
        field.fieldName = "nickname";
        field.fieldType = FieldType.TEXT;
        field.textValue = nickname;
        field.position = 0;
        return field;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String nickname(ProfileDao dao, ProfileEntity profile) {
        List<ProfileCustomFieldEntity> fields = dao.getCustomFields(profile.id);
        for (ProfileCustomFieldEntity field : fields) {
            if (TaxonomyUtil.isNickname(field) && field.textValue != null && !field.textValue.trim().isEmpty()) {
                return field.textValue.trim();
            }
        }
        return "";
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private static ProfileEntity requireProfile(ProfileDao dao, String profileId) throws McpToolException {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new McpToolException("profileId is required");
        }
        ProfileEntity profile = dao.getById(profileId);
        if (profile == null) {
            throw new McpToolException("Profile not found: " + profileId);
        }
        return profile;
    }

    private JsonObject basicProfile(ProfileEntity profile) {
        JsonObject result = new JsonObject();
        result.addProperty("id", profile.id);
        result.addProperty("gender", profile.gender);
        result.addProperty("species", TaxonomyUtil.speciesDisplay(profile));
        result.addProperty("archived", profile.isArchived());
        return result;
    }

    private JsonObject profileJson(ProfileDao dao, Context ctx, ProfileEntity profile, boolean full) {
        JsonObject result = basicProfile(profile);
        result.addProperty("kingdom", nz(profile.kingdom));
        result.addProperty("phylum", nz(profile.phylum));
        result.addProperty("taxClass", nz(profile.taxClass));
        result.addProperty("taxOrder", nz(profile.taxOrder));
        result.addProperty("family", nz(profile.family));
        result.addProperty("genus", nz(profile.genus));
        result.addProperty("species", nz(profile.species));
        result.addProperty("subspecies", nz(profile.subspecies));
        result.addProperty("nickname", nickname(dao, profile));
        if (profile.avatarUri != null) {
            result.addProperty("avatarUri", profile.avatarUri);
        }
        result.addProperty("createdAt", profile.createdAt);
        result.addProperty("updatedAt", profile.updatedAt);
        if (profile.archivedAt != null) {
            result.addProperty("archivedAt", profile.archivedAt);
        }
        if (full) {
            JsonArray customFields = new JsonArray();
            for (ProfileCustomFieldEntity field : dao.getCustomFields(profile.id)) {
                customFields.add(profileCustomFieldJson(field));
            }
            result.add("customFields", customFields);
            JsonObject parents = new JsonObject();
            parents.addProperty("father", dao.getParentIdByRole(profile.id, "FATHER"));
            parents.addProperty("mother", dao.getParentIdByRole(profile.id, "MOTHER"));
            result.add("parents", parents);
            JsonArray childIds = new JsonArray();
            for (String child : dao.getChildIds(profile.id)) {
                childIds.add(child);
            }
            result.add("children", childIds);
            JsonArray recordIds = new JsonArray();
            for (RecordEntity record : AppDatabase.getInstance(ctx).recordDao().getRecordsForProfile(profile.id)) {
                recordIds.add(record.id);
            }
            result.add("recordIds", recordIds);
        }
        return result;
    }

    private JsonObject profileCustomFieldJson(ProfileCustomFieldEntity field) {
        JsonObject result = new JsonObject();
        result.addProperty("key", nz(field.fieldKey));
        result.addProperty("name", nz(field.fieldName));
        result.addProperty("type", nz(field.fieldType));
        if (field.numericValue != null) {
            result.addProperty("value", field.numericValue);
        } else if (field.textValue != null) {
            result.addProperty("value", field.textValue);
        }
        if (field.unit != null) {
            result.addProperty("unit", field.unit);
        }
        return result;
    }

    private JsonObject recordJson(RecordDao dao, RecordEntity record) {
        JsonObject result = new JsonObject();
        result.addProperty("id", record.id);
        if (record.profileId != null) {
            result.addProperty("profileId", record.profileId);
        }
        result.addProperty("title", nz(record.title));
        result.addProperty("type", nz(record.type));
        result.addProperty("timestamp", record.timestamp);
        if (record.locationName != null) {
            result.addProperty("locationName", record.locationName);
        }
        if (record.latitude != null) {
            result.addProperty("latitude", record.latitude);
        }
        if (record.longitude != null) {
            result.addProperty("longitude", record.longitude);
        }
        result.addProperty("notesMarkdown", nz(record.notesMarkdown));
        if (record.keeperName != null) {
            result.addProperty("keeperName", record.keeperName);
        }
        if (record.establishmentSource != null) {
            result.addProperty("establishmentSource", record.establishmentSource);
        }
        if (record.archiveReason != null) {
            result.addProperty("archiveReason", record.archiveReason);
        }
        if (record.transferFromPerson != null) {
            result.addProperty("transferFromPerson", record.transferFromPerson);
        }
        if (record.transferToPerson != null) {
            result.addProperty("transferToPerson", record.transferToPerson);
        }
        if (record.transferFromPlace != null) {
            result.addProperty("transferFromPlace", record.transferFromPlace);
        }
        if (record.transferToPlace != null) {
            result.addProperty("transferToPlace", record.transferToPlace);
        }
        return result;
    }

    private JsonObject recordFieldJson(RecordFieldEntity field) {
        JsonObject result = new JsonObject();
        result.addProperty("key", nz(field.fieldKey));
        result.addProperty("name", nz(field.fieldName));
        result.addProperty("type", nz(field.fieldType));
        if (field.numericValue != null) {
            result.addProperty("value", field.numericValue);
        }
        if (field.textValue != null && !FieldType.NUMBER.equals(field.fieldType)) {
            result.addProperty("textValue", field.textValue);
        }
        if (field.unit != null) {
            result.addProperty("unit", field.unit);
        }
        return result;
    }

    private JsonObject routineJson(RoutineEntity routine) {
        JsonObject result = new JsonObject();
        result.addProperty("id", routine.id);
        result.addProperty("profileId", routine.profileId);
        result.addProperty("title", nz(routine.title));
        result.addProperty("type", nz(routine.type));
        result.addProperty("enabled", routine.enabled);
        result.addProperty("weekdays", nz(routine.weekdays));
        result.addProperty("hour", routine.hour);
        result.addProperty("minute", routine.minute);
        result.addProperty("second", routine.second);
        result.addProperty("policy", nz(routine.policy));
        result.addProperty("completed", routine.completed);
        if (routine.details != null) {
            result.addProperty("details", routine.details);
        }
        if (routine.onceAt != null) {
            result.addProperty("onceAt", routine.onceAt);
        }
        if (routine.lastFiredAt != null) {
            result.addProperty("lastFiredAt", routine.lastFiredAt);
        }
        return result;
    }
}
