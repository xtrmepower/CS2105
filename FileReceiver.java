// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class FileReceiver {

    private final static int PACKET_SIZE = 1000;
    private final static int MAX_FILENAME_LENGTH = 100; // 1 byte per alphabet
    private final static int TOTAL_FILESIZE_BYTE_LENGTH = 8;  // size of long
    private final static int PAYLOAD_FILESIZE_BYTE_LENGTH = 8;   // size of long
    private final static int CHECKSUM_BYTE_LENGTH = 8;  // size of long

    private DatagramSocket _socket;
    private DatagramPacket _packet;
    private boolean _isFileTransferDone = false;
    private long _totalFileSize = 0;
    private long _currFileSize = 0;
    private long _payloadSize = 0;
    private long _rcvChecksum = 0;
    private String _filename = "";
    private FileOutputStream _fos = null;
    private BufferedOutputStream _bos = null;
    private byte[] _rcvBufferArray;
    private byte[] _payloadBufferArray;
    private ByteBuffer _packetBuffer;
    private ByteBuffer _tempBuffer;

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

    private void printCompletionPercentage(float current, float total) {
        float percent = (current/total)*100.0f;

        System.out.println(current + "/" + total + "(" + percent + "%)");
    }

    public FileReceiver(String localPort) {
        int serverPort = Integer.parseInt(localPort);

        try {
            _socket = new DatagramSocket(serverPort);
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        _rcvBufferArray = new byte[PACKET_SIZE];

        try {
            while (!_isFileTransferDone) {
                _packet = new DatagramPacket(_rcvBufferArray, _rcvBufferArray.length);

                try {
                    _socket.receive(_packet);
                    _packetBuffer = ByteBuffer.wrap(_packet.getData());
                    _packetBuffer.clear();

                    _rcvChecksum = _packetBuffer.getLong();

                    // extract file name.
                    _filename = extractFileName(_packetBuffer);
                    if (_fos == null) {
                        try {
                            _fos = new FileOutputStream(_filename);
                            _bos = new BufferedOutputStream(_fos);
                        } catch (FileNotFoundException e) {
                            System.out.println(e.toString());
                        }
                    }

                    // extract target filesize.
                    _totalFileSize = _packetBuffer.getLong();
                    _payloadSize = _packetBuffer.getLong();

                    _payloadBufferArray = new byte[(int)_payloadSize];
                    _packetBuffer.get(_payloadBufferArray);

                    // perform checksum
                    _tempBuffer = ByteBuffer.wrap(new byte[PACKET_SIZE-CHECKSUM_BYTE_LENGTH]);
                    _tempBuffer.put(padFileName(_filename));
                    _tempBuffer.putLong(_totalFileSize);
                    _tempBuffer.putLong(_payloadSize);
                    _tempBuffer.put(_payloadBufferArray);

                    if (validateData(_tempBuffer.array(), _rcvChecksum))
                        System.out.println("valid");
                    else
                        System.out.println("error");

                    _bos.write(_payloadBufferArray);

                    _currFileSize += _payloadSize;
                    printCompletionPercentage(_currFileSize, _totalFileSize);

                    if (_currFileSize >= _totalFileSize)
                        _isFileTransferDone = true;
                } catch (IOException e) {
                    System.out.println(e.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        } finally {
            try {
                if (_bos != null)
                    _bos.close();
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
    }
}
