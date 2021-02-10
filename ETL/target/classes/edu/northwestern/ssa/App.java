package edu.northwestern.ssa;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class App {
    public static <string> void main(String[] args) throws IOException {

        //// STEP1 Download The Warc File
        String fn = "./gqx3267File.gz";
        File f = new File(fn);
        if (!f.exists()) {
            downLoad(f);
        }
        // STEP2 3 4 Parse the Warc, Parse the HTML, Post it
        // I looked at this website:
        // https://www.programcreek.com/java-api-examples/?api=org.archive.io.warc.WARCReaderFactory
        parseAndPost(fn);

        // Delete the File.
        f.delete();
    }

    static void downLoad(File f) throws IOException {
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(30)).build())
                .build();

        String targetFile;
        if (System.getenv("COMMON_CRAWL_FILENAME") == null || System.getenv("COMMON_CRAWL_FILENAME").equals("")) {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            int month = Calendar.getInstance().get(Calendar.MONTH) + 1;

            String prefix = "crawl-data/CC-NEWS/" + year;
            if (month < 10) {
                prefix = prefix + "/" + 0 + month;
            } else {
                prefix = prefix + "/" + month;
            }
            ListObjectsV2Request v2Request = ListObjectsV2Request.builder()
                    .bucket("commoncrawl")
                    .prefix(prefix)
                    .build();

            List<S3Object> listOf = s3.listObjectsV2(v2Request).contents();

            if (listOf.size() == 0) {
                prefix = "crawl-data/CC-NEWS/" + year;
                month = month - 1;
                if (month < 10) {
                    prefix = prefix + "/" + 0 + month;
                } else {
                    prefix = prefix + "/" + month;
                }
                ListObjectsV2Request v2Request2 = ListObjectsV2Request.builder()
                        .bucket("commoncrawl")
                        .prefix(prefix)
                        .build();

                List<S3Object> listOf2 = s3.listObjectsV2(v2Request2).contents();

                targetFile = listOf2.get(listOf2.size() - 1).key();
            } else {
                targetFile = listOf.get(listOf.size() - 1).key();
            }
            if (targetFile.equals(getLastCrawlFileName())) {
                System.out.println("No new Crawl Files, terminate");
                System.exit(0);
            }

            writeToFile(targetFile);


        } else {
            targetFile = System.getenv("COMMON_CRAWL_FILENAME");
        }

        System.out.println("Start Downloading. Target File: " + targetFile);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket("commoncrawl")
                .key(targetFile)
                .build();

        s3.getObject(request, ResponseTransformer.toFile(f));
        s3.close();
        System.out.println("Download Complete. Target File: " + targetFile);

    }

    static void writeToFile(String fileName) throws IOException {
        String fn = "./latestFile.txt";
        FileWriter myWriter = new FileWriter(fn);
        myWriter.write(fileName);
        myWriter.close();
    }

    static String getLastCrawlFileName() throws IOException {
        String fn = "./latestFile.txt";
        File myObj = new File(fn);
        Scanner myReader = new Scanner(myObj);
        if (myReader.hasNextLine()) {
            return myReader.nextLine();
        }
        myReader.close();
        return "";
    }

    static void parseAndPost(String fn) throws IOException {
        System.out.println("Start Parsing and Posting... ");
        FileInputStream is = new FileInputStream(fn);
        ArchiveReader ar = WARCReaderFactory.get(fn, is, true);
        ElasticSearch es = new ElasticSearch();
        es.createIndex();

        int i = 0;
        for (ArchiveRecord r : ar) {
            if (!r.getHeader().getHeaderValue("WARC-Type").equals("response")) {
                continue;
            }
            i++;
            StringBuilder content = new StringBuilder();
            int c;
            byte[] rawData = new byte[128];
            while (r.available() > 128) {
                r.read(rawData, 0, 128);
                String newData = new String(rawData, "UTF-8");
                content.append(newData);
            }
            while ((c = r.read()) != -1) {
                content.append((char) c);
            }

            int startIndex = content.indexOf("\r\n\r\n");
            if (startIndex < 0) {
                continue;
            }
            startIndex += 4;
            Document doc = Jsoup.parse(content.substring(startIndex).replace("\0", ""));
            JSONObject jsonDoc = new JSONObject().put("title", doc.title()).put("txt", doc.text()).put("url", r.getHeader().getUrl());
            es.postDocToIndex(jsonDoc);
            System.out.println(jsonDoc);
            if (i % 1000 == 0) {
                System.out.println("Posted Doc: " + i);
                if (i > 10) {
                    break;
                }
            }
        }
        System.out.println("Posted some Docs, Total Number: " + i);
        es.close();
    }
}

class ElasticSearch extends AwsSignedRestRequest {
    String host;
    String index;

    public ElasticSearch() {
        super("es");
        host = System.getenv("ELASTIC_SEARCH_HOST");
        index = System.getenv("ELASTIC_SEARCH_INDEX") + "/_doc/";
    }

    // I looked at someone on Campuswire answering how to deal with restResponse's excetpion
    // Feed #227
    public void createIndex() throws IOException {
        String host = System.getenv("ELASTIC_SEARCH_HOST");
        String index = System.getenv("ELASTIC_SEARCH_INDEX");
        while (true) {
            try {
                HttpExecuteResponse response = super.restRequest(SdkHttpMethod.PUT, host, index, Optional.empty());
                response.responseBody().get().close();
                return;
            } catch (IOException e) {
                System.out.println("Rest Request Exception, Now retry");
            }
        }

    }

    public void postDocToIndex(JSONObject jsonDoc) throws IOException {
        while (true) {
            try {
                HttpExecuteResponse response = super.restRequest(SdkHttpMethod.POST, host, index, Optional.empty(), Optional.of(jsonDoc));
                response.responseBody().get().close();
                return;
            } catch (IOException e) {
                System.out.println("Rest Request Exception, Now retry");
            }
        }

    }
}
