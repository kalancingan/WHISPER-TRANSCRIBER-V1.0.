package id.ra.whispertranscriber

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.AudioFormat
import java.nio.ByteOrder
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        init { System.loadLibrary("whisper-jni") }
        private val MODEL_URLS = mapOf(
            "Tiny" to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            "Base" to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            "Small" to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            "Medium" to "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin"
        )
    }

    private external fun transcribeNative(modelPath: String, audioSamples: FloatArray): String
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var modelSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var output: EditText
    private lateinit var downloadButton: Button
    private lateinit var transcribeButton: Button
    private var selectedAudio: Uri? = null
    private var lastResult = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 47, 42)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 20); setBackgroundColor(0xFFF5F7F6.toInt()) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(TextView(this).apply { text = "WHISPER TRANSCRIBER"; textSize = 22f; setTextColor(0xFF173D35.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD })
        content.addView(TextView(this).apply { text = "Transkripsi audio Bahasa Indonesia • Diproses lokal di perangkat"; textSize = 14f; setTextColor(0xFF52645F.toInt()); setPadding(0, 8, 0, 20) })

        content.addView(label("MODEL WHISPER"))
        modelSpinner = Spinner(this).also { spinner ->
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Tiny", "Base", "Small", "Medium"))
            spinner.setSelection(1)
            content.addView(spinner)
        }
        content.addView(TextView(this).apply { text = "Base direkomendasikan sebagai titik awal. Model diunduh satu kali dan disimpan di penyimpanan privat aplikasi."; textSize = 12f; setTextColor(0xFF65736F.toInt()); setPadding(0, 6, 0, 14) })

        downloadButton = button("1. Unduh model") { downloadModel() }
        content.addView(downloadButton, matchWrap())
        val chooseButton = button("2. Pilih berkas audio") { pickAudio() }
        content.addView(chooseButton, matchWrap())
        transcribeButton = button("3. Mulai transkripsi") { startTranscription() }
        content.addView(transcribeButton, matchWrap())
        status = TextView(this).apply { text = "Status: Menunggu model dan audio."; textSize = 13f; setTextColor(0xFF176B5B.toInt()); setPadding(0, 16, 0, 12) }
        content.addView(status)
        content.addView(label("HASIL TRANSKRIPSI"))
        output = EditText(this).apply { minLines = 12; gravity = Gravity.TOP; textSize = 14f; hint = "Hasil teks bersih dan timestamp akan muncul di sini…"; setPadding(12, 12, 12, 12); setBackgroundColor(0xFFFFFFFF.toInt()) }
        content.addView(output, LinearLayout.LayoutParams(-1, 440))
        content.addView(button("Simpan hasil sebagai TXT") { saveResult() }, matchWrap())
        content.addView(TextView(this).apply { text = "Privasi: audio dan hasil transkripsi tidak dikirim ke server. Koneksi internet hanya digunakan untuk mengunduh model yang dipilih."; textSize = 12f; setTextColor(0xFF65736F.toInt()); setPadding(0, 18, 0, 12) })
        setContentView(root)
    }

    private fun label(s: String) = TextView(this).apply { text = s; textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xFF52645F.toInt()); setPadding(0, 12, 0, 4) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
    private fun button(s: String, action: () -> Unit) = Button(this).apply { text = s; isAllCaps = false; setOnClickListener { action() } }

    private fun modelFile(): File = File(filesDir, "models/ggml-${modelSpinner.selectedItem.toString().lowercase()}.bin")

    private fun downloadModel() {
        val file = modelFile()
        if (file.exists() && file.length() > 1_000_000) { status.text = "Status: Model tersedia (${file.length() / 1_000_000} MB)."; return }
        downloadButton.isEnabled = false
        status.text = "Status: Mengunduh model ${modelSpinner.selectedItem}…"
        executor.execute {
            try {
                file.parentFile?.mkdirs()
                val conn = (URL(MODEL_URLS[modelSpinner.selectedItem.toString()]!!).openConnection() as HttpURLConnection).apply { connectTimeout = 20000; readTimeout = 60000; instanceFollowRedirects = true }
                conn.connect()
                if (conn.responseCode !in 200..299) throw Exception("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                val temp = File(file.parentFile, file.name + ".part")
                conn.inputStream.use { input -> FileOutputStream(temp).use { out ->
                    val buf = ByteArray(1024 * 256); var count: Long = 0; var n: Int
                    while (input.read(buf).also { n = it } >= 0) { if (n == 0) continue; out.write(buf, 0, n); count += n
                        if (total > 0) runOnUiThread { status.text = "Status: Mengunduh ${(count * 100 / total)}%" }
                    }
                } }
                if (!temp.renameTo(file)) throw Exception("Gagal menyimpan model")
                runOnUiThread { status.text = "Status: Model ${modelSpinner.selectedItem} siap (${file.length() / 1_000_000} MB)."; downloadButton.isEnabled = true }
            } catch (e: Exception) { runOnUiThread { status.text = "Gagal mengunduh model: ${e.message}"; downloadButton.isEnabled = true } }
        }
    }

    private fun pickAudio() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }
        startActivityForResult(i, 100)
    }

    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            selectedAudio = data?.data
            status.text = "Audio dipilih: ${selectedAudio?.lastPathSegment ?: "audio"}"
        } else if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri -> contentResolver.openOutputStream(uri)?.use { it.write(lastResult.toByteArray(Charsets.UTF_8)) }; status.text = "Hasil TXT disimpan." }
        }
    }

    private fun startTranscription() {
        val audio = selectedAudio ?: run { status.text = "Pilih audio terlebih dahulu."; return }
        val model = modelFile()
        if (!model.exists()) { status.text = "Unduh model ${modelSpinner.selectedItem} terlebih dahulu."; return }
        transcribeButton.isEnabled = false
        status.text = "Status: Menyalin audio ke cache…"
        executor.execute {
            try {
                runOnUiThread { status.text = "Status: Membaca dan menyiapkan audio…" }
                val samples = decodeAudioTo16kMono(audio)
                if (samples.isEmpty()) throw Exception("Audio kosong atau format tidak didukung")
                runOnUiThread { status.text = "Status: Transkripsi lokal berjalan (${samples.size / 16000} detik audio)…" }
                val result = transcribeNative(model.absolutePath, samples)
                runOnUiThread {
                    lastResult = result
                    output.setText(result)
                    status.text = if (result.startsWith("ERROR:")) result else "Status: Selesai. ${samples.size / 16000} detik audio diproses."
                    transcribeButton.isEnabled = true
                }
            } catch (e: Exception) { runOnUiThread { status.text = "Transkripsi gagal: ${e.message}"; transcribeButton.isEnabled = true } }
        }
    }

    // Decode supported Android media formats to 16 kHz mono float PCM for whisper.cpp.
    private fun decodeAudioTo16kMono(uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(this, uri, null)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) { track = i; format = f; break }
        }
        if (track < 0 || format == null) { extractor.release(); throw Exception("Track audio tidak ditemukan") }
        extractor.selectTrack(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()
        val chunks = ArrayList<FloatArray>()
        var total = 0
        var inputDone = false
        var outputDone = false
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        val info = MediaCodec.BufferInfo()
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) { codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val data = ByteArray(info.size); buffer.get(data)
                            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frameCount = data.size / (bytesPerSample * channels)
                            val mono = FloatArray(frameCount)
                            val bb = java.nio.ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                            for (frame in 0 until frameCount) {
                                var sum = 0f
                                for (ch in 0 until channels) {
                                    val v = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) bb.float else bb.short / 32768f
                                    sum += v
                                }
                                mono[frame] = (sum / channels).coerceIn(-1f, 1f)
                            }
                            chunks.add(mono); total += mono.size
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally { try { codec.stop() } catch (_: Exception) {}; codec.release(); extractor.release() }
        val source = FloatArray(total)
        var at = 0
        for (c in chunks) { System.arraycopy(c, 0, source, at, c.size); at += c.size }
        if (sampleRate == 16000) return source
        val outCount = (source.size.toDouble() * 16000.0 / sampleRate).toInt()
        val resampled = FloatArray(outCount)
        for (i in 0 until outCount) {
            val pos = i.toDouble() * sampleRate / 16000.0
            val left = pos.toInt().coerceIn(0, source.lastIndex)
            val right = (left + 1).coerceAtMost(source.lastIndex)
            val frac = (pos - left).toFloat()
            resampled[i] = source[left] * (1f - frac) + source[right] * frac
        }
        return resampled
    }

    private fun saveResult() {
        if (lastResult.isBlank()) { status.text = "Belum ada hasil untuk disimpan."; return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, "transkripsi_whisper.txt") }
        startActivityForResult(i, 200)
    }

    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
}
