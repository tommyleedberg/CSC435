/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: October 10, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java BlockChain [ProcessNumber]

    Instructions:
    To Compile:
    javac -cp "gson-2.8.4.jar" *.java

    Notes:
    An additional port is being used by process 2, this port
    is 1524 and it is the port that the KeyManager listens on
----------------------------------------------------------*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
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

    public static void SetKeyManager(KeyManager keyManager)
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
                record.setBlockId(new String(UUID.randomUUID().toString()));
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
                blockRecords.add(record);
            }
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while reading input text file", ex);
        }

        // Print out all the names from the records collected
        System.out.println(blockRecords.size() + " records read.");
        System.out.println("Names from input:");
        for (BlockRecord record : blockRecords)
        {
            System.out.println("\t" + record.getFirstName() + " " + record.getLastName());
        }
        System.out.println("\n");

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

    public static BlockRecord DeserializeRecord(String recordString)
    {
        return new Gson().fromJson(recordString, BlockRecord.class);
    }

    /**
     * Forward the unverified block to the keymanager or create the block and then send it if it's the dummy block
     *
     * @param unverifiedBlock An unverified block
     */
    public static void SendUnverifiedBlocks(BlockRecord unverifiedBlock)
    {
        try
        {
            Socket socket = null;
            PrintStream out = null;

            try
            {
                // create a socket to the key manager for sending the block to be signed
                socket = new Socket(BlockChain.ServerName, Ports.KeyManagerPort);
                out = new PrintStream(socket.getOutputStream());

                // Special condition for startup so all processes have a dummy block
                if (unverifiedBlock == null)
                {
                    BlockRecord record = new BlockRecord();
                    /** Header information for the block **/
                    record.setBlockId("0");
                    record.setCreatingProcess(Integer.toString(BlockChain.PID));

                    // in order to finish the block we have to serialize it, sign it, and then update the block
                    String SHA256String = SerializeRecord(record);

                    // Sign the block and then Base64 Encode the resulting byte[] into a string
                    record.setSHA256String(SHA256String);

                    System.out.println("Sending Unverified Block to be Signed");
                    //Send the record to the key manager to be signed
                    out.println(SerializeRecord(record));
                    out.flush();

                }
                else
                {
                    //Send the record to the key manager to be signed
                    out.println(SerializeRecord(unverifiedBlock));
                    out.flush();
                }
            }
            catch (IOException ex)
            {
                BlockChain.PrintError("Error sending unverified block to key manager to be signed", ex);
            }
            finally
            {
                out.close();
                socket.close();
            }

        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while sending unverified block", ex);
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

                System.out.println("Sending public key to process " + i);
                toServer.writeObject(KeyManager.GetPublicKey());

                toServer.flush();
                toServer.close();
                socket.close();
            }
            catch (IOException ex)
            {
                BlockChain.PrintError("Failed to send public keys to process " + i, ex);
                return;
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
    private PublicKey[] AllPublicKeys = null;

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
            BlockChain.PrintError("Invalid encryption algorithm supplied for signature", ex);
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
            BlockChain.PrintError("Invalid encryption algorithm supplied for key pair generation", ex);
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
            BlockChain.PrintError("Invalid encryption algorithm supplied for keypair generation", ex);
        }
        catch (NoSuchProviderException ex)
        {
            BlockChain.PrintError("Invalid hash algorithm provider supplied for keypair generation", ex);
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
            BlockChain.PrintError("Signature error while trying to sign the data block", ex);
            return null;
        }
        catch (InvalidKeyException ex)
        {
            BlockChain.PrintError("Invalid key exception while trying to sign the data block", ex);
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
            BlockChain.PrintError("Signature error while trying to verify the data block", ex);
            return false;
        }
        catch (InvalidKeyException ex)
        {
            BlockChain.PrintError("Invalid key exception while trying to verify the data block", ex);
            return false;
        }
    }

}

/**
 * Class representing the ports used for each process
 */
class Ports
{
    public static final int KeyManagerPort = 1524;
    private static int KeyServerPortBase = 4710;
    private static int UnverifiedBlockServerPortBase = 4820;
    private static int BlockChainServerPortBase = 4930;
    private static int[] KeyServerPortsInUse = new int[BlockChain.ProcessCount];
    private static int[] UnverifiedBlockServerPortsInUse = new int[BlockChain.ProcessCount];
    private static int[] BlockChainServerPortsInUse = new int[BlockChain.ProcessCount];
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
class BlockRecord implements Comparable<BlockRecord>
{
    @SerializedName (value = "SHA256String")
    private String SHA256String = "SHA256 String";

    @SerializedName (value = "SignedSHA256")
    private String SignedSHA256 = "Signed SHA256 String";

    @SerializedName (value = "CreationTime")
    private Date CreationTime = new Date();

    @SerializedName (value = "BlockId")
    private String BlockId = "";

    @SerializedName (value = "SignedBlockId")
    private String SignedBlockId = "";

    @SerializedName (value = "VerificationProcessId")
    private String VerificationProcessId = "";

    @SerializedName (value = "CreatingProcess")
    private String CreatingProcess = "";

    @SerializedName (value = "PreviousHash")
    private String PreviousHash = "";

    @SerializedName (value = "FirstName")
    private String FirstName = "";

    @SerializedName (value = "LastName")
    private String LastName = "";

    @SerializedName (value = "SSN", alternate = {"SocialSecurityNumber"})
    private String SocialSecurityNumber = "";

    @SerializedName (value = "DOB", alternate = {"DateOfBirth"})
    private String DateOfBirth = "";

    @SerializedName (value = "Diagnosis")
    private String Diagnosis = "";

    @SerializedName (value = "Treatment")
    private String Treatment = "";

    @SerializedName (value = "Medication")
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

    public String getCreationTime()
    {
        return this.CreationTime.toString();
    }

    public void setCreationTime(Date timestamp)
    {
        this.CreationTime = timestamp;
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

    public String getBlockId()
    {
        return this.BlockId;
    }

    public void setBlockId(String blockId)
    {
        this.BlockId = blockId;
    }

    public String getSignedBlockId()
    {
        return this.SignedBlockId;
    }

    public void setSignedBlockId(String SignedBlockId)
    {
        this.SignedBlockId = SignedBlockId;
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

    @Override
    public int compareTo(BlockRecord other)
    {
        return this.CreationTime.compareTo(other.CreationTime);
    }
}

class UnverifiedBlockConsumer implements Runnable
{
    public void run()
    {
        BlockRecord record;

        BlockChain.PrintInformation("Starting the Unverified Block Consumer thread.");
        try
        {
            while (true)
            {
                boolean blockExists = false;

                // Consume from the incoming queue. Do the work to verify. Multi-cast new blockchain
                record = BlockChain.Queue.take();

                BlockChain.PrintInformation("Unverified block consumer got a new unverified block: " + Utilities.SerializeRecord(record));

                for (BlockRecord ledgerRecord : BlockChain.BlockLedger)
                {
                    //If our current ledger already contains a block with this block id that means it's been solved so we dont have to solve it
                    if (ledgerRecord.getBlockId().compareToIgnoreCase(record.getBlockId()) == 0)
                    {
                        blockExists = true;
                        break;
                    }
                }

                byte[] signedSHA256String;
                byte[] signedBlockId;
                // Validate the signature of the block if it's invalid don't bother processing it
                try
                {
                    signedSHA256String = Base64.getDecoder().decode(record.getSignedSHA256());
                }
                catch (IllegalArgumentException ex)
                {
                    BlockChain.PrintError("Failed to base64 decode the signed SHA256 String", ex);
                    continue;
                }

                try
                {
                    signedBlockId = Base64.getDecoder().decode(record.getSignedBlockId());
                }
                catch (IllegalArgumentException ex)
                {
                    BlockChain.PrintError("Failed to base64 decode the signed block id", ex);
                    continue;
                }


                if (!Utilities.GetKeyManager().VerifySignature(record.getSHA256String().getBytes(), signedSHA256String))
                {
                    BlockChain.PrintError("Record's sha256String has an been signed by an invalid private key");
                    continue;
                }

                // Validate the signature of the signed block id if it's invalid dont bother processing it
                if (!Utilities.GetKeyManager().VerifySignature(record.getBlockId().getBytes(), signedBlockId))
                {
                    BlockChain.PrintError("Record's blockId has an been signed by an invalid private key");
                    continue;
                }

                // Do fake work for now
                int j;
                for (int i = 0; i < 100; i++)
                {
                    // put a limit on the fake work for this example
                    j = ThreadLocalRandom.current().nextInt(0, 10);
                    try
                    {
                        Thread.sleep(500);

                        // Make sure the block hasn't already been solved while doing work.
                        for (BlockRecord ledgerRecord : BlockChain.BlockLedger)
                        {
                            //If our current ledger already contains a block with this block id that means it's been solved so we dont have to solve it
                            if (ledgerRecord.getBlockId().compareToIgnoreCase(record.getBlockId()) == 0)
                            {
                                BlockChain.PrintInformation("Block already verified so wait for next block");
                                blockExists = true;
                                break;
                            }
                        }

                        // There is no reason to continue doing work because the block has been added to the ledger
                        if(blockExists)
                        {
                            break;
                        }

                    }
                    catch (Exception ex)
                    {
                        BlockChain.PrintError( "Error occured while doing 'work'", ex);
                    }

                    if (j < 3)
                    {
                        break;
                    }
                }

                for (BlockRecord ledgerRecord : BlockChain.BlockLedger)
                {
                    //If our current ledger already contains a block with this block id that means it's been solved so we dont have to solve it
                    if (ledgerRecord.getBlockId().compareToIgnoreCase(record.getBlockId()) == 0)
                    {
                        BlockChain.PrintInformation("Block already verified so wait for next block");
                        blockExists = true;
                        break;
                    }
                }

                record.setVerificationProcessID(Integer.toString(BlockChain.ProcessId));
                // We made it this far without the puzzle being sovled by another process so send the new record to the block chain server to be added to the ledger
                if(!blockExists)
                {
                    this.SendBlock(record);
                }
            }
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while verifying block", ex);
        }
    }

    public void SendBlock(BlockRecord record)
    {
        PrintStream out;
        Socket sock;

        try
        {
            // Forward the new block to all of the block chain server so it can be added to the ledger
            int[] blockChainServerPorts = Ports.getBlockChainServerPortsInUse();
            for (int i = 0; i < blockChainServerPorts.length; i++)
            {
                // send to each process in group, including us:
                sock = new Socket(BlockChain.ServerName, blockChainServerPorts[i]);
                out = new PrintStream(sock.getOutputStream());
                BlockChain.PrintInformation("Sending new verified block to be stored in the ledger");
                out.println(Utilities.SerializeRecord(record));
                out.flush();
                out.close();
                sock.close();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Failed to send verified block to block chain server", ex);
        }
    }
}

/**
 * The Public Key worker class
 */
class UnverifiedBlockWorker extends Thread
{
    private Socket socket;

    UnverifiedBlockWorker(Socket s)
    {
        this.socket = s;
    }

    public void run()
    {
        BlockChain.PrintInformation("Unverified Block Client Connected");

        BufferedReader in = null;
        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                BlockChain.PrintInformation("Received a new Unverified Block");

                String newBlock = "";
                String incomingBlock;
                while ((incomingBlock = in.readLine()) != null)
                {
                    newBlock += incomingBlock;
                }

                BlockChain.Queue.put(Utilities.DeserializeRecord(newBlock));
            }
            catch (Exception ex)
            {
                BlockChain.PrintError("Server error", ex);
            }
            finally
            {
                in.close();
                this.socket.close();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Error opening i/o pipe on the specified socket", ex);
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
        BlockChain.PrintInformation("Public Key Client Connected");
        ObjectInputStream in;

        // If this is process 2 we've already established the public key so we dont need to do anything else here
        if (BlockChain.ProcessId == 2)
        {
            return;
        }

        try
        {
            // create an input stream on the specified socket
            in = new ObjectInputStream(this.socket.getInputStream());

            try
            {
                PublicKey publicKey = (PublicKey) in.readObject();
                BlockChain.PrintInformation("Process " + BlockChain.ProcessId + " got a new key: " + publicKey.toString());

                if (Utilities.GetKeyManager() == null)
                {
                    BlockChain.PrintInformation("Setting public key");
                    Utilities.SetKeyManager(new KeyManager(publicKey));
                }
            }
            catch (Exception ex)
            {
                BlockChain.PrintError("Server error", ex);
            }
            finally
            {
                in.close();
                this.socket.close();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Error opening i/o pipe on the specified socket: ", ex);
        }
    }

}

/**
 * The Key Manager Worker listens for record blocks when a block comes in it uses the secret key to sign the
 * SHA256 signed string.
 */
class KeyManagerWorker extends Thread
{
    private Socket socket;
    private KeyManager keyManager;

    KeyManagerWorker(Socket s, KeyManager keyManager)
    {
        this.socket = s;
        this.keyManager = keyManager;
    }

    public void run()
    {
        BlockChain.PrintInformation("Key Manager Client Connected");

        PrintStream out = null;
        BufferedReader in = null;

        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                // Listen for a new block to be completed and then add it to the current ledger
                String recordBlock = "";
                String incomingBlock;
                while ((incomingBlock = in.readLine()) != null)
                {
                    recordBlock += incomingBlock;
                }

                //Get the new block, sign the SHA256 string and the blockId with the private key, and send the block out to the unverified block process
                BlockRecord blockToSend = Utilities.DeserializeRecord(recordBlock);

                int[] unverifiedBlockPorts = Ports.getUnverifiedBlockServerPortsInUse();

                //Sign the block and then base 64 encode it
                blockToSend.setSignedSHA256(Base64.getEncoder().encodeToString(this.keyManager.SignData(blockToSend.getSHA256String().getBytes())));
                blockToSend.setSignedBlockId(Base64.getEncoder().encodeToString(this.keyManager.SignData(blockToSend.getBlockId().getBytes())));

                // send the generated block to each process
                for (int i = 0; i < unverifiedBlockPorts.length; i++)
                {
                    BlockChain.PrintInformation("Sending Unverified Block to Process " + i);

                    Socket unverifiedBlockServerSocket = new Socket(BlockChain.ServerName, unverifiedBlockPorts[i]);
                    out = new PrintStream(unverifiedBlockServerSocket.getOutputStream());
                    out.println(Utilities.SerializeRecord(blockToSend));
                    out.flush();
                    out.close();
                    unverifiedBlockServerSocket.close();
                }
            }
            catch (Exception ex)
            {
                BlockChain.PrintError("Server error", ex);
            }
            finally
            {
                this.socket.close();
                in.close();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Error opening i/o pipe on the specified socket: ", ex);
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
        BlockChain.PrintInformation("Block Chain Client Connected");
        BufferedReader in = null;

        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                // Listen for a new block to be completed and then add it to the current ledger
                String newBlock = "";
                String incomingBlock;
                while ((incomingBlock = in.readLine()) != null)
                {
                    newBlock += incomingBlock;
                }

                BlockRecord record = Utilities.DeserializeRecord(newBlock) ;

                boolean blockExists = false;
                // One last test to make sure the block hasnt been added to the ledger already
                for (BlockRecord ledgerRecord : BlockChain.BlockLedger)
                {
                    //If our current ledger already contains a block with this block id that means it's been solved so we don't have to solve it
                    if (ledgerRecord.getBlockId().compareToIgnoreCase(record.getBlockId()) == 0)
                    {
                        BlockChain.PrintInformation("Duplicate record found for blockId " + record.getBlockId() + " in ledger");
                        blockExists = true;
                        break;
                    }
                }

                if(!blockExists)
                {
                    // The record is unique so update the ledger
                    BlockChain.BlockLedger.add(record);
                    //If this is the process with an Id of 0 then we need to export the ledger to disk
                    if (BlockChain.ProcessId == 0)
                    {
                        this.ExportLedger();
                    }
                }
            }
            catch (Exception ex)
            {
                BlockChain.PrintError("Block Chain Server error", ex);
            }
            finally
            {
                this.socket.close();
                in.close();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Error opening i/o pipe on the specified socket", ex);
        }
    }

    private void ExportLedger()
    {
        BlockChain.PrintInformation("Exporting updated ledger");

        try
        {
            BufferedWriter bw = null;

            String serializedBlock = Utilities.SerializeRecord(BlockChain.BlockLedger);

            try
            {
                BlockChain.PrintError("Current Ledger Size: " + BlockChain.BlockLedger.size());
                //BlockChain.PrintInformation("--NEW BLOCKCHAIN--\n" + serializedBlock);

                bw = new BufferedWriter(new FileWriter("BlockChainLedger.json", false));
                bw.write(serializedBlock);
                bw.flush();
            }
           catch( IOException ex)
           {
               BlockChain.PrintError("Error while exporting the blockchain ledger", ex);
           }
            finally
            {
                bw.close();
            }
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while exporting the blockchain ledger", ex);
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
            BlockChain.PrintInformation(String.format("BlockChain Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next ADMIN client connection:
                socket = serverSocket.accept();
                // Once a connection has come in start the admin worker
                new BlockChainWorker(socket).start();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Failed to start block chain worker", ex);
        }
    }
}

/**
 * The UnverifiedBlock thread
 */
class UnverifiedBlockThread implements Runnable
{
    private int port;

    UnverifiedBlockThread(int port)
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
            BlockChain.PrintInformation(String.format("Unverified Block Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next unverified block to be sent
                socket = serverSocket.accept();
                // Once a block has come in start the admin worker
                new UnverifiedBlockWorker(socket).start();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Failed to start block chain worker with exception: ", ex);
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
            BlockChain.PrintInformation(String.format("PublicKey Process listening on the port %s.", port));

            while (true)
            {
                // wait for the next public key to come in
                socket = serverSocket.accept();
                // Once a key has come in start the public key worker
                new PublicKeyWorker(socket).start();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Failed to start public key worker", ex);
        }
    }
}

/**
 * The Key Manager thread
 */
class KeyManagerThread implements Runnable
{
    private int port = 1524;

    public void run()
    {
        int q_len = 6;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            BlockChain.PrintInformation(String.format("Key Manager Process listening on the port %s.", port));

            BlockChain.PrintInformation("Creating private/public key pair");
            // Create the key manager and the public/private key pair
            KeyManager keyManager = new KeyManager();
            keyManager.GenerateKeyPair(1000);

            // Set the Utility classes KeyManager and then send out the public key to all processes
            Utilities.SetKeyManager(keyManager);
            Utilities.SendKeys(Ports.getKeyServerPortsInUse());

            while (true)
            {
                // wait for the next public key to come in
                socket = serverSocket.accept();
                // Once a key has come in start the public key worker
                new KeyManagerWorker(socket, keyManager).start();
            }
        }
        catch (IOException ex)
        {
            BlockChain.PrintError("Failed to start Key Manager Worker", ex);
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
    public static final BlockingQueue<BlockRecord> Queue = new PriorityBlockingQueue<>();
    public static ArrayList<BlockRecord> BlockLedger = new ArrayList<>();
    public static int ProcessId = 0;

    /**
     * The main entry point of the block chain program
     *
     * @param args The arguments
     */
    public static void main(String args[])
    {
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
                // Process 2 hosts the key manager service so start it up
                new Thread(new KeyManagerThread()).start();
                break;
            }
            default:
            {
                inputFileName = "BlockInput0.txt";
                break;
            }
        }

        // Set the ports for this process
        Ports.setPortsForCurrentProcess(ProcessId);

        BlockChain.PrintInformation("Ports:");
        System.out.println("\t\t Public Keys Port: " + Ports.getKeyServerPort());
        System.out.println("\t\t UnverifiedBlocksPort: " + Ports.getUnverifiedBlockServerPort());
        System.out.println("\t\t BlockChainPort: " + Ports.getBlockChainServerPort());

        BlockChain.PrintInformation("\nUsing input file: " + inputFileName + "\n");

        // Start the block chain, unverified blocks, and public keys servers as well as the UnverifiedBlock consumer
        try
        {
            new Thread(new PublicKeyThread(Ports.getKeyServerPort())).start();
            new Thread(new UnverifiedBlockThread(Ports.getUnverifiedBlockServerPort())).start();
            new Thread(new UnverifiedBlockConsumer()).start();
            new Thread(new BlockChainThread(Ports.getBlockChainServerPort())).start();
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Failed to start server threads", ex);
        }

        try
        {
            // Block each process until a public key is available this should signify all the servers are up and running
            BlockChain.PrintInformation("waiting on PublicKey");
            while (Utilities.GetKeyManager() == null)
            {
                Thread.sleep(1000);
            }
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while sleeping", ex);
        }

        // Now that all the servers on each process should be running let process 2 send out the initial unverified block
        if (ProcessId == 2)
        {
            Utilities.SendUnverifiedBlocks(null);
        }

        //All dummy blocks have been sent so get all the blocks from the file and send them out 1 by 1
        ArrayList<BlockRecord> recordBlocks = Utilities.ReadInputFile(inputFileName, ProcessId);

        for (BlockRecord record : recordBlocks)
        {
            Utilities.SendUnverifiedBlocks(record);
        }
    }

    /**
     * Write the log line to System.out
     * @param logString The string to log
     */
    public static void PrintInformation(String logString)
    {
        System.out.println("Process " + BlockChain.ProcessId + ": " + logString);
    }

    /**
     * Writes the error string and exception to System.err
     * @param logString the log line
     * @param ex The exception
     */
    public static void PrintError(String logString, Exception ex)
    {
        String errorStr = "*********************ERROR********************\n";
        errorStr += "Process " + BlockChain.ProcessId + ": " + logString + " with exception: " + ex + "\n";
        errorStr += "*********************ERROR********************";
        System.err.println(errorStr);
    }

    /**
     * Writes the error string and exception to System.err
     * @param logString the log line
     */
    public static void PrintError(String logString)
    {
        String errorStr = "*********************ERROR********************\n";
        errorStr += "Process " + BlockChain.ProcessId + ": " + logString + "\n";
        errorStr += "*********************ERROR********************";
        System.err.println(errorStr);
    }
}



