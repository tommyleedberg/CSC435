/**
 * InetClient.java
 */

import java.io.*;
import java.net.*;

/**
 * Inet Client class to connect to an inet server
 */
public class InetClient
{
    public static void main(String args[])
    {
        // get the server name
        String serverName = "localhost";
        int port = 1565;

        if( args.length == 0)
        {
            System.out.println("No port specified so using default port: 1565");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java InetClient -p [port to open] -h [host name]");
        }

        // look for the CL params for the port or hostname
        for(int i = 0; i < args.length; ++i)
        {
            if( args[i].contains("-p"))
            {
                port = Integer.parseInt(args[i + 1]);
            }

            if( args[i].contains("-h"))
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
            while( !name.contains("quit"))
            {
                System.out.print("Enter a hostname or an IP address to get from the server, (quit) to end: ");
                System.out.flush();
                name = in.readLine();
                if (name.indexOf("quit") < 0)
                {
                    getRemoteAddress(name, serverName, port);
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
     * Get the remote address
     * @param name
     * @param serverName
     */
    private static void getRemoteAddress(String name, String serverName, int port)
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

            // Send the machine name or IP address to server for lookup
            toServer.println(name);
            toServer.flush();

            // read in and then print out the response from the server
            while((textFromServer = fromServer.readLine()) != null && textFromServer.length() != 0)
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