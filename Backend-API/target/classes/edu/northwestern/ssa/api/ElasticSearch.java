package edu.northwestern.ssa.api;

import edu.northwestern.ssa.AwsSignedRestRequest;
import edu.northwestern.ssa.Config;
import org.json.JSONObject;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.utils.IoUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

public class ElasticSearch extends AwsSignedRestRequest {
    ElasticSearch(String serviceName) {
        super(serviceName);
    }
    protected JSONObject search(String query, String count, String offset){
        String pathname = "/" + Config.getParam("ELASTIC_SEARCH_INDEX") + "/_search";
        String hostname = Config.getParam("ELASTIC_SEARCH_HOST");
        HashMap<String, String> q = new HashMap<>();
        q.put("q", query);
        q.put("sort", "_score");
        q.put("track_total_hits", "true");
        if(count != null){
            q.put("size", count);
        }
        if(offset != null) {
            q.put("from", offset);
        }
        JSONObject responseBody = null;
        try{
            HttpExecuteResponse resp = restRequest(SdkHttpMethod.GET, hostname, pathname, Optional.of(q));
            AbortableInputStream responsebody = resp.responseBody().get();
            responseBody = new JSONObject(IoUtils.toUtf8String(responsebody));
            resp.responseBody().get().close();
        }catch(IOException e){
            e.printStackTrace();
        }finally {
            return responseBody;
        }
    }
}
