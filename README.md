[![Build Status](https://travis-ci.org/PavlikPolivka/SolrExtensions.svg?branch=master)](https://travis-ci.org/PavlikPolivka/SolrExtensions)
[![Code Coverage](https://img.shields.io/codecov/c/github/PavlikPolivka/SolrExtensions/master.svg)](https://codecov.io/github/PavlikPolivka/SolrExtensions?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.ppolivka.solr.extensions/extensions/badge.svg)](https://maven-badges.herokuapp.com/maven-central/cz.jirutka.rsql/rsql-parser)


# Solr Extensions

## Extensions

### Softmax

Simple query handler that will transform scores to percentages to determine how sure solr is with it's response.

This may be useful if you want to do something like banner searching. You have bunch of banners in the index and you want to show relevant banner for search term on search page.

#### Maven

    <dependency>
        <groupId>com.ppolivka.solr.extensions</groupId>
        <artifactId>softmax</artifactId>
        <version>1.1.2</version>
    </dependency>

#### Usage

Add softmax-version.jar to your solr lib directory.

Add this snippet to your solrconfig.xml

    <requestHandler name="/softmax" class="com.ppolivka.solr.extensions.softmax.SoftmaxSearchHandler">
        <str name="queryField">title</str>
        <str name="bias">1.0</str>
        <str name="threshold">0.0</str>
        <str name="results">10</str>
        <str name="sampleSize">10</str>
    </requestHandler>
    
All the parameters are not required, they can be added as part of the solr query.
Parameters
- queryFiled - fields where to search, dismax qf syntax (can be more fields comma separeted), by default title
- bias - used as bias * score, bigger values (like 10) will make softmax more sure with results, by default its 1.0
- threshold - what percentage must be associated with the results to be included in the results, by default 0.0
- results - how many results to return, by default 10
- sampleSize - from how many results compute the response, by default 10
    - this is bit tricky, for example if I want one result with 90% accuracy I will set results as 1 but sampleSize as 50 so softmax has data to compute percentages

## Build

Build by maven.

    mvn clean package // for build
    mvn clean test // for testing

