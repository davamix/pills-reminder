import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Crops a screenshot and scales it to a width, for the README (P8). Run with the JDK's single-file launcher:
 * java Crop.java <in.png> <out.png> <top px> <bottom px> <width px>
 * It removes <top> pixels (the status bar) and <bottom> pixels (the navigation bar), then scales to <width>.
 */
public class Crop {
    public static void main(String[] args) throws Exception {
        BufferedImage in = ImageIO.read(new File(args[0]));
        int top = Integer.parseInt(args[2]);
        int bottom = Integer.parseInt(args[3]);
        int width = Integer.parseInt(args[4]);
        BufferedImage cropped = in.getSubimage(0, top, in.getWidth(), in.getHeight() - top - bottom);
        int height = Math.round(cropped.getHeight() * (width / (float) cropped.getWidth()));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Two halving steps first, for a smoother result than one big step.
        BufferedImage source = cropped;
        while (source.getWidth() / 2 >= width) {
            BufferedImage half = new BufferedImage(source.getWidth() / 2, source.getHeight() / 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D h = half.createGraphics();
            h.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            h.drawImage(source, 0, 0, half.getWidth(), half.getHeight(), null);
            h.dispose();
            source = half;
        }
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        ImageIO.write(out, "png", new File(args[1]));
        System.out.println(args[1] + ": " + width + "x" + height);
    }
}
