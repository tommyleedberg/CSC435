/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181
    Command-Line Examples/Instructions:

    Command-Line Examples:
    Usage: java JokeClient -h [host name]
    Note: If no Port is selected it uses a default of 1565

    Instructions:
----------------------------------------------------------*/
import com.sun.security.ntlm.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class JokeClientAdmin
{
    private static final int Port = 5050;
    private static String ServerAddress;
    private static Socket Client;

    public static void main(String args[])
    {
        // get the server name
        ServerAddress = "localhost";

        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient -h [host name]");
            System.out.println("-------------------------------------------------------\n");
        }

        // look for the CL params for the Port or hostname
        for (int i = 0; i < args.length; ++i)
        {
            if (args[i].contains("-h"))
            {
                ServerAddress = args[i + 1];
            }
        }

        System.out.println("Tommy Leedberg's Joke Server Admin Client, 1.8.");
        System.out.println(String.format("Using server: " + ServerAddress + ", Port: %s\n", Port));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            String command = "";
            while (!command.contains("quit"))
            {
                boolean isConnected = false;

                System.out.print("What mode would you like to put the JokeServer in? (JOKE_MODE, PROVERB_MODE)");
                System.out.flush();
                command = in.readLine();

                // If the connection isn't available yet try to connect again.
                // Wrapped in a conditional to avoid excessive console output
                if(!isConnected)
                {
                    isConnected = openConnection();
                    if( !isConnected )
                    {
                        System.out.println("Waiting on Server Connection...");
                        continue;
                    }
                }

                writeServerRequest(command);
                System.out.flush();
            }
            Client.close();
            System.out.println("Cancelled by user request.");

        }
        catch (IOException x)
        {
            x.printStackTrace();
        }
    }

    /**
     * Writes a request to the remote address
     */
    private static void writeServerRequest(String request)
    {
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try
        {
            // Open an I/O pipe with the socket
            fromServer = new BufferedReader(new InputStreamReader(Client.getInputStream()));
            toServer = new PrintStream(Client.getOutputStream());

            // Send the joke server the command
            toServer.println(request);
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
     * @return
     */
    private static Boolean openConnection()
    {
        try
        {
            Client = new Socket(ServerAddress, Port);
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
