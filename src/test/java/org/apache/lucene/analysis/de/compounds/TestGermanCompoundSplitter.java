package org.apache.lucene.analysis.de.compounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import junit.framework.TestCase;


/**
 * Test decompounding functionality
 * @author ben.demott
 */
public class TestGermanCompoundSplitter extends TestCase {
    
    private GermanCompoundSplitter splitter = null;
    
    /**
     * Run the decompounder on a provided list of known splits.
     */
    public void setUp() {
        splitter = new GermanCompoundSplitter();
    }
    
    public void testSplitterBasic() throws Exception {
        List<String> wordsList = Arrays.asList(
                "sünderecke",                     // ambigious, 2 forms
                "Servicebereich",
                "Finanzgrundsatzangelegenheiten", // financial policy matters
                "Finanzermittlung",               // financial investigation
                "Reisekosten",                    // travel expenses
                "Terrorismusfinanzierung",        // terrorist financing
                "Beteiligungsmanagement",
                "Medientechnologie",              // media technology 
                "Hochschulabsolvent",
                "beteiligungsbuchhalter",
                "finanzanalyst",
                "rechtsbeistand",
                "Finanzdienstanwendungen",        // Financial Services Applications
                "Finanzbuchhaltungsleitung",      // financial accounting management
                "Applikationsmanagement",
                "Kundenberater",                  // client advisor
                "Exportfinanzierung",             // export financing
                "Versicherungskaufmann",          // insurance salesman
                "Anwendungsbetreuer"              // application administrator (IT)
        );
        ListIterator<String> it = wordsList.listIterator();
        
        while(it.hasNext()) {
            String germanWord = it.next();
            int idx = it.nextIndex();
            List<ArrayList<CharSequence>> wordSplit;
            wordSplit = splitter.split(germanWord);
            assertNotNull(wordSplit);
            if(wordSplit == null) {
                continue;
            }
            System.out.println(String.format("split test[%d]: input: %s  split: %s", idx, germanWord, wordSplit.toString()));
        }
    }
}
