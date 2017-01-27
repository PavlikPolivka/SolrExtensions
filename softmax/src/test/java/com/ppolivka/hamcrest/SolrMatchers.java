package com.ppolivka.hamcrest;


import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.NamedList;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class SolrMatchers {

    public static Matcher<QueryResponse> docWithValueRetunred(String fieldName, String value) {
        return new BaseMatcher<QueryResponse>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("field: ").appendValue(fieldName).appendText(" value: ").appendValue(value);
            }

            @Override
            public boolean matches(Object o) {
                QueryResponse response = (QueryResponse) o;
                for(SolrDocument document : response.getResults()) {
                    Object retunredValue = document.get(fieldName);
                    if(value != null && value.equals(retunredValue.toString())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    public static Matcher<NamedList> hasParam(String paramName, Object value) {
        return new BaseMatcher<NamedList>() {
            @Override
            public boolean matches(Object o) {
                NamedList list = (NamedList) o;
                Object paramValue = list.get(paramName);
                return value == null && paramName == null || paramValue != null && paramValue.equals(value);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("contains param: ").appendValue(paramName).appendText(" value: ").appendValue(value);
            }
        };
    }

}
