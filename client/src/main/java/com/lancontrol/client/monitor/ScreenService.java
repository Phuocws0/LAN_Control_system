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
    private final ImageWriter writer; // Khởi tạo 1 lần để dùng lại

    public ScreenService() throws AWTException {
        this.robot = new Robot();
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

        // Tìm và giữ lại ImageWriter để không phải tìm lại mỗi frame
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No JPG writers found");
        this.writer = writers.next();
    }

    /**
     * TỐI ƯU CHO STREAMING: Sử dụng Graphics2D và thuật toán nhanh hơn
     */
    public byte[] captureStreaming(float quality) {
        // Chỉ nên lấy 40-50% độ phân giải để đảm bảo mượt
        double scale = 0.5;
        BufferedImage fullImg = robot.createScreenCapture(screenRect);

        int w = (int) (fullImg.getWidth() * scale);
        int h = (int) (fullImg.getHeight() * scale);

        // Sử dụng TYPE_INT_RGB (không có Alpha) để nén nhanh hơn
        BufferedImage scaledImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaledImg.createGraphics();

        // Dùng VALUE_INTERPOLATION_NEAREST_NEIGHBOR nếu muốn cực nhanh,
        // hoặc BILINEAR để cân bằng (đừng dùng BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(fullImg, 0, 0, w, h, null);
        g.dispose();

        try {
            return compress(scaledImg, quality);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * TỐI ƯU CHO THUMBNAIL: Bỏ hoàn toàn SCALE_SMOOTH
     */
    public byte[] captureDownscaledAndCompress(int targetWidth, int targetHeight, float quality) throws IOException {
        BufferedImage screenshot = robot.createScreenCapture(screenRect);

        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = outputImage.createGraphics();
        // Thumbnail nhỏ nên dùng NEAREST_NEIGHBOR cho nhanh nhất có thể
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(screenshot, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        return compress(outputImage, quality);
    }

    /**
     * HÀM NÉN JPEG TỐI ƯU
     */
    private synchronized byte[] compress(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Không dùng try-with-resources cho writer để tránh dispose nhầm
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