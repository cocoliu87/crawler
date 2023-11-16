package cis5550.jobs;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.tools.URLParser;

public class Crawler {
    public static String normalize(String s, String base) {
        if (s.indexOf("#") != -1) {
            s = s.substring(0, s.indexOf("#"));
        }
        if (s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".txt")) {
            return "";
        }
        if (s.equals("")) {
            return base;
        }
        String[] parse1 = URLParser.parseURL(s);
        String[] parse2 = URLParser.parseURL(base);
        String protocal1 = parse1[0];
        String host1 = parse1[1];
        String port1 = parse1[2];
        String detail1 = parse1[3];
        String protocal2 = parse2[0];
        String host2 = parse2[1];
        String port2 = parse2[2];
        String detail2 = parse2[3];
        String ret = "";
        if (s.startsWith("/")) {
            return protocal2 + "://" + host2 + (protocal2.contains("https") ? ":443" : ":80") + s;
        }
        if (protocal1 != null){
            if (protocal1.equals("http") || protocal1.equals("https")) {
                ret += protocal1 + ":";
            } else {
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
            System.out.println("something wrong4, base: "+base+", new_uri: "+s);
            return "";
        }
        if (port1 != null) {
            ret += ":" + port1;
        } else {
            ret += ret.contains("https") ? ":443" : ":80";
        }
        if (detail1 == null) {
            System.out.println("something wrong5, base: "+base+", new_uri: "+s);
            return base;
        } else {
            if (detail1.startsWith("/")) {
                ret += detail1;
            } else {
                String[] tmp = (detail2.substring(0, detail2.lastIndexOf("/")) + "/" + detail1).split("/");
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
        //Creating a pattern object
        Pattern pattern = Pattern.compile(regex);
        //Matching the compiled pattern in the String
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

    private static final Logger logger = Logger.getLogger(Crawler.class);
    public static void run(FlameContext ctx, String[] arr) {

        String kvs_master = ctx.getKVS().getCoordinator();
        ctx.setConcurrencyLevel(10);

        List<String> seed = new LinkedList<String>();

        if (arr.length >= 1 && arr[0].equals("seed")) {
            for (int i=1; i<arr.length; i++) {
                seed.add(normalize(arr[i], ""));
            }
        }
        else {
            try {
                Iterator<Row> iter = ctx.getKVS().scan("frontier");
                while (iter.hasNext()) {
                    seed.add(iter.next().get("url"));
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//			try {
//			    File myObj = new File("frontier.txt");
//			    Scanner myReader = new Scanner(myObj);
//		        while (myReader.hasNextLine()) {
//			        String data = myReader.nextLine();
//			        seed.add(data);
////			        System.out.println(data);
//			    }
//			    myReader.close();
//		    } catch (FileNotFoundException e) {
//		    	System.out.println("An error occurred.");
//			    e.printStackTrace();
//			}
//		logger.info("seed: " + seed.toString());
        ctx.output("OK?");

        FlameRDD urlQueue = null;
//		System.out.println(seed);
        try {
            urlQueue = ctx.parallelize(seed);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try {
            int count = 0;
            while (count < 100 && urlQueue.count() != 0) {
                System.out.println(count+"-----------------------------------");
                count += 1;

                urlQueue = urlQueue.flatMap(u-> {
//	    			logger.info(u);
                    Set<String> new_url_list = new HashSet<String>();
                    try {
//	    				KVSClient kvs_url = new KVSClient(kvs_master_url);
//	    				RandomAccessFile crawled_url =  new RandomAccessFile("crawled_url", "rw");
                        KVSClient kvs = new KVSClient(kvs_master);
                        String hashKey = Hasher.hash(u);

                        if (kvs.existsRow("pt-crawled-url", hashKey)) {
                            return new_url_list;
                        }

                        kvs.put("pt-crawled-url", hashKey, "url", u);

                        String[] parsed_url = URLParser.parseURL(u);
                        Row host_row = kvs.getRow("hosts", parsed_url[1]);
                        if (host_row != null) {
                            String robots_txt = host_row.get("robots_txt");
                            if (robots_txt == null) {
                                robots_txt = "";
                            }
                            boolean flag = false;
                            long time_interval = 10;
                            for (String line: robots_txt.split("\n")) {
                                String[] split = line.replace(" ","").toLowerCase().split(":");
                                if (split.length != 2) {
                                    continue;
                                }
                                if (split[0].equals("user-agent")) {
                                    if (split[1].equals("cis5550-crawler") || split[1].equals("*")) {
                                        flag = true;
                                    } else {
                                        flag = false;
                                    }
                                }
                                else if (flag) {
                                    if (split[0].equals("allow")) {
                                        if (parsed_url[3].startsWith(split[1])) {
//				    						System.out.println("allow");
                                            break;
                                        }
                                    }
                                    if (split[0].equals("disallow")) {
                                        if (parsed_url[3].startsWith(split[1])) {
//				    						System.out.println("disallow");
                                            return new_url_list;
                                        }
                                    }
                                    if (split[0].equals("crawl-delay")) {
                                        time_interval = (long) Double.parseDouble(split[1])*1000;
//				    					System.out.println("delay");
                                    }
                                }
                            }
                            if (System.currentTimeMillis() - Long.parseLong(new String(host_row.get("last_accessed_time"))) < time_interval) {
                                new_url_list.add(u);
                                return new_url_list;
                            }
                        } else {
                            host_row = new Row(parsed_url[1]);
                            HttpURLConnection robot_con = (HttpURLConnection)(new URL(parsed_url[0]+"://"+parsed_url[1]+":"+parsed_url[2]+"/robots.txt")).openConnection();
                            robot_con.setConnectTimeout(5000);
                            robot_con.setReadTimeout(5000);
                            robot_con.setRequestMethod("GET");
                            robot_con.setDoOutput(true);
//				    		robot_con.setRequestProperty("Content-Type", "application/jar-archive");
                            robot_con.setRequestProperty("User-Agent", "cis5550-crawler");
                            robot_con.setInstanceFollowRedirects(false);
                            robot_con.connect();
                            if (robot_con.getResponseCode() == 200) {
                                BufferedReader robot_r = new BufferedReader(new InputStreamReader(robot_con.getInputStream()));
                                String robots_txt = "";
                                while (true) {
                                    String l = robot_r.readLine();
                                    if (l == null)
                                        break;
                                    robots_txt = robots_txt + "\n" + l;
                                }
//					    		System.out.println(robot_txt);
//					    		System.out.println(robot_con.getResponseCode());
//	    						kvs.put("hosts", parsed_url[1], "robots_txt", robots_txt);
                                host_row.put("robots_txt", robots_txt);
                            }
                        }
                        host_row.put("last_accessed_time", String.valueOf(System.currentTimeMillis()));
                        kvs.putRow("hosts", host_row);
                        HttpURLConnection con = (HttpURLConnection)(new URL(u)).openConnection();
                        con.setReadTimeout(5000);
                        con.setConnectTimeout(5000);
                        con.setRequestMethod("HEAD");
                        con.setDoOutput(true);
//					    con.setRequestProperty("Content-Type", "application/jar-archive");
                        con.setInstanceFollowRedirects(false);
                        con.setRequestProperty("User-Agent", "cis5550-crawler");
                        con.connect();
                        Row row = new Row(hashKey);
//	    				Row row = new Row(String.valueOf(u.hashCode()));
                        row.put("url", u);
                        int code = con.getResponseCode();
                        row.put("responseCode", String.valueOf(code));
                        Map<String, List<String>> header = con.getHeaderFields();
                        Map<String, String> lower_header = new HashMap<String, String>();
                        for (String key: header.keySet()) {
                            if (key != null && header.get(key).size() > 0) {
                                lower_header.put(key.toLowerCase(), header.get(key).get(0));
                            }
                        }

//					    System.out.println(header);
                        if (lower_header.containsKey("content-encoding") && lower_header.get("content-encoding").equals("gzip")) {
                            return new_url_list;
                        }
                        if (lower_header.containsKey("content-language") && !lower_header.get("content-language").startsWith("en")) {
                            return new_url_list;
                        }
                        if (lower_header.containsKey("content-type")) {
                            row.put("contentType", lower_header.get("content-type").toString());
                        }
                        if (lower_header.containsKey("content-length")) {
//					    	row.put("length", lower_header.get("content-length").toString());
                            row.put("length", con.getHeaderField("Content-Length"));
                        }
                        HashSet<Integer> s = new HashSet<Integer>(Arrays.asList(301, 302, 303, 307, 308));
                        if (code == 200) {
                            HttpURLConnection con1 = (HttpURLConnection)(new URL(u)).openConnection();
                            con1.setConnectTimeout(5000);
                            con1.setReadTimeout(5000);
                            con1.setRequestMethod("GET");
                            con1.setDoOutput(true);
                            con1.setRequestProperty("User-Agent", "cis5550-crawler");
                            con1.setInstanceFollowRedirects(false);
                            con1.connect();
                            InputStream in = con1.getInputStream();
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                            int b;
                            while ((b = in.read()) != -1) {
                                buffer.write(b);
                            }
                            String result = new String(buffer.toByteArray());
                            for (String line: result.split("\n")) {
                                List<String> new_raw_urls = find_url(line);
                                for (String new_raw_url: new_raw_urls) {
                                    String new_url = normalize(new_raw_url, u);
                                    if (!new_raw_url.equals("") && !new_url.equals("") && !new_url.equals(u) && (new_url.contains("wiki") || new_url.contains("bbc") || new_url.contains("simple.crawltest.cis5550"))) {
                                        new_url_list.add(new_url);
                                    }
                                }
                            }
                            row.put("responseCode", String.valueOf(con1.getResponseCode()));

                            if (row.get("contentType") != null && row.get("contentType").contains("text/html")) {
                                row.put("page", result);
                                if (lower_header.containsKey("content-language") && !lower_header.get("content-language").startsWith("en")) {
//	    							logger.info("language check: " + u+" "+ lower_header.get("content-language"));
                                } else {
                                    if (!lower_header.containsKey("content-language")) {
//	    								logger.info("check2: "+u+" "+Jsoup.parse(result).head().text());
                                    }
                                    Row r2 = new Row(row.key());
                                    Document doc = Jsoup.parse(result);
                                    String head_txt = doc.head().text();
                                    String body_txt = doc.body().text();
                                    r2.put("head", head_txt);
                                    if (!r2.get("head").matches("[a-zA-Z0-9~!@#$%^&*()_–+-=|\\[\\]\\{\\};':\",.<>\\/\\?\\s]+")) {
//	    								logger.info("match: "+r2.get("head")+" "+u);
                                        return new_url_list;
                                    }
//	    							System.out.println("yeah: "+r2.get("head"));
                                    Element desc = doc.head().selectFirst("meta[name$=description]");
                                    if (desc != null && desc.attr("content") != null) {
                                        r2.put("description", desc.attr("content"));
                                    } else {
                                        // if body_txt doesn't have length more than 100,
                                        // view it as ineffective document, and will catch Exception
                                        r2.put("description", body_txt.substring(0, 100));
                                    }
                                    String children_urls = String.join(" ", new_url_list);
                                    r2.put("url", u);
                                    r2.put("text", body_txt);
                                    r2.put("children_urls", children_urls);
                                    kvs.putRow("pt-plain-text", r2);
                                    kvs.put("pt-url", row.key(), "url", u);
                                }
                            }
                        } else if (s.contains(code)){
//	    					System.out.println(u+" "+u.hashCode()+" "+ code);
//	    					System.out.println(lower_header.get("location"));
                            if (!lower_header.get("location").equals(u)) {
                                new_url_list.add(normalize(lower_header.get("location"), u));
                            }
                        }
//	    				kvs.putRow("crawl", row);
                    } catch (Exception e) {
                        logger.error("Error in flatmap: " + u +" "+e.toString());
                    }
//	    			System.out.println(u);
//	    			System.out.println(new_url_list.toString());
//	    			logger.info(String.valueOf(u));
//	    			logger.info(new_url_list.toString());
                    return new_url_list;
                });
//	    		Thread.sleep(1000);
//	    		System.out.println("one iteration: " + urlQueue.collect().toString());
//	    		logger.info("one iteration "+count+": " + urlQueue.collect().toString());
                HashSet<String> urlQueueSet = new HashSet<String>(urlQueue.collect());
                List<String> new_seed = new LinkedList<String>();
//	    		System.out.println(urlQueueSet);
                ctx.getKVS().delete("frontier");

                for (String u: urlQueueSet) {
                    String hashed_u = Hasher.hash(u);
                    if (!ctx.getKVS().existsRow("pt-crawled-url", hashed_u)) {
                        new_seed.add(u);
                        ctx.getKVS().put("frontier", hashed_u, "url", u);
                    }
                }
                urlQueue = ctx.parallelize(new_seed);
                System.gc();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
