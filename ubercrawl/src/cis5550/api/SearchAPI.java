package cis5550.api;

import java.io.FileInputStream;
import java.io.IOException;
import static cis5550.webserver.Server.*;
import cis5550.tools.Logger;

class SearchAPI {

    private static final Logger logger = Logger.getLogger(SearchAPI.class);

    private static String mockSearch(String mockDataFile) throws IOException {
        FileInputStream fis = new FileInputStream(mockDataFile);
        byte[] buffer = new byte[10];
        StringBuilder sb = new StringBuilder();
        while (fis.read(buffer) != -1) {
            sb.append(new String(buffer));
            buffer = new byte[10];
        }
        fis.close();
        return sb.toString();
    }


    public static void main(String args[]) throws IOException {
        // Initialize port
        port(Integer.parseInt(args[0]));
        // Initialize Mock data file path
        String mockDataFile = args[1];

        staticFiles.location("static");

        post("/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            return mockSearch(mockDataFile);
        });

        get("/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            return mockSearch(mockDataFile);
        });
    }
}