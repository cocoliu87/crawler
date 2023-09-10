package cis5550.filehandler;

import cis5550.tools.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Reader {
    static Logger log = Logger.getLogger(Reader.class);

    public String ReadFileToString(String fileName) {
        File f = new File(fileName);
        if (f.exists() && f.canRead()) {
            return readUsingFiles(fileName);
        } else {
            log.warn("The file " + fileName + "is not readable or existing");
            return "";
        }
    }

    private static String readUsingFiles(String fileName) {
        try {
            return new String(Files.readAllBytes(Paths.get(fileName)));
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
