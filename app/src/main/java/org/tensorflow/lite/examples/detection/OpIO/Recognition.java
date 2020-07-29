package org.tensorflow.lite.examples.detection.OpIO;

import android.graphics.RectF;

import com.google.gson.Gson;

import java.io.Serializable;

/** An immutable result returned by a Classifier describing what was recognized. */
public class Recognition implements Serializable {
    public static class RectFF extends RectF implements Serializable {
    }

    private final String id;

    /** Display name for the recognition. */
    private final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Lower should be better.
     */
    private final Float distance;
    private float[][] extra;

    /** Optional location within the source image for the location of the recognized object. */
    private  RectFF location;
    private Integer color;

    public Recognition(
            final String id, final String title, final Float distance, final RectF location) {
        this.id = id;
        this.title = title;
        this.distance = distance;
        this.location = new  Recognition.RectFF();

        this.location.set(location);
        this.color = null;
        this.extra = null;
    }

    public void setExtra(float[][] extra) {
        this.extra = extra;
    }
    public float[][] getExtra() {
        return this.extra;
    }
    public void setColor(Integer color) {
        this.color = color;
    }
    public String getId() {
        return id;
    }
    public String getTitle() {
        return title;
    }
    public Float getDistance() {
        return distance;
    }
    public RectF getLocation() {
        return new RectF(location);
    }

    public void setLocation(RectF location) {
        this.location = new  Recognition.RectFF();
        this.location.set(location);
    }

    @Override
    public String toString() {
        String resultString = "";
        if (id != null) {
            resultString += "[" + id + "] ";
        }

        if (title != null) {
            resultString += title + " ";
        }

        if (distance != null) {
            resultString += String.format("(%.1f%%) ", distance * 100.0f);
        }

        if (location != null) {
            resultString += location + " ";
        }

        return resultString.trim();
    }

    public Integer getColor() {
        return this.color;
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static Recognition fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, Recognition.class);
    }
}
