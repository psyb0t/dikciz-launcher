package eu.psyb0t.dikciz.fixture;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Arrays;

/** Controlled external recognizer boundary for callback and lifecycle tests. */
public final class FixtureRecognitionService extends RecognitionService {
    private static final long RESPONSE_DELAY_MS = 250L;
    private static final long SLOW_RESPONSE_DELAY_MS = 10000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;

    @Override
    protected void onStartListening(Intent intent, Callback callback) {
        String language = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        pending = () -> {
            try {
                if ("zz".equals(language)) {
                    callback.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE);
                    return;
                }
                Bundle results = new Bundle();
                results.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                    new ArrayList<>(Arrays.asList("Fixture recognized speech", "Fixture alternative")));
                results.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, new float[]{0.9f, -1f});
                callback.results(results);
            } catch (RemoteException exception) {
                android.util.Log.w("DikcizSpeechFixture", "Recognition client disconnected", exception);
            }
        };
        handler.postDelayed(pending, "fr".equals(language) ? SLOW_RESPONSE_DELAY_MS : RESPONSE_DELAY_MS);
    }

    @Override
    public void onCheckRecognitionSupport(Intent intent, SupportCallback callback) {
        String language = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        pending = () -> {
            if ("zz".equals(language)) {
                callback.onError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED);
                return;
            }
            callback.onSupportResult(new android.speech.RecognitionSupport.Builder()
                .setInstalledOnDeviceLanguages(Arrays.asList("en-US", "en-GB", "de-DE", "es-ES", "fr-FR", "it-IT", "pt-BR"))
                .setPendingOnDeviceLanguages(Arrays.asList("ja-JP"))
                .setSupportedOnDeviceLanguages(Arrays.asList("ko-KR"))
                .setOnlineLanguages(Arrays.asList("en-US", "de-DE"))
                .build());
        };
        handler.postDelayed(pending, "fr".equals(language) ? SLOW_RESPONSE_DELAY_MS : RESPONSE_DELAY_MS);
    }

    @Override
    public void onTriggerModelDownload(
        Intent intent,
        android.content.AttributionSource attributionSource,
        android.speech.ModelDownloadListener listener
    ) {
        String language = intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE);
        if ("zz".equals(language)) {
            listener.onError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED);
            return;
        }
        if ("es".equals(language)) {
            pending = listener::onScheduled;
            handler.postDelayed(pending, RESPONSE_DELAY_MS);
            return;
        }
        listener.onProgress(15);
        pending = () -> {
            listener.onProgress(100);
            listener.onSuccess();
        };
        long delay = "fr".equals(language) ? SLOW_RESPONSE_DELAY_MS : "de".equals(language) ? 3000L : RESPONSE_DELAY_MS;
        handler.postDelayed(pending, delay);
    }

    @Override
    protected void onStopListening(Callback callback) {
        clearPending();
    }

    @Override
    protected void onCancel(Callback callback) {
        clearPending();
    }

    @Override
    public void onDestroy() {
        clearPending();
        super.onDestroy();
    }

    private void clearPending() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
    }
}
