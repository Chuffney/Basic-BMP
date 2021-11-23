import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BMP {
    private boolean initialised = false;
    private int[][] R;
    private int[][] G;
    private int[][] B;
    private int[][] A;
    private int width;
    private int height;
    private int bitDepth = 24;

    private static final String fourBytes = "00000000";

    public enum RGBA {
        RED(0),
        GREEN(1),
        BLUE(2),
        ALPHA(3);

        private final int value;

        RGBA(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static String getName(RGBA colour) {
            switch (colour) {
                case RED:
                    return "Red";
                case GREEN:
                    return "Green";
                case BLUE:
                    return "Blue";
                case ALPHA:
                    return "Alpha";
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public BMP() {
    }

    public BMP(String fileName) throws IOException {
        open(fileName);
    }

    public BMP(File file) throws IOException {
        open(file);
    }

    public void open(String filePath) throws IOException {
        if (!initialised) {
            if (filePath == null) {
                throw new NullPointerException();
            } else {
                if (!(filePath.endsWith(".bmp") || filePath.endsWith(".BMP"))) {
                    filePath = filePath.concat(".bmp");
                }
                File file = new File(filePath);
                if (file.exists()) {
                    init(file);
                } else {
                    throw new FileNotFoundException(filePath + " could not be found");
                }
                initialised = true;
            }
        } else {
            System.out.println("Close the existing bitmap before opening another");
        }
    }

    public void open(File file) throws IOException {
        open(file.getPath());
    }

    public void close() {
        initialised = false;
        R = null;
        G = null;
        B = null;
        A = null;
        bitDepth = 24;
    }

    public void setSize(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("Image width must be greater than zero. Assigned width: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("Image width must be greater than zero. Assigned width: " + height);
        }
        this.width = width;
        this.height = height;
        R = new int[width][height];
        G = new int[width][height];
        B = new int[width][height];
        if (bitDepth == 32) {
            A = new int[width][height];
            for (int i = 0; i < width; i++) {
                Arrays.fill(A[i], 255);
            }
        }
        initialised = true;
    }

    public void setBitDepth(int value) {
        switch (value) {
            case 32:
                if (A == null && width > 0 && height > 0) {
                    A = new int[width][height];
                    for (int i = 0; i < width; i++) {
                        Arrays.fill(A[i], 255);
                    }
                }
            case 24:
                bitDepth = value;
                break;
            default:
                throwBitDepth(value);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public int getColour(int x, int y, RGBA colour) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throwCoordinates(x, y);
            throw new IllegalStateException();
        }
        switch (colour) {
            case RED:
                return R[x][y];
            case GREEN:
                return G[x][y];
            case BLUE:
                return B[x][y];
            case ALPHA:
                if (bitDepth == 32) {
                    return A[x][y];
                } else {
                    return 255;
                }
            default:
                throw new IllegalStateException();
        }
    }

    private void init(File file) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1));
        int[] header = new int[70];
        for (int i = 0; i < 0x1E; i++) {
            header[i] = reader.read();
        }
        int offset = makeBigEndian(header, 0xA, 0xE);
        width = makeBigEndian(header, 0x12, 0x16);
        height = makeBigEndian(header, 0x16, 0x1A);
        bitDepth = makeBigEndian(header, 0x1C, 0x1E);
        R = new int[width][height];
        G = new int[width][height];
        B = new int[width][height];
        switch (bitDepth) {
            case 32:
                A = new int[width][height];
                for (int i = 0; i < width; i++) {
                    Arrays.fill(A[i], 255);
                }
                for (int i = 0x1E; i < 0x46; i++) {
                    header[i] = reader.read();
                }
                int[] colourTable = digestColourTable(Arrays.copyOfRange(header, 0x36, 0x46));
                if (reader.skip(offset - 0x46) != offset - 0x46) {
                    throw new IOException();
                }
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = 0; x < width; x++) {
                        for (int i = 0; i < 4; i++) {
                            if (colourTable[RGBA.RED.getValue()] == i) {
                                R[x][y] = reader.read();
                            } else if (colourTable[RGBA.GREEN.getValue()] == i) {
                                G[x][y] = reader.read();
                            } else if (colourTable[RGBA.BLUE.getValue()] == i) {
                                B[x][y] = reader.read();
                            } else if (colourTable[RGBA.ALPHA.getValue()] == i) {
                                A[x][y] = reader.read();
                            }
                        }
                        checkPixel(x, y);
                    }
                }
                break;
            case 24:
                if (reader.skip(offset - 0x1E) != offset - 0x1E) {
                    throw new IOException("Corrupted file (" + file.getName() + ")");
                }
                int padding = (4 - ((width * 3) % 4)) % 4;
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = 0; x < width; x++) {
                        B[x][y] = reader.read();
                        G[x][y] = reader.read();
                        R[x][y] = reader.read();
                        checkPixel(x, y);
                    }
                    reader.skip(padding);
                }
                break;
            default:
                throwBitDepth(bitDepth);
        }
    }

    private static int[] digestColourTable(int[] colourTable) {
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) {
            long current = lMakeBigEndian(Arrays.copyOfRange(colourTable, i * 4, (i * 4) + 4));
            if (current == 0xFF) {
                result[RGBA.RED.getValue()] = i;
            } else if (current == 0xFF00) {
                result[RGBA.GREEN.getValue()] = i;
            } else if (current == 0xFF0000) {
                result[RGBA.BLUE.getValue()] = i;
            } else if (current == 0xFF000000L) {
                result[RGBA.ALPHA.getValue()] = i;
            }
        }
        return result;
    }

    private static int makeBigEndian(int[] num) {
        int length = num.length;
        int[] reversed = new int[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = num[length - 1 - i];
        }
        int sum = 0;
        int mul = 1;
        for (int i = length - 1; i >= 0; i--) {
            sum += reversed[i] * mul;
            mul *= 256;
        }
        return sum;
    }

    private static long lMakeBigEndian(int[] num) {
        int length = num.length;
        int[] reversed = new int[length];
        for (int i = 0; i < length; i++) {
            reversed[i] = num[length - 1 - i];
        }
        long sum = 0;
        long mul = 1;
        for (int i = length - 1; i >= 0; i--) {
            sum += (long) reversed[i] * mul;
            mul *= 256;
        }
        return sum;
    }

    private static int makeBigEndian(int[] num, int start, int end) {
        return makeBigEndian(Arrays.copyOfRange(num, start, end));
    }

    private static String makeLittleEndian(int num, int bytes) {
        if (bytes > 4) {
            throw new IllegalArgumentException("This method does not support long int data type (max bytes = 4");
        } else if (bytes < 0) {
            throw new IllegalArgumentException("Number of assigned bytes cannot be negative");
        } else {
            int reversed = Integer.reverseBytes(num);
            StringBuilder str = new StringBuilder(Integer.toHexString(reversed));
            while (str.length() < 8) {
                str.insert(0, "0");
            }
            str = new StringBuilder(str.substring(0, (bytes * 2)));
            if (32 - Integer.numberOfTrailingZeros(reversed) > (bytes * 8)) {
                throw new IllegalArgumentException("Number of assigned bytes is lower than the number's size, resulting in a data loss");
            }
            return str.toString();
        }
    }

    private static String makeLittleEndian(int num) {
        return makeLittleEndian(num, 4);
    }

    private static String extend(String hexValue, int bytes) {
        while (hexValue.length() < bytes * 2) {
            hexValue = "0".concat(hexValue);
        }
        return hexValue;
    }

    private static String encode(String hexValue) {
        String str = "";
        for (int i = 0; i < hexValue.length(); i += 2) {
            int in = Integer.parseInt(hexValue.substring(i, i + 2), 16);
            str = str.concat(String.valueOf((char) in));
        }
        return str;
    }

    public void setColour(int x, int y, RGBA colour, int value) {
        if (value < 0 || value > 255) {
            throwColourValue(colour, value, x, y);
        } else if (x < 0 || x >= width || y < 0 || y >= height) {
            throwCoordinates(x, y);
        }
        switch (colour) {
            case RED:
                R[x][y] = value;
                break;
            case GREEN:
                G[x][y] = value;
                break;
            case BLUE:
                B[x][y] = value;
                break;
            case ALPHA:
                if (bitDepth == 32) {
                    A[x][y] = value;
                } else {
                    System.out.println("24 bpp bitmaps do not support alpha channel, to achieve transparency use 32 bpp");
                }
        }
    }

    public void setColour(int x, int y, int value) {
        setColour(x, y, RGBA.RED, value);
        setColour(x, y, RGBA.GREEN, value);
        setColour(x, y, RGBA.BLUE, value);
    }

    private void checkPixel(int x, int y) {
        if (R[x][y] > 255 || R[x][y] < 0) {
            throwColourValue(RGBA.RED, R[x][y], x, y);
        } else if (G[x][y] > 255 || G[x][y] < 0) {
            throwColourValue(RGBA.GREEN, G[x][y], x, y);
        } else if (B[x][y] > 255 || B[x][y] < 0) {
            throwColourValue(RGBA.BLUE, B[x][y], x, y);
        } else if (bitDepth == 32 && (A[x][y] > 255 || A[x][y] < 0)) {
            throwColourValue(RGBA.ALPHA, A[x][y], x, y);
        }
    }

    public void export(String fileName) {
        if (!initialised) {
            System.out.println("Cannot export - image not initialised");
            return;
        }
        int padding = (4 - ((width * (bitDepth / 8)) % 4)) % 4;
        int rawPixelSize = 4 * (width * height);
        int fileSize;
        switch (bitDepth) {
            case 24:
                fileSize = 54 + (width * height * 3) + height * padding;
                break;
            case 32:
                fileSize = 70 + (width * height * 4);
                break;
            default:
                close();
                throwBitDepth(bitDepth);
                return;
        }
        if (!(fileName.endsWith(".bmp") || fileName.endsWith(".BMP"))) {
            fileName = fileName.concat(".bmp");
        }
        File file = new File(fileName);
        try {
            if (file.exists()) {
                if (!file.delete()) {
                    throw new IOException("File cannot be overwritten (" + fileName + ")");
                }
            }
            if (!file.createNewFile()) {
                return;
            }
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.ISO_8859_1);
            writer.write("BM");
            writer.write(encode(makeLittleEndian(fileSize)));
            writer.write(encode(fourBytes));
            switch (bitDepth) {
                case 24:
                    writer.write(encode(makeLittleEndian(54)));
                    writer.write(encode(makeLittleEndian(40)));
                    writer.write(encode(makeLittleEndian(width)));
                    writer.write(encode(makeLittleEndian(height)));
                    writer.write(encode(makeLittleEndian(1, 2)));
                    writer.write(encode(makeLittleEndian(bitDepth, 2)));
                    for (int i = 0; i < 6; i++) {
                        writer.write(encode(fourBytes));
                    }
                    break;
                case 32:
                    writer.write(encode(makeLittleEndian(70)));
                    writer.write(encode(makeLittleEndian(56)));
                    writer.write(encode(makeLittleEndian(width)));
                    writer.write(encode(makeLittleEndian(height)));
                    writer.write(encode(makeLittleEndian(1, 2)));
                    writer.write(encode(makeLittleEndian(bitDepth, 2)));
                    writer.write(encode(makeLittleEndian(3)));
                    writer.write(encode(makeLittleEndian(rawPixelSize)));
                    for (int i = 0; i < 2; i++) {
                        writer.write(encode("130B0000"));
                    }
                    for (int i = 0; i < 2; i++) {
                        writer.write(encode(fourBytes));
                    }
                    writer.write(encode("000000FF"));
                    writer.write(encode("0000FF00"));
                    writer.write(encode("00FF0000"));
                    writer.write(encode("FF000000"));
                    break;
            }
            for (int i = height - 1; i >= 0; i--) {
                for (int j = 0; j < width; j++) {
                    if (bitDepth == 32) {
                        writer.write(encode(extend(Integer.toHexString(A[j][i]), 1)));
                    }
                    writer.write(encode(extend(Integer.toHexString(B[j][i]), 1)));
                    writer.write(encode(extend(Integer.toHexString(G[j][i]), 1)));
                    writer.write(encode(extend(Integer.toHexString(R[j][i]), 1)));
                }
                for (int k = 0; k < padding; k++) {
                    writer.write(encode("00"));
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void export(File file) {
        export(file.getPath());
    }

    private static void throwCoordinates(int x, int y) {
        throw new IndexOutOfBoundsException("Specified coordinates are outside image boundaries [" + x + ", " + y + "]");
    }

    private void throwBitDepth(int value) {
        throw new IllegalArgumentException("Unsupported bit depth (" + value + ")");
    }

    private void throwColourValue(RGBA colour, int value, int x, int y) {
        throw new IllegalArgumentException("Colour value must be between 0 and 255 (" + RGBA.getName(colour) + ", " + value + ", [" + x + ", " + y + "])");
    }
}
