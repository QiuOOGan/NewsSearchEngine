package edu.northwestern.ssa.api;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/search")
public class Search {

    /** when testing, this is reachable at http://localhost:8080/api/search?query=hello */
    @GET
    public Response getMsg(@QueryParam("query") String q,
                           @QueryParam("language") String lang,
                           @QueryParam("date") String date,
                           @QueryParam("count") String count,
                           @QueryParam("offset") String offset) throws IOException {
        if(q == null){
            return Response.status(400).header("Access-Control-Allow-Origin", "*").build();
        }
        ElasticSearch es = new ElasticSearch("es");
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("txt:" + "(" + q.replaceAll(" ", " AND ") + ")");
        if(lang != null){
            queryBuilder.append(" AND lang:" + lang);
        }
        if(date != null){
            queryBuilder.append(" AND date:" + date);
        }
        String query = queryBuilder.toString();
        JSONObject resp = es.search(query, count, offset);
        JSONObject hits = resp.getJSONObject("hits");
        JSONArray returned_articles = hits.getJSONArray("hits");
        JSONArray articles = new JSONArray();

        for(int i = 0; i < returned_articles.length(); i++){
            JSONObject article = returned_articles.getJSONObject(i).getJSONObject("_source");
            articles.put(i, article);
        }

        JSONObject hitstats = hits.getJSONObject("total");
        JSONObject results = new JSONObject();
//        results.put("resp",resp);
//        results.put("query", query);
//        results.put("start", start);
//        results.put("end", end);
//        results.put("resp", resp);
        results.put("returned_results", articles.length());
        results.put("total_results", hitstats.getInt("value"));
        results.put("articles", articles);
        es.close();
        return Response.status(200).type("application/json").entity(results.toString(4))
                // below header is for CORS
                .header("Access-Control-Allow-Origin", "*").build();
    }

}
