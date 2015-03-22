// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;

class Receiver implements Runnable {

// ***************************************************************************
// Variables
// ***************************************************************************

// Socket stuff.
    private int _port;
    private DatagramSocket _socket;

    private InetAddress _address;
    private final static String HOSTNAME = "localhost";

// File stuff.
    private FileOutputStream _fos;
    private BufferedOutputStream _bos;

    private long _totalFileSize;
    private long _currFileSize;
    private long _seqNo;
    private String _fileName;

    private boolean _done;


// ***************************************************************************
// Functions
// ***************************************************************************

    // Constructor
    public Receiver(String localPort) {

        try {
            _port = Integer.parseInt(localPort);
            _socket = new DatagramSocket(_port);

            _address = InetAddress.getByName(HOSTNAME);
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        _fos = null;
        _bos = null;

        _totalFileSize = 0;
        _currFileSize = 0;
        _seqNo = -1;
        _fileName = "";
    }

    // Main update function
    public void run() {

        Packet sendPkt = null, rcvPkt = null;

        do {
            rcvPkt = new Packet();
            try {
                _socket.receive(rcvPkt.getPacket());

                sendPkt = new Packet(_address, rcvPkt.getPacket().getPort());
                // Verify that the packet is valid.
                if (rcvPkt.verify()) {
                    parsePacket(rcvPkt);

                    sendResponsePacket(sendPkt, Packet.MSG_ACK);
                }

                sendResponsePacket(sendPkt, Packet.MSG_ACK);
            } catch (IOException e) {
                System.out.println(e.toString());
            }

            System.out.println(_currFileSize + "/" + _totalFileSize);
        } while (!_done);

        // Need to flush out those last few bytes.
        try {
            _bos.close();
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    private void parsePacket(Packet pkt) {
        System.out.println("rcvPkt.getSeqNo()="+pkt.getSeqNo());
        if (pkt.getSeqNo() > _seqNo || pkt.getSeqNo() == -1) {
            if (pkt.getSeqNo() > 0) {
                updateFile(pkt);
                //System.out.println("updateFile");
            } else if (pkt.getSeqNo() == 0) {
                createFile(pkt);
                //System.out.println("createFile");
            } else if (pkt.getSeqNo() == -1) {
                _done = true;
                //System.out.println("doneFile");
            }
            _seqNo = pkt.getSeqNo();
        }
    }

    private void sendResponsePacket(Packet pkt, short response) {

        pkt.setResponse(response);
        pkt.setSeqNo(_seqNo);
        pkt.setPacketType(Packet.RESPONSE_PACKET_TYPE);

        try {
            _socket.send(pkt.create());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    private void createFile(Packet pkt) {

        _totalFileSize = pkt.getTotalFileSize();

        if (_fos == null) {
            try {
                _fos = new FileOutputStream(pkt.getFileName());
                _bos = new BufferedOutputStream(_fos);
            } catch (FileNotFoundException e) {
                System.out.println(e.toString());
            }
        }
    }

    private void updateFile(Packet pkt) {

        try {
            _bos.write(pkt.getPayloadData());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
        _currFileSize += pkt.getPayloadDataSize();

        //if (_currFileSize >= _totalFileSize)
        //    _done = true;
    }
};

class FileReceiver {

    public static void main(String[] args) {
        // check if the number of command line argument is 1
        if (args.length != 1) {
            System.out.println("Usage: java FileReceiver <port>");
            System.exit(1);
        }

        Receiver program = new Receiver(args[0]);

        program.run();
    }
}
