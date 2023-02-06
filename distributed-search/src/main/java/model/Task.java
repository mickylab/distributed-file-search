package model;

import java.io.Serializable;
import java.util.List;

public record Task(List<String> searchTerms, List<String> documents) implements Serializable {

    @Override
    public List<String> searchTerms() {
        return searchTerms;
    }

    @Override
    public List<String> documents() {
        return documents;
    }
}
