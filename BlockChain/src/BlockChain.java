/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: October 10, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java BlockChain [ProcessNumber]

    Instructions:
    To Compile:
    javac BlockChain.java
----------------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A singleton for the Utilities class
 */
class Utilities
{
    private static KeyManager KeyManager = null;

    public static KeyManager GetKeyManager()
    {
        return KeyManager;
    }

    public static void SetKeyManager( KeyManager keyManager)
    {
        KeyManager = keyManager;
    }

    /**
     * Read in the input file and deserialize it into a block record
     *
     * @param fileName  the file path to read in
     * @param processId The current processes process Id
     * @return a list of all records in the file
     */
    public static ArrayList<BlockRecord> ReadInputFile(String fileName, int processId)
    {
        ArrayList<BlockRecord> blockRecords = new ArrayList();
        String currentPID = Integer.toString(processId);

        // read through the input file setting each of the BlockRecord fields
        try (BufferedReader br = new BufferedReader(new FileReader(fileName)))
        {

            String inputLine;
            while ((inputLine = br.readLine()) != null)
            {
                BlockRecord record = new BlockRecord();

                /** Header information for the block **/
                record.setBlockID(new String(UUID.randomUUID().toString()));
                record.setCreatingProcess(currentPID);

                // split the input line by 1 or more spaces into a delimited array
                String[] inputData = inputLine.split(" +");

                /** Patient information for the block **/
                // every input file has the same ordering on the data points so we can hard code their index's
                record.setSSNum(inputData[0]);
                record.setFirstName(inputData[1]);
                record.setLastName(inputData[2]);
                record.setDateOfBirth(inputData[3]);
                record.setDiagnosis(inputData[4]);
                record.setTreatment(inputData[5]);
                record.setMedication(inputData[6]);

                // in order to finish the block we have to serialize it, sign it, and then update the block
                String SHA256String = SerializeRecord(record);

                // Sign the block and then Base64 Encode the resulting byte[] into a string
                record.setSHA256String(SHA256String);
                String signedBlock = Base64.getEncoder().encodeToString(KeyManager.SignData(SHA256String.getBytes()));
                record.setSignedSHA256(signedBlock);

                blockRecords.add(record);
            }
        }
        catch (Exception ex)
        {
            System.err.println("Error while reading input text file" + ex);
        }

        // Print out all the names from the records collected
        System.out.println(blockRecords.size() + " records read.");
        System.out.println("Names from input:");
        for (BlockRecord record : blockRecords)
        {
            System.out.println("\t" + record.getFirstName() + " " + record.getLastName());
        }
        System.out.println("\n");

        System.out.println(blockRecords);

        return blockRecords;
    }

    /**
     * Serialize a list of block records
     *
     * @param blockRecords The list of block records to serialize
     */
    public static String SerializeRecord(ArrayList<BlockRecord> blockRecords)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(blockRecords);
    }

    /**
     * Serialize a single block record
     * <p>
     * * @param blockRecord The block record to serialize
     */
    public static String SerializeRecord(BlockRecord blockRecord)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(blockRecord);
    }

    /**
     * Send the unverified block to all unverified block ports
     *
     * @param unverifiedBlockPorts A list of the unverified block ports
     * @param unverifiedBlock      An unverified block
     */
    public static void SendUnverifiedBlocks(int[] unverifiedBlockPorts, ArrayList<BlockRecord> unverifiedBlock)
    {
        Socket sock;
        PrintStream toServer;

        try
        {
            // A serialized representation of the block to be sent
            String blockToSend;

            // Special condition for startup so all processes have a dummy block
            if (unverifiedBlock == null)
            {
                BlockRecord record = new BlockRecord();
                /** Header information for the block **/
                record.setBlockID(UUID.randomUUID().toString());
                record.setCreatingProcess(Integer.toString(BlockChain.PID));

                // in order to finish the block we have to serialize it, sign it, and then update the block
                String SHA256String = SerializeRecord(record);

                // Sign the block and then Base64 Encode the resulting byte[] into a string
                record.setSHA256String(SHA256String);

                String signedBlock = Base64.getEncoder().encodeToString(KeyManager.SignData(SHA256String.getBytes()));
                record.setSignedSHA256(signedBlock);

                blockToSend = SerializeRecord(record);
            }
            else
            {
                blockToSend = SerializeRecord(unverifiedBlock);
            }

            // send the generated block to each process
            for (int i = 0; i < unverifiedBlockPorts.length; i++)
            {
                sock = new Socket(BlockChain.ServerName, unverifiedBlockPorts[i]);
                toServer = new PrintStream(sock.getOutputStream());
                toServer.println(blockToSend);
                toServer.flush();
                toServer.close();
                sock.close();
            }
        }
        catch (Exception ex)
        {
            System.err.println("Error while sending unverified block " + ex);
        }
    }

    /**
     * Send the public key to the Public Key Server port for each process
     *
     * @param keyServerPorts The ports of the key servers
     */
    public static void SendKeys(int[] keyServerPorts)
    {
        Socket socket;
        ObjectOutputStream toServer;

        // If we're going to send the keys() then send them to every process and then return
        for (int i = 0; i < keyServerPorts.length; i++)
        {
            // wrapping the try catch inside the for loop will stop the process from failing completely if it cant
            // send to a single process for some reason
            try
            {
                // Send the public key to all of the running processes key server ports
                socket = new Socket(BlockChain.ServerName, keyServerPorts[i]);
                toServer = new ObjectOutputStream(socket.getOutputStream());

                toServer.writeObject(KeyManager.GetPublicKey());

                toServer.flush();
                toServer.close();
                socket.close();
                return;
            }
            catch (IOException ex)
            {
                System.err.println("Failed to send public keys to process: " + i + "with exception: " + ex);
            }
        }
    }
}

/**
 * The KeyManager class for generating the public/private key pair and also
 * signing and verifying the blocks of data
 */
class KeyManager
{
    private KeyPair keyPair = null;
    private PublicKey publicKey = null;
    private PrivateKey privateKey = null;
    private Signature signer = null;

    /**
     * The KeyManager class used to generate, sign, and verify keys
     */
    public KeyManager(PublicKey publicKey)
    {
        String signatureAlgorithm = "SHA1withRSA";

        try
        {
            this.signer = Signature.getInstance(signatureAlgorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            System.err.println("Invalid encryption algorithm supplied for signature" + ex);
        }

        // This constructor is for services that will only be using the public key
        this.publicKey = publicKey;

    }

    /**
     * The KeyManager class used to generate, sign, and verify keys
     */
    public KeyManager()
    {
        String signatureAlgorithm = "SHA1withRSA";

        try
        {
            // A seed was provided so this call is going to create the kv pair and
            // keep a copy of the private key
            this.signer = Signature.getInstance(signatureAlgorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            System.err.println("Invalid encryption algorithm supplied for keypair generation" + ex);
        }
    }

    /**
     * Generate the public/private key pair to be used with signing/verification
     *
     * @param randomSeed
     */
    public void GenerateKeyPair(long randomSeed)
    {
        String encryptionAlgorithm = "RSA";
        String hashingAlgorithm = "SHA1PRNG";
        String hashAlgorithmProvider = "SUN";

        // NOTE: this method should only be called by the last process in this project
        // as I only want to generate a key pair once then the public key will be shared
        // and identical for all processes
        try
        {
            // A seed was provided so this call is going to create the kv pair and
            // keep a copy of the private key

            KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance(encryptionAlgorithm);
            SecureRandom rng = SecureRandom.getInstance(hashingAlgorithm, hashAlgorithmProvider);
            rng.setSeed(randomSeed);
            keyGenerator.initialize(1024, rng);

            this.keyPair = keyGenerator.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

        }
        catch (NoSuchAlgorithmException ex)
        {
            System.err.println("Invalid encryption algorithm supplied for keypair generation\n" + ex);
        }
        catch (NoSuchProviderException ex)
        {
            System.err.println("Invalid hash algorithm provider supplied for keypair generation\n" + ex);
        }
    }

    /**
     * Get the public key for the Key pair
     *
     * @return the public key of the key pair
     */
    public PublicKey GetPublicKey()
    {
        return this.publicKey;
    }

    /**
     * Sign the data with the private key
     *
     * @param unsignedData The data to be signed
     * @return the signed data or null if an exception occured
     */
    public byte[] SignData(byte[] unsignedData)
    {
        try
        {
            this.signer.initSign(this.privateKey);
            this.signer.update(unsignedData);
            return this.signer.sign();
        }
        catch (SignatureException ex)
        {
            System.err.println("Signature error while trying to sign the data block\n" + ex);
            return null;
        }
        catch (InvalidKeyException ex)
        {
            System.err.println("Invalid key exception while trying to sign the data block\n" + ex);
            return null;
        }
    }

    /**
     * Validate that the signed data matches the unsigned data once using
     * the public key
     *
     * @param unsignedData The unsigned data to use for verification
     * @param signedData   The data that has been signed with the private key
     * @return A value indicating whether or not the data has been signed by
     * the private key
     */
    public boolean VerifySignature(byte[] unsignedData, byte[] signedData)
    {
        try
        {
            this.signer.initVerify(this.publicKey);
            this.signer.update(unsignedData);

            return this.signer.verify(signedData);
        }
        catch (SignatureException ex)
        {
            System.err.println("Signature error while trying to verify the data block\n" + ex);
            return false;
        }
        catch (InvalidKeyException ex)
        {
            System.err.println("Invalid key exception while trying to verify the data block\n" + ex);
            return false;
        }
    }

}

/**
 * Class representing the ports used for each process
 */
class Ports
{
    private static int KeyServerPortBase = 4710;
    private static int UnverifiedBlockServerPortBase = 4820;
    private static int BlockChainServerPortBase = 4930;

    private static int[] KeyServerPortsInUse;
    private static int[] UnverifiedBlockServerPortsInUse;
    private static int[] BlockChainServerPortsInUse;

    private static int KeyServerPort;

    private static int UnverifiedBlockServerPort;
    private static int BlockChainServerPort;

    /**
     * The ports constructor
     *
     * @param runningProcessCount the number of running processes
     */
    public static void setPortsForAllProcesses(int runningProcessCount)
    {
        // this is helpful because every process will know all running ports that are in use by the service
        for (int i = 0; i < runningProcessCount; ++i)
        {
            KeyServerPortsInUse[i] = KeyServerPortBase + i;
            UnverifiedBlockServerPortsInUse[i] = UnverifiedBlockServerPortBase + i;
            BlockChainServerPortsInUse[i] = BlockChainServerPortBase + i;
        }
    }

    /**
     * Set the ports for each of the process types by incrementing the processId to the base value
     *
     * @param processId the process id for this process
     */
    public static void setPortsForCurrentProcess(int processId)
    {
        KeyServerPort = KeyServerPortBase + processId;
        UnverifiedBlockServerPort = UnverifiedBlockServerPortBase + processId;
        BlockChainServerPort = BlockChainServerPortBase + processId;
    }

    /**
     * Get the Key Server Port for this process
     *
     * @return The Key Server Port for the current process
     */
    public static int getKeyServerPort()
    {
        return KeyServerPort;
    }

    /**
     * Get the current processes unverified block server port
     *
     * @return The current processes unverified block server port
     */
    public static int getUnverifiedBlockServerPort()
    {
        return UnverifiedBlockServerPort;
    }

    /**
     * Get the current processes block chain server port
     *
     * @returnThe current processes block chain server port
     */
    public static int getBlockChainServerPort()
    {
        return BlockChainServerPort;
    }

    /**
     * Gets a list of the Key Server Ports that are in use
     *
     * @return A list of the key server ports in use
     */
    public static int[] getKeyServerPortsInUse()
    {
        return KeyServerPortsInUse;
    }

    /**
     * Gets a list of the Unverified Block Server Ports that are in use
     *
     * @return A list of the unverified block ports in use
     */
    public static int[] getUnverifiedBlockServerPortsInUse()
    {
        return UnverifiedBlockServerPortsInUse;
    }

    /**
     * Gets a list of the Block Chain Ports that are in use
     *
     * @return A list of the block chain ports in use
     */
    public static int[] getBlockChainServerPortsInUse()
    {
        return BlockChainServerPortsInUse;
    }
}

/**
 * BlockChain Block object with json serialization
 */
class BlockRecord
{
    // only used to get a readable date format
    private DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");

    @SerializedName(value = "SHA256String")
    private String SHA256String = "SHA256 String";

    @SerializedName(value = "SignedSHA256")
    private String SignedSHA256 = "Signed SHA256 String";

    @SerializedName(value = "SHA256String")
    private String TimeStamp = dateFormat.format(new Date());

    @SerializedName(value = "BlockId")
    private String BlockId = "";

    @SerializedName(value = "VerificationProcessId")
    private String VerificationProcessId = "";

    @SerializedName(value = "CreatingProcess")
    private String CreatingProcess = "";

    @SerializedName(value = "PreviousHash")
    private String PreviousHash = "";

    @SerializedName(value = "FirstName")
    private String FirstName = "";

    @SerializedName(value = "LastName")
    private String LastName = "";

    @SerializedName(value = "SSN", alternate = {"SocialSecurityNumber"})
    private String SocialSecurityNumber = "";

    @SerializedName(value = "DOB", alternate = {"DateOfBirth"})
    private String DateOfBirth = "";

    @SerializedName(value = "Diagnosis")
    private String Diagnosis = "";

    @SerializedName(value = "Treatment")
    private String Treatment = "";

    @SerializedName(value = "Medication")
    private String Medication = "";


    /**
     * Get the SHA256Sting
     *
     * @return
     */
    public String getSHA256String()
    {
        return SHA256String;
    }

    public void setSHA256String(String SH)
    {
        this.SHA256String = SH;
    }

    public String getSignedSHA256()
    {
        return SignedSHA256;
    }

    public void setSignedSHA256(String SH)
    {
        this.SignedSHA256 = SH;
    }

    public String getCreatingProcess()
    {
        return CreatingProcess;
    }

    public void setCreatingProcess(String CP)
    {
        this.CreatingProcess = CP;
    }

    public String getVerificationProcessId()
    {
        return this.VerificationProcessId;
    }

    public void setVerificationProcessID(String verificationProcessId)
    {
        this.VerificationProcessId = verificationProcessId;
    }

    public String getBlockID()
    {
        return this.BlockId;
    }

    public void setBlockID(String blockId)
    {
        this.BlockId = blockId;
    }

    public String getSocialSecurityNumber()
    {
        return this.SocialSecurityNumber;
    }

    public void setSSNum(String socialSecurityNumber)
    {
        this.SocialSecurityNumber = socialSecurityNumber;
    }

    public String getFirstName()
    {
        return this.FirstName;
    }

    public void setFirstName(String firstName)
    {
        this.FirstName = firstName;
    }

    public String getLastName()
    {
        return LastName;
    }

    public void setLastName(String lastName)
    {
        this.LastName = lastName;
    }

    public String getDateOfBirth()
    {
        return this.DateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth)
    {
        this.DateOfBirth = dateOfBirth;
    }

    public String getDiagnosis()
    {
        return this.Diagnosis;
    }

    public void setDiagnosis(String diagnosis)
    {
        this.Diagnosis = diagnosis;
    }

    public String getTreatment()
    {
        return this.Treatment;
    }

    public void setTreatment(String treatment)
    {
        this.Treatment = treatment;
    }

    public String getMedication()
    {
        return this.Medication;
    }

    public void setMedication(String medication)
    {
        this.Medication = medication;
    }
}

class UnverifiedBlockConsumer implements Runnable
{
    private BlockingQueue<String> queue;
    private int port;

    UnverifiedBlockConsumer(int port, BlockingQueue<String> queue)
    {
        this.port = port;
        this.queue = queue;
    }

    public void run()
    {
        String data;
        PrintStream toServer;
        Socket sock;
        String fakeVerifiedBlock;

        System.out.println("Starting the Unverified Block Consumer thread.\n");
        try
        {
            while (true)
            {
                // Consume from the incoming queue. Do the work to verify. Mulitcast new blockchain
                data = queue.take(); // Will blocked-wait on empty queue
                System.out.println("Consumer got unverified: " + data);

                // Ordinarily we would do real work here, based on the incoming data.
                int j; // Here we fake doing some work (That is, here we could cheat, so not ACTUAL work...)
                for (int i = 0; i < 100; i++)
                {
                    // put a limit on the fake work for this example
                    j = ThreadLocalRandom.current().nextInt(0, 10);
                    try
                    {
                        Thread.sleep(500);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    if (j < 3)
                    {
                        break;
                    }
                }

	/* With duplicate blocks that have been verified by different procs ordinarily we would keep only the one with
           the lowest verification timestamp. For the exmple we use a crude filter, which also may let some dups through */
                if (BlockChain.RecordBlockChain.indexOf(data.substring(1, 9)) < 0)
                {
                    // Crude, but excludes most duplicates.
                    fakeVerifiedBlock = "[" + data + " verified by P" + BlockChain.PID + " at time " + Integer.toString(ThreadLocalRandom.current().nextInt(100, 1000)) + "]\n";
                    System.out.println(fakeVerifiedBlock);
                    String tempChain = fakeVerifiedBlock + BlockChain.RecordBlockChain; // add the verified block to the chain
                    for (int i = 0; i < BlockChain.ProcessCount; i++)
                    {
                        // send to each process in group, including us:
                        sock = new Socket(BlockChain.ServerName, this.port);
                        toServer = new PrintStream(sock.getOutputStream());
                        toServer.println(tempChain);
                        toServer.flush(); // make the multicast
                        sock.close();
                    }
                }
                Thread.sleep(1500); // For the example, wait for our blockchain to be updated before processing a new block
            }
        }
        catch (Exception e)
        {
            System.out.println(e);
        }
    }
}

/**
 * The Public Key worker class
 */
class UnverifiedBlockWorker extends Thread
{
    private Socket socket;
    private BlockingQueue<String> queue;

    UnverifiedBlockWorker(Socket s, BlockingQueue<String> queue)
    {
        this.socket = s;
        this.queue = queue;
    }

    public void run()
    {
        System.out.println("Unverified Block Client Connected");

        BufferedReader in = null;
        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                String data = in.readLine();
                System.out.println("Put in priority queue: " + data + "\n");
                queue.put(data);
            }
            catch (Exception e)
            {
                System.out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
                in.close();
            }
        }
        catch (IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }
}

/**
 * The Public Key worker class listens for the public key and then creates the KeyManager if it doesnt already have a
 * publicKey
 */
class PublicKeyWorker extends Thread
{
    private Socket socket;

    PublicKeyWorker(Socket s)
    {
        this.socket = s;
    }

    public void run()
    {
        System.out.println("Public Key Client Connected");

        ObjectInputStream in;
        ObjectOutputStream out;

        try
        {
            // create an input stream on the specified socket
            in = new ObjectInputStream(this.socket.getInputStream());

            // TODO: Use the output stream to send an ack that we got the public key
            out = new ObjectOutputStream(this.socket.getOutputStream());

            try
            {
                // this is in no way, shape, or form secure but for this assignment it's fine
                PublicKey publicKey = (PublicKey) in.readObject();
                System.out.println("Got key: " + publicKey.toString());

                if (Utilities.GetKeyManager() == null)
                {
                    Utilities.SetKeyManager(new KeyManager(publicKey));
                }
            }
            catch (Exception e)
            {
                System.out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
                in.close();
                out.flush();
                out.close();
            }
        }
        catch (IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }

}

/**
 * The BlockChain worker class
 */
class BlockChainWorker extends Thread
{
    private Socket socket;

    BlockChainWorker(Socket s)
    {
        this.socket = s;
    }

    public void run()
    {
        System.out.println("Block Chain Client Connected");

        PrintStream out = null;
        BufferedReader in = null;

        try
        {
            // create an output stream on the specified socket
            out = new PrintStream(this.socket.getOutputStream());
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
            }
            catch (Exception e)
            {
                System.out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
                out.close();
                in.close();
            }
        }
        catch (IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }
}

/**
 * The BlockChain thread
 */
class BlockChainThread implements Runnable
{
    private int port;

    BlockChainThread(int port)
    {
        this.port = port;
    }

    public void run()
    {
        int q_len = 6;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("BlockChain Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next ADMIN client connection:
                socket = serverSocket.accept();
                // Once a connection has come in start the admin worker
                new BlockChainWorker(socket).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to start block chain worker with exception: " + e);
        }
    }
}

/**
 * The UnverifiedBlock thread
 */
class UnverifiedBlockThread implements Runnable
{
    private int port;
    private BlockingQueue<String> queue;

    UnverifiedBlockThread(int port, BlockingQueue<String> queue)
    {
        this.port = port;
        this.queue = queue;
    }

    public void run()
    {
        int q_len = 6;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("Unverified Block Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next unverified block to be sent
                socket = serverSocket.accept();
                // Once a block has come in start the admin worker
                new UnverifiedBlockWorker(socket, queue).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to start block chain worker with exception: " + e);
        }
    }
}

/**
 * The BlockChain thread
 */
class PublicKeyThread implements Runnable
{
    private int port;

    PublicKeyThread(int port)
    {
        this.port = port;
    }

    public void run()
    {
        int q_len = 6;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("PublicKey Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next public key to come in
                socket = serverSocket.accept();
                // Once a key has come in start the public key worker
                new PublicKeyWorker(socket).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to start public key worker with exception: " + e);
        }
    }
}

/**
 * BlockChain class
 */
public class BlockChain
{
    public static final int ProcessCount = 3;
    public static final int PID = 0;
    public static final String ServerName = "localhost";

    // The actual block chain object
    public static String RecordBlockChain = "[First block]";
    public static int ProcessId = 0;

    /**
     * The main entry point of the block chain program
     *
     * @param args The arguments
     */
    public static void main(String args[])
    {
        // Create the queue that will be used to hold unverified blocks
        final BlockingQueue<String> queue = new PriorityBlockingQueue<>();

        String inputFileName;
        // Create the ports object to store the port information about all ports in use as well as the ports per process
        Ports.setPortsForAllProcesses(ProcessCount);

        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("Usage: java BlockChain [ProcessNumber]");
            System.out.println("Missing ProcessNumber parameter so defaulting to 0\n");
            System.out.println("-------------------------------------------------------\n");
        }
        else
        {
            ProcessId = Integer.parseInt(args[0]);
        }

        switch (ProcessId)
        {
            case 1:
            {
                inputFileName = "BlockInput1.txt";
                break;
            }
            case 2:
            {
                // Process 2 is the last process and is a little special, it will multi-cast the public keys
                // which means it also needs to generate the key pair
                inputFileName = "BlockInput2.txt";

                // Create the key manager, generate the key pair, then set up the utilities class
                KeyManager = new KeyManager();
                KeyManager.GenerateKeyPair(1000);
                Utilities.SetKeyManager(KeyManager);
                Utilities.SendKeys(ports.getKeyServerPortsInUse());
                break;
            }
            default:
            {
                inputFileName = "BlockInput0.txt";
                break;
            }
        }

        // Set the ports for this process
        ports.setPorts(ProcessId);

        System.out.println("Process number: " + ProcessId + " Ports:");
        System.out.println("\t\t Public Keys Port: " + ports.getKeyServerPort());
        System.out.println("\t\t UnverifiedBlocksPort: " + ports.getUnverifiedBlockServerPort());
        System.out.println("\t\t BlockChainPort: " + ports.getBlockChainServerPort());

        System.out.println("\nUsing input file: " + inputFileName + "\n");

        // Start the block chain, unverified blocks, and public keys servers as well as the UnverifiedBlock consumer
        try
        {
            new Thread(new BlockChainThread(ports.getBlockChainServerPort())).start();
            new Thread(new PublicKeyThread(ports.getKeyServerPort())).start();
            new Thread(new UnverifiedBlockThread(ports.getUnverifiedBlockServerPort(), queue)).start();
            new Thread(new UnverifiedBlockConsumer(ports.getBlockChainServerPort(), queue)).start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to start server thread with exception: " + e);
        }

        try
        {
            // Block each process until a public key is available this should signify all the servers are up and running
            while (KeyManager == null)
            {
                Thread.sleep(1000);
            }
        }
        catch (Exception e)
        {
            System.out.println("Error while sleeping");
        }


        // Now that all the servers on each process should be running let process 2 send out the initial unverified block
        if (ProcessId == 2)
        {
            Utilities.SendUnverifiedBlocks(ports.getUnverifiedBlockServerPortsInUse(), null);
        }
    }
}



