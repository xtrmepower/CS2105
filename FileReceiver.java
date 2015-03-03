// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

class FileReceiver {

    public DatagramSocket socket;
    public DatagramPacket pkt;

    private final static int PACKET_SIZE = 1000;
    private final static int MAX_FILENAME_LENGTH = 100;
    private final static int FILESIZE_BYTE_LENGTH = 16;

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

    public FileReceiver(String localPort) {
        int serverPort = Integer.parseInt(localPort);

        try {
            socket = new DatagramSocket(serverPort);
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
        String filename = "";
        ByteBuffer buffer;

        try {
            while (!isFileTransferDone) {
                pkt = new DatagramPacket(rcvBuffer, rcvBuffer.length);

                try {
                    socket.receive(pkt);
                    buffer = ByteBuffer.wrap(pkt.getData());
                    buffer.clear();

                    // extract target filesize.
                    totalFileSize = buffer.getLong();
                    fileDataSize = buffer.getLong();

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

                    bos.write(rcvBuffer, MAX_FILENAME_LENGTH+FILESIZE_BYTE_LENGTH, (int)fileDataSize);
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
