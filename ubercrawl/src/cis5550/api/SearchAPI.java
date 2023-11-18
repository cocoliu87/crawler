package cis5550.api;

import java.io.FileInputStream;
import java.io.IOException;
import static cis5550.webserver.Server.*;
import cis5550.tools.Logger;

class SearchAPI {

    private static final Logger logger = Logger.getLogger(SearchAPI.class);

    private static String mockSearch() throws IOException {
        FileInputStream fis = new FileInputStream("ubercrawl/static/MockData.json");
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
        // Initialize defautl port
        if(args.length == 0) {
            port(8081);
        }
        // Allow for changing the port
        else if (args.length > 0) {
            port(Integer.parseInt(args[0]));
        }

        // Allow static file render when provided a path
        if (args.length == 2) {
            String staticFilesPath = args[1];
            System.out.println("Static Folder: " + staticFilesPath);
            staticFiles.location(staticFilesPath);
        }

        post("/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            return mockSearch();
        });

        get("/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            return mockSearch();
        });
    }
}