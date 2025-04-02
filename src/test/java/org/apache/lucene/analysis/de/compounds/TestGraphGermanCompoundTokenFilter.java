package org.apache.lucene.analysis.de.compounds;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

/**
 * Test the functionality of the GraphGermanCompoundTokenFilter.
 * 
 * Correctness of Graph token filters depends on setting posIncrement and posLength attributes
 * correctly.  Each test method ensures that these attributes output is as expected.
 * 
 * Note that the lucene test framework has many signatures for BaseTokenSTreamTestCase.assertAnalyzesTo() 
 * we will use the following signature typically:
 *  <code> assertAnalyzesTo(Analyzer a, String input, String[] output, int[] startOffsets, int[] endOffsets, String[] types, int[] posIncrements, int[] posLengths) </code>
 * 
 * @author ben.demott
 */
public class TestGraphGermanCompoundTokenFilter extends BaseTokenStreamTestCase {
    
    static String WORD = TypeAttribute.DEFAULT_TYPE;
    
    public static void assertAnalyzesTo(Analyzer a, String input, String[] output, int startOffsets[], int endOffsets[], String types[], int posIncrements[], int posLengths[]) throws IOException {
        try {
            BaseTokenStreamTestCase.assertAnalyzesTo(a, input, output, startOffsets, endOffsets, types, posIncrements, posLengths);
        } catch (AssertionError e) {
            // this is a hack to get to the analyzers underlying tokenStream so we can close it.
            TokenStreamComponents comp = a.getReuseStrategy().getReusableComponents(a, "");
            TokenStream stream = comp.getTokenStream();
            stream.close(); // to avoid error: "TokenStream contract violation: close() call missing"
            
            TokenStream debugStream = a.tokenStream("", input);
            String debugging = TokenStreamDebug.debugTokenStream(debugStream, input);
            throw new AssertionError(debugging + "\n" + e.getMessage(), e); 
        } catch (Exception e) {
            e.printStackTrace(System.out);
            throw new AssertionError(e.getMessage(), e);
        }
    }
    
    public void testPassthrough() throws Exception {
        Analyzer analyzer = new GraphGermanCompoundAnalyzer();
        final String input = "eins zwei drei";
        
        assertAnalyzesTo(analyzer, input,              // input - (THE INPUT STRING TO TOKENIZE)
                new String[] {"eins", "zwei", "drei"}, // output - (THE EXPECTED OUTPUT TOKENS)
                new int[] {0, 5, 10},                  // startOffsets - (the position of the first character corresponding to this token in the source text)
                new int[] {4, 9, 14},                  // endOffsets   - (one greater than the position of the last character corresponding to this token in the source text)
                new String[] {WORD, WORD, WORD},       // types        - (the expected token type output)
                new int[] {1, 1, 1},                   // positionIncrements - (The token increment position between each token... 
                                                       //                      0 = Occupy the same position as the previous token
                                                       //                      1 = (normal) occupy the position next to the previous token
                                                       //                      1+ = occupy a position some tokens after the previous token... Used when you want to prevent phrase matching.)
                new int[] {1, 1, 1});                  // posLengths    - the expected position length (1 for any non-compounded term)
                                         
    }
    
    /**
     * Test the preserving the original token works according to setting.
     * 
     * @throws Exception 
     */
    public void testPreserveOriginalFalse() throws Exception {
        
        final String input = "Anwendungsbetreuer";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = false;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"anwendung", "betreuer"}, // terms
                new int[] {0, 0},          // startOffsets
                new int[] {18, 18},        // endOffsets
                new String[] {WORD, WORD}, // types
                new int[] {1, 1},          // posIncrements
                new int[] {1, 1});         // posLengths
    }
    
    public void testPreserveOriginalTrue() throws Exception {
        
        final String input = "Anwendungsbetreuer";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = true;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"Anwendungsbetreuer", "anwendung", "betreuer"}, // terms
                new int[] {0, 0, 0},             // startOffsets
                new int[] {18, 18, 18},          // endOffsets
                new String[] {WORD, WORD, WORD}, // types
                new int[] {1, 0, 1},             // posIncrements
                new int[] {2, 1, 1});            // posLengths
    }
    
    public void testSingleShouldNotDecompose() throws Exception {
        final String input = "Fahrrad";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = true;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        // Fahrrad can be decomposed, but it shouldn't be.
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"Fahrrad"},  // terms
                new int[] {0},             // startOffsets
                new int[] {7},             // endOffsets
                new String[] {WORD},       // types
                new int[] {1},             // posIncrements
                new int[] {1});            // posLengths
    }
    
    public void testTriWordDecompose() throws Exception {
        
        final String input = "Finanzgrundsatzangelegenheiten";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = true;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"Finanzgrundsatzangelegenheiten", "finanz", "grundsatz", "angelegenheiten"},  // terms
                new int[] {0, 0, 0, 0},                // startOffsets
                new int[] {30, 30, 30, 30},            // endOffsets
                new String[] {WORD, WORD, WORD, WORD}, // types
                new int[] {1, 0, 1, 1},                // posIncrements
                new int[] {3, 1, 1, 1});               // posLengths
    }
    
    public void testAmbigiousDecompose() throws Exception {
        
        // amigious - should generate two forms... [sünde, recke] and [sünder, ecke]
        final String input = "sünderecke";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = true;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"sünderecke", "sünde", "sünder", "recke", "ecke"},
                new int[] {0, 0, 0, 0, 0},
                new int[] {10, 10, 10, 10, 10},
                new String[] {WORD, WORD, WORD, WORD, WORD},
                new int[] {1, 0, 0, 1, 0},
                new int[] {2, 1, 1, 1, 1});
    }
    
    public void testLongestMatchTrue() throws Exception {
        
        // amigious - should generate two forms... [sünde, recke] and [sünder, ecke]
        final String input = "sünderecke";
        
        final int minWordSize = 1;
        final boolean onlyLongestMatch = true;
        final boolean preserveOriginal = true;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"sünderecke", "sünder", "ecke"},
                new int[] {0, 0, 0},
                new int[] {10, 10, 10},
                new String[] {WORD, WORD, WORD},
                new int[] {1, 0, 1},
                new int[] {2, 1, 1});
    }
    
    public void testMinWordSize() throws Exception {
        
        // amigious - should generate two forms... [sünde, recke] and [sünder, ecke]
        final String input = "sünderecke";
        
        final int minWordSize = 11;
        final boolean onlyLongestMatch = false;
        final boolean preserveOriginal = false;
        
        Analyzer analyzerFalse = new GraphGermanCompoundAnalyzer(minWordSize, onlyLongestMatch, preserveOriginal);
        
        assertAnalyzesTo(analyzerFalse, input,
                new String[] {"sünderecke"},
                new int[] {0},
                new int[] {10},
                new String[] {WORD},
                new int[] {1},
                new int[] {1});
    }
    
}
