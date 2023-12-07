// import cis5550.flame.FlameContext;
// import cis5550.kvs.KVSClient;
// import cis5550.kvs.Row;

// import java.io.IOException;
// import java.nio.charset.StandardCharsets;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.HashSet;
// import java.util.Iterator;

// public class Indexer {
//     public static void run(FlameContext ctx, String[] args) throws Exception {
//         KVSClient kvs = ctx.getKVS();

//         // Iterate through workers and fetch data for each row individually
//         for (int i = 0; i < kvs.numWorkers(); i++) {
//             String workerAddress = kvs.getWorkerAddress(i);

//             // Fetch rows from pt-crawl table for the current worker
//             String tableName = "pt-crawl";
//             Iterator<Row> rowIterator = fetchRowsFromWorker(kvs, tableName, workerAddress);

//             // Process rows and perform indexing
//             while (rowIterator.hasNext()) {
//                 Row row = rowIterator.next();
//                 String url = row.get("url");
//                 String page = row.get("page");

//                 // Normalize the page content
//                 page = page.replaceAll("<.*?>", "").replaceAll("[.,:;!?’\"()-]", "").toLowerCase();
//                 HashSet<String> uniqueWords = new HashSet<>(Arrays.asList(page.split("\\s+")));

//                 // Insert (word, url) pairs into pt-index
//                 for (String word : uniqueWords) {
//                     // Normalize the word and remove whitespace
//                     word = word.replaceAll(" ", "");

//                     // Skip short words
//                     if (word.length() <= 2) continue;

//                     // Fetch the current index value for the word
//                     byte[] currentValueBytes = kvs.get("pt-index", word, "url");
//                     String currentValue = currentValueBytes == null ? "" : new String(currentValueBytes, StandardCharsets.UTF_8);

//                     // Append the new URL to the existing value if it's not already included
//                     String newValue = currentValue.contains(url) ? currentValue : currentValue + (currentValue.isEmpty() ? "" : ",") + url;
//                     kvs.put("pt-index", word, "url", newValue.getBytes(StandardCharsets.UTF_8));
//                 }
//             }
//         }

//         // After saving the pt-index table, fetch and print its content
//         printIndexTableContent(kvs);
//     }

//     private static Iterator<Row> fetchRowsFromWorker(KVSClient kvs, String tableName, String workerAddress) {
//         try {
//             // Modified to pass the workerAddress if necessary
//             return kvs.scan(tableName, workerAddress, null);
//         } catch (IOException e) {
//             // Handle the exception (e.g., log the error)
//             e.printStackTrace();
//             return Collections.emptyIterator();
//         }
//     }

//     private static void printIndexTableContent(KVSClient kvs) {
//         try {
//             Iterator<Row> indexTableIterator = kvs.scan("pt-index", null, null);
//             System.out.println("Content of pt-index table:");
//             while (indexTableIterator.hasNext()) {
//                 Row indexRow = indexTableIterator.next();
//                 String key = indexRow.key();
//                 String value = indexRow.columns().iterator().next(); // Assuming there's only one column per row
//                 System.out.println(key + " => " + value);
//             }
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }
// }
