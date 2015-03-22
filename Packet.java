// Author: Qwek Siew Weng Melvyn (A0111821X)
import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

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
