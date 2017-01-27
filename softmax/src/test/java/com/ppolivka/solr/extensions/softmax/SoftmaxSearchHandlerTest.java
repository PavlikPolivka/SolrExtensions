package com.ppolivka.solr.extensions.softmax;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.ppolivka.hamcrest.SolrMatchers.docWithValueRetunred;
import static com.ppolivka.hamcrest.SolrMatchers.hasParam;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class SoftmaxSearchHandlerTest {

    EmbeddedSolrServer server;
    CoreContainer container;

    //region Setup
    @Before
    public void setUp() throws Exception {
        container = new CoreContainer("testData/solr");
        container.load();
        server = new EmbeddedSolrServer(container, "collection1");
        indexData();
    }

    private void doc(String id, String title, String description) throws Exception {
        SolrInputDocument doc1 = new SolrInputDocument();
        doc1.addField("id", id);
        doc1.addField("title", title);
        doc1.addField("description", description);
        server.add(doc1);
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        deleteDirectory("testData/solr/collection1/data");
    }

    private void deleteDirectory(String path) throws Exception {
        Path rootPath = Paths.get(path);
        Files.walk(rootPath, FileVisitOption.FOLLOW_LINKS)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .peek(System.out::println)
                .forEach(File::delete);
    }
    //endregion

    //region Index
    private void indexData() throws Exception {
        doc("1", "Luke Skywalker", "Master jedi");
        doc("2", "John Rambo", "Badass");
        server.commit();
    }
    //endregion

    @Test
    public void testDescription() {
        SoftmaxSearchHandler softmaxSearchHandler = new SoftmaxSearchHandler();
        assertThat("incorrect description",
                softmaxSearchHandler.getDescription(),
                equalTo("Transfers scores to probabilities. Will range from 0.0 to 1.0.")
        );
    }

    @Test
    public void testReturnQuery() throws Exception {
        QueryResponse response = defaultQuery().execute();
        assertThat("response does not exists", response, notNullValue());
        assertThat("skywalker result not returned", response, docWithValueRetunred("id", "1"));
    }

    @Test
    public void testSoftmaxHandler() throws Exception {
        QueryResponse response = query("Skywalker").execute();
        assertThat("response does not exists", response, notNullValue());
    }

    @Test
    public void testSoftmaxReturnsResults() throws Exception {
        QueryResponse response = query("Skywalker").execute();
        assertThat("skywalker result not returned", response, docWithValueRetunred("id", "1"));
    }

    @Test
    public void testSoftmaxSampleSize() throws Exception {
        QueryResponse response = query("*:*").param("sampleSize", "1").execute();
        assertThat("more then one result", response.getResults().size(), Matchers.is(1));
    }

    @Test
    public void testSoftmaxResultSize() throws Exception {
        QueryResponse response = query("*:*").param("results", "1").execute();
        assertThat("more then one result", response.getResults().size(), Matchers.is(1));
    }

    @Test
    public void testSoftmaxParamOverride() throws Exception {
        QueryResponse response = query("*:*")
                .param("queryField", "title")
                .param("sampleSize", "2")
                .param("bias", "1.0")
                .param("threshold", "0.3")
                .param("results", "1")
                .execute();
        NamedList params = (NamedList) response.getResponse().get("softmaxParams");
        assertThat("incorrect result count", params, hasParam("results", "1"));
        assertThat("incorrect query field", params, hasParam("queryField", "title"));
        assertThat("incorrect sample size", params, hasParam("sampleSize", "2"));
        assertThat("incorrect bias", params, hasParam("bias", "1.0"));
        assertThat("incorrect threshold", params, hasParam("threshold", "0.3"));
    }

    @Test
    public void testSoftmaxParamOverrideFallbacks() throws Exception {
        QueryResponse response = query("*:*")
                .param("queryField", "title")
                .param("sampleSize", "string")
                .param("bias", "1.0string")
                .param("threshold", "0.3string")
                .param("results", "1string")
                .execute();
        NamedList params = (NamedList) response.getResponse().get("softmaxParams");
        assertThat("incorrect result count", params, hasParam("results", "10"));
        assertThat("incorrect query field", params, hasParam("queryField", "title"));
        assertThat("incorrect sample size", params, hasParam("sampleSize", "10"));
        assertThat("incorrect bias", params, hasParam("bias", "1.0"));
        assertThat("incorrect threshold", params, hasParam("threshold", "0.0"));
    }

    //region Solr Query Utils
    public QueryBuilder query(String query) {
        QueryBuilder queryBuilder = new QueryBuilder(server, "/softmax");
        queryBuilder.queryString = query;
        return queryBuilder;
    }

    public QueryBuilder defaultQuery() {
        return new QueryBuilder(server, "/select");
    }

    private static class QueryBuilder {

        EmbeddedSolrServer server;
        private String handler;
        private String queryString = "*:*";
        private Map<String, List<String>> params;

        QueryBuilder(EmbeddedSolrServer server, String handler) {
            this.server = server;
            this.handler = handler;
            this.params = new HashMap<>();
        }

        public QueryBuilder param(String name, String value) {
            if(!params.containsKey(name)) {
                params.put(name, new ArrayList<>());
            }
            List<String> values = params.get(name);
            values.add(value);
            return this;
        }

        public QueryResponse execute() throws Exception {
            SolrQuery query = new SolrQuery(queryString);
            query.setRequestHandler(handler);
            params.forEach((key, values) ->
                query.set(key, values.toArray(new String[values.size()]))
            );
            return server.query(query);
        }
    }
    //endregion

}