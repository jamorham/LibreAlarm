package com.pimpimmobile.librealarm.shareddata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReadingData {

    public PredictionData prediction;
    public List<GlucoseData> trend;
    public List<GlucoseData> history;
    public byte[] raw_data;

    public ReadingData(PredictionData.Result result) {
        this.prediction = new PredictionData();
        this.prediction.realDate = System.currentTimeMillis();
        this.prediction.errorCode = result;
        this.trend = new ArrayList<>();
        this.history = new ArrayList<>();
        // The two bytes are needed here since some components don't like a null pointer
        this.raw_data = new byte[2];
    }
    

    public ReadingData(PredictionData prediction, List<GlucoseData> trend, List<GlucoseData> history, byte[] data) {
        this.prediction = prediction;
        this.trend = trend;
        this.history = history;
        this.raw_data = Arrays.copyOfRange(data, 0 ,0x158);
    }

    public ReadingData() {}

    public static class TransferObject {
        public long id;
        public ReadingData data;

        public TransferObject() {}

        public TransferObject(long id, ReadingData data) {
            this.id = id;
            this.data = data;
        }
    }
}
