// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class FileReceiver {

    private DatagramSocket _socket;
    private DatagramPacket _packet;
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

        // Receive the file header packet.
        try {
            _socket.receive(pkt);
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        // Parse the packet.
        FileHeaderPacket fhPkt = new FileHeaderPacket(_port);
        if (fhPkt.parsePacket(pkt.getData()) == false) {
            //TODO: Send a NAK.
            return;
        } else {
            //TODO: Send a ACK.
        }

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
                pkt = new DatagramPacket(payloadByteArray, payloadByteArray.length);

                // Receive the payload packets.
                _socket.receive(pkt);

                // Parse the packet.
                PayloadPacket payloadPkt = new PayloadPacket(_port, seqNo);
                if (payloadPkt.parsePacket(pkt.getData()) == false) {
                    //TODO: Send a NAK.
                    return;
                } else {
                    //TODO: Send a ACK.
                }

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
