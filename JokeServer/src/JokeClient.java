/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181

     Command-Line Examples:
     Usage: java JokeClient [server address] [server address]

           Connect to 1 server only
           java JokeClient localhost

           NOTE: Not implemented yet
           Start to 2 servers
           java jokeClient localhost localhost

    Instructions:
    To Compile:
    javac JokeClient.java
----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * The Joke Server client
 */
public class JokeClient
{
    /**
     * The main entry point of the JokeClient
     * @param args The command Line Arguments
     */
    public static void main(String args[])
    {
        if (args.length == 0)
        {
            System.out.println("\n-----------------------------------------------------------------------");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient [server address] [secondary server address]");
            System.out.println("--------------------------------------------------------------------------\n");
        }

        // The default server address is localhost
        String serverAddress = "localhost";

        // Check if there is a command line argument supplied specifying the server address
        if (args.length == 1)
        {
            serverAddress = args[0];
        }

        try
        {
            // create the primary client thread to listen on port 4545
            JokeClientThread primaryClientThread = new JokeClientThread(4545, serverAddress);
            Thread aThread = new Thread(primaryClientThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create primary client thread. Exception: " + e);
        }

        // Check if we want to start a secondary server connection
        if (args.length == 2)
        {
            serverAddress = args[1];
        }

        /*try
        {
            // create a secondary client thread to listen on port 5050
            JokeClientThread secondaryJokeClientThread = new JokeClientThread(4546, serverAddress);
            Thread aThread = new Thread(secondaryJokeClientThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create second client thread. Exception: " + e);
        }*/
    }
}

/**
 * The Joke Client thread to start up instances of the joke client
 */
class JokeClientThread implements Runnable
{
    private int port;
    private String serverAddress;

    /**
     * Creates an instance of the JokeClientThread
     *
     * @param port          The port to use when starting the worker
     * @param serverAddress The server address to use when starting the worker
     */
    JokeClientThread(int port, String serverAddress)
    {
        this.port = port;
        this.serverAddress = serverAddress;
    }

    public void run()
    {
        try
        {
            new JokeClientWorker(this.port, this.serverAddress).start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to start client admin worker for server " + this.serverAddress + ". Exception: " + e);
        }
    }
}

/**
 * The Joke Client Worker
 */
class JokeClientWorker extends Thread
{
    private int port;
    private String serverAddress;
    private String userToken;
    private Socket client;

    /**
     * Joke Client Worker Constructor
     * @param port The port to open a connection on
     * @param serverAddress The server address to open a connection to
     */
    JokeClientWorker(int port, String serverAddress)
    {

        this.port = port;
        this.serverAddress = serverAddress;
    }

    /**
     * The threads run method
     */
    public void run()
    {
        System.out.println("Tommy Leedberg's Joke Server Client, 1.8.\n");
        System.out.println(String.format("Using server: " + this.serverAddress + ", Port: %s\n", this.port));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            // Ask for the user credentials
            generateUserToken(in);

            String request;
            while (!(request = in.readLine()).contains("quit"))
            {
                // The server only accepts 1 command "GET" so we aren't worried about the input
                writeServerRequest("GET");
            }
            System.out.println("Cancelled by user request.");
        }
        catch (IOException e)
        {
            System.out.println("Error getting user input. Exception: " + e);
        }
    }

    /**
     * Generate a user token( in this case it's a simple user name )
     * @param in The buffered reader's input stream
     */
    private void generateUserToken(BufferedReader in)
    {
        try
        {
            System.out.println("What is your username?");
            System.out.flush();
            this.userToken = in.readLine();

            if (this.userToken.length() == 0)
            {
                System.out.println("You must enter a valid username.");
                this.generateUserToken(in);
            }
        }
        catch (IOException e)
        {
            System.out.println("Error reading username. Exception: " + e);
        }
    }

    /**
     * Sends the command to the joke server
     * @param commandString The command to send to the Joke Server
     */
    private void writeServerRequest(String commandString)
    {
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try
        {
            // Open a connection to server, if you can't let the user know and wait for futher input
            if (!openConnection())
            {
                System.out.println("Waiting on Server Connection...");
                return;
            }

            ServerRequest request = new ServerRequest(commandString, this.userToken);

            // create an output stream on the specified socket
            toServer = new PrintStream(this.client.getOutputStream());
            // create an input stream on the specified socket
            fromServer = new BufferedReader(new InputStreamReader(this.client.getInputStream()));

            // Send the joke server the command
            toServer.println(request.toString());
            toServer.flush();

            // read in and then print out the response from the server
            while ((textFromServer = fromServer.readLine()) != null && textFromServer.length() != 0)
            {
                System.out.println(textFromServer);
            }
        }
        catch (IOException e)
        {
            System.out.println("Socket error. Exception: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Open a new connection to the server on the specified port
     * due to limitations on files code is duplicated
     *
     * @return A value indicating whether or not a connection was able to be made
     */
    private Boolean openConnection()
    {
        try
        {
            this.client = new Socket(this.serverAddress, this.port);
            return true;
        }
        catch (UnknownHostException e)
        {
            System.out.println("Invalid Host. Exception " + e);
            return false;
        }
        catch (IOException e)
        {
            System.out.println( "Failed to connect to socket. Exception: " + e);
            return false;
        }
    }
}

