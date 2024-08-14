package com.lannooo.audiocenter.audio;

import java.io.File;

public interface AudioEventListener {
    void onRecordStart();
    void onRecordStop(File outputFile);
}
