# Pet Profile

Pet Profile is a local-first Android app for reptile keepers and breeders. It manages animal profiles, husbandry records, pedigree charts, numeric visualization, backup/sharing, and QR-based transfer between devices.

The project is written in Java and built with Android Studio and Gradle.

## Features

- **Profile management**
  - Create, edit, and delete profiles.
  - Search by nickname or any taxonomy level: kingdom, phylum, class, order, family, genus, species, and subspecies.
  - Multi-level filtering by year, gender, status (active/archived), and taxonomy.
  - List and pedigree view modes.
  - Expandable per-profile attribute table showing field name and value/unit.

- **Profile card and records**
  - Taxonomy fields for kingdom through subspecies, plus mandatory nickname and optional gender.
  - One profile avatar.
  - User-defined profile attributes and per-record attributes.
  - Parent selection with taxonomy and family-tree cycle validation.
  - Record types: establishment, daily, transfer, and archive.
  - Timestamp, map-picked location, numeric/tag fields, Markdown notes, and images.
  - Record ordering constraints: one earliest establishment record and at most one latest archive record.

- **Visualization**
  - Numeric record fields can be plotted as line charts.

- **Backup, sharing, and transfer**
  - ZIP import/export.
  - PNG profile-card sharing.
  - QR code combined with same-LAN TCP transfer for complete profile trees, including images.

- **Localization**
  - Simplified Chinese, Traditional Chinese (Hong Kong), English, and Japanese.

## Tech stack

- Android app: Java 17
- UI: AndroidX, Material Components, RecyclerView
- Local database: Room
- Images: Glide
- Markdown: Markwon
- QR code: ZXing
- QR scanning: CameraX + ML Kit Barcode Scanning

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

With Android SDK and JDK 17 configured:

```bash
./gradlew assembleDebug
```

Project configuration:

- `minSdk`: 26
- `targetSdk`: 35
- `compileSdk`: 35
- Java compatibility: 17

## Data and images

- Data is stored locally in a Room database.
- Images selected through the system file picker are stored as `content://` URIs.
- Markdown image references are rendered with Markwon and Glide.
- ZIP export packs related images and restores them to app-private storage on import.

## Map providers

The map picker supports:

- AMap (default, recommended for mainland China)
- Google
- OpenStreetMap

AMap uses GCJ-02 coordinates; the app converts between WGS-84 and GCJ-02 when placing and picking the marker. The marker defaults to the last known GPS location when available.

## Localization

Language resources are maintained under:

- `values/` — Simplified Chinese
- `values-zh-rHK/` — Traditional Chinese (Hong Kong)
- `values-en/` — English
- `values-ja/` — Japanese

## Repository and feedback

- Source repository: [https://github.com/Jaffe2718/PetProfile](https://github.com/Jaffe2718/PetProfile)
- Issues: [https://github.com/Jaffe2718/PetProfile/issues](https://github.com/Jaffe2718/PetProfile/issues)

## Notes

- The app is local-first and does not currently provide cloud sync.
- QR transfer requires both devices to be on the same local network; the QR code contains connection metadata only, while the full profile tree is transferred over TCP.
