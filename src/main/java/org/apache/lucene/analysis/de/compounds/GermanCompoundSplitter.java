package org.apache.lucene.analysis.de.compounds;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.UnicodeUtil;
import org.apache.lucene.util.fst.*;
import org.apache.lucene.util.fst.FST.BytesReader;
import org.apache.lucene.util.fst.FST.INPUT_TYPE;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.NoOutputs;
import java.net.URL;

/**
 * Simple greedy compound splitter for German. 
 * This class has been made thread safe.
 */
public class GermanCompoundSplitter
{
    /*
     * Ideas for improvement: Strip off affixes?
     * http://german.about.com/library/verbs/blverb_pre01.htm 
     * 
     * Use POS tags and morphological patterns, as described here? This will probably 
     * be difficult without a disambiguation engine in place, otherwise lots of things 
     * will match. 
     * 
     * http://www.canoo.net/services/WordformationRules/Komposition/N-Comp/Adj+N/Komp+N.html?MenuId=WordFormation115012
     */
    
    static final String FST_WORDS_FILE = "words.fst";

    /**
     * A static FSA with inflected and base surface forms from Morphy.
     * 
     * @see "http://www.wolfganglezius.de/doku.php?id=cl:surfaceForms"
     */
    private FST<Object> surfaceForms;

    /**
     * A static FSA with glue glueMorphemes. This could be merged into a single FSA
     * together with {@link #surfaceForms}, but I leave it separate for now.
     */
    private FST<Object> glueMorphemes;

    /**
     * left-to-right word encoding symbol (FST).
     */
    static final char LTR_SYMBOL = '>';

    /**
     * right-to-left word encoding symbol (FST).
     */
    static final char RTL_SYMBOL = '<';

    /**
     * Constructor, original implementation makes the FST's static, and references them from instance
     * members. We want a thread-safe implementation per instance
     */
    public GermanCompoundSplitter() {
        try
        {
            surfaceForms = this.readMorphyFST();
            glueMorphemes = this.createMorphemesFST();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to initialize FST data structures.", e);
        }
    }

    /**
     * Category for a given chunk of a compound.
     */
    public static enum ChunkType
    {
        GLUE_MORPHEME, WORD,
    }

    /**
     * A slice of a compound word.
     */
    public final class Chunk
    {
        public final int start;
        public final int end;
        public final ChunkType type;

        Chunk(int start, int end, ChunkType type)
        {
            this.start = start;
            this.end = end;
            this.type = type;
        }

        @Override
        public String toString()
        {
            final StringBuilder b = new StringBuilder(
                UnicodeUtil.newString(utf32.ints, start, end - start)).reverse();

            if (type == ChunkType.GLUE_MORPHEME) 
                b.append("<G>");

            return b.toString();
        }
    }

    /**
     * A decomposition listener accepts potential decompositions of a word.
     */
    public static interface DecompositionListener
    {
        /**
         * @param utf32 Full unicode points of the input sequence.
         * @param chunks Chunks with decomposed parts and matching regions.
         */
        void decomposition(IntsRef utf32, ArrayDeque<Chunk> chunks);
    }

    /**
     * Full unicode points representation of the input compound.
     */
    private IntsRef utf32;
    private IntsRefBuilder utf32Builder = new IntsRefBuilder();

    /**
     * This array stores the minimum number of decomposition words during traversals to
     * avoid splitting a larger word into smaller chunks.
     */
    private IntsRefBuilder maxPathsBuilder = new IntsRefBuilder();

    /**
     * Reusable array of decomposition chunks.
     */
    private final ArrayDeque<Chunk> chunks = new ArrayDeque<Chunk>();

    /**
     * A decomposition listener accepts potential decompositions of a word.
     */
    private DecompositionListener listener;
    
    /**
     * Splits the input sequence of characters into separate words if this sequence is
     * potentially a compound word.
     * 
     * The response structure is similar to:
     *  <code>
     *          String word = "Finanzgrundsatzangelegenheiten";
     *          wordSequences = {
     *              {"finanz", "grundsatz", "angelegenheiten"},
     *              {"finanzgrundsatz", "angelegenheiten"}
     *          };
     * </code>
     * 
     * @param word The word to be split.  (note that CharSequence is an interface that String implements)
     * @return Returns <code>null</code> if this word is not recognized at all.
     *          A String (CharacterSequence) is returned for each part of the word decomposition.
     *          Each string is contained within an ArrayList, which is contained within another 
     *          List.
     */
    public synchronized List<ArrayList<CharSequence>> split(CharSequence word)
    {
        try
        {
            // Lowercase and reverse the input.
            final String wordLower = word.toString().toLowerCase();
            final String wordReversed = new StringBuffer(word).reverse().toString().toLowerCase();
            this.utf32 = UTF16ToUTF32(wordReversed, utf32Builder).get();
            
            /*
                a list of lists containing decompoundings, typically the top-level list will only
                contain a single element.  When there are multiple interpretations of the word 
                decomposition then all possibilities will be represented.
            
                The structure of wordSequences typically looks like:
                
                word = "Finanzgrundsatzangelegenheiten";
                wordSequences = {
                    {"finanz", "grundsatz", "angelegenheiten"},
                    {"finanzgrundsatz", "angelegenheiten"}
                };
            */
            List<ArrayList<CharSequence>> wordSequences = new ArrayList<>(2);

            // Anonymous class for DecompisitionListener interface.
            this.listener = new DecompositionListener()
            {
                @Override
                public void decomposition(IntsRef utf32, ArrayDeque<Chunk> chunks)
                {
                    // multiple decompositions can be found, which is why we have a list of lists.
                    ArrayList<CharSequence> wordList = new ArrayList<>(3);
                    
                    // each chunk is a word-part.
                    Iterator<Chunk> i = chunks.descendingIterator();
                    while (i.hasNext())
                    {
                        Chunk chunk = i.next();
                        if (chunk.type == ChunkType.WORD)
                        {
                            final String wordChunk = chunk.toString();
                            // skip the word if its identical to the input.
                            if(wordChunk.equals(wordLower)) {
                                continue;
                            }
                            wordList.add(wordChunk);
                        }
                    }
                    // don't add empty lists
                    if(wordList.isEmpty()) 
                        return;
                    
                    // Add the word sub-parts list to wordSequences
                    wordSequences.add(wordList);
                }
            };

            maxPathsBuilder.clear();
            maxPathsBuilder.grow(utf32.length + 1);
            Arrays.fill(maxPathsBuilder.ints(), 0, utf32.length + 1, Integer.MAX_VALUE);

            // matches the sub-words, triggers the decompisition (this.listener) above.
            matchWord(utf32, utf32.offset);

            // sort the word sequences by the number of terms in each sequence, the sequence
            // with the greatest number of terms will be first in the returned list.
            sortListBySizeDescending(wordSequences);
            
            return wordSequences;
        }
        catch (IOException e)
        {
            // Shouldn't happen, but just in case.
            throw new RuntimeException(e);
        }
    }

    /**
     * Consume a word, then recurse into glue morphemes/ further words.
     */
    private void matchWord(IntsRef utf32, int offset) throws IOException
    {
        FST.Arc<Object> arc = surfaceForms.getFirstArc(new FST.Arc<>());
        FST.Arc<Object> scratch = new FST.Arc<>();
        List<Chunk> wordsFromHere = new ArrayList<>();

        BytesReader br = surfaceForms.getBytesReader();
        for (int i = offset; i < utf32.length; i++)
        {
            int chr = utf32.ints[i];

            arc = surfaceForms.findTargetArc(chr, arc, arc, br);
            if (arc == null) break;

            if (surfaceForms.findTargetArc(RTL_SYMBOL, arc, scratch, br) != null)
            {
                Chunk ch = new Chunk(offset, i + 1, ChunkType.WORD);
                wordsFromHere.add(ch);
            }
        }

        int [] maxPaths = maxPathsBuilder.ints();
        for (int j = wordsFromHere.size(); --j >= 0;)
        {
            final Chunk ch = wordsFromHere.get(j);

            if (chunks.size() + 1 > maxPaths[ch.end]) continue;
            maxPaths[ch.end] = chunks.size() + 1;

            chunks.addLast(ch);
            if (ch.end == utf32.offset + utf32.length)
            {
                listener.decomposition(this.utf32, chunks);
            }
            else
            {
                // no glue.
                matchWord(utf32, ch.end);
                // with glue.
                matchGlueMorpheme(utf32, ch.end);
            }
            chunks.removeLast();
        }
    }

    /**
     * Consume a maximal glue morpheme, if any, and consume the next word.
     */
    private void matchGlueMorpheme(IntsRef utf32, final int offset) throws IOException
    {
        FST.Arc<Object> arc = glueMorphemes.getFirstArc(new FST.Arc<Object>());
        BytesReader br = glueMorphemes.getBytesReader();
        for (int i = offset; i < utf32.length; i++)
        {
            int chr = utf32.ints[i];

            arc = glueMorphemes.findTargetArc(chr, arc, arc, br);
            if (arc == null) break;

            if (arc.isFinal())
            {
                Chunk ch = new Chunk(offset, i + 1, ChunkType.GLUE_MORPHEME);
                chunks.addLast(ch);
                if (i + 1 < utf32.offset + utf32.length)
                {
                    matchWord(utf32, i + 1);
                }
                chunks.removeLast();
            }
        }
    }

    /**
     * Convert a character sequence <code>s</code> into full unicode codepoints.
     */
    private IntsRefBuilder UTF16ToUTF32(CharSequence s, IntsRefBuilder builder)
    {
        builder.clear();

        for (int charIdx = 0, charLimit = s.length(); charIdx < charLimit;)
        {
            final int u32 = Character.codePointAt(s, charIdx);
            builder.append(u32);
            charIdx += Character.charCount(u32);
        }
        return builder;
    }

    /**
     * Load surface forms FST.
     */
    private FST<Object> readMorphyFST() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(FST_WORDS_FILE);
            
            if (inputStream == null) {
                throw new IOException("Ressource " + FST_WORDS_FILE + " nicht gefunden");
            }

            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            DataInput dataInput = new InputStreamDataInput(bufferedInputStream);

            Outputs<Object> outputs = NoOutputs.getSingleton();
            FST.FSTMetadata<Object> metadata = FST.readMetadata(dataInput, outputs);
            
            return new FST<>(metadata, dataInput);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Create glue morphemes FST.
     */
    private FST<Object> createMorphemesFST() throws IOException
    {
        String [] morphemes = {
            "e", "es", "en", "er", "n", "ens", "ns", "s"
        };

        // Inverse and sort.
        for (int i = 0; i < morphemes.length; i++)
        {
            morphemes[i] = new StringBuilder(morphemes[i]).reverse().toString();
        }
        Arrays.sort(morphemes);

        // Build FST.
        FSTCompiler<Object> compiler = new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE4, 
            NoOutputs.getSingleton()).build();
        final Object nothing = NoOutputs.getSingleton().getNoOutput();
        IntsRefBuilder intsBuilder = new IntsRefBuilder();
        
        for (String morpheme : morphemes)
        {
            UTF16ToUTF32(morpheme, intsBuilder);
            compiler.add(intsBuilder.get(), nothing);
        }
        
        FST.FSTMetadata<Object> metadata = compiler.compile();
        return FST.fromFSTReader(metadata, compiler.getFSTReader());
    }
    
    /**
     * Custom Comparator for sorting a list of lists.
     * 
     * @param <T>
     * @param list
     * @return 
     */
    public static <T> List<? extends List<T>> sortListBySizeDescending(List<? extends List<T>> list) {
        Collections.sort(list, (List<T> o1, List<T> o2) -> Integer.compare(o2.size(), o1.size()));
        
        Collections.sort(list, new Comparator<List>() {
            @Override
            public int compare(List o1, List o2) {
                if (o2.size() != o1.size() || o2.isEmpty() || o1.isEmpty()) {
                    return Integer.compare(o2.size(), o1.size());
                } else {
                    String o2ele0 = (String)o2.get(0);
                    String o1ele0 = (String)o1.get(0);
                    return Integer.compare(o1ele0.length(), o2ele0.length());
                }
                
            }
        });
        return list;

    } 
}
