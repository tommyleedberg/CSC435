/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181
    Command-Line Examples/Instructions:

    Command-Line Examples:
    Usage: java JokeClientAdmin [server address] [server address]

           Connect to 1 server only
           java JokeClientAdmin localhost

           NOTE: Not implemented yet
           Start to 2 servers
           java jokeClientAdmin localhost localhost

    Instructions:
    To Compile:
    javac JokeClientAdmin.java

    To change mode enter either JOKE_MODE or PROVERB_MODE
    depending on the mode you want to switch to
----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * The Joke Client Admin
 */
public class JokeClientAdmin
{
    public static void main(String args[])
    {
        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient -h [host name]");
            System.out.println("-------------------------------------------------------\n");
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
            // create the primary admin client thread to listen on port 5050
            AdminClientThread primaryAdminClientThread = new AdminClientThread(5050, serverAddress);
            Thread aThread = new Thread(primaryAdminClientThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create primary admin client thread. Exception: " + e);
        }

        // Check if we want to start a secondary server connection
        if (args.length == 2)
        {
            serverAddress = args[1];
        }

        try
        {
            // create a secondary admin client thread to listen on port 5051
            AdminClientThread secondaryAdminClientThread = new AdminClientThread(5051, serverAddress);
            Thread aThread = new Thread(secondaryAdminClientThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create second admin client thread. Exception: " + e);
        }
    }
}

/**
 * The admin client thread to start instances of the admin client
 */
class  AdminClientThread implements Runnable
{
    private int port;
    private String serverAddress;

    /**
     * Creates an instance of the AdminClientThread
     *
     * @param port          The port to use when starting the worker
     * @param serverAddress The server address to use when starting the worker
     */
    AdminClientThread(int port, String serverAddress)
    {
        this.port = port;
        this.serverAddress = serverAddress;
    }

    public void run()
    {
        try
        {
            new AdminClientWorker(this.port, this.serverAddress).start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to start client admin worker for server " + this.serverAddress + ". Exception: " + e);
        }
    }
}

/**
 * The Admin Client worker
 */
class AdminClientWorker extends Thread
{
    private int port;
    private String serverAddress;
    private Socket client;

    /**
     * Joke Client Worker Constructor
     * @param port The port to open a connection on
     * @param serverAddress The server address to open a connection to
     */
    AdminClientWorker(int port, String serverAddress)
    {

        this.port = port;
        this.serverAddress = serverAddress;
    }

    /**
     * The threads run method
     */
    public void run()
    {
        System.out.println("Tommy Leedberg's Joke Server Admin Client, 1.8.");
        System.out.println(String.format("Using server: " + this.serverAddress + ", Port: %s\n", this.port));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            String command = "";
            while (!command.contains("quit"))
            {
                System.out.print("What mode would you like to put the JokeServer in? (JOKE_MODE, PROVERB_MODE)");
                System.out.flush();
                command = in.readLine();

                // If the connection isn't available yet do not try to send a message.
                if (!openConnection())
                {
                    System.out.println("Waiting on Server Connection...");
                    continue;
                }

                writeServerRequest(command);
                System.out.flush();
                this.client.close();
            }

            System.out.println("Connection cancelled by user request.");

        }
        catch (IOException x)
        {
            x.printStackTrace();
        }
    }

    /**
     * Sends the admin command to the Joke Server
     * @param command The command to send
     */
    private void writeServerRequest(String command)
    {
        PrintStream toServer;
        BufferedReader fromServer;
        String textFromServer;

        try
        {
            ServerRequest request = new ServerRequest();
            request.clientRequest = command;

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
            System.out.println("Error writing error. Exception: " + e);
            e.printStackTrace();
        }
        catch (Exception e)
        {
            System.out.println("Socket error. Exception: " + e);
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