package com.lancontrol.client.monitor;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ScreenService {
    private final Robot robot;
    private final Rectangle screenRect;
    private final ImageWriter writer;

    // khoi tao bot chup man hinh
    public ScreenService() throws AWTException {
        this.robot = new Robot();
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        // Khoi tao ImageWriter cho JPG
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPG writers found");
        this.writer = writers.next();
    }

    // toi uu hoa cho streaming
    public byte[] captureStreaming(float quality) {
        // cho lay 1/4 kich thuoc man hinh
        double scale = 0.5;
        BufferedImage fullImg = robot.createScreenCapture(screenRect);
        int w = (int) (fullImg.getWidth() * scale);
        int h = (int) (fullImg.getHeight() * scale);
        // nen hinh ve kich thuoc nho hon
        BufferedImage scaledImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImg.createGraphics();
        // Dung SCALE_BILINEAR de chat luong hinh tot hon
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(fullImg, 0, 0, w, h, null);
        g.dispose();
        try {
            return compress(scaledImg, quality);
        } catch (IOException e) {
            return null;
        }
    }

    // chup man hinh va nen
    public byte[] captureDownscaledAndCompress(int targetWidth, int targetHeight, float quality) throws IOException {
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = outputImage.createGraphics();
        // Dung NEAREST_NEIGHBOR de giam thieu thoi gian xu ly
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(screenshot, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        return compress(outputImage, quality);
    }

    // nen hinh voi chat luong cho truoc
    private synchronized byte[] compress(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        }
        return baos.toByteArray();
    }
}