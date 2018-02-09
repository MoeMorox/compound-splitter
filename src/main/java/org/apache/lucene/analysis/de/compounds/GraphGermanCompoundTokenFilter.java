package org.apache.lucene.analysis.de.compounds;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;


import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * A German decompounding token filter that maintains proper graphs of generated tokens.
 * 
 * German words are compounded so that a single complex word composed of multiple sub-words is
 * created.
 * 
 * This filter breaks apart those sub-words in such a way as to preserve the original word and
 * create a graph of sub-words that are contained within the dimensions of the original word.
 * 
 * @author ben.demott
 */
public class GraphGermanCompoundTokenFilter extends TokenFilter {
    
    static final int DEFAULT_MIN_WORD_SIZE = 5;
    static final boolean DEFAULT_ONLY_LONGEST_MATCH = false;
    static final boolean DEFAULT_PRESERVE_ORIGINAL = true;
    
    private GermanCompoundSplitter splitter;
    private final int minWordSize;
    private final boolean onlyLongestMatch;
    private final boolean preserveOriginal;
    
    private State inputTokenState;
    private TokenAttributes currentToken = null;
    private LinkedList<TokenAttributes> tokenQueue;
    
    // The term attribute holds the string text of the token.
    private final CharTermAttribute termAttr = addAttribute(CharTermAttribute.class);
    // Offset attribute controls the string offset of the token, typically a synonym this is used
    // in highlighting.
    private final OffsetAttribute offsetAttr = addAttribute(OffsetAttribute.class);
    // Position increment attribute controls the POSITION in text of a token. A position increment
    // of 0 (zero) means that this token is a synonym.
    private final PositionIncrementAttribute posIncAttr = addAttribute(PositionIncrementAttribute.class);
    // Position length controls how many positions a token spans
    private final PositionLengthAttribute posLengthAttr = addAttribute(PositionLengthAttribute.class);
    // The type attribute of a token explicitly controls the tokens type as stored in the index.
    private final TypeAttribute typeAttr = addAttribute(TypeAttribute.class);
   
    public GraphGermanCompoundTokenFilter(TokenStream input) {
        this(input, DEFAULT_MIN_WORD_SIZE, DEFAULT_ONLY_LONGEST_MATCH, DEFAULT_PRESERVE_ORIGINAL);
    }
    
    /**
     * 
     * 
     * @param input  The TokenStream from lucene
     * @param minWordSize  The minimum length of a term to attempt decompounding on.
     * @param onlyLongestMatch  Only use the longest term match if there are multiple ways to decompound the token
     * @param preserveOriginal  In addition to outputting the decompounded tokens, output the original token as well
     */
    public GraphGermanCompoundTokenFilter(TokenStream input, int minWordSize, boolean onlyLongestMatch, boolean preserveOriginal) {
        super(input); // will be stored as this.input
        this.minWordSize = minWordSize;
        this.onlyLongestMatch = onlyLongestMatch;
        this.preserveOriginal = preserveOriginal;
        this.buildGermanSplitter();
        this.setup();
    }
    
    private void setup() {
        this.tokenQueue = new LinkedList<>();
        this.currentToken = null;
        this.clearAttributes();
    }
    
    private void buildGermanSplitter() {
        this.splitter = new GermanCompoundSplitter();
    }
    
    /**
     * incrementToken() is called by the token consumer to set attributes for the next token 
     * available in the token stream.
     * 
     * When we break apart a German word into more than 1 token we'll add all intermediate tokens
     * to the stream.
     * 
     * These pages explain how token graphs work.
     * https://lucidworks.com/2017/04/18/multi-word-synonyms-solr-adds-query-time-support/
     * https://www.elastic.co/blog/multitoken-synonyms-and-graph-queries-in-elasticsearch
     * 
     * The key to our Graph generating filter is the use of the PositionLength attribute.
     * Any word that encompasses other tokens, must have its length set to the maximum number of 
     * 
     * The german word "Finanzgrundsatzangelegenheiten" will generate a graph as follows (l=PositionLength, inc=PositionIncrement)
     * assuming you're using the setting "preserveOriginal=true".
     * 
     * (Finanzgrundsatzangelegenheiten,l:3,inc:1)
     *                            \
     *                         (finanz,l:1,inc:0)  -->  (grundsatz,l:1,inc:1)   -->  (angelegenheiten,l:1,inc:1)
     *                            \                             
     *               (finanzgrundsatz,l:2, inc:0)  ------------------------------->  (angelegenheiten,l:1,inc:0)
     *       
     * 
     * @return true when a token is available, false when tokens are exhausted.
     * @throws IOException 
     */
    @Override
    public final boolean incrementToken() throws IOException {
       
        // If the tokenQueue is empty, there are no decompoundings to emit, so we need to move onto
        // the next input token.
        boolean moreTokens = false;
        if(tokenQueue.isEmpty()) {
             moreTokens = input.incrementToken();
             
             // If there are no more input tokens we need to stop token emission by returning false.
             // this means incrementToken() will not be called again.
             if(!moreTokens) {
                 return false;
             }
        }
        
        // If there are more tokens available in the stream, and the output queue is empty, we 
        // need to get the next tokens text and decompound it.
        if (tokenQueue.isEmpty() && moreTokens) {
            
            currentToken = this.currentToken();
            
            // Obey minimum word size setting... Shortcut splitting the word if it doesn't 
            if(this.termAttr.length() >= this.minWordSize) {
                List<ArrayList<CharSequence>> termSequences = this.splitter.split(this.termAttr.toString());
                if(!termSequences.isEmpty() && this.onlyLongestMatch) {
                    termSequences = termSequences.subList(termSequences.size() - 1, termSequences.size());
                }
                
                List<ArrayList<TokenAttributes>> decompoundedTerms = this.termSeqTokenAttributes(currentToken, termSequences);
                tokenQueue = this.generateTokenQueue(decompoundedTerms);
                currentToken.posLength = calculateSourceTermPosLength(decompoundedTerms);
                
            }

            // If we are supposed to output the original token along with the decompounded tokens
            // we must return the token.  Or if the word has no decompundings, we just pass-through
            // the token without modifying it.
            if(preserveOriginal || tokenQueue.isEmpty()) {
                // emit the current token
                // or no decompoundings were generated
                this.setAttributes(currentToken);
                return true;
            }
        }
        
        // If we've reached this point in code, we have compoundings (tokenQueue is not empty)
        // export the sequence of tokens at the given position
        this.clearAttributes();  // get clearAttributes() was not called correctly in TokenStream chain if this isn't called every time!
        TokenAttributes token = tokenQueue.pop();
        this.setAttributes(token);
        return true;
    }
  
    @Override
    public void reset() throws IOException {
        this.setup();
        super.reset();
    }
    
    /**
     * Flatten tokens into a queue that is in the correct order for emission.
     * This means walking the token sequences in columnar order.
     */
    private LinkedList<TokenAttributes> generateTokenQueue(List<ArrayList<TokenAttributes>> tokenSequences) {
        LinkedList<TokenAttributes> tokens = new LinkedList<>();
        if(tokenSequences.isEmpty()) {
            return tokens;
        }
        
        // numTerms is the maximum size of a given token sequence (should be ordered from longest
        //   to shortest)
        int numTerms = tokenSequences.get(0).size();
        
        // Iterate over the tokens, and place them into a flat list by columnar order.
        for(int col=0; col<numTerms; col++) {
            for (List<TokenAttributes> sequence : tokenSequences) {
                if(col >= sequence.size()) {
                    continue;
                }
                tokens.add(sequence.get(col));
            }
        }
        
        return tokens;
    }
    
    /**
     * lucene TokenFilter's are state machines, when a token is ready to be emitted, the state of 
     * the various token attributes are set, and 'true' is returned from incrementToken() to 
     * indicate that the current state represents a new token.
     * 
     * This method sets the token state from a TokenAttributes object.
     * 
     * @param token 
     */
    private void setAttributes(TokenAttributes token) {
        this.termAttr.setEmpty().append(token.getTerm());
        this.offsetAttr.setOffset(token.offsetStart, token.offsetEnd);
        this.posIncAttr.setPositionIncrement(token.posIncrement);
        this.posLengthAttr.setPositionLength(token.posLength);
        this.typeAttr.setType(TypeAttribute.DEFAULT_TYPE);
    }
    
    /**
     * Get a representation of the current token state
     * 
     * @return 
     */
    private TokenAttributes currentToken() {
        TokenAttributes token = new TokenAttributes(
                this.termAttr.toString(),
                this.offsetAttr.startOffset(),
                this.offsetAttr.endOffset(),
                this.posIncAttr.getPositionIncrement(),
                this.posLengthAttr.getPositionLength()
        );
        return token;
    }
    
    /**
     * The source term length, is equal to the sum of posLength for a given token sequence.
     * 
     * @param tokenSequences
     * @return 
     */
    private int calculateSourceTermPosLength(List<ArrayList<TokenAttributes>> tokenSequences) {
        if(tokenSequences.isEmpty()) {
            return 1;
        } else {
             return tokenSequences.get(0).size();
        }
    }
    
    /**
     * Calculate term attributes from the list of term sequences
     * 
     * This method converts a list of lists of terms, into a list of lists of TokenAttributes
     * and calculates the attributes.
     */
    private List<ArrayList<TokenAttributes>> termSeqTokenAttributes(TokenAttributes srcToken, List<ArrayList<CharSequence>> termSequences) throws IOException {
        List<ArrayList<TokenAttributes>> attrSequences = new ArrayList<>(termSequences.size());
        List<TokenAttributes> previous = null;
        for (List<CharSequence> terms : termSequences) {
            previous = this.calculateTermAttributes(srcToken, terms, previous);
            attrSequences.add((ArrayList)previous);
        }
        return attrSequences;
    }
    
    /**
     * The purpose of this method is to determine token attributes for each term returned from the 
     * german decompounding splitter.  The splitter can return multiple term-sequences when there
     * is more than one way to represent a decompounding.  In this scenario we have to infer the
     * length of each term from the contents of the term.
     * 
     * @param srcToken
     * @param terms
     * @param previous
     * @return 
     */
    private List<TokenAttributes> calculateTermAttributes(TokenAttributes srcToken, List<CharSequence> terms, List<TokenAttributes> previous) throws IOException {
        List<TokenAttributes> tokens = new ArrayList<>(terms.size());
        if(previous == null) {
            // if there is no previous line, we assume this term sequence is the longest,
            // thats because the splitter returns the longest sequence first.
            ListIterator<CharSequence> termsIt = terms.listIterator();
            while (termsIt.hasNext()) {
                int idx = termsIt.nextIndex();
                TokenAttributes token = new TokenAttributes(srcToken);
                token.setTerm(termsIt.next());
                // very important detail here... If the option to preserveOriginal token
                // is set to true, then the first token we emit in decompounding will be at
                // the same position (posIncrement=0), otherwise, it will occupy a new position,
                // (posIncrement=1).
                token.posIncrement = (idx == 0 && this.preserveOriginal) ? 0 : 1;
                token.posLength = 1;
                tokens.add(token);
            }
        } else if(previous.size() >= terms.size()) {
            // if there is a previous line, then this lines term posLengths need to be inferred
            
            int totalLength = 0;
            for (TokenAttributes prevTok : previous) {
                totalLength += prevTok.posLength;
            }
            int termsRemain = terms.size();
            int lengthRemain = totalLength;
            
            // Iterate through each term of the current sequence and generate a TokenAttributes object
            // for each term.
            ListIterator<CharSequence> termsIt = terms.listIterator();
            while (termsIt.hasNext()) {
                int idx = termsIt.nextIndex();
                int posLength = 0;
                TokenAttributes token = new TokenAttributes(srcToken);
                token.setTerm(termsIt.next());
                token.posIncrement = 0; // we occupy the same position as the previous token
                TokenAttributes previousToken = previous.get(idx);
                
                // if the current term length is less than or equal to the previous terms length
                // we assume the posLength is the same for each token.
                if(token.getTermLength() <= previousToken.getTermLength()) {
                    posLength = previousToken.posLength;
                } else {
                    // If our term length exceeds the previous tokens length, iterate through terms
                    // until we've matched or exceeded the length of combined terms. Infer length
                    // from the size of combined terms.
                    int matchLength = 0;
                    int termLenTotal = 0;
                    int curTermLength = token.getTermLength();
                    for (TokenAttributes matchPrev : previous.subList(idx, previous.size())) {
                        termLenTotal += matchPrev.getTermLength();
                        matchLength += matchPrev.posLength;
                        if(termLenTotal >= curTermLength) {
                            posLength = matchLength;
                            break;
                        }
                    }
                }
                
                lengthRemain-=posLength;
                termsRemain--;
                
                posLength = (posLength < 1) ? 1 : posLength;
                
                if(termsRemain > lengthRemain) {
                    // if there isn't enough length left for each remaining term
                    // adjust the length so there is.
                    int badLength = termsRemain - lengthRemain;
                    posLength = Math.max(1, posLength - badLength);
                } else if (termsRemain == 0) {
                    // if this is the last term in the sequence, ensure it occupies the entire 
                    // length of the src token.
                    posLength += lengthRemain;
                }
                token.posLength = posLength;
                tokens.add(token);
            }
        } else {
            throw new IOException("term sequence length exceeds previous sequence length, this should not be possible");
        }
       
        return tokens;
    }
    
}
