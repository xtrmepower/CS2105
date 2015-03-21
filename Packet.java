import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/*
 *
 *  change the way that the data is being extracted out of a packet
 *  make it such that only need to extract out the CRC32 checksum
 *  then can do checksum
 *  make every packet uniform (same size?)
 *
 *
 */

class Packet {

// ***************************************************************************
// Variables
// ***************************************************************************

// Packet constants
    private final static int PACKET_SIZE = 1000;
    private final static int CHECKSUM_BYTE_LENGTH = 8;          // long
    private final static int SEQUENCE_NO_BYTE_LENGTH = 8;       // long
    private final static int MAX_FILENAME_LENGTH = 100;         // 1 byte per character
    private final static int TOTAL_FILESIZE_BYTE_LENGTH = 8;    // long
    private final static int PAYLOAD_FILESIZE_BYTE_LENGTH = 8;  // long
    private final static int RESPONSE_BYTE_LENGTH = 2;          // short
    private final static int PACKET_TYPE_BYTE_LENGTH = 2;       // short

// Response Messages.
    public final static short MSG_NIL = 0;
    public final static short MSG_ACK = 10;
    public final static short MSG_NAK = 20;

// Termination Sequence Number
    public final static short TERMINATION_SEQUENCE_NO = -1;

// Sizes for the different packets.
    private final static int FILEHEADER_PACKET_SIZE = CHECKSUM_BYTE_LENGTH +
                                                      PACKET_TYPE_BYTE_LENGTH +
                                                      SEQUENCE_NO_BYTE_LENGTH +
                                                      TOTAL_FILESIZE_BYTE_LENGTH +
                                                      MAX_FILENAME_LENGTH;

    private final static int PAYLOAD_PACKET_SIZE = PACKET_SIZE;
    public final static int PAYLOAD_MAX_DATA_SIZE = PAYLOAD_PACKET_SIZE -
                                                    PACKET_TYPE_BYTE_LENGTH -
                                                    CHECKSUM_BYTE_LENGTH -
                                                    SEQUENCE_NO_BYTE_LENGTH -
                                                    PAYLOAD_FILESIZE_BYTE_LENGTH;

    private final static int RESPONSE_PACKET_SIZE = CHECKSUM_BYTE_LENGTH +
                                                    PACKET_TYPE_BYTE_LENGTH +
                                                    SEQUENCE_NO_BYTE_LENGTH +
                                                    RESPONSE_BYTE_LENGTH;

    // 0 ~ 7:           [CRC32 Checksum]
    // 8 ~ 9:           [PacketType]
    // 10 ~ 17:         [SequenceNo]
    // 17 ~ 20:         [Response]
    public final static short RESPONSE_PACKET_TYPE = 100;

    // 0 ~ 7:           [CRC32 Checksum]
    // 8 ~ 9:           [PacketType]
    // 10 ~ 17:         [SequenceNo]
    // 18 ~ 25:         [TotalFileSize]
    // 26 ~ 125:        [FileName]
    public final static short FILE_HEADER_PACKET_TYPE = 200;

    // 0 ~ 7:           [CRC32 Checksum]
    // 8 ~ 9:           [PacketType]
    // 10 ~ 17:         [SequenceNo]
    // 18 ~ 25:         [PayloadDataSize]
    // 26 ~ LAST:       [PayloadData]
    public final static short PAYLOAD_PACKET_TYPE = 300;

    // 0 ~ 7:           [CRC32 Checksum]
    // 8 ~ 9:           [PacketType]
    // 10 ~ 17:         [SequenceNo]
    public final static short TERMINATION_PACKET_TYPE = 400;

// The packet itself.
    private byte[] _packetData;
    private DatagramPacket _packet;

// Socket stuff
    private InetAddress _address;
    private int _port;

// Packet contents. (Separated by packet types)

    // All
    private CRC32 _checksumObj;
    private long _checksum;
    private long _seqNo;
    private short _pktType;

    // File Header
    private long _totalFileSize;
    private String _fileName;

    // Payload
    private long _payloadDataSize;
    private byte[] _payloadData;

    // Response
    private short _response;


// ***************************************************************************
// Functions
// ***************************************************************************

    // Constructor for sending packet.
    public Packet(InetAddress address, int port) {

        _packetData = new byte[PACKET_SIZE];
        _packet = null;

        _address = address;
        _port = port;

        _checksumObj = new CRC32();
        _checksum = 0;
        _seqNo = 0;
        _pktType = 0;

        _totalFileSize = 0;
        _fileName = "";

        _payloadDataSize = 0;
        _payloadData = null;

        _response = MSG_NIL;
    }

    // Constructor for receiving packet.
    public Packet() {

        // Create a byte array to store our receiving data.
        /*switch (packetType) {
        case FileHeader: _packetData = new byte[FILEHEADER_PACKET_SIZE]; break;
        case Payload: _packetData = new byte[PAYLOAD_PACKET_SIZE]; break;
        case Response: _packetData = new byte[RESPONSE_PACKET_SIZE]; break;
        default: break;
    }*/

        _packetData = new byte[PACKET_SIZE];
        _packet = new DatagramPacket(_packetData, _packetData.length);

        _address = null;
        _port = 0;

        _checksumObj = new CRC32();
        _checksum = 0;
        _seqNo = 0;
        _pktType = RESPONSE_PACKET_TYPE;

        _totalFileSize = 0;
        _fileName = "";

        _payloadDataSize = 0;
        _payloadData = null;

        _response = MSG_NIL;
    }

    public boolean verify() {

        // Wrap packet data in a byte buffer.
        ByteBuffer bf = ByteBuffer.wrap(_packet.getData());

        // Extract out the checksum from the object.
        long checksum = bf.getLong();

        // Zero out the checksum field in the packet.
        bf.putLong(0, 0);

        // Generate the checksum for the received packet.
        _checksumObj.update(bf.array());
        _checksum = _checksumObj.getValue();

        // Check if it is correct.
        if (checksum == _checksum) {
            _pktType = bf.getShort();
            _seqNo = bf.getLong();

            // Parse the file into the appropriate type.
            switch (_pktType) {
                case RESPONSE_PACKET_TYPE: parseResponsePacket(bf); break;
                case FILE_HEADER_PACKET_TYPE: parseFileHeaderPacket(bf); break;
                case PAYLOAD_PACKET_TYPE: parsePayloadPacket(bf); break;
                case TERMINATION_PACKET_TYPE: parseTerminationPacket(bf); break;
            }

            // Let the caller know that it was successful.
            return true;
        }

        return false;
    }

    public DatagramPacket create() {

        // Check to see if we have already created the packet.
        if (_packet != null)
            return _packet;

        ByteBuffer bf = ByteBuffer.wrap(_packetData);

        // Reserve space for checksum.
        bf.putLong(0);

        switch (_pktType) {
            case RESPONSE_PACKET_TYPE: createResponsePacket(bf); break;
            case FILE_HEADER_PACKET_TYPE: createFileHeaderPacket(bf); break;
            case PAYLOAD_PACKET_TYPE: createPayloadPacket(bf); break;
            case TERMINATION_PACKET_TYPE: createTerminationPacket(bf); break;
        }

        // Create CRC32 checksum
        _checksumObj.update(bf.array());
        _checksum = _checksumObj.getValue();

        // Insert checksum into the data.
        bf.putLong(0, _checksum);

        _packet = new DatagramPacket(bf.array(), bf.array().length, _address, _port);

        // return the packet.
        return _packet;
    }

    private void createResponsePacket(ByteBuffer bf) {
        bf.putShort(_pktType);
        bf.putLong(_seqNo);
        bf.putShort(_response);
    }

    private void createFileHeaderPacket(ByteBuffer bf) {
        bf.putShort(_pktType);
        bf.putLong(_seqNo);
        bf.putLong(_totalFileSize);
        bf.put(padFileName(_fileName));
    }

    private void createPayloadPacket(ByteBuffer bf) {
        bf.putShort(_pktType);
        bf.putLong(_seqNo);
        bf.putLong(_payloadDataSize);
        bf.put(_payloadData);
    }

    private void createTerminationPacket(ByteBuffer bf) {
        bf.putShort(_pktType);
        bf.putLong(_seqNo);
    }

    private void parseResponsePacket(ByteBuffer bf) {
        _response = bf.getShort();
    }

    private void parseFileHeaderPacket(ByteBuffer bf) {
        _totalFileSize = bf.getLong();
        _fileName = extractFileName(bf);
    }

    private void parsePayloadPacket(ByteBuffer bf) {
        _payloadDataSize = bf.getLong();

        _payloadData = new byte[(int)_payloadDataSize];
        bf.get(_payloadData);
    }

    private void parseTerminationPacket(ByteBuffer bf) {
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


// Getters & Setters

    public DatagramPacket getPacket() {
        return _packet;
    }

    public long getSeqNo() {
        return _seqNo;
    }

    public void setSeqNo(long seqNo) {
        _seqNo = seqNo;
    }

    public short getPacketType() {
        return _pktType;
    }

    public void setPacketType(short pktType) {
        _pktType = pktType;
    }

    public long getTotalFileSize() {
        return _totalFileSize;
    }

    public void setTotalFileSize(long totalFileSize) {
        _totalFileSize = totalFileSize;
    }

    public String getFileName() {
        return _fileName;
    }

    public void setFileName(String fileName) {
        _fileName = fileName;
    }

    public long getPayloadDataSize() {
        return _payloadDataSize;
    }

    public void setPayloadDataSize(long payloadDataSize) {
        _payloadDataSize = payloadDataSize;
    }

    public byte[] getPayloadData() {
        return _payloadData;
    }

    public void setPayloadData(byte[] payloadData) {
        _payloadData = payloadData;
    }

    public short getResponse() {
        return _response;
    }

    public void setResponse(short response) {
        _response = response;
    }
};

/*
// Parent class for the various packet classes.
abstract class DataPacket {

    // Packet constants
    protected final static int PACKET_SIZE = 1000;
    protected final static int CHECKSUM_BYTE_LENGTH = 8;  // size of long
    protected final static int SEQUENCE_NO_BYTE_LENGTH = 8;   // size of long
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
        if (rcvPayloadSize <= 0 || rcvPayloadSize > PACKET_SIZE) {
            System.out.println("ERROR: Corrupted Packet!");

            return false;
        }

        // Extract our payload data.
        System.out.println("rcvPayloadSize = "+rcvPayloadSize);
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


// Contains the response from the receiver.
//
// 0 ~ 7:           [CRC32 Checksum]
// 8 ~ 15:          [SequenceNo]
// 16 ~ 20:         [Response]
class ResponsePacket extends DataPacket {

    private final static String MSG_ACK = "GET_Y";
    private final static String MSG_NAK = "GET_N";

    private final static int RESPONSE_BYTE_LENGTH = 5;


    public enum ResponseType {
        NIL,
        ACK,
        NAK
    };

    private ResponseType _responseType;

    public ResponsePacket(int port, int seqNo, ResponseType responseType) {
        super();

        _port = port;
        _seqNo = seqNo;
        _responseType = responseType;
    }

    public ResponseType getResponseType() {
        return _responseType;
    }

    protected boolean hasAllData() {
        if (_seqNo != 0 &&
            (_responseType == ResponseType.ACK || _responseType == ResponseType.NAK))
            return true;

        return false;
    }

    // Creates a DatagramPacket that contains the response and checksum.
    public DatagramPacket createPacket() {

        //if (!hasAllData())
        //    return null;

        // Create byte array to store data.
        byte[] temp = new byte[ SEQUENCE_NO_BYTE_LENGTH +
                                RESPONSE_BYTE_LENGTH ];

        // Wrap it with a ByteBuffer to facilite operations.
        ByteBuffer bf = ByteBuffer.wrap(temp);

        // Insert data into the buffer.
        bf.putLong(_seqNo);

        switch (_responseType) {
            case ACK: bf.put(MSG_ACK.getBytes()); break;
            case NAK: bf.put(MSG_NAK.getBytes()); break;
            default: return null;
        }

        // Generate the checksum for the data.
        _checksumObj.update(bf.array());
        _checksum = _checksumObj.getValue();

        // Create 2nd byte array to store data AND checksum.
        byte[] temp2 = new byte[ SEQUENCE_NO_BYTE_LENGTH +
                                 RESPONSE_BYTE_LENGTH +
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
        long rcvSeqNo = bf.getLong();
        String rcvResponseType = extractResponseType(bf);

        // Create byte array to store data to be checksumed.
        byte[] temp = new byte[ SEQUENCE_NO_BYTE_LENGTH +
                                RESPONSE_BYTE_LENGTH ];

        // Wrap temp byte array with ByteBuffer to ease operations.
        ByteBuffer bf2 = ByteBuffer.wrap(temp);

        // Insert received data into ByteBuffer to checksum.
        bf2.putLong(rcvSeqNo);
        bf2.put(rcvResponseType.getBytes());

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

        if (rcvResponseType.equals(MSG_ACK))
            _responseType = ResponseType.ACK;
        else if (rcvResponseType.equals(MSG_NAK))
            _responseType = ResponseType.NAK;

        return true;
    }

    // Helper function: To extract the response.
    private String extractResponseType(ByteBuffer bb) {
        String response = "";

        byte[] tempResponse = new byte[RESPONSE_BYTE_LENGTH];
        bb.get(tempResponse, 0, RESPONSE_BYTE_LENGTH);

        response = new String(tempResponse);
        response = response.trim();

        return response;
    }
};
*/
