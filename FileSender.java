// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.Timer;
import java.util.TimerTask;

class SendTask extends TimerTask {

// ***************************************************************************
// Variables
// ***************************************************************************

// Socket stuff.
    private DatagramSocket _socket;

// Packet to send.
    private Packet _pkt;

// ***************************************************************************
// Functions
// ***************************************************************************

    // Constructor.
    public SendTask(DatagramSocket socket, Packet pkt) {
        _socket = socket;
        _pkt = pkt;
    }

    // Thing to do every interval.
    public void run() {
        try {
            _socket.send(_pkt.create());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
}

class Sender implements Runnable {

// ***************************************************************************
// Variables
// ***************************************************************************

// Socket stuff.
    private int _port;
    private InetAddress _address;
    private DatagramSocket _socket;

// File stuff.
    private FileInputStream _fis;
    private BufferedInputStream _bis;

    private long _totalFileSize;
    private long _currFileSize;
    private long _seqNo;
    private String _fileName;

    private boolean _done;

// Timer
    private Timer _sendTimer;
    private TimerTask _sendTask;


// ***************************************************************************
// Functions
// ***************************************************************************

    // Constructor
    public Sender(String fileToOpen, String host, String port, String rcvFileName) {

        // Open the socket to send.
        try {
            _address = InetAddress.getByName(host);
            _port = Integer.parseInt(port);
            _socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        // Open the file.
        try {
            _fis = new FileInputStream(fileToOpen);
            _bis = new BufferedInputStream(_fis);

            _totalFileSize = _fis.available();
            _fileName = rcvFileName;

            System.out.println("filesize = " + _totalFileSize);
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        _seqNo = 0;
        _sendTimer = new Timer();
    }

    // Main update function.
    public void run() {

        Packet sendPkt = null, rcvPkt = null;
        boolean sendingSuccessful = true;

        do {
            // Make packet first.
            if (sendingSuccessful) {
                sendPkt = new Packet(_address, _port);

                if (_seqNo == 0) {
                    makeFileHeaderPacket(sendPkt);
                } else {
                    _done = makePayloadPacket(sendPkt);
                }
                sendingSuccessful = false;
            }

            if (_done) {
                System.out.println("DONE");
                break;
            }

            System.out.println("seqNo="+_seqNo);

            //sendPacket(sendPkt);
            startSendPacket(sendPkt);

            // Wait for ACK
            rcvPkt = new Packet();
            receivePacket(rcvPkt);

            stopSendPacket();

            //TODO: when receive packet from the receiver then stop the send task

            if (rcvPkt.verify() && rcvPkt.getSeqNo() == _seqNo) {
                _seqNo++;
                sendingSuccessful = true;
            }

            // Sleep the thread to prevent overloading the pipeline.
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                System.out.println(e.toString());
            }
        } while (!_done);

        // Send a file termination packet.
        boolean terminationSignalSent = false;
        Packet tPkt = new Packet(_address, _port);
        makeTerminationPacket(tPkt);

        while (!terminationSignalSent) {
            //sendPacket(tPkt);
            startSendPacket(tPkt);

            rcvPkt = new Packet();
            receivePacket(rcvPkt);
            stopSendPacket();

            // TODO: Account for lost ACK/NAK
            if (rcvPkt.verify() && (rcvPkt.getSeqNo() == _seqNo || rcvPkt.getSeqNo() == -1))
                terminationSignalSent = true;
        }
    }

    private void startSendPacket(Packet pkt) {
        _sendTimer = new Timer();
        _sendTask = new SendTask(_socket, pkt);
        _sendTimer.schedule(_sendTask, 0, 50);
    }

    private void stopSendPacket() {
        _sendTimer.cancel();
        _sendTimer = null;
    }

    private void makeFileHeaderPacket(Packet pkt) {

        pkt.setPacketType(Packet.FILE_HEADER_PACKET_TYPE);
        pkt.setSeqNo(_seqNo);
        pkt.setTotalFileSize(_totalFileSize);
        pkt.setFileName(_fileName);
    }

    private boolean makePayloadPacket(Packet pkt) {

        byte[] payloadData = new byte[Packet.PAYLOAD_MAX_DATA_SIZE];
        long payloadDataSize = 0;

        try {
            payloadDataSize = _bis.read(payloadData);
            //System.out.println("payloadDataSize="+payloadDataSize);
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        if (payloadDataSize <= 0)
            return true;

        pkt.setPacketType(Packet.PAYLOAD_PACKET_TYPE);
        pkt.setSeqNo(_seqNo);
        pkt.setPayloadDataSize(payloadDataSize);
        pkt.setPayloadData(payloadData);

        return false;
    }

    private void makeTerminationPacket(Packet pkt) {

        pkt.setPacketType(Packet.TERMINATION_PACKET_TYPE);
        pkt.setSeqNo(Packet.TERMINATION_SEQUENCE_NO);
    }

    private void sendPacket(Packet pkt) {
        try {
            _socket.send(pkt.create());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    private void receivePacket(Packet pkt) {
        try {
            _socket.receive(pkt.getPacket());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
}

class FileSender {

    private final static String HOSTNAME = "localhost";

    public static void main(String[] args) {
        // Check if the number of command line argument is 3
        if (args.length != 3) {
            System.out.println("Usage: java FileSender <path/filename> "
                                   + "<unreliNetPort> <rcvFileName>");
            System.exit(1);
        }

        Sender program = new Sender(args[0], HOSTNAME, args[1], args[2]);

        program.run();
    }
}
