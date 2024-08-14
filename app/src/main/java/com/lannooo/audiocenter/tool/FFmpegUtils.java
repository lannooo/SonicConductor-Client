package com.lannooo.audiocenter.tool;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

public class FFmpegUtils {
    private static final String TAG = "FFmpeg";
    public static void showFFmpegInfo() {
        FFmpegSession session = FFmpegKit.execute("-version");
        if (ReturnCode.isSuccess(session.getReturnCode())) {
            String output = session.getOutput();
            Log.d(TAG, output);
        }
    }
}
