package com.lannooo.audiocenter.audio;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;

public class ClientAudioHandler extends AbstractAudioHandler {
    private static final String TAG = "ClientAudioHandler";

    public ClientAudioHandler(Context context, ExecutorService executor) {
        super(context, executor);
    }

    @Override
    public void configure(String outputFile,
                          int duration,
                          boolean enableProcess,
                          boolean isCustom,
                          AudioEventListener listener) {
        // UNPROCESSED and VOICE_RECOGNITION type audio sources do not
        // employ 'AGC' or 'Noise Suppression', while DEFAULT or MIC do.
        int audioSource = getAudioSource(enableProcess);
        int durationMs = (duration == -1) ? -1 : duration * 1000;
        File audioFile = new File(baseDir, outputFile);
        Log.i(TAG, "Recording file: " + audioFile.getAbsolutePath() + ", duration: " + durationMs);
        if (isCustom) {
            recorder = new CustomAudioRecorder(this, audioFile, audioSource);
        } else {
            recorder = new SimpleAudioRecorder(this, audioFile, audioSource, durationMs);
        }
        recorder.setListener(listener);
    }
}
