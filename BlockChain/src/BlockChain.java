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
import com.google.gson.reflect.TypeToken;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

class DataBlock
{
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
     * SEt the social security number for the person in this record block
     *
     * @socialSecurityNumber The social security number to set
     */
    public void setSocialSecurityNumber(String socialSecurityNumber)
    {
        this.SocialSecurityNumber = socialSecurityNumber;
    }

    /**
     * Get the first name for the person in this record block
     *
     * @return The first name for the person in this record block
     */
    public String getFirstName()
    {
        return this.FirstName;
    }

    /**
     * Set first name for the person in this record block
     *
     * @firstName The first name for the person in this record block
     */
    public void setFirstName(String firstName)
    {
        this.FirstName = firstName;
    }

    /**
     * Get the last name for the person in this record block
     *
     * @return The last name for the person in this record block
     */
    public String getLastName()
    {
        return LastName;
    }

    /**
     * Set the last name for the person in this record block
     *
     * @lastName The last name for the person in this record block
     */
    public void setLastName(String lastName)
    {
        this.LastName = lastName;
    }

    /**
     * Set the date of birth for the person in this record block
     *
     * @dateOfBirth The date of birth for the person in this record block
     */
    public void setDateOfBirth(String dateOfBirth)
    {
        this.DateOfBirth = dateOfBirth;
    }

    /**
     * Set the diagnosis for the person in this record block
     *
     * @diagnosis The diagnosis for the person in this record block
     */
    public void setDiagnosis(String diagnosis)
    {
        this.Diagnosis = diagnosis;
    }

    /**
     * Set the treatment for the person in this record block
     *
     * @treatment The treatment for the person in this record block
     */
    public void setTreatment(String treatment)
    {
        this.Treatment = treatment;
    }

    /**
     * Set the medication for the person in this record block
     *
     * @medication The medication for the person in this record block
     */
    public void setMedication(String medication)
    {
        this.Medication = medication;
    }
}

/**
 * BlockChain Block object with json serialization
 */
class BlockRecord implements Comparable<BlockRecord>, Comparator<BlockRecord>
{
    @SerializedName (value = "DataBlock")
    private DataBlock DataBlock = new DataBlock();

    @SerializedName( value = "BlockNumber")
    private int BlockNumber  = 0;

    @SerializedName (value = "SHA256HashedDataBlock")
    private String SHA256HashedDataBlock = "";

    @SerializedName (value = "SignedSHA256DataBlock")
    private String SignedSHA256DataBlock = "";

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

    @SerializedName( value = "Seed")
    private String Seed = "";

    public DataBlock getDataBlock()
    {
        return this.DataBlock;
    }

    /**
     * Get the current block number
     * @return The current block number
     */
    public int getBlockNumber()
    {
        return BlockNumber;
    }

    /**
     * Set the current block number
     * @param blockNumber The current block number
     */
    public void setBlockNumber(int blockNumber)
    {
        BlockNumber = blockNumber;
    }

    /**
     * Get the SHA256Sting
     *
     * @return
     */
    public String getSHA256HashedDataBlock()
    {
        return SHA256HashedDataBlock;
    }

    /**
     * Set teh SHA256 String
     *
     * @param sha256String the SHA256HashedDataBlock to set
     */
    public void setSHA256HashedDataBlock(String sha256String)
    {
        this.SHA256HashedDataBlock = sha256String;
    }

    /**
     * Get the signed SHA256 string
     *
     * @return The Signed version of the SHA256 string
     */
    public String getSignedSHA256DataBlock()
    {
        return SignedSHA256DataBlock;
    }

    /**
     * Set the signed version of the SHA256 signed string
     *
     * @param sha256String the signed SHA256 string
     */
    public void setSignedSHA256DataBlock(String sha256String)
    {
        this.SignedSHA256DataBlock = sha256String;
    }

    /**
     * Get the signed previous hash
     *
     * @return The signedPreviousHash
     */
    public String getPreviousHash()
    {
        return this.PreviousHash;
    }

    /**
     * Set the signed previous Hash
     *
     * @param previousHash the signed previous hash
     */
    public void setPreviousHash(String previousHash)
    {
        this.PreviousHash = previousHash;
    }

    /**
     * Get the random seed value from the block
     * @return An int representing the random seed value
     */
    public String getSeed()
    {
        return this.Seed;
    }

    /**
     * Set the Seed value (the value that sovled the puzzle ) for this block
     * @param seed The new seed value
     */
    public void setSeed( String seed)
    {
        this.Seed = seed;
    }

    /**
     * Set the process that created this blocks id
     *
     * @param creatingProcess creating processes id
     */
    public void setCreatingProcess(String creatingProcess)
    {
        this.CreatingProcess = creatingProcess;
    }

    /**
     * Set teh verifying process's id
     *
     * @param verificationProcessId The id of the verifying process
     */
    public void setVerificationProcessID(String verificationProcessId)
    {
        this.VerificationProcessId = verificationProcessId;
    }

    /**
     * Get the block id for this record block
     *
     * @return The block id for this record block
     */
    public String getBlockId()
    {
        return this.BlockId;
    }

    /**
     * Set the block Id for this record block
     *
     * @param blockId the block Id to set
     */
    public void setBlockId(String blockId)
    {
        this.BlockId = blockId;
    }

    /**
     * Get the signed blockId for this block
     *
     * @return The signed blockId for this block
     */
    public String getSignedBlockId()
    {
        return this.SignedBlockId;
    }

    /**
     * Set the signed blockId for this record block
     *
     * @param SignedBlockId The signed block Id for this record block
     */
    public void setSignedBlockId(String SignedBlockId)
    {
        this.SignedBlockId = SignedBlockId;
    }


    /**
     * Comparision override for the block record. Compares the creation date's
     *
     * @param other the BlockRecord to compare against
     * @return An int representing the comparison evaluation
     */
    @Override
    public int compareTo(BlockRecord other)
    {
        return this.CreationTime.compareTo(other.CreationTime);
    }

    @Override
    public int compare(BlockRecord a, BlockRecord b)
    {
        return a.BlockNumber - b.BlockNumber;
    }
}

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
        ArrayList<BlockRecord> blockRecords = new ArrayList<BlockRecord>();
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
                record.getDataBlock().setFirstName(inputData[0]);
                record.getDataBlock().setLastName(inputData[1]);
                record.getDataBlock().setDateOfBirth(inputData[2]);
                record.getDataBlock().setSocialSecurityNumber(inputData[3]);
                record.getDataBlock().setDiagnosis(inputData[4]);
                record.getDataBlock().setTreatment(inputData[5]);
                record.getDataBlock().setMedication(inputData[6]);

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
            System.out.println("\t" + record.getDataBlock().getFirstName() + " " + record.getDataBlock().getLastName());
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
     * Serialize a DataBlock
     *
     * @param dataBlock The list of block records to serialize
     */
    public static String SerializeDataBlock(DataBlock dataBlock)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(dataBlock);
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
     * Deserialize the BlockRecord Ledger
     *
     * @return A deserialized block ledger
     * @param: a serialized block ledger
     */
    public static ArrayList<BlockRecord> DeserializeLedger(String ledgerString)
    {
        Type listType = new TypeToken<ArrayList<BlockRecord>>(){}.getType();
        return new Gson().fromJson(ledgerString, listType);
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

    /**
     * Using the given stringToHash generate a new hash string
     * @param stringToHash The string to get a Hash From
     * @return the Hash of the stringToHash
     */
    public static byte[] GetHash( String stringToHash )
    {
        try
        {
            MessageDigest MD = MessageDigest.getInstance("SHA-256");
            // Get the hash value
            return MD.digest(stringToHash.getBytes("UTF-8"));
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Failed to get hash for string" + stringToHash);
            return null;
        }
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

                if (!isValidBlock(record))
                {
                    continue;
                }

                try
                {
                    // Set the new block number
                    int currentBlockNum = BlockChain.BlockLedger.size() + 1;
                    record.setBlockNumber(currentBlockNum);

                    // Set the verifiers process id
                    record.setVerificationProcessID(Integer.toString(BlockChain.ProcessId));

                    String previousHash;

                    // If this is the dummy block there isnt anything in the ledger yet so create the previousHash from the current block
                    if (!record.getBlockId().equals("0") && BlockChain.BlockLedger.size() != 0)
                    {
                        int previousBlockNum = BlockChain.BlockLedger.size() - 1;
                        //Need to validate that this will produce the previous hash
                        previousHash = BlockChain.BlockLedger.get(previousBlockNum).getPreviousHash();
                    }
                    else
                    {
                        previousHash = DatatypeConverter.printHexBinary(Utilities.GetHash(record.getSHA256HashedDataBlock()));
                    }

                    for (int i = 1; i < 200; i++)
                    {
                        // Generate an alpha numeric string and append it to the current serialized block of data
                        String randString = this.randomAlphaNumeric(10);
                        String concatString = previousHash + randString;

                        //Generate the new Hash
                        String newHash = DatatypeConverter.printHexBinary(Utilities.GetHash(concatString)); // Turn into a string of hex values

                        if (this.IsValidAnswer(newHash))
                        {
                            // The puzzle was solved so update the seed with the answer to the puzzle
                            record.setSeed(randString);

                            // Update the previous hash value with the new hash containing the previoius hash and the current blocks
                            record.setPreviousHash(concatString);
                            break;
                        }

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
                        if (blockExists)
                        {
                            break;
                        }

                    }
                }
                catch (Exception ex)
                {
                    BlockChain.PrintError("Error occurred while doing 'work'", ex);
                }

                if (!blockExists)
                {
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

                    // We made it this far without the puzzle being solved by another process send the new record to be validated and added to the ledger
                    if (!blockExists)
                    {
                        BlockChain.BlockLedger.add(record);
                        this.SendVerifiedBlock();
                    }
                }
            }
        }
        catch (Exception ex)
        {
            BlockChain.PrintError("Error while verifying block", ex);
        }
    }

    /**
     * Validate that the "answer" to the puzzle
     * @param answer A string representing our answer
     * @return A value indicating if this is the answer to the puzzle
     */
    private boolean IsValidAnswer(String answer)
    {
        try
        {
            // Collect only the first 16 bits from the new hash and get the Base 16 representation of it converted into an int
            int workNumber = Integer.parseInt(answer.substring(0, 4), 16);

            // if the work # is less that 20000k we've solved the puzzle
            if (workNumber < 20000)
            {
                return true;
            }
        }
        catch (IndexOutOfBoundsException ex)
        {
            BlockChain.PrintError("Index out of bounds", ex);
            return false;
        }

        return false;
    }

    /**
     * Code Used from WorkA to Generate a random Alpha Numeric String
     * https://condor.depaul.edu/elliott/435/hw/programs/Blockchain/WorkA.java
     * @param seed The number of randomly chosen characters you want to use
     * @return A random Alpha Numeric string
     */
    private String randomAlphaNumeric(int seed)
    {

        String alphaNumeric = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        StringBuilder builder = new StringBuilder();

        // Loop until the seed value is 0
        while (seed-- != 0)
        {
            // Using Randomly choose a characters index from the A String of all Alpha Numeric values
            // repeat until the seed count is 0
            int character = (int)(Math.random() * alphaNumeric.length());
            builder.append(alphaNumeric.charAt(character));
        }

        return builder.toString();
    }

    /**
     * Send an updated ledger to all processes
     */
    public void SendVerifiedBlock()
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

                BlockChain.PrintInformation("Sending updated block ledger");
                out.println(Utilities.SerializeRecord(BlockChain.BlockLedger));

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

    /**
     * Validate the block checking the signed blockId, the signed data block, and the previous hash
     * @param record The record block to validate
     * @return
     */
    private boolean isValidBlock(BlockRecord record)
    {
        // Get the signed block id to verify it
        try
        {
            byte[] signedBlockId = Base64.getDecoder().decode(record.getSignedBlockId());
            // Validate the signature of the signed block id if it's invalid dont bother processing it
            if (!Utilities.GetKeyManager().VerifySignature(record.getBlockId().getBytes(), signedBlockId))
            {
                BlockChain.PrintError("Record's blockId has an been signed by an invalid private key");
                return false;
            }
        }
        catch (IllegalArgumentException ex)
        {
            BlockChain.PrintError("Failed to base64 decode the signed block id", ex);
            return false;
        }

        // Get the signed data block to verify it
        try
        {
            byte[] signedDataBlock = Base64.getDecoder().decode(record.getSignedSHA256DataBlock());

            // Validate the signature of the signed data id if it's invalid dont bother processing it
            if (!Utilities.GetKeyManager().VerifySignature(record.getSHA256HashedDataBlock().getBytes(), signedDataBlock))
            {
                BlockChain.PrintError("Record's data block has an been signed by an invalid private key");
                //return false;
            }
        }
        catch (IllegalArgumentException ex)
        {
            BlockChain.PrintError("Failed to base64 decode the signed dat block", ex);
            return false;
        }

        return true;
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

                // Get the new block, sign the SHA256 string and the blockId with the private key, and send the block out to the unverified block process
                BlockRecord blockToSend = Utilities.DeserializeRecord(recordBlock);

                // Generate a hash of the data block
                byte[] blockHash = Utilities.GetHash(Utilities.SerializeDataBlock(blockToSend.getDataBlock()));

                // This code was taken from Blockh.java @ https://condor.depaul.edu/elliott/435/hw/programs/Blockchain/BlockH.java
                // I actually had this working without building the hex string and then when I switched to using more complex data in the hash it broke everything and
                // i ran out of time to figure out why
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < blockHash.length; i++)
                {
                    sb.append(Integer.toString((blockHash[i] & 0xff) + 0x100, 16).substring(1));
                }

                String SHA256String = sb.toString();

                blockToSend.setSHA256HashedDataBlock(SHA256String);

                // Sign the data block and base 64 encode it
                byte[] signedDataBlock = this.keyManager.SignData(blockToSend.getSHA256HashedDataBlock().getBytes("UTF-8"));

                blockToSend.setSignedSHA256DataBlock(Base64.getEncoder().encodeToString(signedDataBlock));

                // Sign the block and then base 64 encode it
                byte[] signedBlockId = this.keyManager.SignData(blockToSend.getBlockId().getBytes("UTF-8"));
                blockToSend.setSignedBlockId(Base64.getEncoder().encodeToString(signedBlockId));

                int[] unverifiedBlockPorts = Ports.getUnverifiedBlockServerPortsInUse();
                // send the generated block to each process
                for (int i = 0; i < unverifiedBlockPorts.length; i++)
                {
                    if( blockToSend.getBlockId().equals("0"))
                    {
                        BlockChain.PrintInformation("Sending DummyBlock Block to Process " + i);
                    }
                    else
                    {
                        BlockChain.PrintInformation("Sending Unverified Block to Process " + i);
                    }

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
    private static final Lock serializeLock = new ReentrantLock();
    private static boolean IsSerializing = false;

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
                // Listen for a new ledger to come in
                String newLedger = "";
                String incomingBlock;
                while ((incomingBlock = in.readLine()) != null)
                {
                    newLedger += incomingBlock;
                }

                BlockChain.BlockLedger = Utilities.DeserializeLedger(newLedger);

                if (BlockChain.ProcessId == 0)
                {
                    this.ExportLedger();
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
            String serializedBlock;
            serializeLock.lock();
            try
            {
                serializedBlock = this.SerializeRecord(BlockChain.BlockLedger);
            }
            finally
            {
                serializeLock.unlock();
            }

            try
            {
                BlockChain.PrintError("Current Ledger Size: " + BlockChain.BlockLedger.size());
                //BlockChain.PrintInformation("--NEW BLOCKCHAIN--\n" + serializedBlock);

                bw = new BufferedWriter(new FileWriter("BlockChainLedger.json", false));
                bw.write(serializedBlock);
                bw.flush();
            }
            catch (IOException ex)
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

    /**
     * Serialize a list of block records
     *
     * @param blockRecords The list of block records to serialize
     */
    private String SerializeRecord(ArrayList<BlockRecord> blockRecords)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(blockRecords);
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
    public static final boolean DEBUGMODE = true;
    public static final int ProcessCount = 3;
    public static final int PID = 0;
    public static final String ServerName = "localhost";
    public static final BlockingQueue<BlockRecord> Queue = new PriorityBlockingQueue<BlockRecord>();
    public static ArrayList<BlockRecord> BlockLedger = new ArrayList<BlockRecord>();
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

            // A little extra sleep to make sure the key is set up
            Thread.sleep(1000);
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
     *
     * @param logString The string to log
     */
    public static void PrintInformation(String logString)
    {
        System.out.println("Process " + BlockChain.ProcessId + ": " + logString);
    }

    /**
     * Writes the error string and exception to System.err
     *
     * @param logString the log line
     * @param ex        The exception
     */
    public static void PrintError(String logString, Exception ex)
    {
        String errorStr = "*********************ERROR********************\n";
        errorStr += "Process " + BlockChain.ProcessId + ": " + logString + " with exception: " + ex + "\n";
        System.err.println(errorStr);
        ex.printStackTrace();
        System.err.println("*********************ERROR********************");
    }

    /**
     * Writes the error string and exception to System.err
     *
     * @param logString the log line
     */
    public static void PrintError(String logString)
    {
        if (BlockChain.DEBUGMODE)
        {
            String errorStr = "*********************ERROR********************\n";
            errorStr += "Process " + BlockChain.ProcessId + ": " + logString + "\n";
            errorStr += "*********************ERROR********************";
            System.err.println(errorStr);
        }

    }
}



