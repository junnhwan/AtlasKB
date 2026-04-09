package io.hwan.atlaskb.search.dto;

public class SearchRequest {

    private String query;
    private Integer topK;

    public SearchRequest() {
    }

    public SearchRequest(String query, Integer topK) {
        this.query = query;
        this.topK = topK;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
