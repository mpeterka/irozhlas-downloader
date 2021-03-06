package cz.peterka.irozhlas;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Opravátor již pokažených dlouhých souborů
 */
public class Rename {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Zadejte adresář");
        }
        File folder = new File(args[0]);

        final File[] files = folder.listFiles((FilenameFilter) new SuffixFileFilter(".mp3"));
        for (File file : files) {
            final String fixed = fixFilenameLength(FilenameUtils.getName(file.getName()), 250);
            final File dest = new File(FilenameUtils.getFullPath(file.getPath()) + "/" + fixed);
            System.out.println(file + "\n" + dest);
            System.out.println();
//            FileUtils.moveFile(file, dest);
            Files.move(Paths.get(file.toURI()), Paths.get(dest.toURI()));
        }
    }

    /**
     * FIXME: tohle stejne nefunguje dobre, mohl by stacit datum + nejake ID
     * @param input
     * @param maxLength
     * @return
     */
    protected static String fixFilenameLength(String input, int maxLength) {
        String result = input;
        result = StringUtils.stripAccents(result);
        result =
                result.replaceAll("\uF022", "_")
                        .replaceAll("„", "_")
                        .replaceAll("“", "_")
                        .replaceAll(":", ".")
                        .replaceAll("'", "_")
                        .replaceAll("..mp3", ".mp3")
                ;
        int length = maxLength;
        while (result.getBytes(StandardCharsets.UTF_8).length > maxLength) {
            length--;
            result = StringUtils.abbreviate(result, "...", length);
        }
        return result;
    }
}
