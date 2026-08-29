# Pet Profile

[简体中文](README.zh_cn.md) | [English](README.md)

> A local-first Android app for reptile keepers, breeders, and pet shops.

Pet Profile is a local-first Android app that manages animal profiles, individual husbandry records, pedigree (family-tree) charts, numeric visualization, routine-reminder notifications, backup/sharing, and QR-based device-to-device transfer. Everything runs on-device; no account or cloud sync is required, and the network is only used when you need map tiles or a LAN transfer.

The project is written in Java and built with Android Studio and Gradle.

## Highlights

- **Profile management** — create, edit, and delete profiles; search by nickname or any taxonomy level; multi-level filters by date range, gender, status (active/archived), and taxonomy; list and pedigree views; expandable per-profile attribute tables.
- **Records** — establishment / daily / transfer / archive record types, each with a mandatory title, timestamp, map-picked location, custom numeric and tag fields, Markdown notes, and images.
- **Pedigree tree** — a generation-aware family-tree layout with gender-shaped avatars, mating lines, and cycle prevention so bloodlines stay traceable.
- **Visualization** — numeric record fields are plotted as line charts.
- **Routine reminders** — per-profile feeding / watering / cleaning reminders with weekly or one-time schedules, a completion policy (Skip / Retain), and system notifications.
- **Daily todo** — a dedicated screen listing today's tasks with due/upcoming status, per-pet filters, search, and completion tracking.
- **Backup & sharing** — ZIP export/import, PNG long-image sharing, and QR + same-LAN transfer of complete profile trees (including ancestors and images).
- **Keeper info** — a global keeper profile (nickname + home place) that pre-fills transfer and archive metadata.
- **Localization** — Simplified Chinese, Traditional Chinese (Hong Kong), English, and Japanese.

## Features

### Profiles

- Taxonomy fields from kingdom to subspecies (each optional) plus a mandatory nickname and an optional gender (male / female / unknown).
- One avatar image per profile; a unique, immutable short ID (base64-encoded) that stays stable across backup, restore, and transfer.
- Custom profile attributes and per-record attributes, each either numeric (name / value / unit) or text/tag (name / description).
- Parent selection: the father must be male and the mother female; the kingdom–phylum–class–order–family must match; a child cannot be an ancestor of its own parent (family-tree cycle check); a parent's establishment must precede the child's.
- Expandable per-profile attribute table on the management list.

### Records

- Four types: **Establishment** (mandatory, first, unique), **Daily**, **Transfer**, and **Archive** (at most one, and it must be last).
- Establishment reasons: Bred / Wild-caught / Purchased. Archive reasons: Death / Transferred out.
- Transfer records include the previous keeper, the new keeper, the from-place, and the to-place, all map-picked and stored with an address plus degree-minute-second coordinates.
- Every record has a mandatory title, a timestamp, an optional map-picked location, custom fields, Markdown notes, and images.
- Ordering constraints are enforced so a profile always starts with one establishment record and ends with at most one archive record.
- When you create a new record, custom fields are pre-filled from the previous record while notes, images, time, and location are left blank for you to fill in.

### Pedigree view

- Generation-aware grid layout; avatars are shaped by gender (square = male, circle = female, hexagon = unknown).
- The nickname is shown under each avatar; tap an avatar to open its records.
- Sibling groups stay contiguous; the mating line runs between the parents and the branch nodes are drawn as dots.
- Independent family trees are laid out side by side and never connected with stray lines.
- Drag (pan) the canvas to explore large trees.

### Routine reminders & notifications

- Each profile can hold multiple routine reminders (feeding / watering / cleaning, and so on).
- Each reminder has a title, a weekly multi-select (Sun–Sat) or a one-time date/time, a time, and plain-text details.
- A switch toggles each reminder on/off, and a completion policy chooses between **Skip** (dropped once past due) and **Retain** (kept until done).
- Notifications fire only for active (non-archived) profiles, are silent, group into one notification, and persist until the task is completed or removed.
- For reliable delivery in the background, on the lock screen, or after the app is swiped away, grant notification permission and, on many devices, allow auto-start and background / battery-unrestricted access.

### Daily todo

- Open the bell icon on a profile's top bar to see today's tasks.
- Each card shows the pet's avatar, routine title, nickname + gender symbol, taxonomy, details, and time.
- A checkbox marks a task done (reversible); only reached tasks can be checked.
- Upcoming tasks are green with no checkbox, reached tasks are orange, and completed tasks are gray.
- Search, filter (from–to time, completed/not, and by pet with avatars), and sort.
- Tasks are generated on-device from each profile's routines and are not exported/imported; they refresh on app launch.

### Backup, sharing & transfer

- ZIP export/import covers the whole database: profiles, records, attributes, images, and keeper info.
- PNG long-image sharing renders a profile card followed by every record, including attributes and Markdown notes.
- QR + same-LAN transfer: the QR code carries only connection metadata while the full profile tree (including ancestors and images) is streamed over TCP.
- When transferring, an archive (transferred out) record is added, and ancestor profiles that are not present locally are archived as transferred so bloodlines remain traceable.

### Keeper info

- A global nickname and a home place (map-picked, address + DMS).
- Used to pre-fill transfer and archive keeper names and locations, and shared through ZIP export/import.

## Map providers

The location picker supports AMap (default; recommended in mainland China), Google, and OpenStreetMap. AMap uses GCJ-02 coordinates, and the app converts between WGS-84 and GCJ-02 when placing and picking the marker. The marker defaults to the last known GPS position when available, and the picked location is stored as a readable address plus degree-minute-second coordinates (negative for south latitude / west longitude).

## Localization

Language resources live under:

- `values/` — Simplified Chinese
- `values-zh-rHK/` — Traditional Chinese (Hong Kong)
- `values-en/` — English
- `values-ja/` — Japanese

## Tech stack

- Android app: Java 17
- UI: AndroidX, Material Components, RecyclerView
- Local database: Room
- Images: Glide (stored in app-private storage)
- Markdown rendering: Markwon
- QR generation: ZXing
- QR scanning: CameraX + ML Kit Barcode Scanning
- Map tiles: AMap / Google Maps / OpenStreetMap

## Build

### Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. Open this repository as an Android project.
3. Let Gradle sync and download the required SDK components.
4. Run **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Command line

With the Android SDK and JDK 17 configured:

```bash
./gradlew assembleDebug
```

Project configuration:

- `minSdk`: 26
- `targetSdk`: 35
- `compileSdk`: 35
- Java compatibility: 17

## Permissions

- `CAMERA` — scanning transfer QR codes.
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — map picker and GPS.
- `INTERNET` — map tiles and LAN transfer.
- `POST_NOTIFICATIONS` — routine reminders.
- `RECEIVE_BOOT_COMPLETED` — re-schedule reminders after a reboot.

## MCP (Model Context Protocol)

Pet Profile exposes a local Model Context Protocol server so an AI agent on the same LAN can read and manage the app's data.

### Enable

1. In the app, open **About → MCP**.
2. Turn the **enable switch** on. This starts a foreground service that keeps the MCP server alive in the background, on the lock screen, and (on devices where the process survives) after the app is swiped away.
3. Copy the **URL** (`http://<phone-ip>:18999/petprofile`) and the **Authorization key** from the dialog.

### Connect an agent

Configure the agent's MCP client with a `streamable-http` server:

```json
{
  "type": "streamable-http",
  "url": "http://x.x.x.x:18999/petprofile",
  "headers": {
    "Authorization": "Bearer <your-key>"
  }
}
```

Requirements: the phone and the agent must be on the same network, and the app must be running with the switch on. The key is auto-generated; use the one shown in the app (it changes if you press **Refresh**).

### Tools

The server exposes these tools over JSON-RPC (`initialize` / `tools/list` / `tools/call`):

**Read** — `list_profiles`, `search_profiles`, `get_profile`, `get_profile_family`, `list_records`, `get_record`, `get_record_timeseries`, `list_routines`, `get_daily_todo`, `get_keeper_info`, `get_stats`, `export_json`, `get_app_version`.

**Write** — `create_profile`, `update_profile`, `delete_profile`, `set_profile_parents`, `set_profile_custom_fields`, `create_record`, `update_record`, `delete_record`, `create_routine`, `update_routine`, `delete_routine`, `complete_routine`, `save_keeper_info`, `export_zip`, `import_zip`.

Images are handled as part of the profile/record operations rather than a separate import: `create_record` / `update_record` accept an `images` array, and `create_profile` / `update_profile` accept `avatarData`. An image entry can be a content/`file:` `uri` or base64 `data` (with optional `extension` / `mimeType`) and is stored in app-private storage. `update_record` also supports `imagesMode` (`append` / `replace`, default `replace`) to decide whether to add to or replace the existing images, and `removeImages` (image ids, readable via `get_record`) to delete specific images. Markdown notes may embed inline `![alt](data:image/...;base64,....)` images, which are decoded and rewritten to private `file://` URIs. `export_zip` returns base64 `data` (or writes to `targetUri`) and `import_zip` accepts base64 `data` (or a `uri`).

After a successful write, the foreground data screens (profile list, records, daily todo, record detail, chart) reload automatically, while any open dialog is kept.

> Note: `import_zip` replaces the whole database (destructive).

## Data & images

- Everything is stored locally in a Room database.
- Images are copied into app-private storage (`files/images/`), so records stay intact even if the original photo is removed from the gallery. Markdown image references are rewritten to these stored files.
- ZIP export packs the related images and restores them to app-private storage on import; unique filenames prevent cross-device collisions.
- The app is local-first and does not provide cloud sync.

## Repository & feedback

- Source repository: [https://github.com/Jaffe2718/PetProfile](https://github.com/Jaffe2718/PetProfile)
- Issues: [https://github.com/Jaffe2718/PetProfile/issues](https://github.com/Jaffe2718/PetProfile/issues)