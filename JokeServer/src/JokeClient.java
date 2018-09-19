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
import java.util.ArrayList;

/**
 * The Joke Server client
 */
public class JokeClient
{
    private static ArrayList<String> jokes = new ArrayList<>();
    private static ArrayList<String> proverbs = new ArrayList<>();
    private String userToken;

    public static void main(String args[])
    {
        // get the server name
        String serverName = "localhost";
        int port = 1565;

        if (args.length == 0)
        {
            System.out.println("No port specified so using default port: 1565");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient -p [port to open] -h [host name]");
        }

        // look for the CL params for the port or hostname
        for (int i = 0; i < args.length; ++i)
        {
            if (args[i].contains("-p"))
            {
                port = Integer.parseInt(args[i + 1]);
            }

            if (args[i].contains("-h"))
            {
                serverName = args[i + 1];
            }
        }

        System.out.println("Tommy Leedberg's Inet Client, 1.8.\n");
        System.out.println(String.format("Using server: " + serverName + ", Port: %s\n", port));
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        try
        {
            String name = "";
            while (!name.contains("quit"))
            {
                System.out.print("Enter a hostname or an IP address to get from the server, (quit) to end: ");
                System.out.flush();
                name = in.readLine();
                if (name.indexOf("quit") < 0)
                {
                    writeServerRequest(name, serverName, port);
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
    private static void writeServerRequest(String name, String serverName, int port)
    {
        Socket socket;
        BufferedReader fromServer;
        PrintStream toServer;
        String textFromServer;

        try
        {
            // Open a connection to server
            socket = new Socket(serverName, port);

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
}
