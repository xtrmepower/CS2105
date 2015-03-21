// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

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

            //System.out.println("seqNo="+_seqNo);

            sendPacket(sendPkt);

            // Wait for ACK
            rcvPkt = new Packet();
            receivePacket(rcvPkt);

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
            sendPacket(tPkt);

            rcvPkt = new Packet();
            receivePacket(rcvPkt);

            // TODO: Account for lost ACK/NAK
            terminationSignalSent = true;
        }
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

    /*// Pad the filename with spaces until 100 characters.
    private byte[] padFileName(String rcvFileName) {
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    public FileSender(String fileToOpen, String host, String port, String rcvFileName) {

        FileInputStream fis;
        BufferedInputStream bis;

        long totalFileSize = 0;
        long payloadSize = 0;
        long seqNo = 0;
        byte[] payloadData = new byte[PayloadPacket.getMaxPayloadSize()];

        DatagramPacket pkt = null;

        try {
            _address = InetAddress.getByName(host);
            _port = Integer.parseInt(port);
            _socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        fis = null;
        bis = null;

        try {
            fis = new FileInputStream(fileToOpen);
            bis = new BufferedInputStream(fis);

            totalFileSize = fis.available();

            System.out.println("filesize = " + totalFileSize);
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        // Create file header packet.
        FileHeaderPacket fhPkt = new FileHeaderPacket(_port);
        fhPkt.setFileName(rcvFileName);
        fhPkt.setTotalFileSize(totalFileSize);
        pkt = fhPkt.createPacket();

        // Send it.
        byte[] rPktByteArray = new byte[21];
        DatagramPacket rPktRaw = new DatagramPacket(rPktByteArray, rPktByteArray.length);
        boolean fhPacketSendSuccess = false;
        do {
            try {
                _socket.send(pkt);

                // Wait for a reply.
                _socket.receive(rPktRaw);
                ResponsePacket rPkt = new ResponsePacket(0, 0, ResponsePacket.ResponseType.NIL);
                if (rPkt.parsePacket(rPktRaw.getData()) == false) {
                    // ResponsePacket was corrupted.
                    //TODO: something
                } else {
                    // ResponsePacket was received successfully.
                    if (rPkt.getResponseType() == ResponsePacket.ResponseType.ACK) {
                        fhPacketSendSuccess = true;
                    } else if (rPkt.getResponseType() == ResponsePacket.ResponseType.NAK) {
                        // Do nothing here. Send again.
                    }
                }
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        } while (!fhPacketSendSuccess);

        // Clean up.
        rPktRaw = null;
        rPktByteArray = new byte[21];

        PayloadPacket payloadPkt;
        try {
            payloadSize = bis.read(payloadData);

            while (payloadSize > 0) {
                boolean payloadPktSendSuccess = false;
                do {
                    // Create payload packet.
                    payloadPkt = new PayloadPacket(_port, seqNo);

                    payloadPkt.setPayloadSize(payloadSize);
                    payloadPkt.setPayloadData(payloadData);

                    pkt = payloadPkt.createPacket();

                    _socket.send(pkt);

                    // Wait for a reply.
                    rPktByteArray = new byte[21];
                    rPktRaw = new DatagramPacket(rPktByteArray, rPktByteArray.length);
                    _socket.receive(rPktRaw);
                    ResponsePacket rPkt = new ResponsePacket(0, 0, ResponsePacket.ResponseType.NIL);
                    if (rPkt.parsePacket(rPktRaw.getData()) == false) {
                        // ResponsePacket was corrupted.
                        //TODO: something
                    } else {
                        // ResponsePacket was received successfully.
                        if (rPkt.getResponseType() == ResponsePacket.ResponseType.ACK) {
                            payloadPktSendSuccess = true;
                        } else if (rPkt.getResponseType() == ResponsePacket.ResponseType.NAK) {
                            // Do nothing here. Send again.
                        }
                    }
                } while (!payloadPktSendSuccess);

                // Clean up a little.
                pkt = null;
                payloadPkt = null;
                payloadData = null;

                // Read for more data.
                payloadData = new byte[PayloadPacket.getMaxPayloadSize()];
                payloadSize = bis.read(payloadData);

                // Increment sequence number.
                seqNo++;
                System.out.println("seqNo = " + seqNo);

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    System.out.println(e.toString());
                }
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
    }*/
}

/*
class FileSender {

    private DatagramSocket _socket;
    private InetAddress _address;
    private int _port;

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

    // Pad the filename with spaces until 100 characters.
    private byte[] padFileName(String rcvFileName) {
        String paddedName = String.format("%-100s", rcvFileName);

        return paddedName.getBytes();
    }

    public FileSender(String fileToOpen, String host, String port, String rcvFileName) {

        FileInputStream fis;
        BufferedInputStream bis;

        long totalFileSize = 0;
        long payloadSize = 0;
        long seqNo = 0;
        byte[] payloadData = new byte[PayloadPacket.getMaxPayloadSize()];

        DatagramPacket pkt = null;

        try {
            _address = InetAddress.getByName(host);
            _port = Integer.parseInt(port);
            _socket = new DatagramSocket();
        } catch (UnknownHostException e) {
            System.out.println(e.toString());
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        fis = null;
        bis = null;

        try {
            fis = new FileInputStream(fileToOpen);
            bis = new BufferedInputStream(fis);

            totalFileSize = fis.available();

            System.out.println("filesize = " + totalFileSize);
        } catch (FileNotFoundException e) {
            System.out.println(e.toString());
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        // Create file header packet.
        FileHeaderPacket fhPkt = new FileHeaderPacket(_port);
        fhPkt.setFileName(rcvFileName);
        fhPkt.setTotalFileSize(totalFileSize);
        pkt = fhPkt.createPacket();

        // Send it.
        byte[] rPktByteArray = new byte[21];
        DatagramPacket rPktRaw = new DatagramPacket(rPktByteArray, rPktByteArray.length);
        boolean fhPacketSendSuccess = false;
        do {
            try {
                _socket.send(pkt);

                // Wait for a reply.
                _socket.receive(rPktRaw);
                ResponsePacket rPkt = new ResponsePacket(0, 0, ResponsePacket.ResponseType.NIL);
                if (rPkt.parsePacket(rPktRaw.getData()) == false) {
                    // ResponsePacket was corrupted.
                    //TODO: something
                } else {
                    // ResponsePacket was received successfully.
                    if (rPkt.getResponseType() == ResponsePacket.ResponseType.ACK) {
                        fhPacketSendSuccess = true;
                    } else if (rPkt.getResponseType() == ResponsePacket.ResponseType.NAK) {
                        // Do nothing here. Send again.
                    }
                }
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        } while (!fhPacketSendSuccess);

        // Clean up.
        rPktRaw = null;
        rPktByteArray = new byte[21];

        PayloadPacket payloadPkt;
        try {
            payloadSize = bis.read(payloadData);

            while (payloadSize > 0) {
                boolean payloadPktSendSuccess = false;
                do {
                    // Create payload packet.
                    payloadPkt = new PayloadPacket(_port, seqNo);

                    payloadPkt.setPayloadSize(payloadSize);
                    payloadPkt.setPayloadData(payloadData);

                    pkt = payloadPkt.createPacket();

                    _socket.send(pkt);

                    // Wait for a reply.
                    rPktByteArray = new byte[21];
                    rPktRaw = new DatagramPacket(rPktByteArray, rPktByteArray.length);
                    _socket.receive(rPktRaw);
                    ResponsePacket rPkt = new ResponsePacket(0, 0, ResponsePacket.ResponseType.NIL);
                    if (rPkt.parsePacket(rPktRaw.getData()) == false) {
                        // ResponsePacket was corrupted.
                        //TODO: something
                    } else {
                        // ResponsePacket was received successfully.
                        if (rPkt.getResponseType() == ResponsePacket.ResponseType.ACK) {
                            payloadPktSendSuccess = true;
                        } else if (rPkt.getResponseType() == ResponsePacket.ResponseType.NAK) {
                            // Do nothing here. Send again.
                        }
                    }
                } while (!payloadPktSendSuccess);

                // Clean up a little.
                pkt = null;
                payloadPkt = null;
                payloadData = null;

                // Read for more data.
                payloadData = new byte[PayloadPacket.getMaxPayloadSize()];
                payloadSize = bis.read(payloadData);

                // Increment sequence number.
                seqNo++;
                System.out.println("seqNo = " + seqNo);

                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    System.out.println(e.toString());
                }
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
}*/
