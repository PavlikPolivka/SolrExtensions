package com.ppolivka.solr.extensions.softmax;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.BasicResultContext;
import org.apache.solr.response.ResultContext;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.range;

public class SoftmaxSearchHandler extends RequestHandlerBase {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private String queryField = "title";
    private Float bias = 1.0F;
    private Float threshold = 0.0F;
    private Integer results = 10;
    private Integer sampleSize = 10;

    //region Init block
    @Override
    public void init(NamedList args) {
        super.init(args);
        queryField = extractStringParam(args, "queryField", queryField);
        bias = extractFloatParam(args, "bias", bias);
        threshold = extractFloatParam(args, "threshold", threshold);
        results = extractIntegerParam(args, "results", results);
        sampleSize = extractIntegerParam(args, "sampleSize", sampleSize);
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

        //region Param override
        Integer finalSampleSize =
                getIntegerParam(req.getParams(), "sampleSize", sampleSize);
        Integer finalResultSize =
                getIntegerParam(req.getParams(), "results", results);
        Float finalThreshold =
                getFloatParam(req.getParams(), "threshold", threshold);
        Float finalBias =
                getFloatParam(req.getParams(), "bias", bias);
        String finalQueryField =
                getStringParam(req.getParams(), "queryField", queryField);
        NamedList<String> usedParams = new NamedList<>();
        usedParams.add("queryField", finalQueryField);
        usedParams.add("results", finalResultSize.toString());
        usedParams.add("threshold", finalThreshold.toString());
        usedParams.add("bias", finalBias.toString());
        usedParams.add("sampleSize", finalSampleSize.toString());
        rsp.add("softmaxParams", usedParams);
        //endregion

        SolrIndexSearcher solrIndexSearcher = req.getSearcher();
        ResponseBuilder responseBuilder = new ResponseBuilder(req, rsp, new ArrayList<>());

        Query query = query(req, finalQueryField);
        DocSlice slice = (DocSlice) solrIndexSearcher.getDocList(query, null, Sort.RELEVANCE, 0, finalSampleSize, 1);

        int resultSize = finalResultSize;
        int[] softmaxDocs = new int[slice.size()];
        float[] softmaxScores = new float[slice.size()];
        Map<Integer, Float> softmaxMap = new HashMap<>();
        DocIterator docIterator = slice.iterator();
        double sumOfScores = 0;
        while (docIterator.hasNext()) {
            int doc = docIterator.nextDoc();
            float score = docIterator.score();
            softmaxMap.put(doc, score);
            sumOfScores += Math.exp(finalBias*score);
        }
        final double scoreSum = sumOfScores;
        softmaxMap.forEach((doc, score) -> {
            double expScore = Math.exp(finalBias * score);
            double newScore = expScore / scoreSum;
            softmaxMap.put(doc, (float) newScore);
        });
        Map<Integer,Float> transformedSoftmaxMap =
                softmaxMap.entrySet().stream()
                        .filter(doc -> doc.getValue() > finalThreshold)
                        .sorted(Map.Entry.<Integer, Float>comparingByValue().reversed())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                ;
        List<Integer> transformedDocs = new ArrayList<>();
        transformedDocs.addAll(transformedSoftmaxMap.keySet());
        if(resultSize > transformedDocs.size()) {
            resultSize = transformedDocs.size();
        }
        range(0, transformedDocs.size()).forEach(index -> {
            Integer doc = transformedDocs.get(index);
            softmaxDocs[index] = doc;
            softmaxScores[index] = transformedSoftmaxMap.get(doc);
        });

        // Return results
        // Modify result size
        int[] docs = new int[resultSize];
        float[] scores = new float[resultSize];
        range(0, resultSize).forEach(index -> {
            docs[index] = softmaxDocs[index];
            scores[index] = softmaxScores[index];
        });
        float maxScore = 1.0F;
        DocSlice modifiedSlice = new DocSlice(0, resultSize, docs, scores, slice.matches(), maxScore);
        DocListAndSet docListAndSet = new DocListAndSet();
        docListAndSet.docList = modifiedSlice;
        responseBuilder.setResults(docListAndSet);
        ResultContext ctx = new BasicResultContext(responseBuilder);
        rsp.addResponse(ctx);
    }

    //region Query builder
    private Query query(SolrQueryRequest request, String queryField) throws SyntaxError {
        String query = request.getParams().get("q");
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
    //endregion

    //region URL param getters
    private Integer getIntegerParam(SolrParams params, String name, Integer defaultValue) {
        String paramUrlValue = params.get(name);
        if(StringUtils.isNotBlank(paramUrlValue)) {
            try {
                return Integer.parseInt(paramUrlValue);
            } catch (Exception e) {
                log.warn("Provided param " + name + " was not integer. Falling back to default.");
            }
        }
        return defaultValue;
    }

    private Float getFloatParam(SolrParams params, String name, Float defaultValue) {
        String paramUrlValue = params.get(name);
        if(StringUtils.isNotBlank(paramUrlValue)) {
            try {
                return Float.parseFloat(paramUrlValue);
            } catch (Exception e) {
                log.warn("Provided param " + name + " was not float. Falling back to default.");
            }
        }
        return defaultValue;
    }

    private String getStringParam(SolrParams params, String name, String defaultValue) {
        String paramUrlValue = params.get(name);
        if(StringUtils.isNotBlank(paramUrlValue)) {
            return paramUrlValue;
        }
        return defaultValue;
    }
    //endregion

    @Override
    public String getDescription() {
        return "Transfers scores to probabilities. Will range from 0.0 to 1.0.";
    }
}
