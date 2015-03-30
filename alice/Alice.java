// Author:

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import javax.crypto.*;
/*
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.SealedObject;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
*/

/************************************************
  * This skeleton program is prepared for weak  *
  * and average students.                       *
  * If you are very strong in programming. DIY! *
  * Feel free to modify this program.           *
  ***********************************************/

// Alice knows Bob's public key
// Alice sends Bob session (AES) key
// Alice receives messages from Bob, decrypts and saves them to file

class Alice {  // Alice is a TCP client

    String bobIP;  // ip address of Bob
    int bobPort;   // port Bob listens to
    Socket connectionSkt;  // socket used to talk to Bob
    private ObjectOutputStream toBob;   // to send session key to Bob
    private ObjectInputStream fromBob;  // to read encrypted messages from Bob
    private Crypto crypto;        // object for encryption and decryption
    // file to store received and decrypted messages
    public static final String MESSAGE_FILE = "msgs.txt";

    public static void main(String[] args) {

        // Check if the number of command line argument is 2
        if (args.length != 2) {
            System.err.println("Usage: java Alice BobIP BobPort");
            System.exit(1);
        }

        new Alice(args[0], args[1]);
    }

    // Constructor
    public Alice(String ipStr, String portStr) {

// Initialize them variables.

        this.crypto = new Crypto();
        this.bobPort = Integer.parseInt(portStr);
        this.bobIP = ipStr;


// Create socket to Bob.

        try {
            this.connectionSkt = new Socket(this.bobIP, this.bobPort);
        } catch (IOException e) {
            System.out.println(e.toString());
            return;
        }


// Create connections to Bob.

        try {
            this.toBob = new ObjectOutputStream(this.connectionSkt.getOutputStream());
            this.fromBob = new ObjectInputStream(this.connectionSkt.getInputStream());
        } catch (IOException e) {
            System.out.println("ERROR: Unable to get input/output stream.");
            System.exit(1);
        }


// Send session key to Bob

        sendSessionKey();


// Receive encrypted messages from Bob, decrypt and save them to file

        receiveMessages();


// Clean up

        try {
            this.connectionSkt.close();
        } catch (IOException e) {
            System.out.println("ERROR: Unable to close TCP socket.");
            System.exit(1);
        }
    }

    // Send session key to Bob
    public void sendSessionKey() {

        try {
            this.toBob.writeObject(this.crypto.getSessionKey());
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    // Receive messages one by one from Bob, decrypt and write to file
    public void receiveMessages() {

        try {

            PrintWriter toFile = new PrintWriter(new File(MESSAGE_FILE));

            for (int i = 0; i < 10; i++) {
                SealedObject encryptedMsg = (SealedObject)this.fromBob.readObject();
                String message = this.crypto.decryptMsg(encryptedMsg);
                toFile.println(message);
            }

            toFile.close();
            System.out.println("SUCCESS: All messages received successfully!");
        } catch (ClassNotFoundException e) {
            System.out.println("ERROR: Unable to typecast to class SealedObject.");
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: " + MESSAGE_FILE + " doesn't exist.");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("ERROR: Unable to receive messages from Bob.");
            System.exit(1);
        }
    }

    /*****************/
    /** inner class **/
    /*****************/
    class Crypto {

        // Bob's public key, to be read from file
        private PublicKey pubKey;
        // Alice generates a new session key for each communication session
        private SecretKey sessionKey;
        // File that contains Bob' public key
        public static final String PUBLIC_KEY_FILE = "public.key";

        // Constructor
        public Crypto() {

            // See if file exists.
            File pubKeyFile = new File(PUBLIC_KEY_FILE);
            if (pubKeyFile.exists() && !pubKeyFile.isDirectory()) {

                // Read Bob's public key from file
                readPublicKey();
                // Generate session key dynamically
                initSessionKey();
            } else {
                System.out.println("ERROR: Alice cannot find RSA public key.");
                System.exit(1);
            }
        }

        // Read Bob's public key from file
        public void readPublicKey() {
            // key is stored as an object and need to be read using ObjectInputStream.
            // See how Bob read his private key as an example.
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(PUBLIC_KEY_FILE));
                this.pubKey = (PublicKey)ois.readObject();
                ois.close();
            } catch (IOException e) {
                System.out.println("ERROR: Unable to read public key from file.");
                System.exit(1);
            } catch (ClassNotFoundException e) {
                System.out.println("ERROR: Cannot typecast to class PublicKey.");
                System.exit(1);
            }

            System.out.println("SUCCESS: Public key read from file " + PUBLIC_KEY_FILE);

            System.out.println("pubkey = " + this.pubKey.toString());
        }

        // Generate a session key
        public void initSessionKey() {
            // Generate a 128-bit long key.
            KeyGenerator kgen = null;

            try {
                kgen = KeyGenerator.getInstance("AES");
            } catch (NoSuchAlgorithmException e) {
                System.out.println("ERROR: AES algorithm unavailable.");
                System.exit(1);
            }
            kgen.init(128);

            // Set it to be our session key.
            this.sessionKey = kgen.generateKey();

            System.out.println("sessionkey = " + this.sessionKey.toString());
        }

        // Seal session key with RSA public key in a SealedObject and return
        public SealedObject getSessionKey() {

            // Alice must use the same RSA key/transformation as Bob specified
            Cipher cipher = null;
            SealedObject so = null;

            try {
                cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, this.pubKey);

                byte[] rawSessionKey = (byte[])this.sessionKey.getEncoded();
                so = new SealedObject(rawSessionKey, cipher);

            } catch (IllegalBlockSizeException e) {
                System.out.println("ERROR: Unable to decrypt message.");
                return null;
            } catch (IOException e) {
                System.out.println(e.toString());
                return null;
            } catch (InvalidKeyException e) {
                System.out.println(e.toString());
                return null;
            } catch (NoSuchAlgorithmException e) {
                System.out.println(e.toString());
                return null;
            } catch (NoSuchPaddingException e) {
                System.out.println(e.toString());
                return null;
            }

            // RSA imposes size restriction on the object being encrypted (117 bytes).
            // Instead of sealing a Key object which is way over the size restriction,
            // we shall encrypt AES key in its byte format (using getEncoded() method).

            return so;
        }

        // Decrypt and extract a message from SealedObject
        public String decryptMsg(SealedObject encryptedMsgObj) {

            String plainText = null;

            // Alice and Bob use the same AES key/transformation
            Cipher cipher = null;

            try {
                cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

                try {
                    cipher.init(Cipher.DECRYPT_MODE, sessionKey);
                } catch (InvalidKeyException e) {
                    System.out.println("ERROR: Invalid session key.");
                    return null;
                }

                try {
                    plainText = (String)encryptedMsgObj.getObject(cipher);
                } catch (IOException e) {
                    System.out.println("ERROR: Unable to decrypt message.");
                    return null;
                } catch (ClassNotFoundException e) {
                    System.out.println("ERROR: Unable to decrypt message.");
                    return null;
                } catch (IllegalBlockSizeException e) {
                    System.out.println("ERROR: Unable to decrypt message.");
                    return null;
                } catch (BadPaddingException e) {
                    System.out.println("ERROR: Bad padding in message.");
                    return null;
                }

                System.out.println(plainText);

            } catch (NoSuchAlgorithmException e) {
                System.out.println(e.toString());
                return null;
            } catch (NoSuchPaddingException e) {
                System.out.println(e.toString());
                return null;
            }


            return plainText;
        }
    }
}
