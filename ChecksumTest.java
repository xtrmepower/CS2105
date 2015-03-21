import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class ChecksumTest {
    public static void main(String[] args) {
        byte[] ba = new byte[1000];
        long checksum = 0;
        long stuff = 123456789;

        ByteBuffer bf = ByteBuffer.wrap(ba);
        //bf.putLong(checksum);
        bf.putLong(8, stuff);

        CRC32 checksumObj = new CRC32();
        checksumObj.update(bf.array());
        checksum = checksumObj.getValue();

        System.out.println("checksum = "+checksum);

        bf.putLong(0, checksum);

        byte[] dest = new byte[1000];
        bf.get(dest);

        ByteBuffer bf2 = ByteBuffer.wrap(dest);

        CRC32 checksumObj1 = new CRC32();
        long checksum1 = bf2.getLong();
        System.out.println("checksum1 = " + checksum1);
        bf2.putLong(0, 0);

        checksumObj1.update(bf2.array());

        long checksum2 = checksumObj1.getValue();
        System.out.println("checksum2 = " + checksum2);
        if (checksum1 == checksum2)
            System.out.println("YAY!");
        else
            System.out.println("???");
    }
}
