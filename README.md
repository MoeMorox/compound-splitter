
GERMAN COMPOUND SPLITTER
========================

A German decomposition filter that breaks complex words into other forms, written in **JAVA**.

**features 4 components:**
* CompoundSplitter build class for composing FST from dictionaries.
* CompoundSplitter class, for splitting german words.
* Lucene TokenFilter, and TokenFilterFactory that generates split tokens.
* Lucene Analyzer that encompasses TokenFilter for test cases.

## German Decomposition Examples:
    sünderecke                      [sünde, recke], [sünder, ecke]   (outputs 2 forms, ambigious)
    Servicebereich                  [service, bereich]
    Finanzgrundsatzangelegenheiten  [finanz, grundsatz, angelegenheiten]
    Finanzermittlung                [finanz, ermittlung]
    Reisekosten                     [reise, kosten]
    Terrorismusfinanzierung         [terrorismus, finanzierung]
    Beteiligungsmanagement          [beteiligung, management]
    Medientechnologie               [medien, technologie]
    Hochschulabsolvent              [hochschul, absolvent]
    beteiligungsbuchhalter          [beteiligung, buchhalter]
    finanzanalyst                   [finanz, analyst]
    rechtsbeistand                  [rechts, beistand]
    Finanzdienstanwendungen         [finanz, dienst, anwendungen]
    Finanzbuchhaltungsleitung       [finanz, buch, haltung, leitung]
    Applikationsmanagement          [applikation, management]
    Kundenberater                   [kunden, berater]
    Exportfinanzierung              [export, finanzierung]
    Versicherungskaufmann           [versicherung, kauf, mann]
    Anwendungsbetreuer              [anwendung, betreuer]
    
## Lucene, Solr, ElasticSearch Analysis
The lucene analyzer that comes with this package is a Graph filter, which means if used on the query-side of the analysis chain it will properly generate query graphs for overlapping terms. This is accomplished by correctly setting the ``posIncrement`` and ``posLength`` attributes of each token.
For this to work in **Solr**, you must not split on whitespace by setting the ``sow=false``.  This has the unfortunate downside of preventing phrase queries from working correctly, so you will be unable to use the ``pf``, ``pf2`` parameters to generate phrase queries.

For more information about Token Graphs in modern versions of lucene, solr, or elasticsearch see the following:
-    https://lucidworks.com/2017/04/18/multi-word-synonyms-solr-adds-query-time-support/
-    https://www.elastic.co/blog/multitoken-synonyms-and-graph-queries-in-elasticsearch

**Solr Analyzer Configuration:**
```xml
<fieldType name="field" class="solr.TextField">
    <analyzer class="org.apache.lucene.analysis.de.compounds.GraphGermanCompoundAnalyzer"/>
</fieldType>
```

**Solr TokenFilterFactory Configuration:**

```xml
<filter class="org.apache.lucene.analysis.de.compounds.GraphGermanCompoundTokenFilterFactory"
        minWordSize="5" 
        onlyLongestMatch="false" 
        preserveOriginal="true" />
```

**TokenFilter Parameters:**

* ``minWordSize`` (*default=5*) The minimum length of a term to attempt decompounding on.
* ``onlyLongestMatch`` (*default=false*) Only use the longest term match if there are multiple ways to decompound the token
* ``preserveOriginal`` (*default=true*) In addition to outputting the decompounded tokens, output the original token as well

##### Recommended Analysis Configuration
**Note:** ``GraphGermanCompoundTokenFilter`` should come before any stemming, lemmatization or german normalization.
```xml
<!-- German -->
<fieldType name="text_de" class="solr.TextField" positionIncrementGap="100">
  <analyzer> 
    <tokenizer class="solr.StandardTokenizerFactory"/>
    <filter class="solr.LowerCaseFilterFactory"/>
    <filter class="org.apache.lucene.analysis.de.compounds.GraphGermanCompoundTokenFilterFactory"
            minWordSize="5" 
            onlyLongestMatch="false" 
            preserveOriginal="true" />
    <filter class="solr.GermanNormalizationFilterFactory"/>
    <filter class="solr.GermanLightStemFilterFactory"/>
  </analyzer>
</fieldType>
```

##### German Normalization
With german analysis it's fairly important to allow for phonetic matching, this can be for multiple reasons, but often mobile users search *phonetically* as opposed to populating the text with an umlat.

**Description of German Normalization** from ``GermanNormalizationFilterFactory``**:**
* Normalizes German characters according to the heuristics of the *German2 snowball algorithm*. 
* It allows for the fact that ä, ö and ü are sometimes written as ae, oe and ue.
* ``ß`` is replaced by ``ss``
* ``ä``, ``ö``, ``ü`` are replaced by ``a``, ``o``, ``u``, respectively.
* ``ae`` and ``oe`` are replaced by ``a``, and ``o``, respectively.
* ``ue`` is replaced by ``u``, when not following a vowel or ``q``.
* *This is useful if you want this normalization without using the German2 stemmer, or perhaps no stemming at all.*
    
## FST Build Process
The first part of the splitter is the class that is responsible for compiling the lucene ``FST``.
A ``finite state automation (fst)`` is a *trie* like data structure that can be used to
build graphs of relationships of words to quickly traverse.  From this FST **arcs** can be 
discovered that are compositions of sub-words.

Within ``CompileCompoundDictionaries.java`` there is a main method.  This method uses input files
provided in the ``pom.xml`` file as arguments by default.  

The default german language files:

    morphy.txt
    morphy-unknown.txt
    
``morphy.txt`` is the base dictionary.
``morphy-unknown.txt`` is composed of customizations to the base dictionary

The build process runs and saves its output (`src/main/resources/words.fst`) within the java package. The ``FST`` file is 
packaged with the ``jar`` output and will be loaded from within the jar when the splitter is used.

## German Word Splitter
The ``GermanCompoundSplitter`` class is responsible for splitting an input word.
Upon instantiation the class reads from the internal FST saved into the java package.
**Note:** *each instance of GermanCompoundSplitter will load an FST into memory, and this is a
          fairly large data structure.*
After you instantiate an instance of the class it has one public method ``split``.

``GermanCompoundSplitter.split()`` is the only method you should ultimately concern yourself with.
It accepts 1 argument which is the input string, and returns a list of lists.  Each sub-list is a 
list of terms broken apart.

     Splits the input sequence of characters into separate words if this sequence is
     potentially a compound word.
     
     The response structure is similar to:
     
              String word = "Finanzgrundsatzangelegenheiten";
              wordSequences = {
                  {"finanz", "grundsatz", "angelegenheiten"},
                  {"finanzgrundsatz", "angelegenheiten"}
              };

## Testing
There are some basic tests for the Splitter class.  These tests are in-code and use junit (which lucene also uses).

For the ``GraphGermanCompoundTokenFilter`` class the lucene test framework is used.  This test framework ensures that
all attribute output for any given token is correctly formed.  There are a good number of tests cases to cover the majority of functionality and edge cases within both the TokenFilter and the 

All tests should be run as part of the build process and be discovered by the maven surefire plugin.

**Example Test Case** using ``BaseTokenStreamTestCase``

```java
public void testPassthrough() throws Exception {
    Analyzer analyzer = new GraphGermanCompoundAnalyzer();
    final String input = "eins zwei drei";
    
    assertAnalyzesTo(analyzer, input,              // input - (THE INPUT STRING TO TOKENIZE)
            new String[] {"eins", "zwei", "drei"}, // output - (THE EXPECTED OUTPUT TOKENS)
            new int[] {0, 5, 10},                  // startOffsets
            new int[] {4, 9, 14},                  // endOffsets
            new String[] {WORD, WORD, WORD},       // types
            new int[] {1, 1, 1},                   // positionIncrements
            new int[] {1, 1, 1});                  // posLengths
                                     
}
```

**Debug Output on Test Failure**
A custom class (``TokenStreamDebug``) that is part of the test modules will output debugging information to help you understand the token output.  Below is an example failure and the debug output that is generated from the TokenSream:

       original: Finanzgrundsatzangelegenheiten
      increment:                1              
         tokens: Finanzgrundsatzangelegenheiten
      positions: ------------------------------
                 finanz                        
                 ------                        
                 grundsatz                     
                 ---------                     
                 angelegenheiten               
                 ---------------               
        lengths:                3              
       sequence:                1              
                 012345678901234567890123456789
                          10        20        30
      start-end: 1:[0-30], 1:[0-30], 2:[0-30], 3:[0-30]
    term 0 expected:<[finanz]> but was:<[Finanzgrundsatzangelegenheiten]>

## Future Improvements
There are many improvements that could be made to the splitter, the algorithm is fairly simple as it stands right now.
* statistical / probabilistic splitter - use a statistical data-set in the decomposition.
* next-word awareness - when we look-ahead at the next word in the stream, we can decide if a word should be decomposed.
* better dictionary of compoundings - awareness of multiple forms of compoundings

For more information on using statistical input to better decide how to decompose words see the following ebay article on how they approached the problem.
http://www.ebaytechblog.com/2012/03/12/german-compound-words/

**Other Resources:**
https://github.com/PhilippGawlik/compoundtree
https://github.com/dtuggener/CharSplit


##### Prefixes
We need to think if "fixed" prefixes are indeed useful and should be detached/ removed
in the initial stages of processing. Daniel came up with a list that I stored here:

src/data/compound-prefixes.txt

It is temporarily not used because I've tried this, for example:

    $ grep -i "^abbrenn" ./consolidated.bycount
    abbrennen   4063
    abbrennt    713
    abbrennens  131
    abbrennenden    80
    abbrennende 62
    abbrennung  56
    abbrenne    45

this is Google 1-gram corpus from 1980-200(7?) and it is clear that this prefix is not a compound
forming one at all. 