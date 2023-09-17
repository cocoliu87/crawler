package cis5550.filehandler;

import cis5550.tools.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Reader {
    static Logger log = Logger.getLogger(Reader.class);

    public byte[] ReadTxtFile(String fileName) {
        File f = new File(fileName);
        if (f.exists() && f.canRead()) {
            return readUsingFiles(fileName);
        } else {
            log.warn("The file " + fileName + "is not readable or existing");
            return null;
        }
    }

    public byte[] ReadImageFile(String fileName) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(new File(fileName));
        WritableRaster raster = bufferedImage.getRaster();
        return ((DataBufferByte)raster.getDataBuffer()).getData();
    }

    public byte[] ReadBinaryFile(String fileName) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        byte[] buffer = new byte[2048];
        InputStream in = new FileInputStream(fileName);
        int count = in.read(buffer);
        while (count != -1) {
            d.write(buffer, 0, count);
            count = in.read(buffer);
        }
        return b.toByteArray();
    }

    private static byte[] readUsingFiles(String fileName) {
        try {
            return Files.readAllBytes(Paths.get(fileName));
        } catch (IOException e) {
            log.error("reading file has error", e);
            return null;
        }
    }


    private static String readUsingBufferedReaderCharArray(String fileName) {
        BufferedReader reader = null;
        StringBuilder stringBuilder = new StringBuilder();
        char[] buffer = new char[10];
        try {
            reader = new BufferedReader(new FileReader(fileName));
            while (reader.read(buffer) != -1) {
                stringBuilder.append(new String(buffer));
                buffer = new char[10];
            }
        } catch (IOException e) {
            log.error("reading file has error", e);
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                    log.error("closing file reader has error", e);
                }
        }

        return stringBuilder.toString();
    }
}
