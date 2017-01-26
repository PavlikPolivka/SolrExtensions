package com.ppolivka.solr.extensions.softmax;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SoftmaxSearchHandler extends RequestHandlerBase {

    private String queryField;
    private Float bias = 1.0F;
    private Float threshold = 0.0F;
    private Integer results = 10;
    //TODO add sample size

    //region Init block
    @Override
    public void init(NamedList args) {
        super.init(args);
        queryField = extractStringParam(args, "queryField", null);
        bias = extractFloatParam(args, "bias", bias);
        threshold = extractFloatParam(args, "threshold", threshold);
        results = extractIntegerParam(args, "results", results);
    }

    private Integer extractIntegerParam(NamedList args, String name, Integer defaultValue) {
        String param = extractStringParam(args, name, null);
        return StringUtils.isNotBlank(param) ? Integer.parseInt(param) : defaultValue;
    }

    private Float extractFloatParam(NamedList args, String name, Float defaultValue) {
        String param = extractStringParam(args, name, null);
        return StringUtils.isNotBlank(param) ? Float.parseFloat(param) : defaultValue;
    }

    private String extractStringParam(NamedList args, String name, String defaultValue) {
        Object param = extractParam(args, name);
        return Objects.isNull(param)? defaultValue : param.toString();
    }

    private Object extractParam(NamedList args, String name) {
        return args.get(name);
    }
    //endregion


    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {

        //TODO override default params

        SolrIndexSearcher solrIndexSearcher = req.getSearcher();
        String queryString = req.getParams().get("q");
        ResponseBuilder responseBuilder = new ResponseBuilder(req, rsp, new ArrayList<>());

        Query query = query(req, queryString);
        DocSlice slice = (DocSlice) solrIndexSearcher.getDocList(query, null, Sort.RELEVANCE, 0, 25, 1);

        //TODO do softmax modifications

        DocListAndSet docListAndSet = new DocListAndSet();
        docListAndSet.docList = slice;
        responseBuilder.setResults(docListAndSet);
        ResultContext ctx = new BasicResultContext(responseBuilder);
        rsp.addResponse(ctx);
    }

    private Query query(SolrQueryRequest request, String query) throws SyntaxError {
        Map<String, String> edismaxParams = new HashMap<>();
        edismaxParams.put("qf", queryField);
        ExtendedDismaxQParser extendedDismaxQParser = new ExtendedDismaxQParser(
                query,
                new MapSolrParams(edismaxParams),
                null,
                request
        );
        return extendedDismaxQParser.parse();
    }

    @Override
    public String getDescription() {
        return null;
    }
}
