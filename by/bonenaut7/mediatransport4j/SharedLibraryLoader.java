package by.bonenaut7.mediatransport4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SharedLibraryLoader {
    public static boolean isWindows = System.getProperty("os.name").contains("Windows");
    public static boolean isLinux = System.getProperty("os.name").contains("Linux");
    public static boolean isMac = System.getProperty("os.name").contains("Mac");
    public static boolean is64Bit = System.getProperty("os.arch").equals("amd64");
    private static HashSet<String> loadedLibraries = new HashSet();
    private String nativesJar;

    public SharedLibraryLoader() {
    }

    public SharedLibraryLoader(String nativesJar) {
        this.nativesJar = nativesJar;
    }

    public String crc(InputStream input) {
        if (input == null) {
            return "" + System.nanoTime();
        } else {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[4096];

            try {
                while(true) {
                    int length = input.read(buffer);
                    if (length == -1) {
                        break;
                    }

                    crc.update(buffer, 0, length);
                }
            } catch (Exception var7) {
                try {
                    input.close();
                } catch (Exception var6) {
                }
            }

            return Long.toString(crc.getValue());
        }
    }

    public String mapLibraryName(String libraryName) {
        if (isWindows) {
            return libraryName + (is64Bit ? "64.dll" : ".dll");
        } else if (isLinux) {
            return "lib" + libraryName + (is64Bit ? "64.so" : ".so");
        } else {
            return isMac ? "lib" + libraryName + ".dylib" : libraryName;
        }
    }

    public synchronized void load(String libraryName) {
        libraryName = this.mapLibraryName(libraryName);
        if (!loadedLibraries.contains(libraryName)) {
            try {
                System.load(this.extractFile(libraryName, (String)null).getAbsolutePath());
            } catch (Throwable ex) {
                throw new RuntimeException("Couldn't load shared library '" + libraryName + "' for target: " + System.getProperty("os.name") + (is64Bit ? ", 64-bit" : ", 32-bit"), ex);
            }

            loadedLibraries.add(libraryName);
        }
    }

    private InputStream readFile(String path) {
        if (this.nativesJar == null) {
            return SharedLibraryLoader.class.getResourceAsStream("/" + path);
        } else {
            try {
                ZipFile file = new ZipFile(this.nativesJar);
                ZipEntry entry = file.getEntry(path);
                if (entry == null) {
                    throw new RuntimeException("Couldn't find '" + path + "' in JAR: " + this.nativesJar);
                } else {
                    return file.getInputStream(entry);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error reading '" + path + "' in JAR: " + this.nativesJar, ex);
            }
        }
    }

    public File extractFile(String sourcePath, String dirName) throws IOException {
        String sourceCrc = this.crc(this.readFile(sourcePath));
        if (dirName == null) {
            dirName = sourceCrc;
        }

        File extractedDir = new File(System.getProperty("java.io.tmpdir") + "/mediatransport4j" + System.getProperty("user.name") + "/" + dirName);
        File extractedFile = new File(extractedDir, (new File(sourcePath)).getName());
        String extractedCrc = null;
        if (extractedFile.exists()) {
            try {
                extractedCrc = this.crc(new FileInputStream(extractedFile));
            } catch (FileNotFoundException var11) {
            }
        }

        if (extractedCrc == null || !extractedCrc.equals(sourceCrc)) {
            try {
                InputStream input = this.readFile(sourcePath);
                if (input == null) {
                    return null;
                }

                extractedDir.mkdirs();
                FileOutputStream output = new FileOutputStream(extractedFile);
                byte[] buffer = new byte[4096];

                while(true) {
                    int length = input.read(buffer);
                    if (length == -1) {
                        input.close();
                        output.close();
                        break;
                    }

                    output.write(buffer, 0, length);
                }
            } catch (IOException ex) {
                throw new RuntimeException("Error extracting file: " + sourcePath, ex);
            }
        }

        return extractedFile.exists() ? extractedFile : null;
    }
}
