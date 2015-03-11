// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class FileSender {

    private final static int PACKET_SIZE = 1000;
    private final static int MAX_FILENAME_LENGTH = 100; // 1 byte per alphabet
    private final static int TOTAL_FILESIZE_BYTE_LENGTH = 8;  // size of long
    private final static int PAYLOAD_FILESIZE_BYTE_LENGTH = 8;   // size of long
    private final static int CHECKSUM_BYTE_LENGTH = 8;  // size of long

    private final static String HOSTNAME = "localhost";

    private DatagramSocket _socket;
    private DatagramPacket _packet;
    private InetAddress _serverAddress;
    private int _serverPort;
    private FileInputStream _fis;
    private BufferedInputStream _bis;
    private int _totalFileSize;
    private int _payloadSize;
    private ByteBuffer _packetBuffer;
    private ByteBuffer _tempBuffer;

    public static void main(String[] args) {

        // Check if the number of command line argument is 3
        if (args.length != 3) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<unreliNetPort> <rcvFileName>");
            System.exit(1);
        }

        new FileSender(args[0], HOSTNAME, args[1], args[2]);
    }

    // Pad the filename with spaces until 100 characters.
    private byte[] padFileName(String rcvFileName) {
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    private void flushByteBuffer(ByteBuffer bb, int bbsize) {
        bb.clear();
        bb.put(new byte[bbsize]);
        bb.clear();
    }

    private long getChecksum(byte[] data) {
        CRC32 checksum = new CRC32();

        checksum.update(data);

        return checksum.getValue();
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

        _serverPort = Integer.parseInt(port);
        _fis = null;
        _bis = null;
        _payloadSize = 0;
        _totalFileSize = 0;
        byte[] fileDataBuffer = new byte[PACKET_SIZE -
                                      TOTAL_FILESIZE_BYTE_LENGTH -
                                      PAYLOAD_FILESIZE_BYTE_LENGTH -
                                      CHECKSUM_BYTE_LENGTH -
                                      MAX_FILENAME_LENGTH];
        _tempBuffer = ByteBuffer.wrap(new byte[PACKET_SIZE-CHECKSUM_BYTE_LENGTH]);
        _packetBuffer = ByteBuffer.wrap(new byte[PACKET_SIZE]);

        try {
            _fis = new FileInputStream(fileToOpen);
            _totalFileSize = _fis.available();

            System.out.println("filesize = " + _totalFileSize);
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        // Send the actual file itself.
        _bis = new BufferedInputStream(_fis);
        try {
            _payloadSize = _bis.read(fileDataBuffer);

            while (_payloadSize > 0) {
                _tempBuffer.put(padFileName(rcvFileName));   // received file name
                _tempBuffer.putLong(_totalFileSize);    // total file size
                _tempBuffer.putLong(_payloadSize);    // payload size (amt of file data)
                _tempBuffer.put(fileDataBuffer);  // actual payload data

                // Perform checksum on the data and put it into the packet buffer.
                long checksum = getChecksum(_tempBuffer.array());
                System.out.println("checksum="+checksum);
                _packetBuffer.putLong(checksum);
                _packetBuffer.put(_tempBuffer.array());

                // Create a new packet with the data in the buffer.
                _packet = new DatagramPacket(_packetBuffer.array(), PACKET_SIZE, _serverAddress, _serverPort);

                // Send the packet via the client side's socket.
                _socket.send(_packet);

                // Continue reading the file for more data.
                fileDataBuffer = new byte[PACKET_SIZE -
                                          TOTAL_FILESIZE_BYTE_LENGTH -
                                          PAYLOAD_FILESIZE_BYTE_LENGTH -
                                          CHECKSUM_BYTE_LENGTH -
                                          MAX_FILENAME_LENGTH];
                _payloadSize = _bis.read(fileDataBuffer);

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    System.out.println(e.toString());
                }

                flushByteBuffer(_packetBuffer, PACKET_SIZE);
                flushByteBuffer(_tempBuffer, PACKET_SIZE-CHECKSUM_BYTE_LENGTH);
            }
        } catch (IOException e) {
            System.out.println(e.toString());
        } finally {
            try {
                _bis.close();
                _socket.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
    }
}
