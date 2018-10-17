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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Class representing the ports used for each process
 */
class Ports
{
    private int KeyServerPortBase = 4710;
    private int UnverifiedBlockServerPortBase = 4820;
    private int BlockChainServerPortBase = 4930;

    private int KeyServerPort;
    private int UnverifiedBlockServerPort;
    private int BlockChainServerPort;

    /**
     * Set the ports for each of the process types by incrementing the processId to the base value
     *
     * @param processId the process id
     */
    public void setPorts(int processId)
    {
        this.KeyServerPort = this.KeyServerPortBase + processId;
        this.UnverifiedBlockServerPort = this.UnverifiedBlockServerPortBase + processId;
        this.BlockChainServerPort = this.BlockChainServerPortBase + processId;
    }

    /**
     * Get teh KeyServerPort
     *
     * @return The Key Server Port
     */
    public int getKeyServerPort()
    {
        return KeyServerPort;
    }

    /**
     * Get the UnverifiedBlockServerPort
     *
     * @return The Unverified Block Server Port
     */
    public int getUnverifiedBlockServerPort()
    {
        return UnverifiedBlockServerPort;
    }

    /**
     * Get teh BlockChain Port
     *
     * @return The BlockChain port
     */
    public int getBlockChainServerPort()
    {
        return BlockChainServerPort;
    }
}

/**
 * BlockChain Block object with json serialization
 */
class BlockRecord
{
    @SerializedName (value = "SHA256String")
    private String SHA256String;

    @SerializedName (value = "SignedSHA256")
    private String SignedSHA256;

    @SerializedName (value = "BlockId")
    private String BlockId;

    @SerializedName (value = "VerificationProcessId")
    private String VerificationProcessId;

    @SerializedName (value = "CreatingProcess")
    private String CreatingProcess;

    @SerializedName (value = "PreviousHash")
    private String PreviousHash;

    @SerializedName (value = "FirstName")
    private String FirstName;

    @SerializedName (value = "LastName")
    private String LastName;

    @SerializedName (value = "SSN", alternate = {"SocialSecurityNumber"})
    private String SocialSecurityNumber;

    @SerializedName (value = "DOB", alternate = {"DateOfBirth"})
    private String DateOfBirth;

    @SerializedName (value = "Diagnosis")
    private String Diagnosis;

    @SerializedName (value = "Treatment")
    private String Treatment;

    @SerializedName (value = "Medication")
    private String Medication;


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
                if (BlockChain.Blockchain.indexOf(data.substring(1, 9)) < 0)
                {
                    // Crude, but excludes most duplicates.
                    fakeVerifiedBlock = "[" + data + " verified by P" + BlockChain.PID + " at time " + Integer.toString(ThreadLocalRandom.current().nextInt(100, 1000)) + "]\n";
                    System.out.println(fakeVerifiedBlock);
                    String tempChain = fakeVerifiedBlock + BlockChain.Blockchain; // add the verified block to the chain
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
 * The Public Key worker class
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
        System.out.println("Block Chain Client Connected");

        BufferedReader in = null;
        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                String data = in.readLine();
                System.out.println("Got key: " + data);
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
    public static final String ServerName = "localhost";
    public static final int ProcessCount = 3;
    public static final int PID = 0;
    public static String Blockchain = "[First block]";
    private static final int WaitTimeInMSec = 1000;

    /**
     * The main entry point of the block chain program
     *
     * @param args The arguments
     */
    public static void main(String args[])
    {
        int processId = 0;
        String inputFileName;

        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("Usage: java BlockChain [ProcessNumber]");
            System.out.println("Missing ProcessNumber parameter so defaulting to 0\n");
            System.out.println("-------------------------------------------------------\n");
        }
        else
        {
            processId = Integer.parseInt(args[0]);
        }

        switch (processId)
        {
            case 1:
            {
                inputFileName = "BlockInput1.txt";
                break;
            }
            case 2:
            {
                inputFileName = "BlockInput2.txt";
                break;
            }
            default:
            {
                inputFileName = "BlockInput0.txt";
                break;
            }
        }

        // Create the ports object to store the port information per process
        Ports ports = new Ports();
        ports.setPorts(processId);

        // Create the queue that will be used to hold unverified blocks
        final BlockingQueue<String> queue = new PriorityBlockingQueue<>();

        System.out.println("Process number: " + processId + " Ports:");
        System.out.println("\t\t Public Keys Port: " + ports.getKeyServerPort());
        System.out.println("\t\t UnverifiedBlocksPort: " + ports.getUnverifiedBlockServerPort());
        System.out.println("\t\t BlockChainPort: " + ports.getBlockChainServerPort());

        System.out.println("\nUsing input file: " + inputFileName + "\n");
        ReadInputFile(inputFileName, processId);

        // Start the block chain, unverified blocks, and public keys servers
        try
        {
            new Thread(new BlockChainThread(ports.getBlockChainServerPort())).start();
            new Thread(new PublicKeyThread(ports.getKeyServerPort())).start();
            new Thread(new UnverifiedBlockThread(ports.getUnverifiedBlockServerPort(), queue)).start();

        }
        catch (Exception e)
        {
            System.out.println("Failed to create client admin thread with exception: " + e);
        }

        // Wait for servers to start.
        try
        {
            Thread.sleep(WaitTimeInMSec);
        }
        catch (Exception e)
        {
            System.out.println("Error while sleeping");
        }

        // Wait for multicast to fill incoming queue for our example.
        MultiCast(ports); //
        try
        {
            Thread.sleep(WaitTimeInMSec);
        }
        catch (Exception e)
        {
        }

        new Thread(new UnverifiedBlockConsumer(ports.getBlockChainServerPort(), queue)).start(); // Start consuming the queued-up unverified blocks
    }

    private static void MultiCast(Ports ports)
    {
        // Multicast some data to each of the processes.
        Socket sock;
        PrintStream toServer;

        try
        {
            for (int i = 0; i < BlockChain.ProcessCount; i++)
            {
                // Send our key to all servers.
                sock = new Socket(ServerName, ports.getKeyServerPort());
                toServer = new PrintStream(sock.getOutputStream());
                toServer.println("FakeKeyProcess" + BlockChain.PID);
                toServer.flush();
                sock.close();
            }

            Thread.sleep(1000); // wait for keys to settle, normally would wait for an ack
            //Fancy arithmetic is just to generate identifiable blockIDs out of numerical sort order:
            String fakeBlockA = "(Block#" + Integer.toString(((BlockChain.PID + 1) * 10) + 4) + " from P" + BlockChain.PID + ")";
            String fakeBlockB = "(Block#" + Integer.toString(((BlockChain.PID + 1) * 10) + 3) + " from P" + BlockChain.PID + ")";

            sock = new Socket(BlockChain.ServerName, ports.getUnverifiedBlockServerPort());
            toServer = new PrintStream(sock.getOutputStream());

            // Send a sample unverified block A to each server
            for (int i = 0; i < BlockChain.ProcessCount; i++)
            {
                toServer.println(fakeBlockA);
                toServer.flush();
                sock.close();
            }

            // Send a sample unverified block B to each server
            for (int i = 0; i < BlockChain.ProcessCount; i++)
            {
                toServer.println(fakeBlockB);
                toServer.flush();
                sock.close();
            }
        }
        catch (Exception x)
        {
            x.printStackTrace();
        }
    }

    private static void ReadInputFile(String fileName, int processId)
    {
        ArrayList<BlockRecord> blockRecords = new ArrayList();

        // read through the input file setting each of the BlockRecord fields
        try (BufferedReader br = new BufferedReader(new FileReader(fileName)))
        {

            String inputLine;
            while ((inputLine = br.readLine()) != null)
            {
                BlockRecord record = new BlockRecord();
                record.setSHA256String("SHA string goes here...");
                record.setSignedSHA256("Signed SHA string goes here...");
                record.setBlockID(new String(UUID.randomUUID().toString()));
                record.setCreatingProcess("Process" + Integer.toString(processId));
                record.setVerificationProcessID("To be set later...");

                // split the input line by 1 or more spaces into a delimited array
                String[] inputData = inputLine.split(" +");

                // every input file has the same ordering on the data points so we can hard code their index's
                record.setSSNum(inputData[0]);
                record.setFirstName(inputData[1]);
                record.setLastName(inputData[2]);
                record.setDateOfBirth(inputData[3]);
                record.setDiagnosis(inputData[4]);
                record.setTreatment(inputData[5]);
                record.setMedication(inputData[6]);
                blockRecords.add(record);
            }
        }
        catch (IOException ex)
        {
            System.out.println("Error reading input text file" + ex);
        }

        // Print out all the names from the records collected
        System.out.println(blockRecords.size() + " records read.");
        System.out.println("Names from input:");
        for (BlockRecord record : blockRecords)
        {
            System.out.println("\t" + record.getFirstName() + " " + record.getLastName());
        }
        System.out.println("\n");

        Print(blockRecords);
    }

    /**
     * Auzillary method to print out the block records
     *
     * @param blockRecords The block records to print
     */
    private static void Print(ArrayList<BlockRecord> blockRecords)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        System.out.println(gson.toJson(blockRecords));
    }
}



