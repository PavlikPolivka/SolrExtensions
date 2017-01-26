package com.ppolivka.solr.extensions.softmax;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static com.ppolivka.hamcrest.SolrMatchers.docWithValueRetunred;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@DisplayName("Softmax Request Handler Test")
class SoftmaxSearchHandlerTest {

    EmbeddedSolrServer server;
    CoreContainer container;

    //region Setup
    @BeforeEach
    void setUp() throws Exception {
        container = new CoreContainer("testData/solr");
        container.load();
        server = new EmbeddedSolrServer(container, "collection1");
        indexData();
    }

    private void indexData() throws Exception {
        doc("1", "Luke Skywalker", "Master jedi");
        server.commit();
    }

    private void doc(String id, String title, String description) throws Exception {
        SolrInputDocument doc1 = new SolrInputDocument();
        doc1.addField("id", id);
        doc1.addField("title", title);
        doc1.addField("description", description);
        server.add(doc1);
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Test
    @DisplayName("Embedded solr is working")
    public void testReturnQuery() throws Exception {
        QueryResponse response = server.query(new SolrQuery("*:*"));
        assertThat("response does not exists", response, notNullValue());
        assertThat("skywalker result not returned", response, docWithValueRetunred("id", "1"));
    }

    @Test
    @DisplayName("Softmax handler is working")
    public void testSoftmaxHandler() throws Exception {
        QueryResponse response = query("Skywalker");
        assertThat("response does not exists", response, notNullValue());
    }

    @Test
    @DisplayName("Softmax returned results")
    public void testSoftmaxReturnsResults() throws Exception {
        QueryResponse response = query("Skywalker");
        assertThat("skywalker result not returned", response, docWithValueRetunred("id", "1"));
    }

    private QueryResponse query(String queryString) throws Exception {
        SolrQuery query = new SolrQuery(queryString);
        query.setRequestHandler("/softmax");
        return server.query(query);
    }

}