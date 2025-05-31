package com.example.demo.util;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CustomBarcodeProcessor {

    /**
     * Detects and decodes a barcode from the input BufferedImage.
     * This method first converts the image to grayscale, then applies custom noise reduction (Gaussian blur),
     * adaptive thresholding, and edge detection (Sobel). It then attempts barcode decoding on each variant
     * and on multiple rotations (0°, 90°, 180°, 270°).
     *
     * Supports UPC, EAN, Code 128, and Interleaved 2 of 5 (ITF) formats.
     *
     * @param inputImage the input image
     * @return the decoded barcode string, or null if none is found.
     */
    public static String detectBarcode(BufferedImage inputImage) {
        if (inputImage == null) {
            return null;
        }

        // Convert to grayscale.
        BufferedImage gray = toGrayscale(inputImage);
        // Apply noise reduction using a simple Gaussian blur.
        BufferedImage blurred = gaussianBlur(gray);
        // Apply adaptive thresholding.
        BufferedImage thresholded = adaptiveThreshold(blurred, 11, 2);
        // Apply edge detection using a Sobel operator.
        BufferedImage edges = sobelEdgeDetection(thresholded);

        // Try decoding using different pre-processed variants.
        String result = tryDecodeMultipleOrientations(blurred);
        if (result != null) return result;
        result = tryDecodeMultipleOrientations(thresholded);
        if (result != null) return result;
        result = tryDecodeMultipleOrientations(edges);
        if (result != null) return result;
        result = tryDecodeMultipleOrientations(gray);
        return result;
    }

    //IMAGE PROCESSING METHODS

    /**
     * Converts the given image to grayscale using the weighted average method.
     */
    public static BufferedImage toGrayscale(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int grayValue = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                int newRgb = (grayValue << 16) | (grayValue << 8) | grayValue;
                gray.setRGB(x, y, (0xff << 24) | newRgb);
            }
        }
        return gray;
    }

    /**
     * Applies a simple Gaussian blur using a 3×3 kernel.
     */
    public static BufferedImage gaussianBlur(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dst = new BufferedImage(width, height, src.getType());
        // 3x3 Gaussian kernel [1, 2, 1; 2, 4, 2; 1, 2, 1] normalized by 16.
        int[][] kernel = { {1, 2, 1}, {2, 4, 2}, {1, 2, 1} };
        int kernelSum = 16;
        for (int y = 1; y < height - 1; y++){
            for (int x = 1; x < width - 1; x++){
                int sum = 0;
                for (int ky = -1; ky <= 1; ky++){
                    for (int kx = -1; kx <= 1; kx++){
                        int pixel = src.getRGB(x + kx, y + ky) & 0xff;
                        sum += pixel * kernel[ky+1][kx+1];
                    }
                }
                int blurredValue = sum / kernelSum;
                int newRgb = (blurredValue << 16) | (blurredValue << 8) | blurredValue;
                dst.setRGB(x, y, (0xff << 24) | newRgb);
            }
        }
        // Copy border pixels directly.
        for (int x = 0; x < width; x++){
            dst.setRGB(x, 0, src.getRGB(x, 0));
            dst.setRGB(x, height - 1, src.getRGB(x, height - 1));
        }
        for (int y = 0; y < height; y++){
            dst.setRGB(0, y, src.getRGB(0, y));
            dst.setRGB(width - 1, y, src.getRGB(width - 1, y));
        }
        return dst;
    }

    /**
     * Applies adaptive thresholding to convert the image to a binary image.
     * For each pixel, it computes the average over a local block and compares the pixel value against (average C).
     */
    public static BufferedImage adaptiveThreshold(BufferedImage src, int blockSize, double C) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        int half = blockSize / 2;
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int sum = 0;
                int count = 0;
                for (int j = -half; j <= half; j++){
                    for (int i = -half; i <= half; i++){
                        int xx = x + i;
                        int yy = y + j;
                        if (xx >= 0 && xx < width && yy >= 0 && yy < height) {
                            int pixel = src.getRGB(xx, yy) & 0xff;
                            sum += pixel;
                            count++;
                        }
                    }
                }
                int avg = sum / count;
                int pixelVal = src.getRGB(x, y) & 0xff;
                int newValue = (pixelVal < (avg - C)) ? 0 : 255;
                int rgb = (0xff << 24) | (newValue << 16) | (newValue << 8) | newValue;
                dst.setRGB(x, y, rgb);
            }
        }
        return dst;
    }

    /**
     * Applies edge detection using the Sobel operator.
     */
    public static BufferedImage sobelEdgeDetection(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Sobel kernels.
        int[][] gxKernel = { {-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1} };
        int[][] gyKernel = { {-1, -2, -1}, {0, 0, 0}, {1, 2, 1} };

        for (int y = 1; y < height - 1; y++){
            for (int x = 1; x < width - 1; x++){
                int gx = 0;
                int gy = 0;
                for (int j = -1; j <= 1; j++){
                    for (int i = -1; i <= 1; i++){
                        int pixel = src.getRGB(x + i, y + j) & 0xff;
                        gx += gxKernel[j+1][i+1] * pixel;
                        gy += gyKernel[j+1][i+1] * pixel;
                    }
                }
                int g = (int)Math.sqrt(gx * gx + gy * gy);
                if (g > 255) g = 255;
                int newRgb = (g << 16) | (g << 8) | g;
                dst.setRGB(x, y, (0xff << 24) | newRgb);
            }
        }
        // Copy border pixels.
        for (int x = 0; x < width; x++){
            dst.setRGB(x, 0, src.getRGB(x, 0));
            dst.setRGB(x, height - 1, src.getRGB(x, height - 1));
        }
        for (int y = 0; y < height; y++){
            dst.setRGB(0, y, src.getRGB(0, y));
            dst.setRGB(width - 1, y, src.getRGB(width - 1, y));
        }
        return dst;
    }

    /**
     * Rotates the image by the specified angle (degrees: 90, 180, or 270).
     */
    public static BufferedImage rotateImage(BufferedImage src, int angle) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dst;
        if (angle == 90 || angle == 270) {
            dst = new BufferedImage(height, width, src.getType());
        } else if (angle == 180) {
            dst = new BufferedImage(width, height, src.getType());
        } else {
            return src; // 0 degrees.
        }
        for (int y = 0; y < height; y++){
            for (int x = 0; x < width; x++){
                int pixel = src.getRGB(x, y);
                switch(angle) {
                    case 90:
                        dst.setRGB(height - 1 - y, x, pixel);
                        break;
                    case 180:
                        dst.setRGB(width - 1 - x, height - 1 - y, pixel);
                        break;
                    case 270:
                        dst.setRGB(y, width - 1 - x, pixel);
                        break;
                }
            }
        }
        return dst;
    }

    // BARCODE DECODING METHODS

    /**
     * Tries to decode a barcode from the provided image at multiple rotations.
     */
    public static String tryDecodeMultipleOrientations(BufferedImage image) {
        String decoded = tryDecode(image);
        if (decoded != null) return decoded;
        int[] angles = {90, 180, 270};
        for (int angle : angles) {
            BufferedImage rotated = rotateImage(image, angle);
            decoded = tryDecode(rotated);
            if (decoded != null) return decoded;
        }
        return null;
    }

    /**
     * Uses ZXing to decode a barcode from the given BufferedImage.
     */
    private static String tryDecode(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        // Set up decoding hints.
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.UPC_A);
        formats.add(BarcodeFormat.EAN_8);
        formats.add(BarcodeFormat.EAN_13);
        formats.add(BarcodeFormat.CODE_128);
        formats.add(BarcodeFormat.ITF);  // Interleaved 2 of 5
        hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        try {
            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
