package cis5550.jobs;

import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.URLParser;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class PageRank {
	static final String tableName = "pt-crawl", rankTableName = "pt-pageranks", delimiter = ",";
	static final double decayFactor = 0.85;
	public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
		double convergence = 0.1;
		int totalProcessed = 0;
		AtomicInteger converged = new AtomicInteger();
		double cutoff = -1;

		if (args.length > 0) {
			try {
				convergence = Double.parseDouble(args[0]);
				if (args.length == 2) cutoff = Double.parseDouble(args[1]);
			} catch (Exception ignored) {
			}
		}
		FlameRDD rdd = ctx.fromTable(tableName, row -> row.get("url") + "@" + row.get("page"));
		FlamePairRDD state = rdd.mapToPair(s -> {
			int idx = s.indexOf('@');
			String url = s.substring(0, idx);
			String page = s.substring(idx + 1);
			List<String> links = processUrls(getAllUrls(page), url);
			List<String> hashedLinks = new ArrayList<>();
			for (String link: links) hashedLinks.add(Hasher.hash(link));
			return new FlamePair(Hasher.hash(url), "1.0,1.0," + String.join(delimiter, hashedLinks));
		});
		while (true) {
			// Compute and aggregate transfer table
			FlamePairRDD transfer = state.flatMapToPair(p -> {
				List<FlamePair> pairs = new ArrayList<>();
				String[] values = p._2().split(delimiter);
				double cRank = Double.parseDouble(values[0].strip());
				double n =values.length - 2;
				for (int i = 2; i < values.length; i++) {
					pairs.add(new FlamePair(values[i], String.valueOf(cRank * decayFactor / n)));
				}
				// You may want to additionally send rank 0.0 from each vertex to itself,
				// to prevent vertexes with indegree zero from disappearing during the join later on.
				pairs.add(new FlamePair(p._1(), "0.0"));
//                System.out.println(Arrays.toString(pairs.toArray()));
				return pairs;
			}).foldByKey("0", (a, b) -> "" + (Double.parseDouble(a) + Double.parseDouble(b)));

			// Join state and transfer tables
			state = state.join(transfer).flatMapToPair(p -> {
				List<FlamePair> pairs = new ArrayList<>();
				String[] values = p._2().split(delimiter);
				int len = values.length;
				// This is also a good opportunity to add the 0.15 from the rank source.
				values[1] = values[0];
				values[0] = String.valueOf(Double.parseDouble(values[len - 1]) + 0.15);
				values = Arrays.copyOf(values, values.length-1);
				pairs.add(new FlamePair(p._1(), String.join(delimiter, values)));
				return pairs;
			});

			totalProcessed++;

			// calculate the convergence
			double finalConvergence = convergence;
			String max = state.flatMap(p -> {
				String[] values = p._2().split(delimiter);
				double diff = Math.abs(Double.parseDouble(values[0]) - Double.parseDouble(values[1]));
				if (diff < finalConvergence) converged.getAndIncrement();
				return List.of(new String[]{String.valueOf(diff)});
			}).fold("0", (s1, s2) -> Double.parseDouble(s1) >= Double.parseDouble(s2)? s1 : s2);

			// if meet the requirement, quit the loop
			if (Double.parseDouble(max) < convergence) break;
			else if (cutoff >= 0 && (double) (converged.get() / totalProcessed) * 100 >= cutoff) break;
		}


		state.flatMapToPair(p -> {
			KVSClient client = ctx.getKVS();
			Row r = new Row(p._1());
			r.put("rank", p._2().strip().split(delimiter)[0]);
			client.putRow(rankTableName, r);
			return List.of();
		});
	}

	public static List<String> processUrls(List<String> urls, String host) {
		List<String> processed = new ArrayList<>();

		String[] hosts = URLParser.parseURL(host);
		String protocol = hosts[0].toLowerCase(), hostName = hosts[1].toLowerCase(), port = hosts[2], subDomains = hosts[3];
		if (protocol.equals("http") && port == null) port = "80";
		else if (protocol.equals("https") && port == null) port = "443";

		for (String url: urls) {
			if (url.startsWith("http://") || url.startsWith("https://")) {
				processed.add(url);
			} else {
				StringBuilder newUrl = new StringBuilder(protocol).append("://").append(hostName).append(":").append(port);
				if (url.contains("#")) {
					url = url.split("#")[0];
					if (url.isEmpty()) {
						continue;
					} else {
						String[] domains = subDomains.split("/");
						domains[domains.length-1] = url;
						for (String d: domains) if (!d.isEmpty()) newUrl.append("/").append(d);
					}
				} else if (url.startsWith("/")) {
					newUrl.append(url);
				} else if (url.startsWith("..")) {
					String[] domains = subDomains.split("/");
					String[] strs = url.split("/");
					int count = 0;
					String p = strs[0];
					while (p.equals("..")) {
						count++;
						p = strs[count];
					}
					for (int i = 0; i < domains.length-1-count; i++) {
						if (!domains[i].isEmpty()) newUrl.append("/").append(domains[i]);
					}
					for (; count < strs.length; count++) {
						if (!strs[count].isEmpty()) newUrl.append("/").append(strs[count]);
					}
				}
				processed.add(newUrl.toString());
			}

		}
		return processed;
	}

	public static List<String> getAllUrls(String page) throws IOException {
		List<String> links = new ArrayList<>();
		Reader reader = new StringReader(page);
		HTMLEditorKit.Parser parser = new ParserDelegator();
		parser.parse(reader, new HTMLEditorKit.ParserCallback(){
			public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
				if (t == HTML.Tag.A) {
					Object link = a.getAttribute(HTML.Attribute.HREF);
					if (link != null) {
						links.add(String.valueOf(link));
					}
				}
			}
		}, true);

		reader.close();
		return links;
	}
}
