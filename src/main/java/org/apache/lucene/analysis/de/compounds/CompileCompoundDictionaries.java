package org.apache.lucene.analysis.de.compounds;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Pattern;

import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;
import org.apache.lucene.util.fst.FSTCompiler;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.FST.INPUT_TYPE;
import org.apache.lucene.util.fst.NoOutputs;


/**
 * Compile an FSA from an UTF-8 text file (must be properly sorted).
 */
public class CompileCompoundDictionaries
{
    public static void main(String [] args) throws Exception
    {
        if (args.length < 1)
        {
            System.out.println("No arguments provided! Please provide input file(s) as arguments");
            System.out.println("Args: input1.txt input2.txt ...");
            System.exit(-1);
        }
        String clsName =  CompileCompoundDictionaries.class.getSimpleName();
        System.out.println(String.format("%s arguments: %s", clsName, Arrays.toString(args)));

        final HashSet<BytesRef> words = new HashSet<BytesRef>();
        for (int i = 0; i < args.length; i++)
        {
            int count = 0;
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(args[i]), "UTF-8"));

            Pattern pattern = Pattern.compile("\\s+");
            String line, last = null;
            StringBuilder buffer = new StringBuilder();
            System.out.println(String.format("%s iterating file: %s",  clsName, args[i]));
            while ((line = reader.readLine()) != null)
            {
                // ignore comments
                if (line.trim().startsWith("#"))
                    continue;

                line = pattern.split(line)[0].trim();
                line = line.toLowerCase();

                if (line.equals(last)) continue;
                last = line;

                /*
                 * Add the word to the hash set in left-to-right characters order and reversed
                 * for easier matching later on.
                 */

                buffer.setLength(0);
                buffer.append(line);
                final int len = buffer.length();

                buffer.append(GermanCompoundSplitter.LTR_SYMBOL);
                words.add(new BytesRef(buffer));

                buffer.setLength(len);
                buffer.reverse().append(GermanCompoundSplitter.RTL_SYMBOL);
                words.add(new BytesRef(buffer));
                if ((++count % 100000) == 0) System.out.println("Line: " + count);
            }
            reader.close();

            System.out.println(String.format("%s, words: %d", args[i], count));
        }

        final BytesRef [] all = new BytesRef [words.size()];
        words.toArray(all);

        // These lines were modified for 6.x api changes...
        // removed in https://issues.apache.org/jira/browse/LUCENE-7053
        // ""This patch also removes the BytesRef-Comparator completely and just 
        // implements compareTo. So all code can rely on natural ordering.""
        // Arrays.sort(all, BytesRef.getUTF8SortedAsUnicodeComparator());
        Arrays.sort(all); // rely on natural ordering
        serialize("src/main/resources/words.fst", all);
    }

    private static void serialize(String file, BytesRef [] all) throws IOException
    {
        final Object nothing = NoOutputs.getSingleton().getNoOutput();
        FSTCompiler.Builder<Object> builder = new FSTCompiler.Builder<>(
            FST.INPUT_TYPE.BYTE4,
            NoOutputs.getSingleton()
        );
        FSTCompiler<Object> compiler = builder.build();
        final IntsRefBuilder intsRef = new IntsRefBuilder();
        for (BytesRef br : all)
        {
            intsRef.clear();
            intsRef.copyUTF8Bytes(br);
            compiler.add(intsRef.get(), nothing);
        }

        FST.FSTMetadata<Object> fstMetadata = compiler.compile();
        final FST<Object> fst = FST.fromFSTReader(fstMetadata, compiler.getFSTReader());

        fst.save(Paths.get(file));
    }
}
