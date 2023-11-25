package cis5550.jobs;

import cis5550.external.PorterStemmer;
import cis5550.flame.FlameContext;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Helpers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CalculateTF {
	// Taken from pythons stop words library, also found in other nlp libraries.
	private static Set<String> stopWords = Helpers.loadWordsFromFile("./dictStopWords.txt");;
	private static Set<String> englishWords = Helpers.loadWordsFromFile("./dictWiki100k.txt");

	private static final String documentFrequenciesTable = "pt-document-freq";
	private static final String termFrequenciesTable = "pt-term-freq";


	/**
	 * The purpose of this job is to pre-process all exising documents
	 * from the raw text and calculate the Term frequency, the
	 * document frequency, and to store those values accordingly.
	 * @param context
	 * @param args
	 * @throws Exception
	 */
	public static void run(FlameContext context, String[] args) throws Exception {

		String kvsCoordinator = context.getKVS().getCoordinator();
		int totalDocuments = context.getKVS().count("pt-crawl");
		context.output("totalDocuments: " + totalDocuments);

		context.output("stopWords: " + stopWords.size());
		context.output("englishWords: " + englishWords.size());


		context.fromTable("pt-crawl", row -> {
			String url = row.get("url");
			String pageString = cleanText(row.get("page"));
			KVSClient kvsClient = new KVSClient(kvsCoordinator);

			HashMap<String, Integer> tf = getDocumentTF(pageString);

			String termFreq = Helpers.serializeHashMap(tf);

			try {
				putTermFreq(kvsClient, url, termFreq);

				// Update document frequencies
				for (String word : tf.keySet()) {
					int currentVal = getCurrentDocFreq(kvsClient, word, 0);
					System.out.println("Current value for '" + word + "': " + currentVal);
					putDocumentFreqValue(kvsClient, word, currentVal + 1);
					System.out.println("New value for '" + word + "': " + (currentVal + 1));
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			String output = Hasher.hash(url) + "@" + termFreq;



			System.out.println("output: " + output);
			return "";
		});

//		baseRdd.saveAsTable("test");
	}

	private static String cleanText(String term) {
		if(term != null && !term.isEmpty()) {
			return term.replaceAll("[^a-zA-Z]", " ").toLowerCase();
		}
		return "";
	}

	private static String stemWord(String term) {
		PorterStemmer porterStemmer = new PorterStemmer();
		char[] charArray = term.toLowerCase().trim().toCharArray();
		porterStemmer.add(charArray, charArray.length);
		porterStemmer.stem();
		return porterStemmer.toString();
	}

	private static String[] getWords(String document) {
		if(document == null || document.isEmpty()) return new String[0];

		String[] firstSet = document
			.toLowerCase()
			.split("\\s+");

		return Arrays
			.stream(firstSet)
			.filter(word -> !stopWords.contains(word))
			.filter(word -> !englishWords.contains(word))
			.filter(word -> !word.isEmpty())
			.filter(word -> !(word.length() < 2))
			.map(CalculateTF::stemWord)
			.toArray(String[]::new);
	}

	private static HashMap<String, Integer> getDocumentTF(String documentContent) {
		HashMap<String, Integer> tf = new HashMap<>();
		String[] words = getWords(documentContent);

		// Compute term frequencies for the document
		for (String word : words) {
			tf.put(word, tf.getOrDefault(word, 0) + 1);
		}
		return tf;
	}

	private static void putTermFreq(KVSClient kvs, String url, String docTf) throws IOException {
		String urlHash = Hasher.hash(url);
		System.out.println("Writing tf for '" +urlHash + "': " + docTf);
		Row tfRow = new Row(urlHash);
		tfRow.put("url", url);
		tfRow.put("term_freq", docTf);
		kvs.putRow(termFrequenciesTable, tfRow);
	}

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

		System.out.println("getCurrentDocFreq('" + word + "'): '" + currentValue + "'");

		try {
			return Integer.parseInt(currentValue);
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	private static void putDocumentFreqValue(KVSClient kvs, String word, Integer newValue) throws IOException {
		String key = Hasher.hash(word);
		String value = Integer.toString(newValue);

		Row docTfRow = new Row(key);
		docTfRow.put("word", word);
		docTfRow.put("value", value);
		kvs.putRow(documentFrequenciesTable, docTfRow);
	}
}
