package com.example.attendanceportal;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads Uniclare screenshots with ML Kit's on-device text recognition (the model ships inside the
 * app, so the images never leave the phone) and hands the text to UniclareScreenshotParser.
 */
final class ScreenshotImporter {
    private ScreenshotImporter() { }

    interface Callback {
        void onDone(UniclareScreenshotParser.Result result);
        void onError(Exception e);
    }

    /** Processes the images one after another and calls back on the main thread. */
    static void read(Context context, List<Uri> images, Callback callback) {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        next(context.getApplicationContext(), recognizer, images, 0, new ArrayList<>(), callback);
    }

    private static void next(Context app, TextRecognizer recognizer, List<Uri> images, int index,
                             List<UniclareScreenshotParser.Result> parts, Callback callback) {
        if (index >= images.size()) {
            recognizer.close();
            callback.onDone(UniclareScreenshotParser.merge(parts));
            return;
        }
        InputImage image;
        try {
            image = InputImage.fromFilePath(app, images.get(index));
        } catch (Exception e) {
            recognizer.close();
            callback.onError(e);
            return;
        }
        int width = image.getWidth();
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    parts.add(UniclareScreenshotParser.parse(lines(text), width));
                    next(app, recognizer, images, index + 1, parts, callback);
                })
                .addOnFailureListener(e -> {
                    recognizer.close();
                    callback.onError(e);
                });
    }

    private static List<UniclareScreenshotParser.Line> lines(Text text) {
        List<UniclareScreenshotParser.Line> out = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect b = line.getBoundingBox();
                if (b == null) continue;
                out.add(new UniclareScreenshotParser.Line(line.getText(), b.left, b.top, b.right, b.bottom));
            }
        }
        return out;
    }
}
