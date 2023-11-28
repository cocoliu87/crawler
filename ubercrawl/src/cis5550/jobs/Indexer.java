/**
 * Indexer program
 *
 * The purpose of this job is to process every record
 * in the pt-crawl table, to extract the urls and text,
 * and from them to create a reverse index that maps
 * words to urls.
 *
 * Notice that in order to work with complex url
 * characters, it uses Base64 encoding. The document
 * text is filtered, so it does not present any issues.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.jobs;

import cis5550.external.PorterStemmer;
import cis5550.flame.*;
import cis5550.tools.Hasher;
import cis5550.tools.Helpers;

import java.util.*;

public class Indexer {

	// Stop words, taken from 
	private static final Set<String> stopWords = new HashSet<>(List.of("a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in", "into", "is", "isnt", "it", "its", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well", "were", "weve", "were", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves"));

	/**
	 * As a first step, create the class for the indexer, and, in its run method.
	 * @param context
	 * @param args
	 * @throws Exception
	 */
	public static void run(FlameContext context, String[] args) throws Exception {
		/**
		 * Load the data from the pt-crawl table. Ideally, we would like a PairRDD of (u, p) pairs,
		 * where u is a normalized URL and p is the contents of the corresponding page,
		 * but we only have a fromTable for normal RDDs.
		 */


		/**
		 * First, use fromTable and map each Row of the pt-crawl table to a string u,p,
		 * where u is the URL and p the page, and then use mapToPair to convert this
		 * into a PairRDD again.
		 */

		FlamePairRDD flamePairRDD = context.fromTable(
			"pt-crawl",
			// we would like a PairRDD of (u, p) pairs,
			row -> {
				return
					row.get("url") +  // where u is the URL
					"@" +
					row.get("page");  // and p the page
			}
		)
		// And then use mapToPair
		.mapToPair(pairString -> {
			try {
				String[] pairParts = pairString.split("@", 2);
				// It helps avoid insertion errors with strange urls
				String url = Helpers.encode64(pairParts[0]);
				String page = pairParts[1];

				if(!url.isEmpty() && !page.isEmpty()) {
					return new FlamePair(url, page);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			return new FlamePair("", "");
		});


		/**
		 * Next, create the inverted index. This involves two simple steps.
		 * First, we need to convert each (u, p) pair to lots of (w, u) pairs,
		 * where w is a word that occurs in p. You can use flatMapToPair for this.
		 */
		flamePairRDD.flatMapToPair(kvPair -> {
				// First, we create a set of FlamePairs, which is our output
				// A hashmap would be faster, but
				HashSet<FlamePair> wordUrlPairs = new HashSet<>();
				// Gather the url, and the page
				String url = kvPair._1().trim(); // Key: url
				String page = kvPair._2().trim(); // Value: page contents

				/**
				 * Make sure to filter out all HTML tags, to remove all punctuation,
				 * and to convert everything to lower case.
				 */
				// Filter out all html tags
				page = page.replaceAll("<[^>]*>", "");
				// Remove all punctuation
				page = page.replaceAll("\\p{Punct}", ""); // !”#$%&'()*+,-./:;<=>?@[\]^_`{|}~:
				// convert everything to lower case
				page = page.toLowerCase();

				/**
				 * The resulting PairRDD will still contain lots of (w, ui) pairs,
				 * with the same word w but different URLs ui.
				 */
				// Break the page contents into words
				Arrays.stream(page.split("\\s+")).forEach(word -> {

					// If the word is null, then make it an empty string
					String cleanWord = word != null
						? stemWord(word.trim().toLowerCase())
						: "";

					if(!cleanWord.isEmpty() && !stopWords.contains(cleanWord)) {
						if(url != null && !url.isEmpty()) {
							// For each word, crate a unique pair. Uniqueness guaranteed by HashSet.
							wordUrlPairs.add(
								// Note that the word is now hashed
								new FlamePair(Hasher.hash(cleanWord), url)
							);
						}
					}

				});
				// Output the unique set for folding
				return wordUrlPairs;
			})
			/**
			 * We’ll need to fold all the URLs into a single
			 * comma-separated list, using foldByKey.
			 */
			.foldByKey(
				"",
				(a, b) ->
					// Lastly we just have to join with a comma
					a.isEmpty()
						? a + b // If empty, we just put them together
						: a + "," + b // If not, then separate
			)
			/**
			 * This should produce the required data; you can rename the final PairRDD
			 * to pt-index using the saveAsTable method.
			 */
			.saveAsTable("pt-index");
	}

	public static String stemWord(String word) {
		PorterStemmer s = new PorterStemmer();
		for (char ch: word.toCharArray()) {
			if (Character.isLetter(ch)) {
				s.add(ch);
			}
		}
		s.stem();
		return s.toString();
	}
}
