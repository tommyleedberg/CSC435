/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java JokeClient -p [port to open] -h [host name]
    Note: If no port is selected it uses a default of 1565

    Instructions:
----------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;

/**
 * The Joke Server client
 */
public class JokeClient
{
    private static ArrayList<String> jokes = new ArrayList<>();
    private static ArrayList<String> proverbs = new ArrayList<>();
    private static String UserToken;
    private static String ServerAddress;
    private static int Port;
    private static Socket Client;

    public static void main(String args[])
    {

        // set the default server address and port
        ServerAddress = "localhost";
        Port = 1565;

        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("No port specified so using default port: 1565");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient -p [port to open] -h [host name]");
            System.out.println("-------------------------------------------------------\n");
        }

        // look for the CL params for the port or hostname
        for (int i = 0; i < args.length; ++i)
        {
            if (args[i].contains("-p"))
            {
                Port = Integer.parseInt(args[i + 1]);
            }

            if (args[i].contains("-h"))
            {
                ServerAddress = args[i + 1];
            }
        }

        System.out.println("Tommy Leedberg's Joke Server Client, 1.8.\n");
        System.out.println(String.format("Using server: " + ServerAddress + ", Port: %s\n", Port));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            generateUserToken(in);
            String request = "";
            while (!request.contains("quit"))
            {
                System.out.print("Enter a hostname or an IP address to get from the server, (quit) to end: ");
                System.out.flush();
                name = in.readLine();
                if (name.indexOf("quit") < 0)
                {
                    writeServerRequest(request);
                }
            }
            System.out.println("Cancelled by user request.");
        }
        catch (IOException x)
        {
            x.printStackTrace();
        }
    }

    /**
     * Writes a request to the remote address
     *
     * @param name
     * @param serverName
     */
    private static void writeServerRequest()
    {
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try
        {
            // Open a connection to server
            socket = new Socket(ServerName, port);

            // Open an I/O pipe with the socket
            fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            toServer = new PrintStream(socket.getOutputStream());

            // Send machine name or IP address to server
            toServer.println(name);
            toServer.flush();

            // read in and then print out the response from the server
            while ((textFromServer = fromServer.readLine()) != null && textFromServer.length() != 0)
            {
                System.out.println(textFromServer);
            }
            socket.close();
        }
        catch (IOException x)
        {
            System.out.println("Socket error.");
            x.printStackTrace();
        }
    }

    private static ServerRequest generateRequest(String requestMessage)
    {
        ServerRequest request = new ServerRequest();
        request.userId = UserToken;
        request.clientRequest = requestMessage;

        return request;
    }

    private static void generateUserToken( BufferedReader in)
    {
        try
        {
            System.out.println( "What is your username?");
            System.out.flush();
            String userToken = in.readLine();

            System.out.println( "What is your password?");
            System.out.flush();
            userToken = userToken + in.readLine();

            UserToken = Base64.getEncoder().encodeToString(userToken.getBytes());
        }
        catch (IOException ex)
        {
            System.out.println("Error reading username. Exception: " + ex);
        }
    }

    /**
     * Open a new connection on the requested server address and port
     * due to limitations on files code is duplicated
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

