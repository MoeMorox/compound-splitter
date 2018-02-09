package org.apache.lucene.analysis.de.compounds;

/**
 * Note that lucene features a State object to store attribute states, 
 * but this state object requires you to call 'restoreState' and 'captureState' to change 
 * between states, this is a bit cumbersome.
 * 
 * So for this reason we create a simple class to hold all the calculated attributes of a given
 * token.
 * 
 * Store token attributes like:
 *  
 *  term
 *  offset
 *  positionIncrement
 *  positionLength
 * 
 * Useful for when using a State class isn't convenient
 * 
 * @author ben.demott
 */
public class TokenAttributes {
    protected CharSequence term;
    protected int termLength;
    public int offsetStart;
    public int offsetEnd;
    public int posIncrement;
    public int posLength;
    
    /**
     * Initialize an empty token attributes class.
     */
    TokenAttributes() {
        this.term = null;
        this.termLength = 0;
        this.offsetStart = 0;
        this.offsetEnd = 0;
        this.posIncrement = 1;
        this.posLength = 1;
    }
    
    /**
     * Initialize token attributes from an existing TokenAttributes class.
     * 
     * @param from 
     */
    TokenAttributes(TokenAttributes from) {
        this.copyAttributesFrom(from);
    }

    /**
     * Create token attributes from individual attributes
     * 
     * @param term - The term string
     * @param offsetStart
     * @param offsetEnd
     * @param posIncrement
     * @param posLength 
     */
    TokenAttributes(CharSequence term, int offsetStart, int offsetEnd, int posIncrement, int posLength) {
        this.setTerm(term);
        this.offsetStart = offsetStart;
        this.offsetEnd = offsetEnd;
        this.posIncrement = posIncrement;
        this.posLength = posLength;
    }
    
    /**
     * Set the term string
     * 
     * @param term 
     */
    public final void setTerm(CharSequence term) {
        this.term = term;
        this.termLength = term.length();
    }
    
    /**
     * Get the term string
     * 
     * @return 
     */
    public CharSequence getTerm() {
        return this.term;
    }
    
    /**
     * Get the length of the term string
     * 
     * @return 
     */
    public int getTermLength() {
        return this.termLength;
    }
    
    /**
     * Copy attributes from `token` into this.
     * @param token 
     */
    public final void copyAttributesFrom(TokenAttributes token) {
        this.term = token.term;
        this.termLength = token.termLength;
        this.offsetStart = token.offsetStart;
        this.offsetEnd = token.offsetEnd;
        this.posIncrement = token.posIncrement;
        this.posLength = token.posLength;
    }
}
