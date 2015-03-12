import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

// Parent class for the various packet classes.
abstract class DataPacket {

    // Packet constants
    protected final static int PACKET_SIZE = 1000;
    protected final static int CHECKSUM_BYTE_LENGTH = 8;  // size of long
    protected final static String HOSTNAME = "localhost";

    // The packet itself
    protected DatagramPacket _packet;

    // Common things required by all packet types
    protected CRC32 _checksumObj;
    protected long _checksum;
    protected long _seqNo;
    protected InetAddress _address; // Used for sending data.
    protected int _port;            // Used for sending data.

    // Constructor
    public DataPacket() {
        _checksumObj = new CRC32();
        _checksum = 0;
        _seqNo = 0;
        _port = 0;

        // Assumption: Everyone is on localhost
        try {
            _address = InetAddress.getByName(HOSTNAME);
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        }
    }

    public long getSeqNo() {
        return _seqNo;
    }

    // Checks to see if all the data needed to make a packet is obtained.
    protected abstract boolean hasAllData();

    // Creates and returns DatagramPacket sent through a socket
    public abstract DatagramPacket createPacket();
};


// Contains the file name, total file size, and CRC32 checksum in a
// DatagramPacket.
//
// 0 ~ 7:           [CRC32 Checksum]
// 8 ~ 15:          [TotalFileSize]
// 16 ~ 115:        [FileName]
class FileHeaderPacket extends DataPacket {

    // Packet constants
    private final static int MAX_FILENAME_LENGTH = 100; // 1 byte per alphabet
    private final static int TOTAL_FILESIZE_BYTE_LENGTH = 8;  // size of long

    private long _totalFileSize;
    private String _fileName;

    public FileHeaderPacket(int port) {
        super();

        _totalFileSize = 0;
        _fileName = "";
        _port = port;
    }

    // To be used only by the sender class.
    public void setFileName(String fileName) {
        _fileName = fileName;
    }

    public void setTotalFileSize(long totalFileSize) {
        _totalFileSize = totalFileSize;
    }

    // To be used only by the receiver class.
    public String getFileName() {
        return _fileName;
    }

    public long getTotalFileSize() {
        return _totalFileSize;
    }

    // Checks to see if all the data needed to construct a packet
    // has been obtained.
    protected boolean hasAllData() {
        if (!_fileName.equals("") && _totalFileSize != 0)
            return true;

        return false;
    }

    // Creates a DatagramPacket that contains the file header information
    // as well as the checksum.
    public DatagramPacket createPacket() {

        if (!hasAllData())
            return null;

        // Create byte array to store data.
        byte[] temp = new byte[ MAX_FILENAME_LENGTH +
                                TOTAL_FILESIZE_BYTE_LENGTH ];

        // Wrap it with a ByteBuffer to facilite operations.
        ByteBuffer bf = ByteBuffer.wrap(temp);

        // Insert data into the buffer.
        bf.putLong(_totalFileSize);
        bf.put(padFileName(_fileName));

        // Generate the checksum for the data.
        _checksumObj.update(bf.array());
        _checksum = _checksumObj.getValue();

        // Create 2nd byte array to store data AND checksum.
        byte[] temp2 = new byte[ MAX_FILENAME_LENGTH +
                                TOTAL_FILESIZE_BYTE_LENGTH +
                                CHECKSUM_BYTE_LENGTH ];

        // Wrap it with a ByteBuffer to facilite operations.
        ByteBuffer bf2 = ByteBuffer.wrap(temp2);

        // Insert checksum and data into buffer.
        bf2.putLong(_checksum);
        bf2.put(bf.array());

        _packet = new DatagramPacket(   bf2.array(),
                                        bf2.array().length,
                                        _address,
                                        _port   );

        return _packet;
    }

    // Parses the data received into its respective format and locations
    // to ease obtaining information.
    // Returns true if the data received matches the checksum included (i.e. valid),
    // false otherwise (i.e. invalid/corrupt).
    public boolean parsePacket(byte[] data) {

        // Ensure that we have data being passed in.
        if (data == null) {
            return false;
        }

        // Wrap data with ByteBuffer to ease operations.
        ByteBuffer bf = ByteBuffer.wrap(data);

        long rcvChecksum = bf.getLong();
        long rcvTotalFileSize = bf.getLong();
        String rcvFileName = extractFileName(bf);

        // Create byte array to store data to be checksumed.
        byte[] temp = new byte[ MAX_FILENAME_LENGTH +
                                TOTAL_FILESIZE_BYTE_LENGTH ];

        // Wrap temp byte array with ByteBuffer to ease operations.
        ByteBuffer bf2 = ByteBuffer.wrap(temp);

        // Insert received data into ByteBuffer to checksum.
        bf2.putLong(rcvTotalFileSize);
        bf2.put(padFileName(rcvFileName));

        // Generate the checksum for the received data.
        _checksumObj.update(bf2.array());
        _checksum = _checksumObj.getValue();

        // Compare the checksums.
        if (rcvChecksum != _checksum) {
            System.out.println("ERROR: Corrupted Packet!");

            return false;
        }

        // Received data is valid, store them.
        _totalFileSize = rcvTotalFileSize;
        _fileName = rcvFileName;

        return true;
    }

    // Helper function: To pad file name until 100 characters.
    private byte[] padFileName(String rcvFileName) {
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    // Helper function: To extract the file name from the padded one.
    private String extractFileName(ByteBuffer bb) {
        String filename = "";

        byte[] tempFilename = new byte[MAX_FILENAME_LENGTH];
        bb.get(tempFilename, 0, MAX_FILENAME_LENGTH);

        filename = new String(tempFilename);
        filename = filename.trim();

        return filename;
    }
};


// Contains the payload data size, payload data itself, and CRC32 checksum in a
// DatagramPacket.
//
// 0 ~ 7:           [CRC32 Checksum]
// 8 ~ 15:          [SequenceNo]
// 16 ~ 23:         [PayloadDataSize]
// 23 ~ LAST:       [PayloadData]
class PayloadPacket extends DataPacket {

    // Packet constants
    private final static int SEQUENCE_NO_BYTE_LENGTH = 8;   // size of long
    private final static int PAYLOAD_SIZE_BYTE_LENGTH = 8;  // size of long

    private long _payloadSize;
    private byte[] _payloadData;

    public PayloadPacket(int port, long seqNo) {
        super();

        _payloadSize = 0;
        _payloadData = null;
        _port = port;
        _seqNo = seqNo;
    }

    public static int getMaxPayloadSize() {
        return  PACKET_SIZE -               // 1000 -
                CHECKSUM_BYTE_LENGTH -      // 8    -
                SEQUENCE_NO_BYTE_LENGTH -   // 8    -
                PAYLOAD_SIZE_BYTE_LENGTH;   // 8    -
                                            // = 976
    }

    // To be used only by the sender class.
    public void setPayloadSize(long payloadSize) {
        _payloadSize = payloadSize;
    }

    public void setPayloadData(byte[] payloadData) {
        _payloadData = payloadData;
    }

    // To be used only by the receiver class.
    public long getPayloadSize() {
        return _payloadSize;
    }

    public byte[] getPayloadData() {
        return _payloadData;
    }

    // Checks to see if all the data needed to construct a packet
    // has been obtained.
    protected boolean hasAllData() {
        if (_payloadSize != 0 && _payloadData != null)
            return true;

        return false;
    }

    // Creates a DatagramPacket that contains the file header information
    // as well as the checksum.
    public DatagramPacket createPacket() {

        if (!hasAllData())
            return null;

        // Create byte array to store data.
        byte[] temp = new byte[ PACKET_SIZE -
                                CHECKSUM_BYTE_LENGTH ];

        // Wrap it with a ByteBuffer to facilite operations.
        ByteBuffer bf = ByteBuffer.wrap(temp);

        // Insert data into the buffer.
        bf.putLong(_seqNo);
        bf.putLong(_payloadSize);
        bf.put(_payloadData);

        // Generate the checksum for the data.
        _checksumObj.update(bf.array());
        _checksum = _checksumObj.getValue();

        // Create 2nd byte array to store data AND checksum.
        byte[] temp2 = new byte[PACKET_SIZE];

        // Wrap it with a ByteBuffer to facilite operations.
        ByteBuffer bf2 = ByteBuffer.wrap(temp2);

        // Insert checksum and data into buffer.
        bf2.putLong(_checksum);
        bf2.put(bf.array());

        _packet = new DatagramPacket(   bf2.array(),
                                        bf2.array().length,
                                        _address,
                                        _port   );

        return _packet;
    }

    // Parses the data received into its respective format and locations
    // to ease obtaining information.
    // Returns true if the data received matches the checksum included (i.e. valid),
    // false otherwise (i.e. invalid/corrupt).
    public boolean parsePacket(byte[] data) {

        // Ensure that we have data being passed in.
        if (data == null) {
            return false;
        }

        // Wrap data with ByteBuffer to ease operations.
        ByteBuffer bf = ByteBuffer.wrap(data);

        long rcvChecksum = bf.getLong();
        long rcvSeqNo = bf.getLong();
        long rcvPayloadSize = bf.getLong();

        // Do a quick check to see if the payload size is relatively valid.
        if (rcvPayloadSize <= 0) {
            System.out.println("ERROR: Corrupted Packet!");

            return false;
        }

        // Extract our payload data.
        byte[] rcvPayloadData = new byte[(int)rcvPayloadSize];
        bf.get(rcvPayloadData);

        // Create byte array to store data to be checksumed.
        byte[] temp = new byte[ PACKET_SIZE -
                                CHECKSUM_BYTE_LENGTH ];

        // Wrap temp byte array with ByteBuffer to ease operations.
        ByteBuffer bf2 = ByteBuffer.wrap(temp);

        // Insert received data into ByteBuffer to checksum.
        bf2.putLong(rcvSeqNo);
        bf2.putLong(rcvPayloadSize);
        bf2.put(rcvPayloadData);

        // Generate the checksum for the received data.
        _checksumObj.update(bf2.array());
        _checksum = _checksumObj.getValue();

        // Compare the checksums.
        if (rcvChecksum != _checksum) {
            System.out.println("ERROR: Corrupted Packet!");

            return false;
        }

        // Received data is valid, store them.
        _seqNo = rcvSeqNo;
        _payloadSize = rcvPayloadSize;
        _payloadData = rcvPayloadData;

        return true;
    }
};

class ResponsePacket extends DataPacket {

    private final static String MSG_ACK = "GET_Y";
    private final static String MSG_NAK = "GET_N";

    public enum ResponseType {
        NIL,
        ACK,
        NAK
    };

    private ResponseType _responseType;

    public ResponsePacket(int seqNo, ResponseType responseType) {
        super();

        _seqNo = seqNo;
        _responseType = responseType;
    }

    protected boolean hasAllData() {
        return false;
    }

    public DatagramPacket createPacket() {
        return null;
    }
};
