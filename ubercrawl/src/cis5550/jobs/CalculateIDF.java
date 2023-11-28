/**
 * Inverted document frequency calculator program.
 *
 * The purpose of this job is to calculate the IDF value
 * of all scanned documents in the RDD.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import java.io.IOException;

public class CalculateIDF {

	private static final String idfTable = "pt-term-idf";

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


		/**
		 * For every word in pt-document-freq, get its
		 * value and perform the calculation based on the
		 * total number of documents. Then it saves the
		 * results in the pt-term-idf table.
		 */
		context.fromTable("pt-document-freq", row -> {
			String word = row.get("word");
			String dfValue = row.get("value");

			if(word != null && !word.isEmpty()) {
				KVSClient kvsClient = new KVSClient(kvsCoordinator);

				double df = Double.parseDouble(dfValue);
				double idfValue = Math.log((double) totalDocuments / (double) (df + 1));

				try {
					String key = Hasher.hash(word);
					String value = Double.toString(idfValue);

					Row idfRow = new Row(key);
					idfRow.put("word", word);
					idfRow.put("value", value);
					kvsClient.putRow(idfTable, idfRow);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				System.out.println("word: " + word + ", value: " + dfValue + ", idfValue: " + idfValue);
			}

			return "";
		});
	}
}
