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
    To compile javac JokeClientAdmin

    To change mode enter either JOKE_MODE or PROVERB_MODE
    depending on the mode you want to switch to
----------------------------------------------------------*/
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class JokeClientAdmin
{
    //The default port is 5050 or 5051 when two servers are running
    private static final int Port = 5050;
    private static final int SecondaryServerPort = 5051;
    private static String ServerAddress = "localHost";
    private static String SecondaryServerAddress = "localHost";
    private static Socket Client;

    public static void main(String args[])
    {
        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("No hostname specified so using default: localhost");
            System.out.println("Usage: java JokeClient -h [host name]");
            System.out.println("-------------------------------------------------------\n");
        }

        // Check if there were command line arguments supplied specifying the server address
        if (args.length == 1)
        {
            ServerAddress = args[0];
        }
        else if(args.length == 2)
        {
            ServerAddress = args[0];
            SecondaryServerAddress = args[1];
        }

        System.out.println("Tommy Leedberg's Joke Server Admin Client, 1.8.");
        System.out.println(String.format("Using server: " + ServerAddress + ", Port: %s\n", Port));
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
                if(!openConnection())
                {
                    System.out.println("Waiting on Server Connection...");
                    continue;
                }

                writeServerRequest(command);
                System.out.flush();
                Client.close();
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
     */
    private static void writeServerRequest(String command)
    {
        PrintStream toServer;
        BufferedReader fromServer;
        String textFromServer;

        try
        {
            ServerRequest request = new ServerRequest();
            request.clientRequest = command;

            // create an output stream on the specified socket
            toServer = new PrintStream(Client.getOutputStream());
            // create an input stream on the specified socket
            fromServer = new BufferedReader( new InputStreamReader(Client.getInputStream()));

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
        catch( Exception e)
        {
            System.out.println( "Socket error.");
        }
    }

    /**
     * Open a new connection on the requested server address and port, normally this would be in a common lib shared
     * by the clients that need it but due to the file name restrictions of the assignment it has to be duplicated code
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
