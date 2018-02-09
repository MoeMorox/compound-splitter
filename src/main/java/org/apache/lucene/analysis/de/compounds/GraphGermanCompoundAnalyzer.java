package org.apache.lucene.analysis.de.compounds;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

/**
 * Analyzer for GraphGermanCompoundTokenFilter to assist with unit testing.
 * 
 * @author ben.demott
 */
public class GraphGermanCompoundAnalyzer extends Analyzer {
    public final int minWordSize;
    public final boolean onlyLongestMatch;
    public final boolean preserveOriginal;
    
    public GraphGermanCompoundAnalyzer() {
        this(GraphGermanCompoundTokenFilter.DEFAULT_MIN_WORD_SIZE, 
                GraphGermanCompoundTokenFilter.DEFAULT_ONLY_LONGEST_MATCH, 
                GraphGermanCompoundTokenFilter.DEFAULT_PRESERVE_ORIGINAL);
    }

    public GraphGermanCompoundAnalyzer(int minWordSize, boolean onlyLongestMatch, boolean preserveOriginal) {
        this.minWordSize = minWordSize;
        this.onlyLongestMatch = onlyLongestMatch;
        this.preserveOriginal = preserveOriginal;
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        tokenizer.setReader(new StringReader(s));
        GraphGermanCompoundTokenFilter tokenFilter;
        if(this.minWordSize == 0) {
            tokenFilter = new GraphGermanCompoundTokenFilter(tokenizer);
        } else {
            tokenFilter = new GraphGermanCompoundTokenFilter(tokenizer, minWordSize, onlyLongestMatch, preserveOriginal);
        }
        return new TokenStreamComponents(tokenizer, tokenFilter);
    }
}