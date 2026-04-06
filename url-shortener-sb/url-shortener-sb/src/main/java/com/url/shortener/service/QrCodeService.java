package com.url.shortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    private static final int DEFAULT_SIZE = 280;

    public byte[] generateQRCode(String url) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new MultiFormatWriter().encode(
                    url,
                    BarcodeFormat.QR_CODE,
                    DEFAULT_SIZE,
                    DEFAULT_SIZE,
                    hints
            );

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Unable to generate QR code", ex);
        }
    }

    public String generateQRCodeBase64(String url) {
        return Base64.getEncoder().encodeToString(generateQRCode(url));
    }
}

