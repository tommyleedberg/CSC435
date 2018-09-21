/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181

     Command-Line Examples:
     Usage: java JokeClient [server address] [server address]

           Connect to 1 server only
           java JokeClientlocalhost

           NOTE: Not implemented yet
           Start to 2 servers
           java jokeClient localhost localhost

    Instructions:
    javac JokeClient
----------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

/**
 * The Joke Server client
 */
public class JokeClient
{
     static String PrimaryServerAddress = "localhost";
    static int PrimaryPort = 4545;
    static String SecondaryServerAddress = "localhost";
    static int SecondaryPort = 4546;


    public static void main(String args[])
    {
        if (args.length == 0)
        {
            System.out.println("\n-----------------------------------------------------------------------");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient [server address] [secondary server address]");
            System.out.println("--------------------------------------------------------------------------\n");
        }

        // Check if there were command line arguments supplied specifying the server address
        if (args.length == 1)
        {
            PrimaryServerAddress = args[0];
        }

        try
        {
            // create a new admin client thread to listen on port 5050
            PrimaryJokeClient primaryClientThread = new PrimaryJokeClient();
            Thread aThread = new Thread(primaryClientThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create second client thread with exception: " + e);
        }

    }
}

class PrimaryJokeClient implements Runnable
{
    public void run()
    {
        try
        {
            new JokeClientWorker(JokeClient.PrimaryPort, JokeClient.PrimaryServerAddress).start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to start client admin worker with exception: " + e);
        }
    }
}

class SecondaryJokeClient implements Runnable
{
    public void run()
    {
        try
        {
            new JokeClientWorker(JokeClient.SecondaryPort, JokeClient.SecondaryServerAddress).start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to start client admin worker with exception: " + e);
        }
    }
}

class JokeClientWorker extends Thread
{
    private int port;
    private String serverAddress;
    private String userToken;
    private Socket client;

    JokeClientWorker(int port, String serverAddress)
    {

        this.port = port;
        this.serverAddress = serverAddress;
    }

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
                writeServerRequest( "GET");
            }
            System.out.println("Cancelled by user request.");
        }
        catch (IOException e)
        {
            System.out.println("Error getting user input. Exception: " + e);
        }
    }

    private  void generateUserToken( BufferedReader in)
    {
        try
        {
            System.out.println( "What is your username?");
            System.out.flush();
            this.userToken = in.readLine();
            if(this.userToken.length() == 0)
            {
                System.out.println( "You must enter a valid username");
                this.generateUserToken(in);
            }
        }
        catch (IOException ex)
        {
            System.out.println("Error reading username. Exception: " + ex);
        }
    }

    /**
     * Writes a request to the remote address
     */
    private void writeServerRequest(String userRequest)
    {
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try
        {
            // Open a connection to server
            if( !openConnection() )
            {
                System.out.println("Waiting on Server Connection...");
                return;
            }

            ServerRequest request = new ServerRequest( userRequest, this.userToken );

            // create an output stream on the specified socket
            toServer = new PrintStream(this.client.getOutputStream());
            // create an input stream on the specified socket
            fromServer = new BufferedReader( new InputStreamReader(this.client.getInputStream()));

            // Send the joke server the command
            toServer.println(request.toString());
            toServer.flush();

            // read in and then print out the response from the server
            while ((textFromServer = fromServer.readLine()) != null && textFromServer.length() != 0)
            {
                System.out.println(textFromServer);
            }
        }
        catch (IOException x)
        {
            System.out.println("Socket error.");
            x.printStackTrace();
        }
    }
    /**
     * Open a new connection on the requested server address and port
     * due to limitations on files code is duplicated
     * @return
     */
    private  Boolean openConnection()
    {
        try
        {
            this.client = new Socket(this.serverAddress, this.port);
            return true;
        }
        catch (UnknownHostException ex)
        {
            System.out.println("Invalid Host exception " + ex);
            return false;
        }
        catch (IOException ex)
        {
            return false;
        }
    }
}

