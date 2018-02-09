/*
 * The MIT License
 *
 * Copyright 2017 ben.demott.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.apache.lucene.analysis.de.compounds;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;

/**
 * String utility class with all static methods
 * 
 * All indexes start at ZERO
 * 
 * The goal of this helper class is to avoid raising errors whenever possible.
 * We trust the developer, and assume the arguments provided are what they intended.
 */
public class StringUtils {
    public static final Character[] PUNCTUATION = ArrayUtils.toObject("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".toCharArray());
    
    /**
     * Pad a string up to a given length with the given character.  If the input string (s)
     * is shorter than (length) no padding will occur.
     * 
     * @param s
     * @param length The total number of characters the string should contain.
     * @param c
     * @return 
     */
    public static String pad(String s, int length, Character c) {
        int padlen = length - s.length();
        String append = fill(padlen, c);
        return s + append;
    }
    
    /**
     * Pad a string with the given number of characters on the left in right.
     * 
     * This version of the function pads the String in absolute lengths of characters.
     * 
     * @param s The string to pad
     * @param left  How many characters to pad on the left side of the string
     * @param right How many characters to pad on the right side of the string
     * @param c The character to pad string with
     * @return
     */
    public static String pad(String s, int left, int right, Character c) {
        String sleft = fill(left, c);
        String sright = fill(right, c);
        return sleft + s + sright;
    }
    
    /**
     * Construct a new string filled to the given length with spaces
     * 
     * @param length The number of characters to fill with
     * @return 
     */
    public static String fill(int length) {
        return fill(length, ' ');
    }
    
    /**
     * Construct a new string filled to the given length with (c) 
     * 
     * If length is less than 1, an empty string is returned.
     * 
     * @param length The number of characters to fill with
     * @param c The character to fill string with
     * @return 
     */
    public static String fill(int length, Character c) {
        if(length < 1) {
            return "";
        }
        String r = new String(new char[length]).replace('\0', c);
        return r;
    }
    
    public static String ljust(String s, int length, Character c) {
        // left adjustment is the same as pad()
        return pad(s, length, c);
    }
    
    public static String rjust(String s, int length, Character c) {
        if(length < s.length()) {
            return s;
        }
        int lpad = length - s.length();
        return pad(s, lpad, 0, c);
    }
    
    
    /**
     * Insert a string into another, appending it within the string.
     * The string will grow in length to equal s.length() + insert.length()
     * 
     * If index is out of bounds of the string, the intervening characters will be filled with
     * a space (' ') character.
     * 
     * Examples;
     * 
     * insert("hello world", 5, "...")
     * >>> hello... world
     * 
     * insert("hello world", 0, "planet")
     * >>> ...hello world
     * 
     * @param s The string to insert into
     * @param index The index in (s) at which to insert, the index can fall outside of s, intervening characters will be filled
     * @param insert The string to insert into (s)
     * @return 
     */
    public static String insert(String s, int index, String insert) {
        return insert(s, index, insert, ' ');
    }
    
    /**
     * See insert()
     * 
     * 
     * 
     * @param s The string to insert into
     * @param index The index in (s) at which to insert, the index can fall outside of s, intervening characters will be filled
     * @param insert The string to insert into (s)
     * @param fill The character to fill the string with, if index is outside the bounds of (s)
     * @return 
     */
    public static String insert(String s, int index, String insert, Character fill) {
        if(index > s.length()) {
            s = pad(s, index, fill);
        }
        index = Math.min(index, s.length());
        String r = s.substring(0, index) + insert + s.substring(index, s.length());
        return r;
    }
    
    /**
     * Insert a string into another string, the string you are inserting will overwrite
     * characters it encounters, as if your keyboard was in 'insert' mode.
     * If the insert string extends past the end of the source string (s) the source string
     * will be extended to fit the insert string.  
     * 
     * If the (index) argument falls outside the bounds of the source string (s), the source string
     * will be filled with a space (' ') in the intervening characters between the end of the 
     * source string and the index.
     * 
     * Examples;
     * 
     * insert("hello world", 5, "...")
     * >>> hello...rld
     * 
     * insert("hello world", 6, "planet")
     * >>> hello planet
     * 
     * @param s The string to modify/insert into
     * @param index The index within the string to insert
     * @param insert The string to insert
     * @return 
     */
    public static String insertReplace(String s, int index, String insert) {
        return insertReplace(s, index, insert, ' ');
    }
    
    
    /**
     * See insertReplace()
     * 
     * @param s The string to modify/insert into
     * @param index The index within the string to insert
     * @param insert The string to insert
     * @param fill The character to fill the string with, if index is outside the bounds of (s)
     * @return 
     */
    public static String insertReplace(String s, int index, String insert, Character fill) {
        if(index > s.length()) {
            s = pad(s, index, fill);
        }
        
        index = Math.min(index, s.length());
        int ilen = insert.length();
        // Ensure we don't get an index outside the bounds of the string.
        int i2len = Math.min(index+ilen, s.length());
        String r = s.substring(0, index) + insert + s.substring(i2len, s.length());
        return r;
    }
    
    /**
     * Insert a character into a string at an index, repeating enough times to fill characters
     * between (start) and (stop)
     * 
     * 
     * 
     * @param s  The string to modify
     * @param repeat The string to repeat
     * @param start The index to start the character insertion at
     * @param stop  The index to stop the character insertion at
     * 
     * @return 
     */
    public static String insertReplaceCharRange(String s, Character repeat, int start, int stop) {
        if(stop < start) {
            return s;
        }
        int nchars = stop - start;
        return insertReplaceCharLength(s, repeat, start, nchars);
    }
    
    /**
     * Insert a character into a string at an index, repeating enough times to fill characters
     * (length) times.  Length is always treated as an absolute number.
     * 
     * @param s  The string to modify
     * @param repeat The string to repeat
     * @param start The index to start the character insertion at
     * @param length The number of times to repeat character (repeat)
     * @return 
     */
    public static String insertReplaceCharLength(String s, Character repeat, int start, int length) {
        if(length < 1) {
            return s;
        }
        String replace = fill(length, repeat);
        return insertReplace(s, start, replace);
    }
    
    
    /**
     * Creates an array in which each position in the array corresponds to a character in the index
     * in the string (s).  For every position in (s) that ANY character in (chars) appears a [true]
     * is stored, otherwise [false]
     * 
     * @param s  A string to search characters in and record true/false
     * @param chars Chars to search for
     * @return 
     */
    public static Boolean[] charsPresentArray(String s, Character[] chars) {
        Boolean[] r = new Boolean[s.length()];
        char[] sArray = s.toCharArray();
        Set<Character> charSet = new HashSet<>(Arrays.asList(chars));
        for(int i=0; i < sArray.length ; i++) {
            char c = sArray[i];
            r[i] = charSet.contains(c);
        }
        return r;
    }
    
    /**
     * The inverse of charsPresentArray()
     * 
     * Creates an array in which each position in the array corresponds to a character in the index
     * in the string (s).  For every position in (s) that NO character in (chars) appears a [true]
     * is stored, otherwise [false]
     * 
     * @param s  A string to search characters in and record true/false
     * @param chars Chars to search for
     * @return 
     */
    public static Boolean[] charsNotPresentArray(String s, Character[] chars) {
        Boolean[] r = new Boolean[s.length()];
        Boolean[] presence = charsPresentArray(s, chars);
        for(int i=0; i < presence.length ; i++) {
            r[i] = !presence[i]; // Flip the true/false
        }
       
        return r;
    }
    
    /**
     * Return an array of boolean values or each character in (s).  Any character in (s) that is 
     * a punctuation character, will be stored as true in the returned array.
     * 
     * @param s Input string to generate character boolean array for
     * @return 
     */
    public static Boolean[] punctuationPresentArray(String s) {
        return charsPresentArray(s, PUNCTUATION);
    }
    

    /**
     * Return an array of boolean values or each character in (s).  Any character in (s) that is 
     * a punctuation character (or appears in others), will be stored as true in the returned array.
     * 
     * @param s Input string to generate character boolean array for
     * @param others Additional characters you want marked as (true) in the returned boolean array.
     * @return 
     */
    public static Boolean[] punctuationPresentArray(String s, Character[] others) {
        // Copy both PUNCTUATION and others into a single array
        Character[] chars = (Character[]) ArrayUtils.addAll(PUNCTUATION, others);
        return charsPresentArray(s, chars);
    }
    

    public static Integer findChar(String s, int start, Character search) {
        // TODO !
        return null;
    }
    
    
}
