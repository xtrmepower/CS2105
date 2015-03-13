// Author: Qwek Siew Weng Melvyn (A0111821X)

import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

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
        try {
            _socket.send(pkt);
        } catch (IOException e) {
            System.out.println(e.toString());
        }

        PayloadPacket payloadPkt;
        try {
            payloadSize = bis.read(payloadData);

            while (payloadSize > 0) {
                // Create payload packet.
                payloadPkt = new PayloadPacket(_port, seqNo);

                payloadPkt.setPayloadSize(payloadSize);
                payloadPkt.setPayloadData(payloadData);

                pkt = payloadPkt.createPacket();

                _socket.send(pkt);

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
}
