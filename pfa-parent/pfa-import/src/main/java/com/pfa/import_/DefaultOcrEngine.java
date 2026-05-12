package com.pfa.import_;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import com.pfa.core.OcrEngine;
import com.pfa.core.OcrMode;
import com.pfa.core.OcrOptions;
import com.pfa.core.OcrPage;
import com.pfa.core.OcrResult;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * Default OCR engine using Tess4J (Tesseract wrapper).
 * Detects CUDA availability at startup and falls back to CPU.
 */
public class DefaultOcrEngine implements OcrEngine {

    private static final int DEFAULT_DPI = 300;
    private static final String DEFAULT_LANGUAGE = "spa+eng";

    private final OcrMode mode;
    private final ExecutorService ocrPool;
    private final String tessDataPath;
    private final Object renderLock = new Object();

    public DefaultOcrEngine(String tessDataPath) {
        this.tessDataPath = tessDataPath;
        this.mode = detectCudaMode();
        int poolSize = (mode == OcrMode.GPU_CUDA) ? 1 : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.ocrPool = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public OcrResult extract(byte[] pdfBytes, OcrOptions options) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new OcrResult(List.of());
        }

        int dpi = options.dpiOverride().orElse(DEFAULT_DPI);
        String language = options.languageHint().orElse(DEFAULT_LANGUAGE);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            List<Future<OcrPage>> futures = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                final int pageIndex = i;
                futures.add(ocrPool.submit(() -> processPage(renderer, pageIndex, dpi, language)));
            }

            List<OcrPage> pages = new ArrayList<>();
            for (Future<OcrPage> future : futures) {
                pages.add(future.get());
            }

            return new OcrResult(pages);
        } catch (IOException | java.util.concurrent.ExecutionException e) {
            return new OcrResult(List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new OcrResult(List.of());
        }
    }

    @Override
    public OcrMode activeMode() {
        return mode;
    }

    private OcrPage processPage(PDFRenderer renderer, int pageIndex, int dpi, String language) {
        try {
            BufferedImage image;
            synchronized (renderLock) {
                image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
            }

            // Preprocess: convert to grayscale
            BufferedImage grayscale = toGrayscale(image);

            Tesseract tesseract = new Tesseract();
            if (tessDataPath != null && !tessDataPath.isEmpty()) {
                tesseract.setDatapath(tessDataPath);
            }
            tesseract.setLanguage(language);
            tesseract.setPageSegMode(6); // Assume uniform block of text

            String text = tesseract.doOCR(grayscale);
            // Tess4J doesn't expose per-page confidence easily; use 0.85 as default
            double confidence = 0.85;

            return new OcrPage(pageIndex, text, confidence);
        } catch (IOException | TesseractException e) {
            return new OcrPage(pageIndex, "", 0.0);
        }
    }

    private BufferedImage toGrayscale(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        ColorConvertOp op = new ColorConvertOp(
                ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        BufferedImage grayscale = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        op.filter(image, grayscale);
        return grayscale;
    }

    private OcrMode detectCudaMode() {
        try {
            System.loadLibrary("nvcuda");
            return OcrMode.GPU_CUDA;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            return OcrMode.CPU;
        }
    }

    /**
     * Shuts down the OCR thread pool. Call on application exit.
     */
    public void shutdown() {
        ocrPool.shutdown();
    }
}
