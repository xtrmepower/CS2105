// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

class FileSender {

    public DatagramSocket _socket;
    public DatagramPacket _packet;
    public InetAddress _serverAddress;

    private final static int PACKET_SIZE = 1000;
    private final static int MAX_FILENAME_LENGTH = 100;
    private final static int FILESIZE_BYTE_LENGTH = 16;

    private final static String HOSTNAME = "localhost";

    public static void main(String[] args) {

        // Check if the number of command line argument is 3
        if (args.length != 3) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<unreliNetPort> <rcvFileName>");
            System.exit(1);
        }

        new FileSender(args[0], HOSTNAME, args[1], args[2]);
    }

    private byte[] parseRcvFileName(String rcvFileName) {
        // Pad the file name with spaces till 255 characters.
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    private void flushByteBuffer(ByteBuffer bb) {
        bb.clear();
        bb.put(new byte[PACKET_SIZE]);
        bb.clear();
    }

    public FileSender(String fileToOpen, String host, String port, String rcvFileName) {

        try {
            _serverAddress = InetAddress.getByName(host);
            _socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        int serverPort = Integer.parseInt(port);
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        long numBytes = 0;
        long fileSize = 0;
        byte[] fileDataBuffer = new byte[PACKET_SIZE -
                                      FILESIZE_BYTE_LENGTH -
                                      MAX_FILENAME_LENGTH];
        byte[] tempPktBuffer = new byte[PACKET_SIZE];

        try {
            fis = new FileInputStream(fileToOpen);
            fileSize = fis.available();

            System.out.println("filesize = " + fileSize);
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        ByteBuffer pktBuffer = ByteBuffer.wrap(tempPktBuffer);

        // Send the actual file itself.
        bis = new BufferedInputStream(fis);
        try {
            numBytes = bis.read(fileDataBuffer);

            while (numBytes > 0) {
                pktBuffer.putLong(fileSize);
                pktBuffer.putLong(numBytes);
                pktBuffer.put(parseRcvFileName(rcvFileName));
                pktBuffer.put(fileDataBuffer);

                // Create a new packet with the data in the buffer.
                _packet = new DatagramPacket(pktBuffer.array(), PACKET_SIZE, _serverAddress, serverPort);

                // Send the packet via the client side's socket.
                _socket.send(_packet);

                // Continue reading the file for more data.
                fileDataBuffer = new byte[PACKET_SIZE -
                                        FILESIZE_BYTE_LENGTH -
                                        MAX_FILENAME_LENGTH];
                numBytes = bis.read(fileDataBuffer);

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println(e.toString());
                }

                flushByteBuffer(pktBuffer);
            }
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            try {
                bis.close();
                _socket.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
    }
}
