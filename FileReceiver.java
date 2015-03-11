// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class FileReceiver {

    public DatagramSocket _socket;
    public DatagramPacket _packet;
    public CRC32 _checksum;

    private final static int PACKET_SIZE = 1000;
    private final static int MAX_FILENAME_LENGTH = 100; // 1 byte per alphabet
    private final static int TOTAL_FILESIZE_BYTE_LENGTH = 8;  // size of long
    private final static int PAYLOAD_FILESIZE_BYTE_LENGTH = 8;   // size of long
    private final static int CHECKSUM_BYTE_LENGTH = 8;  // size of long


    public static void main(String[] args) {

        // check if the number of command line argument is 1
        if (args.length != 1) {
            System.out.println("Usage: java FileReceiver <port>");
            System.exit(1);
        }

        new FileReceiver(args[0]);
    }

    private void flushByteBuffer(ByteBuffer bb) {
        bb.clear();
        bb.put(new byte[PACKET_SIZE]);
        bb.clear();
    }

    private String extractFileName(ByteBuffer bb) {
        String filename = "";

        byte[] tempFilename = new byte[MAX_FILENAME_LENGTH];
        bb.get(tempFilename, 0, MAX_FILENAME_LENGTH);

        filename = new String(tempFilename);
        filename = filename.trim();

        return filename;
    }

    private byte[] padFileName(String rcvFileName) {
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    private boolean validateData(byte[] data, long rcvChecksum) {
        CRC32 checksum = new CRC32();

        checksum.update(data);

        System.out.println("v1="+checksum.getValue()+" v2="+rcvChecksum);

        if (rcvChecksum == checksum.getValue())
            return true;

        return false;
    }

    public FileReceiver(String localPort) {
        int serverPort = Integer.parseInt(localPort);

        try {
            _socket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        byte[] rcvBuffer = new byte[1000];

        StringBuffer temp = new StringBuffer();

        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        boolean isFileTransferDone = false;
        long totalFileSize = 0;
        long currFileSize = 0;
        long fileDataSize = 0;
        long rcvChecksum = 0;
        String filename = "";
        ByteBuffer buffer;
        ByteBuffer hello;

        try {
            while (!isFileTransferDone) {
                _packet = new DatagramPacket(rcvBuffer, rcvBuffer.length);

                try {
                    _socket.receive(_packet);
                    buffer = ByteBuffer.wrap(_packet.getData());
                    buffer.clear();

                    //TODO: checksum check here
                    rcvChecksum = buffer.getLong();
                    System.out.println("checksum="+rcvChecksum);

                    // extract file name.
                    filename = extractFileName(buffer);
                    if (fos == null) {
                        try {
                            fos = new FileOutputStream(filename);
                            bos = new BufferedOutputStream(fos);
                        } catch (FileNotFoundException e) {
                            System.out.println(e.toString());
                        }
                    }

                    // extract target filesize.
                    totalFileSize = buffer.getLong();
                    fileDataSize = buffer.getLong();

                    byte[] fileDataBuffer = new byte[(int)fileDataSize];
                    buffer.get(fileDataBuffer);

                    // perform checksum
                    hello = ByteBuffer.wrap(new byte[PACKET_SIZE-CHECKSUM_BYTE_LENGTH]);
                    hello.put(padFileName(filename));
                    hello.putLong(totalFileSize);
                    hello.putLong(fileDataSize);
                    hello.put(fileDataBuffer);

                    if (validateData(hello.array(), rcvChecksum))
                        System.out.println("valid");
                    else
                        System.out.println("error");

                    bos.write(fileDataBuffer);

                    currFileSize += fileDataSize;
                    System.out.println(currFileSize + "/" + totalFileSize + "(" + (float)(((float)currFileSize/(float)totalFileSize)*100.0f) + "%)");

                    if (currFileSize >= totalFileSize)
                        isFileTransferDone = true;
                } catch (IOException e) {
                    System.out.println(e.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            try {
                bos.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
    }
}
