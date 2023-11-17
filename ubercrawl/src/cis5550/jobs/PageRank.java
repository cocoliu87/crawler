package cis5550.jobs;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.kvs.KVSClient;
import cis5550.tools.Hasher;
import cis5550.tools.URLParser;

public class PageRank {
	public static String normalize(String s, String base) {
		if (s.indexOf("#") != -1) {
			s = s.substring(0, s.indexOf("#"));
		}
		if (s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".txt"))
		if (s.equals("")) {
			return base;
		}
		String[] parse1 = URLParser.parseURL(s);
		String[] parse2 = URLParser.parseURL(base);
		String protocal1 = parse1[0];
		String host1 = parse1[1];
		String port1 = parse1[2];
		String whole1 = parse1[3];
		String protocal2 = parse2[0];
		String host2 = parse2[1];
		String port2 = parse2[2];
		String whole2 = parse2[3];
		String ret = "";

		if (protocal1 != null){
			if (protocal1.equals("http") || protocal1.equals("https")) {
				ret += protocal1 + ":";
			} else {
				System.out.println("something wrong1, base: "+base+", new_uri: "+s);
				return "";
			}
		} else if (protocal2 != null) {
			if (protocal2.equals("http") || protocal2.equals("https")) {
				ret += protocal2 + ":";
			} else {
				System.out.println("something wrong2, base: "+base+", new_uri: "+s);
				return "";
			}
		} else {
			System.out.println("something wrong3, base: "+base+", new_uri: "+s);
			return "";
		}
		if (host1 != null) {
			ret += "//" + host1;
		} else if (host2 != null){
			ret += "//" + host2;
		} else {
			System.out.println("something wrong, base: "+base+", new_uri: "+s);
			return "";
		}
		if (port1 != null) {
			ret += ":" + port1;
		} else if (port2 != null){
			ret += ":" + port2;
		} else {
			ret += ret.contains("https") ? ":443" : ":80";
		}
		if (whole1 == null) {
			return base;
		} else {
			if (whole1.startsWith("/")) {
				ret += whole1;
			} else {
				String[] tmp = (whole2.substring(0, whole2.lastIndexOf("/")) + "/" + whole1).split("/");
				LinkedList<String> url_parts = new LinkedList<String>();
				for (int i=0; i<tmp.length; i++) {
					if (tmp[i].equals("")) {
						continue;
					}
					if (!tmp[i].equals("..")) {
						url_parts.add(tmp[i]);
					} else {
						if (url_parts.size() == 0) {
							return "";
						}
						url_parts.removeLast();
					}
				}
				ret += "/" + String.join("/", url_parts);
			}
		}
		return ret;
	}

	public static List<String> find_url(String content) {
		String regex = "<[aA](.*?)>";
	    Pattern pattern = Pattern.compile(regex);
	    LinkedList<String> list = new LinkedList<String>();
	    Matcher matcher = pattern.matcher(content);
	    LinkedList<String> ret = new LinkedList<String>();
	    while (matcher.find()) {
	    	String possible_groups = matcher.group(1);
	    	for (String group: possible_groups.split(" ")) {
	    		String[] group_list = group.replaceAll(" ", "").split("=");
	    		if (group_list.length == 2 && group_list[0].equals("href")) {
	    			ret.add(group_list[1].replaceAll("'", "").replaceAll("\"", ""));
	    		}
	    	}
	    }
	    return ret;
	}
	
	public static void run(FlameContext ctx, String[] arr) {
		ctx.output("in PageRank run");
		Double threshold = 0.01;
		if (arr.length != 0) {			
			try {
				threshold = Double.valueOf(arr[0]);				
			} catch (Exception e) {
				System.out.println("the first argument is not a double(threshold)");
				e.printStackTrace();
			}
		}
		try {
			FlamePairRDD flame = ctx.fromTable("pt-crawl", r -> r.get("url") +","+r.get("page"))
					.mapToPair(s -> {
						int i = s.indexOf(",");
						String page = s.substring(i+1);
						String L = "";
						for (String line: page.split("\n")) {
							List<String> new_raw_urls = find_url(line);
							for (String new_raw_url: new_raw_urls) {						
								String new_url = normalize(new_raw_url, s.substring(0,i));
								System.out.println(new_url+" "+ new_raw_url);
								if (!new_raw_url.equals("") && !new_url.equals("")) {
									L += (L.equals("")?"":",") + new_url;
								}
							}
						}
						return new FlamePair(s.substring(0,i), "1.0,1.0,"+L);
					});

			int j = 0;
			while (true) {		
				j+=1;
				FlamePairRDD one_transfer = flame.flatMapToPair(p -> {
					LinkedList<FlamePair> ret = new LinkedList<FlamePair>();
					String url = p._1();
					String[] to_urls =  p._2().split(",");
					Set<String> set = new HashSet<String>(Arrays.asList(Arrays.copyOfRange(to_urls,2,to_urls.length)));
					Double r_c = Double.valueOf(to_urls[0]);
					Double r_p = Double.valueOf(to_urls[1]);
					for (String l: set) {
						ret.add(new FlamePair(l, String.valueOf(0.85*r_c/(set.size()))));
					}
					if (!set.contains(url)) {						
						ret.add(new FlamePair(url, "0"));
					}
					return ret;
				}).foldByKey("0", (s1,s2) -> String.valueOf(Double.valueOf(s1) + Double.valueOf(s2)));
				flame = flame.join(one_transfer).flatMapToPair(p -> {
					LinkedList<FlamePair> ret = new LinkedList<FlamePair>();
					String[] values = p._2().split(",");
					ret.add(new FlamePair(p._1(), 
							String.valueOf(0.15+Double.valueOf(values[values.length-1]))+","
									+values[0]+","+String.join(",",Arrays.copyOfRange(values,2,values.length-1))));
					return ret;
				});
				String diff = flame.flatMap(p -> {
					LinkedList<String> ret = new LinkedList<String>();
					Double r_c = Double.valueOf(p._2().split(",")[0]);
					Double r_p = Double.valueOf(p._2().split(",")[1]);
					ret.add(String.valueOf(Math.abs(r_c - r_p)));
					return ret;
				}).fold("0", (s1,s2) -> String.valueOf(Math.max(Double.valueOf(s1),Double.valueOf(s2))));
				System.out.println(diff);
				System.out.println("------------\n");
				if (Double.valueOf(diff) < threshold) break;
			}
			System.out.print("out of loop\n");
			flame.flatMapToPair(p -> {
				LinkedList<FlamePair> ret = new LinkedList<FlamePair>();
				KVSClient kvs = ctx.getKVS();

				System.out.println("url: " + Hasher.hash(p._1()) + " | " + "2nd: " + p._2());
				kvs.put("pt-pageranks", Hasher.hash(p._1()), "rank", p._2().split(",")[0]);
				return ret;
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
