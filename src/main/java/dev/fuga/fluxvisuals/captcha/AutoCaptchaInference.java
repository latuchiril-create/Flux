package dev.fuga.fluxvisuals.captcha;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class AutoCaptchaInference {
    private static final String MODEL_PATH = "/assets/fluxvisuals/models/best.onnx";
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Flux-AutoCaptcha-Inference");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile OrtEnvironment environment;
    private static volatile OrtSession session;
    private static CompletableFuture<Void> initFuture;
    private static volatile boolean closed;

    private AutoCaptchaInference() {
    }

    public static CompletableFuture<Void> initAsync() {
        synchronized (LOCK) {
            if (session != null) {
                return CompletableFuture.completedFuture(null);
            }
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("AutoCaptcha inference runtime is closed"));
            }
            if (initFuture == null) {
                initFuture = CompletableFuture.runAsync(AutoCaptchaInference::loadModelInternal, EXECUTOR);
                initFuture.whenComplete((v, throwable) -> {
                    if (throwable != null) {
                        synchronized (LOCK) {
                            initFuture = null;
                        }
                    }
                });
            }
            return initFuture;
        }
    }

    public static boolean isReady() {
        return session != null && !closed;
    }

    public static Future<?> submit(Runnable runnable) {
        if (closed) {
            throw new IllegalStateException("AutoCaptcha inference runtime is closed");
        }
        return EXECUTOR.submit(runnable);
    }

    public static float[][] runInference(float[] inputData) throws Exception {
        if (inputData == null) {
            throw new IllegalArgumentException("inputData");
        }
        synchronized (LOCK) {
            OrtEnvironment env = environment;
            OrtSession sess = session;
            if (closed || env == null || sess == null) {
                throw new IllegalStateException("AutoCaptcha model is not ready");
            }
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), new long[]{1L, 3L, 640L, 640L})) {
                try (OrtSession.Result result = sess.run(Collections.singletonMap("images", inputTensor))) {
                    OnnxTensor outputTensor = (OnnxTensor) result.get(0);
                    float[][][] raw = (float[][][]) outputTensor.getValue();
                    return raw[0];
                }
            }
        }
    }

    public static void close() {
        closed = true;
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(3L, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            EXECUTOR.shutdownNow();
        }
        synchronized (LOCK) {
            OrtSession sess = session;
            session = null;
            initFuture = null;
            if (sess != null) {
                try {
                    sess.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void loadModelInternal() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("flux_best_shared", ".onnx");
            try (InputStream is = AutoCaptchaInference.class.getResourceAsStream(MODEL_PATH)) {
                if (is == null) {
                    throw new IllegalStateException("AutoCaptcha model not found at " + MODEL_PATH);
                }
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession sess;
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                sess = env.createSession(tempFile.getAbsolutePath(), opts);
            }
            synchronized (LOCK) {
                if (closed) {
                    sess.close();
                    throw new IllegalStateException("AutoCaptcha runtime closed while loading");
                }
                environment = env;
                session = sess;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load AutoCaptcha model", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (Exception ignored) {
                }
            }
        }
    }
}
