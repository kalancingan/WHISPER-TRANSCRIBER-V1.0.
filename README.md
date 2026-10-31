# Whisper Transcriber (Android)

A native Android starter project for offline Indonesian speech transcription on ARM phones, targeted at Samsung Galaxy A55 5G.

## Architecture
- Kotlin Android UI and Android Storage Access Framework for selecting audio and exporting TXT.
- Android `MediaExtractor` + `MediaCodec` decode audio to 16 kHz mono float PCM.
- `whisper.cpp` native C++ engine via JNI; inference runs locally on CPU.
- Whisper model binaries are downloaded on demand from the `ggerganov/whisper.cpp` Hugging Face model repository and stored in the app-private `files/models` directory.
- No audio upload or remote transcription API is implemented. Internet permission is used for model downloads only.

## Build prerequisites
- Android Studio with Android SDK Platform 35
- Android NDK (side-by-side) and CMake 3.22.1
- JDK 17 (JDK 21 may also work with current Android Gradle Plugin)
- Internet during first Gradle/CMake build (Gradle dependencies and whisper.cpp source are fetched)

Open this folder in Android Studio and allow Gradle sync. Then select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

CLI build after SDK setup:
```bash
./gradlew assembleDebug
```
The APK should appear at `app/build/outputs/apk/debug/app-debug.apk`.

## Model guidance for Galaxy A55 (12 GB RAM)
- Tiny: smallest and fastest, lower accuracy.
- Base: recommended first test; moderate memory/storage.
- Small: higher accuracy, slower and heavier.
- Medium: very large and slow on phone CPU; use only for testing short recordings.

Models are separate downloads; selecting another model downloads that model separately. Keep sufficient free storage. A failed/incomplete download is written as `.part` and is not treated as a valid model.

## Current implementation scope / limitations
- Language is fixed to Indonesian (`id`) as in the supplied Python script.
- CPU inference is selected for compatibility; no GPU/NPU acceleration is enabled.
- Android's installed media decoders determine which audio containers/codecs can be opened. Common AAC/M4A, MP3, and WAV support depends on device codec support.
- Very long audio can consume substantial RAM because decoded PCM is held in memory. Split long recordings for best reliability.
- The current UI is a functional starter, not yet a production background-service implementation; keep the app open during transcription.
- Build has not been verified in an Android SDK environment unless a successful Gradle build log and APK are produced.

## Project structure
```
app/src/main/java/id/ra/whispertranscriber/MainActivity.kt
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/whisper-jni.cpp
```
