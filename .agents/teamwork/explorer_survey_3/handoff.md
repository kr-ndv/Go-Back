# Explorer Survey 3: Launcher Icons, Adaptive Configuration & Production Release APK Report

## 1. Observation

### 1.1 User-Uploaded Icon Analysis
- **File Location**: `C:/Users/visha/.gemini/antigravity/brain/6137acee-e60f-4e8e-b4f3-cb7f6c52ab66/.user_uploaded/media_1794057979794_1140e298.jpg`
- **File Format & Dimensions**: 256 x 256 pixels, RGB JPEG.
- **Direct Color Sampling**:
  - Sample background color (corner 30x30 pixels): `#081B15` (100% of tested corner pixels).
  - Sample arrow color (interior stroke): `#C9F387` (lime / mint-green).
  - Arrow geometry: Curved counter-clockwise turn arrow pointing towards the left, with an arc that curves downwards and clockwise towards the bottom right.
- **Repository Vector Master Discovery**:
  - During codebase search, the exact original source vector and raster files were located at:
    - `c:\Users\visha\Desktop\Projects\Go-Back\assets\icon.svg`
    - `c:\Users\visha\Desktop\Projects\Go-Back\assets\icon.png` (256x256 PNG)
    - `c:\Users\visha\Desktop\Projects\Go-Back\assets\adaptive-icon.png` (256x256 PNG)
  - Content of `assets/icon.svg` (lines 1-6):
    ```xml
    <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
      <rect width="1024" height="1024" fill="#081B14"/>
      <path d="M650 735 C775 665 775 525 715 448 C660 378 595 348 520 348 L392 348"
            fill="none" stroke="#C7F485" stroke-width="92" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M250 348 L410 235 L410 461 Z" fill="#C7F485"/>
    </svg>
    ```
  - Background color specified in vector: `#081B14`
  - Foreground stroke & arrowhead fill: `#C7F485`

### 1.2 Existing Android Launcher Resources
- **Location**: `c:\Users\visha\Desktop\Projects\Go-Back\android\app\src\main\res`
- **Observed Resources**:
  - `drawable/ic_launcher_foreground.xml`:
    ```xml
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M42,32v11h0.02L42,43.02L50,51l-8,8 0.02,0.02H42V70h24v-10.98h-0.02L66,59l-8,-8 8,-7.98 -0.02,-0.02H66V32H42zM62,59.5V66H46v-6.5l8,-8 8,8.5zM54,49.5l-8,-8V36h16v5.5l-8,8z" />
    </vector>
    ```
    *Note: Currently contains an outdated generic white hourglass icon, completely unrelated to Go-Back.*
  - `mipmap-anydpi-v26/ic_launcher.xml` and `mipmap-anydpi-v26/ic_launcher_round.xml`:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
        <background android:drawable="@color/primary" />
        <foreground android:drawable="@drawable/ic_launcher_foreground" />
    </adaptive-icon>
    ```
  - `values/colors.xml`:
    - `@color/primary` is defined as `#365C32` (medium olive-green), not the launcher background `#081B14`.
    - No `ic_launcher_background` color resource currently exists.
  - **Missing Raster Mipmaps**:
    - The following standard Android density mipmap directories are completely missing:
      - `res/mipmap-mdpi/`
      - `res/mipmap-hdpi/`
      - `res/mipmap-xhdpi/`
      - `res/mipmap-xxhdpi/`
      - `res/mipmap-xxxhdpi/`
  - `AndroidManifest.xml` (lines 28, 30):
    - `android:icon="@mipmap/ic_launcher"`
    - `android:roundIcon="@mipmap/ic_launcher_round"`

### 1.3 Gradle Build and Signing Configuration
- **File**: `c:\Users\visha\Desktop\Projects\Go-Back\android\app\build.gradle.kts`
  - Lines 25-33:
    ```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    ```
  - Observation: No `signingConfigs` block is defined.
  - Gradle default behavior: Running `./gradlew assembleRelease` outputs an unsigned APK at `android/app/build/outputs/apk/release/app-release-unsigned.apk`.
  - Unsigned APKs cannot be installed on standard Android devices or distributed on GitHub Releases.
- **Keystore Scan Results**:
  - No `.keystore` or `.jks` file exists inside `android/` or the root project.
  - `C:\Users\visha\.android\debug.keystore` exists on the local machine.
- **Android SDK and Build Tools**:
  - `C:\Users\visha\AppData\Local\Android\Sdk` is present.
  - Platforms: `android-35` installed.
  - Build-tools: `34.0.0` and `35.0.0` (including `apksigner.bat` and `zipalign.exe`) installed.
  - `android/local.properties` correctly points to `C\:\\Users\\visha\\AppData\\Local\\Android\\Sdk`.
- **Dry-run verification**:
  - `./gradlew tasks --dry-run` was executed and completed with `BUILD SUCCESSFUL` (1m 40s daemon initialization).
- **Output Destination**:
  - Target destination `C:\Users\visha\Downloads\` was verified to exist.

---

## 2. Logic Chain

1. **Launcher Icon Assets & Adaptive Icon Layers**:
   - *From Observation 1.1 & 1.2*: Android 8.0+ (API 26+) uses adaptive icons composed of two separate layers: background and foreground, each sized 108dp x 108dp.
   - The safe zone for adaptive icons is a 72dp diameter centered circle (from x=18 to x=90, y=18 to y=90).
   - In `assets/icon.svg`, the icon geometry spans x=250..821 (55.7% of 1024 width) and y=235..781 (53.3% of 1024 height), perfectly centered at (535, 508).
   - Therefore, converting `assets/icon.svg` into an Android VectorDrawable with `viewportWidth="1024"` and `viewportHeight="1024"` will place the curved back-arrow precisely inside the 72dp safe zone without any mask clipping by circular, squircle, or rounded-rectangular launcher shapes.
   - For the background layer, defining `<color name="ic_launcher_background">#081B14</color>` in `res/values/colors.xml` and binding it in `ic_launcher.xml` and `ic_launcher_round.xml` provides a resolution-independent, crisp dark background matching the user request.
   - For legacy launchers (Android 7.1 and below, and certain third-party launchers), raster icons (`ic_launcher.png` and `ic_launcher_round.png`) across `mdpi` (48x48), `hdpi` (72x72), `xhdpi` (96x96), `xxhdpi` (144x144), and `xxxhdpi` (192x192) must be generated and placed in their respective `mipmap-*` directories.

2. **Release Signing Configuration**:
   - *From Observation 1.3*: `android/app/build.gradle.kts` lacks `signingConfigs`. Running `assembleRelease` produces an unsigned APK.
   - According to Requirement R4 in `ORIGINAL_REQUEST.md`: "Sign the release APK using an existing release keystore / signing configuration in the repository if present; otherwise configure standard release signing."
   - Since no release keystore exists in the repository, standard release signing requires:
     a) Generating a production release keystore (e.g. `android/app/release.keystore`) via `keytool` (from OpenJDK 17).
     b) Adding a `signingConfigs { create("release") { ... } }` in `android/app/build.gradle.kts` that signs with `release.keystore` (or falls back gracefully to `debug` keystore if secrets are omitted in external CI).
     c) Setting `signingConfig = signingConfigs.getByName("release")` under `buildTypes.release`.

3. **Release Build & Delivery**:
   - Executing `.\gradlew assembleRelease` will compile, shrink (if configured), and sign the release artifact to:
     `c:\Users\visha\Desktop\Projects\Go-Back\android\app\build\outputs\apk\release\app-release.apk`.
   - Copying the generated `app-release.apk` to `C:\Users\visha\Downloads\` satisfies Acceptance Criteria R4.

---

## 3. Caveats

1. **Keystore Security & Version Control**:
   - Generating a local `release.keystore` is necessary to produce an installable release APK. However, keystore passwords or raw keystores should typically not be checked into public repositories. A `.gitignore` entry (`*.keystore`, `*.jks`) should be maintained.
2. **ProGuard / R8 Minification**:
   - Currently `isMinifyEnabled = false` in `build.gradle.kts`. If minification/obfuscation is enabled in the future, ProGuard keep rules for Glance, Room, and Jetpack Compose reflection must be tested. Keeping `isMinifyEnabled = false` ensures stability for immediate release while preventing symbol stripping.
3. **AppWidget & Notification Icons**:
   - The app also contains `ic_focus_tile.xml` and widget assets. These are separate from the launcher icon, but should also harmonize with `#081B14` / `#C7F485`.

---

## 4. Conclusion & Actionable Implementation Plan

### Step 1: Update Colors Resource (`android/app/src/main/res/values/colors.xml`)
Add the official launcher background color:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#365C32</color>
    <color name="primary_container">#D9F7B5</color>
    <color name="background_light">#F5F6F0</color>
    <color name="background_dark">#0C1712</color>
    <color name="ic_launcher_background">#081B14</color>
</resources>
```

### Step 2: Replace Foreground Vector (`android/app/src/main/res/drawable/ic_launcher_foreground.xml`)
Replace the white hourglass with the green curved back-arrow vector:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="1024"
    android:viewportHeight="1024">
    <path
        android:pathData="M650,735 C775,665 775,525 715,448 C660,378 595,348 520,348 L392,348"
        android:strokeColor="#C7F485"
        android:strokeWidth="92"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M250,348 L410,235 L410,461 Z"
        android:fillColor="#C7F485" />
</vector>
```

### Step 3: Update Adaptive Icon XMLs (`mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`)
Ensure background points to `@color/ic_launcher_background`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

### Step 4: Generate Legacy Raster Mipmaps
Generate raster files (`ic_launcher.png` and `ic_launcher_round.png`) across densities from `assets/icon.png`:
- `res/mipmap-mdpi/`: 48 x 48 px
- `res/mipmap-hdpi/`: 72 x 72 px
- `res/mipmap-xhdpi/`: 96 x 96 px
- `res/mipmap-xxhdpi/`: 144 x 144 px
- `res/mipmap-xxxhdpi/`: 192 x 192 px

*(Can be executed via a simple .NET / PowerShell script with high-quality bicubic resampling and circular clipping).*

### Step 5: Configure Release Signing in `android/app/build.gradle.kts`
1. Generate keystore:
   ```powershell
   keytool -genkeypair -v -keystore android/app/release.keystore -alias goback -keyalg RSA -keysize 2048 -validity 10000 -storepass gobackrelease -keypass gobackrelease -dname "CN=GoBack, OU=Mobile, O=GoBack, L=Unknown, ST=Unknown, C=US"
   ```
2. Update `android/app/build.gradle.kts`:
   ```kotlin
   android {
       ...
       signingConfigs {
           create("release") {
               val keystoreFile = file("release.keystore")
               if (keystoreFile.exists()) {
                   storeFile = keystoreFile
                   storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
                       ?: System.getenv("RELEASE_STORE_PASSWORD") ?: "gobackrelease"
                   keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
                       ?: System.getenv("RELEASE_KEY_ALIAS") ?: "goback"
                   keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
                       ?: System.getenv("RELEASE_KEY_PASSWORD") ?: "gobackrelease"
               } else {
                   val debug = getByName("debug")
                   storeFile = debug.storeFile
                   storePassword = debug.storePassword
                   keyAlias = debug.keyAlias
                   keyPassword = debug.keyPassword
               }
           }
       }

       buildTypes {
           release {
               isMinifyEnabled = false
               proguardFiles(
                   getDefaultProguardFile("proguard-android-optimize.txt"),
                   "proguard-rules.pro"
               )
               signingConfig = signingConfigs.getByName("release")
           }
       }
   }
   ```

### Step 6: Assemble and Copy Release Deliverable
```powershell
cd android
.\gradlew assembleRelease
Copy-Item -Path "app\build\outputs\apk\release\app-release.apk" -Destination "C:\Users\visha\Downloads\GoBack-release.apk" -Force
```

---

## 5. Verification Method

To independently verify the implementation:
1. **Icon Asset Verification**:
   - Inspect `android/app/src/main/res/drawable/ic_launcher_foreground.xml` and verify it contains the vector path `M650,735...` with stroke color `#C7F485`.
   - Inspect `android/app/src/main/res/values/colors.xml` and verify `<color name="ic_launcher_background">#081B14</color>` exists.
   - Verify all 5 raster mipmap folders exist and contain valid non-zero PNGs:
     ```powershell
     Get-ChildItem -Path "android\app\src\main\res\mipmap-*" -Filter "*.png" | Select-Object FullName, Length
     ```
2. **Release APK Build & Signing Verification**:
   - Run:
     ```powershell
     cd c:\Users\visha\Desktop\Projects\Go-Back\android
     .\gradlew assembleRelease
     ```
   - Check exit code: `0` (BUILD SUCCESSFUL).
   - Verify output APK exists:
     `c:\Users\visha\Desktop\Projects\Go-Back\android\app\build\outputs\apk\release\app-release.apk`
   - Verify APK signature:
     ```powershell
     & "C:\Users\visha\AppData\Local\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose "android\app\build\outputs\apk\release\app-release.apk"
     ```
     Expected output: `Verifies: true`, `Verified using v1 scheme (JAR signing): true`, `Verified using v2 scheme (APK Signature Scheme v2): true`.
3. **File Copy Verification**:
   - Run:
     ```powershell
     Test-Path "C:\Users\visha\Downloads\GoBack-release.apk"
     ```
     Expected output: `True`.
