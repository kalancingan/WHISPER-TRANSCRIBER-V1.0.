#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>
#include "whisper.h"

static std::string fmt_time(int64_t t) {
    int64_t s = t / 100;
    int sec = (int)(s % 60);
    int min = (int)((s / 60) % 60);
    int hour = (int)(s / 3600);
    std::ostringstream o;
    if (hour > 0) o << std::setfill('0') << std::setw(2) << hour << ":";
    o << std::setfill('0') << std::setw(2) << min << ":" << std::setw(2) << sec;
    return o.str();
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_ra_whispertranscriber_MainActivity_transcribeNative(JNIEnv *env, jobject, jstring modelPath, jfloatArray audioSamples) {
    const char *mp = env->GetStringUTFChars(modelPath, nullptr);
    jsize sampleCount = env->GetArrayLength(audioSamples);
    jfloat *samples = env->GetFloatArrayElements(audioSamples, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Conservative CPU path for broad Android compatibility.
    whisper_context *ctx = whisper_init_from_file_with_params(mp, cparams);
    env->ReleaseStringUTFChars(modelPath, mp);
    if (!ctx) { env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT); return env->NewStringUTF("ERROR: Model tidak dapat dibuka."); }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false; params.print_realtime = false; params.print_timestamps = false;
    params.translate = false; params.language = "id"; params.n_threads = 4;
    params.no_context = true; params.single_segment = false;
    int rc = whisper_full(ctx, params, samples, sampleCount);
    env->ReleaseFloatArrayElements(audioSamples, samples, JNI_ABORT);
    if (rc != 0) { whisper_free(ctx); return env->NewStringUTF("ERROR: Proses transkripsi gagal. Pastikan format audio didukung."); }

    std::ostringstream out;
    out << "[TEXT]\n";
    const int n = whisper_full_n_segments(ctx);
    for (int i = 0; i < n; ++i) out << whisper_full_get_segment_text(ctx, i);
    out << "\n[SEGMENTS]\n";
    for (int i = 0; i < n; ++i) {
        out << "[" << fmt_time(whisper_full_get_segment_t0(ctx, i)) << " -> "
            << fmt_time(whisper_full_get_segment_t1(ctx, i)) << "] "
            << whisper_full_get_segment_text(ctx, i) << "\n";
    }
    whisper_free(ctx);
    return env->NewStringUTF(out.str().c_str());
}
