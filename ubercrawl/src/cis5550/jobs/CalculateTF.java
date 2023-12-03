/**
 * Term frequency and document frequency calculator program.
 *
 * The purpose of this job is to pre-process all exising documents
 * from the raw text and calculate the Term frequency, the
 * document frequency, and to store those values accordingly.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Helpers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;



public class CalculateTF {
	private static final String documentFrequenciesTable = "pt-document-freq";
	private static final String termFrequenciesTable = "pt-term-freq";


	/**
	 * Main function or entry point.
	 *
	 * @param context - The FlameContext object it is being run under.
	 * @param args - The arguments for this job.
	 * @throws Exception
	 */
	public static void run(FlameContext context, String[] args) throws Exception {

		// Retrieve the coordinator
		String kvsCoordinator = context.getKVS().getCoordinator();
		HashSet<String> englishWords;

		// Load English Words if provided
		if(args.length > 0 && args[0] != null && !args[0].isEmpty()) {
			englishWords = Helpers.loadWordsFromFile(args[0]);
		} else {
            englishWords = new HashSet<>();
        }

        if(englishWords.isEmpty()) {
			context.output("English words file not found: " + args[0] + ". Needs to be first argument.");
			return;
		}

		/**
		 * Main loop, for every row in the pt-crawl table it
		 * performs the TF/DF calculations.
		 *
		 */
		context.fromTable("pt-crawl", row -> {

			// Retrieve the url columnm and page column
			String url = row.get("url");
			String rawText = row.get("page");

			if(rawText == null || rawText.isEmpty()) return "";


			// Initialize KVS client
			KVSClient kvsClient = new KVSClient(kvsCoordinator);

			// Calculate the current document's TF and serialize
			HashMap<String, Integer> tf = getDocumentTF(rawText, englishWords);
			String termFreq = Helpers.serializeHashMap(tf);
			StringBuilder entryBody = new StringBuilder();

			System.out.println("TermFreq: " + tf);


			// Next try to put term frequency into RDD
			try {
				// Retrieve the page rank value for this url
//				String pageRank = Helpers.getKvsDefault(
//					kvsClient,
//					"pt-pageranks",
//					Hasher.hash(url),
//					"rank",
//					"0.0"
//				);

				// Generate cache and page rank values
				String textCache = rawText.substring(0, Math.min(500, rawText.length()));
				String pageRank = "0.0";

				/**
				 * Now combine both PR and TF into a formatted string for
				 * "deserialization". This is going to help make thousands less
				 * network calls. Then insert document into RDD.
				 */
				entryBody
					.append(pageRank)
					.append("@")
					.append(termFreq)
					.append("@")
					.append(textCache);

				putTermFreq(kvsClient, url, entryBody.toString());

				/**
				 * Next we need to get and update the current document frequency (TF)
				 */
				for (String word : tf.keySet()) {
					int currentVal = getCurrentDocFreq(kvsClient, word, 0);
//					System.out.println("Current value for '" + word + "': " + currentVal);
					putDocumentFreqValue(kvsClient, word, currentVal + 1);
//					System.out.println("New value for '" + word + "': " + (currentVal + 1));
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			System.out.println("output: " + Hasher.hash(url) + " >> " + entryBody.toString());
			return "";
		});

	}

	/**
	 * Calculates a document's term frequency
	 * @param documentContent - Raw text of the document
	 * @return
	 */
	private static HashMap<String, Integer> getDocumentTF(String documentContent,  HashSet<String> englishWords) {
		HashMap<String, Integer> tf = new HashMap<>();
		String[] words = Helpers.getWords(documentContent, englishWords);

		// Compute term frequencies for the document
		for (String word : words) {
			tf.put(word, tf.getOrDefault(word, 0) + 1);
		}
		return tf;
	}

	/**
	 * Saves the term frequency into the RDD
	 * @param kvs - The kvs client to save to
	 * @param url - The url of the page being processed
	 * @param docTf - The document's serialized term frequency map
	 * @throws IOException
	 */
	private static void putTermFreq(KVSClient kvs, String url, String docTf) throws IOException {
		String urlHash = Hasher.hash(url);
//		System.out.println("Writing tf for '" +urlHash + "': " + docTf);
		Row tfRow = new Row(urlHash);
		tfRow.put("url", url);
		tfRow.put("term_freq", docTf);
		kvs.putRow(termFrequenciesTable, tfRow);
	}

	/**
	 * Retrieve the current document frequency for a word
	 * @param kvs - The kvs client
	 * @param word - The word
	 * @param defaultVal - A default value in case it can't find it.
	 * @return
	 * @throws IOException
	 */
	private static Integer getCurrentDocFreq(KVSClient kvs, String word, int defaultVal) throws IOException {
		String currentValue = "0";
		String key = Hasher.hash(word);
		try {
			currentValue = new String(
				kvs.get(
					documentFrequenciesTable,
					key,
					"value"
				),
				StandardCharsets.UTF_8
			);
		} catch (Exception e) {
			currentValue = "0";
		}

//		System.out.println("getCurrentDocFreq('" + word + "'): '" + currentValue + "'");

		try {
			return Integer.parseInt(currentValue);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * Updates the document frequency of a word
	 * @param kvs - The kvs client to use
	 * @param word - the word to update
	 * @param newValue - The new value to save
	 * @throws IOException
	 */
	private static void putDocumentFreqValue(KVSClient kvs, String word, Integer newValue) throws IOException {
		String key = Hasher.hash(word);
		String value = Integer.toString(newValue);

		Row docTfRow = new Row(key);
		docTfRow.put("word", word);
		docTfRow.put("value", value);
		kvs.putRow(documentFrequenciesTable, docTfRow);
	}
}
