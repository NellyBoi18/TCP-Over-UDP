import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Utils {
    
    /**
     * Reads the entire content of a file into a byte array.
     * @param fileName the path of the file to read
     * @return a byte array containing the contents of the file
     * @throws IOException if an I/O error occurs reading from the stream
     */
    public static byte[] readFile(String fileName) throws IOException {
        File file = new File(fileName);
        byte[] fileData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(fileData);
            if (bytesRead != fileData.length) {
                throw new IOException("Entire file not read; expected " + fileData.length + " bytes, got " + bytesRead);
            }
        }
        return fileData;
    }
}
