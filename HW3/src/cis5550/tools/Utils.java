package cis5550.tools;

public class Utils {
    public static boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
        } catch(NumberFormatException | NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }

    public static byte[] copyByteArray(byte[] ori, int start, int end) {
        byte[] copy = new byte[end-start+1+2];
        int copyStart = 0;
        while (start <= end) {
            copy[copyStart] = ori[start];
            copyStart ++;
            start ++;
        }
//        System.arraycopy(ori, start, copy, 0, end-start+1);
        byte[] crlf = "\r\n".getBytes();
//        System.arraycopy(crlf, 0, copy, start, crlf.length);
        for (int i = 0; i < crlf.length; i++) {
            copy[copyStart+i] = crlf[i];
        }
        return copy;
    }

    public static byte[] combineByteArrays(byte[] one, byte[] two) {
        byte[] combined = new byte[one.length+two.length];
        for (int i = 0; i < combined.length; ++i)
        {
            combined[i] = i < one.length ? one[i] : two[i - one.length];
        }
        return combined;
    }
}
