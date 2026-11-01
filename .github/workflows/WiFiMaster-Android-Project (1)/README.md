# WiFi Master & Hardware Monitor (Android 16 / API 34+ MVVM)

## How to Build the APK File

### Method 1: In Android Studio (Recommended & Easiest)
1. Extract this `.zip` archive.
2. Open **Android Studio** (Koala, Ladybug, Meerkat, or newer).
3. Select **File > Open** and select this extracted folder.
4. Let Gradle sync dependencies.
5. In the top menu, click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Android Studio will generate the APK at:
   `app/build/outputs/apk/debug/app-debug.apk`
7. Click the **"locate"** popup in Android Studio or copy `app-debug.apk` to your Android phone and install!

---

### Method 2: Command Line (Terminal / Mac / Linux / Windows)
Open your terminal in the extracted folder and run:

**On macOS / Linux:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

**On Windows (PowerShell / Command Prompt):**
```cmd
gradlew.bat assembleDebug
```

The compiled APK will be created at:
`app/build/outputs/apk/debug/app-debug.apk`

To build an optimized release APK:
```bash
./gradlew assembleRelease
```

---

### Method 3: 100% Cloud Automated Build via GitHub Actions (No Android Studio Needed!)
1. Create a free repository on GitHub and push these files.
2. GitHub Actions will automatically detect `.github/workflows/build-apk.yml`.
3. The runner will automatically download JDK 17, Android SDK 35, and compile the APK.
4. Go to the **Actions** tab on your GitHub repository, click the latest workflow run, and download **`WiFiMaster-Debug-APK`** under the Artifacts section!

---

### Method 4: Docker 1-Command Build (Without installing Android SDK)
```bash
docker run --rm -v $(pwd):/project -w /project alvr/alpine-android:latest ./gradlew assembleDebug
```
