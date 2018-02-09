/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.analysis.de.compounds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;
/**
 *
 * @author ben.demott
 */
public class TokenStreamDebug {

    /**
     * When an AssertionError is thrown, due to one of our tests failing, the output is NOT
     * very helpful.  This presents high-quality output for each failure.
     * 
     * When called this method will reset() the stream, then when its done call end() and close()
     * on the stream, if the stream is implemented correctly it should be iterable again.
     * 
     * Example debug string:

             original:  hello my name is donald duck I lived in places
            increment:    1        1           2         1        1
               tokens:  hello ** name ** donald duck * live  ** place
            positions:  -----    ----    -----------   ====    ------
              lengths:    1       1           2         1        1
                        01234567890123456789012345678901234567890123456
                                 10        20        30        40     
                                 
       In the case of a Shingle-Filter (with overlapping tokens, output looks like this)
       
        original: faster than a cheetah more clever than a fox
       increment:    1     1       1      1     1     1     1
          tokens: faster than * cheetah more clever than * fox
       positions: ------ ----   ------- ---- ------ ----   ---
                  faster than   _ cheetah    clever than   _ fox
                  -----------   ---------    -----------   -----
                  faster than _ _ cheetah more      than _
                  ------------- --------------      ------
                         than _ cheetah more clever than _
                         ------ ------------ -------------
                         than _ cheetah more clever than _ fox
                         -------------- ----------- ----------
                                cheetah more clever
                                -------------------
                                        more clever than
                                        ----------------
         lengths:    1     1       1      1     1     1     1
        sequence:    1     2       4      5     6     7     9
                  01234567890123456789012345678901234567890123
                           10        20        30        40
     * 
     * @param stream The token stream from an analyzer
     * @param input The original string passed to the analyzer/tokenizer
     * @throws IOException 
     * @return A multi-line debugging string containing all information about the Analyzer
     */
    public static String debugTokenStream(TokenStream stream, String input) throws IOException {
        
        KeywordAttribute keywordAttr = stream.addAttribute(KeywordAttribute.class);
        OffsetAttribute offsetAttr = stream.addAttribute(OffsetAttribute.class);
        CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute incrAttr = stream.addAttribute(PositionIncrementAttribute.class);
        PositionLengthAttribute lenAttr = stream.addAttribute(PositionLengthAttribute.class);
        
        // TODO the hash map is kinda gross, use a HashMap of arrays, that will be easier to pass
        // around, each index in each arraqy corresponds to the same data  TODO !
        ArrayList<HashMap> tokenData = new ArrayList<>();
        ArrayList<String>  tokenTerms = new ArrayList<>();
        ArrayList<Integer> tokenStarts = new ArrayList<>();
        ArrayList<Integer> tokenEnds = new ArrayList<>();
        ArrayList<Boolean> tokenKeywords = new ArrayList<>();
        ArrayList<Integer> tokenLengths = new ArrayList<>();
        ArrayList<Integer> tokenIncrements = new ArrayList<>();
        ArrayList<Integer> tokenSequence = new ArrayList<>();
        ArrayList<String> startAndEnd = new ArrayList<>();
        
        try {
            stream.reset(); // Resets this stream to the beginning. (Required)
            int tokenSeq = 0;
            while (stream.incrementToken()) {
                // for token stream debugging.
                // debug string = stream.reflectAsString(true);
                
                String tokenTerm = termAttr.toString();
                int startOffset  = offsetAttr.startOffset();
                int endOffset    = offsetAttr.endOffset();
                boolean keyword  = keywordAttr.isKeyword();
                int increment    = incrAttr.getPositionIncrement();
                int posLength       = lenAttr.getPositionLength();
                tokenSeq += increment;
                
                HashMap<String, String> tokenAttrs = new HashMap<>();
                tokenAttrs.put("term", tokenTerm);
                tokenAttrs.put("start", Integer.toString(startOffset));
                tokenAttrs.put("end", Integer.toString(endOffset));
                tokenAttrs.put("keyword", Boolean.toString(keyword));
                tokenAttrs.put("increment", Integer.toString(increment));
                tokenAttrs.put("length", Integer.toString(posLength));
                tokenAttrs.put("sequence", Integer.toString(tokenSeq));
                
                //System.out.println(tokenAttrs.toString());
                
                tokenData.add(tokenAttrs);
                
                startAndEnd.add(String.format("%d:[%d-%d]", tokenSeq, startOffset, endOffset));
                
                tokenTerms.add(tokenTerm);
                tokenStarts.add(startOffset);
                tokenEnds.add(endOffset);
                tokenKeywords.add(keyword);
                tokenIncrements.add(increment);
                tokenLengths.add(posLength);
                tokenSequence.add(tokenSeq);
                
            }
            
          stream.end();   // Perform end-of-stream operations, e.g. set the final offset.
          
        } finally {
          stream.close(); // Release resources associated with this stream.
        }
        
        // ---------------------------------------------------------------------------------------
        // endOffset must be <= finalOffset: got endOffset=27 vs finalOffset=23 term=aalaarm
        // This method can be used to perform any end-of-stream operations, such as setting the 
        // final offset of a stream. The final offset of a stream might differ from the offset of 
        // the last token eg in case one or more whitespaces followed after the last token, 
        // but a WhitespaceTokenizer was used.
        // ---------------------------------------------------------------------------------------
        
        // DEBUGGING LIKE THIS:
        //  original:  hello my name is donald duck I lived in places
        // increment:    1        1           2         1        1
        //    tokens:  hello ** name ** donald duck * live  ** place
        // positions:  -----    ----    -----------   ====    ------
        //   lengths:    1       1           2         1        1
        //             01234567890123456789012345678901234567890123456
        //                      10        20        30        40      
        // 
        String originalStr = input;
        String incrementStr = StringUtils.fill(input.length());
        String tokenStr = StringUtils.fill(input.length());
        String positionsStr = StringUtils.fill(input.length());
        String sequenceStr = StringUtils.fill(input.length());
        String positionLengthStr = StringUtils.fill(input.length());
        String startEndStr = String.join(", ", startAndEnd);
        
        // We want to be able to debug tokens that overlap, in this event...
        // 
        ArrayList<String> overlapTokens = new ArrayList<>();
        ArrayList<String> overlapPositions = new ArrayList<>();
        
        // the term must occupy the same amount of room as the start and end position, which
        // is a reference to the original string....
        // The tokens must place * characters where there was text (that is not punctuation)
        // and a token does not occupy this space...
        
        // the position must occupy HALF of the string, and be padded on to start and end
        // The term must start at start, and end at end... if the term is less than end it must be padded
        int lastEnd = -1;
        for(int i=0; i < tokenTerms.size() ; i++) {

            
            String term = tokenTerms.get(i);
            int termLength = term.length();
            Boolean isKeyword = tokenKeywords.get(i);
            int start = tokenStarts.get(i);
            int end = tokenEnds.get(i);
            int realEnd = start + termLength; // +1 for 1 space of padding, +2 for enclosure
            int increment = tokenIncrements.get(i);
            int sequence = tokenSequence.get(i);
            int lengthPos = tokenLengths.get(i);
            
            Character posChar;
            if(isKeyword) {
                posChar = '=';
            } else {
                posChar = '-';
            }
            
            // Check for overlapping
            // If the start of this token, is before the end of the last, it overlaps
            boolean isOverlapped = false;
            if(!hasAvailableWhitespace(tokenStr, start, term) || (realEnd > end)) {
                isOverlapped = true;
                String overlapTerm = String.format("%s", term);

                // Find a line in the overlap lines that contains whitespace for us to write to.
                // Because of how shingles and other tokens can extend past their own length,
                // this functionality must exist to ensure we don't write-over and existing token
                // on multiple lines.
                int overlapWriteIdx = findAvailableWhitespaceLines(overlapTokens, start, overlapTerm);
                        
                if(overlapWriteIdx < 0) {
                    overlapTokens.add(StringUtils.fill(input.length()));
                    overlapPositions.add(StringUtils.fill(input.length()));
                    overlapWriteIdx = overlapTokens.size() - 1;
                }
                
                String overlapLine = overlapTokens.get(overlapWriteIdx);
                overlapLine = StringUtils.insertReplace(overlapLine, start, overlapTerm);
                overlapTokens.set(overlapWriteIdx, overlapLine);
                
                String overlapPosLine = overlapPositions.get(overlapWriteIdx);
                overlapPosLine = StringUtils.insertReplaceCharRange(overlapPosLine, posChar, start, realEnd);
                overlapPositions.set(overlapWriteIdx, overlapPosLine);
                continue;
            }

            
            if(isOverlapped) {
                continue;
            }
            
            // insert the increment value
            int halfIncPos = start + (termLength / 2) - (Integer.toString(increment).length() / 2);
            incrementStr = StringUtils.insertReplace(incrementStr, halfIncPos, Integer.toString(increment));
            
            // Insert the sequence position value
            int halfSeqPos = start + (termLength / 2) - (Integer.toString(sequence).length() / 2);
            sequenceStr = StringUtils.insertReplace(sequenceStr, halfSeqPos, Integer.toString(sequence));
            
            // insert the length position value
            // Only show the length position, if it can fit underneath the token
            String lengthPosStr = Integer.toString(lengthPos);
            if(lengthPosStr.length() < termLength) {
                int halfLenPos = start + (termLength / 2) - (lengthPosStr.length() / 2);
                positionLengthStr = StringUtils.insertReplace(positionLengthStr, halfLenPos, lengthPosStr);
            }
            
            // insert the token into the string at its position
            tokenStr = StringUtils.insertReplace(tokenStr, start, term);
            

            // underline the token between its start and stop position
            positionsStr = StringUtils.insertReplaceCharRange(positionsStr, posChar, start, end);
            
        }
        
        // Insert stopword markers
        // Apply * asterisks for stopwords as a different operation.
        // any location that positionsStr is whitespace, and there is a non-whitespace, and 
        // non-punctuation character in the original string, is replaced with *
        // TODO
        Boolean[] punctuationIndex = StringUtils.punctuationPresentArray(input, new Character[]{' '});
        for (int i = 0; i < input.length(); i++) {
            boolean isTermPosition = positionsStr.charAt(i) != ' ';
            boolean isPunctuationOrWs  = punctuationIndex[i];
            if(isTermPosition || isPunctuationOrWs) {
                continue;
            }
            
            tokenStr = StringUtils.insertReplace(tokenStr, i, "*");
        }
        
        
        // Generate character counts 0-9
        List<String> counterArray = new ArrayList<>();
        for(int i=0; i <  input.length(); i++) {
            String istr = Integer.toString(i);
            String idigit = istr.substring(istr.length() - 1);
            counterArray.add(idigit);
        }
        
        String counterStr = String.join("", counterArray);
        
        // Generate 10 counts
        String tenCountStr = StringUtils.fill(input.length());
        int char10Count = (int) Math.floor(input.length() / 10);
  
        for(int i=0; i < char10Count ; i++) {
            int tenCount = (i+1) * 10;
            int idx = tenCount - 1;
            tenCountStr = StringUtils.insertReplace(tenCountStr, idx, Integer.toString(tenCount));
        }
        
        boolean showOriginal  = true;
        boolean showIncrement = true;
        boolean showPositions = true;
        boolean showLengths   = true;
        boolean showSequence  = true;
        boolean showStartEnd  = true;
        boolean showOverlaps  = true;
        ArrayList<String> debugStrings = new ArrayList<>();
        debugStrings.add("\n");
        
        if(showOriginal) {
            debugStrings.add("   original: " + originalStr);
        }
        
        if(showIncrement) {
            debugStrings.add("  increment: " + incrementStr);
        }
        

        if(true) {
            debugStrings.add("     tokens: " + tokenStr);
        }
        
        if(showPositions) {
            debugStrings.add("  positions: " + positionsStr);
        }
        
        if(showOverlaps) {
            for(int i=0; i < overlapTokens.size(); i++) {
                
                debugStrings.add("             " + overlapTokens.get(i));
                debugStrings.add("             " + overlapPositions.get(i));
            }
        }
        
        if(showLengths) {
            debugStrings.add("    lengths: " + positionLengthStr);
        }
        
        if(showSequence) {
            debugStrings.add("   sequence: " + sequenceStr);
        }
        
        if(true) {
            debugStrings.add("             " + counterStr);
            debugStrings.add("             " + tenCountStr);
        }
        
        if(showStartEnd) {
            debugStrings.add("  start-end: " + startEndStr);
        }
        
        String debug = String.join("\n", debugStrings);
        return debug;
    } 
    
    /**
     * Given lines of text, search through each line of text, return the first index 
     * 
     * @param lines
     * @param index
     * @param text
     * @return Returns the line index, or -1 if no line was found.
     */
    public static int findAvailableWhitespaceLines(ArrayList<String> lines, int index, String text) {
        for(int i = 0; i < lines.size(); i++) {
            if(hasAvailableWhitespace(lines.get(i), index, text)) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Test if the line has available whitespace within it at the given index
     * 
     * @param line
     * @param index
     * @param text
     * @return 
     */
    public static boolean hasAvailableWhitespace(String line, int index, String text) {
        // If the index starts outside the line, then you wont be overwriting anything.
        // thus it has room for text.
        if(index >= line.length()) {
            return true;
        }
        
        
        for (int i = index; i < line.length(); i++) {
            if(line.charAt(i) != ' ') {
                return false;
            }
        }
        return true;
    }
}
