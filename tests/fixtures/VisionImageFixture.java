import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class VisionImageFixture {
    public static void main(String[] arguments) throws Exception {
        BufferedImage image = new BufferedImage(1400, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            String[] glyphs = {
                "11111/10000/10000/11110/10000/10000/10000",
                "01110/00100/00100/00100/00100/00100/01110",
                "10001/10001/01010/00100/01010/10001/10001",
                "11111/00100/00100/00100/00100/00100/00100",
                "10001/10001/10001/10001/10001/10001/01110",
                "11110/10001/10001/11110/10100/10010/10001",
                "11111/10000/10000/11110/10000/10000/11111",
                "00000/00000/00000/00000/00000/00000/00000",
                "10010/10010/10010/11111/00010/00010/00010",
                "01110/10001/00001/00010/00100/01000/11111",
                "01110/10001/10001/01110/10001/10001/01110"
            };
            int scale = 16;
            for (int index = 0; index < glyphs.length; index++) {
                String[] rows = glyphs[index].split("/");
                for (int y = 0; y < rows.length; y++) {
                    for (int x = 0; x < rows[y].length(); x++) {
                        if (rows[y].charAt(x) == '1') graphics.fillRect(60 + index * 112 + x * scale, 140 + y * scale, scale, scale);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", new File(arguments[0]))) throw new IllegalStateException("PNG encoder unavailable");
    }
}
