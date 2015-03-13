// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class FileReceiver {

    private DatagramSocket _socket;
    private InetAddress _address;
    private int _port;

    public static void main(String[] args) {
        // check if the number of command line argument is 1
        if (args.length != 1) {
            System.out.println("Usage: java FileReceiver <port>");
            System.exit(1);
        }

        FileReceiver program = new FileReceiver(args[0]);
    }

    public FileReceiver(String localPort) {

        try {
            _port = Integer.parseInt(localPort);
            _socket = new DatagramSocket(_port);
        } catch (SocketException e) {
            System.out.println(e.toString());
        }

        long totalFileSize = 0;
        long currFileSize = 0;
        long payloadSize = 0;
        long seqNo = 0;
        String fileName = "";
        byte[] fhByteArray = new byte[116];
        byte[] payloadByteArray = new byte[1000];
        boolean isFileTransferComplete = false;

        FileOutputStream fos = null;
        BufferedOutputStream bos = null;

        DatagramPacket pkt = new DatagramPacket(fhByteArray, fhByteArray.length);

        boolean fhPacketValid = false;
        FileHeaderPacket fhPkt = null;
        do {
            // Receive the file header packet.
            try {
                _socket.receive(pkt);
            } catch (IOException e) {
                System.out.println(e.toString());
            }

            // Parse the packet.
            fhPkt = new FileHeaderPacket(_port);
            ResponsePacket rPkt = null;
            boolean pktValid = fhPkt.parsePacket(pkt.getData());
            if (pktValid == false) {
                rPkt = new ResponsePacket(pkt.getPort(), 0, ResponsePacket.ResponseType.NAK);
            } else {
                rPkt = new ResponsePacket(pkt.getPort(), 0, ResponsePacket.ResponseType.ACK);
                fhPacketValid = true;
            }
            try {
                _socket.send(rPkt.createPacket());
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        } while (!fhPacketValid);

        // Extract file header details.
        fileName = fhPkt.getFileName();
        totalFileSize = fhPkt.getTotalFileSize();

        // Create the destination file.
        if (fos == null) {
            try {
                fos = new FileOutputStream(fileName);
                bos = new BufferedOutputStream(fos);
            } catch (FileNotFoundException e) {
                System.out.println(e.toString());
            }
        }

        try {
            while (!isFileTransferComplete) {

                boolean payloadPktReceiveSuccess = false;
                PayloadPacket payloadPkt = null;
                do {
                    pkt = new DatagramPacket(payloadByteArray, payloadByteArray.length);

                    // Receive the payload packets.
                    _socket.receive(pkt);

                    // Parse the packet.
                    payloadPkt = new PayloadPacket(_port, seqNo);
                    ResponsePacket rPkt = null;

                    if (payloadPkt.parsePacket(pkt.getData()) == false) {
                        rPkt = new ResponsePacket(pkt.getPort(), (int)seqNo, ResponsePacket.ResponseType.NAK);
                    } else {
                        rPkt = new ResponsePacket(pkt.getPort(), (int)seqNo, ResponsePacket.ResponseType.ACK);
                        payloadPktReceiveSuccess = true;
                    }

                    try {
                        _socket.send(rPkt.createPacket());
                    } catch (IOException e) {
                        System.out.println(e.toString());
                    }
                } while (!payloadPktReceiveSuccess);


                bos.write(payloadPkt.getPayloadData());

                currFileSize += payloadPkt.getPayloadSize();

                System.out.println(payloadPkt.getSeqNo() + " >> "+currFileSize + "/" + totalFileSize + "(" + (float)(((float)currFileSize/(float)totalFileSize)*100.0f) + "%)");

                // Clean up a little.
                pkt = null;
                payloadByteArray = null;
                payloadPkt = null;

                payloadByteArray = new byte[1000];

                if (currFileSize >= totalFileSize)
                    isFileTransferComplete = true;
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
