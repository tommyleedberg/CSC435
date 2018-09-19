/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java JokeServer -p [port to open]
    Note: If no port is selected it uses a default of 1565

    Instructions:
----------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

enum ServerModes
{
    JOKES,
    PROVERBS
}

/**
 * ClientWorker thread class
 */
class ClientWorker extends Thread
{
    public static ServerModes ServerMode = ServerModes.JOKES;
    private static Map<Integer, String> Jokes = new HashMap<>();
    private static Map<Integer, String> Proverbs = new HashMap<>();
    private static Map<Integer, String> Users = new HashMap<>();
    private Socket socket;

    ClientWorker(Socket s)
    {
        this.socket = s;
    }

    /**
     * Run the joke server
     */
    public void run()
    {

        System.out.println("Client Connected");

        PrintStream out = null;
        BufferedReader in = null;

        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            // create an output stream on the specified socket
            out = new PrintStream(this.socket.getOutputStream());

            try
            {
                // get the request from the client
                ServerRequest request = this.ProcessRequest(in.readLine());

                switch (request.clientRequest)
                {
                    case "JOKE_MODE":
                    {
                        ServerMode = ServerMode.JOKES;
                        System.out.print("Setting server in Joke Mode");
                        break;
                    }
                    case "PROVERB_MODE":
                    {
                        ServerMode = ServerMode.PROVERBS;
                        System.out.print("Setting server in Proverb Mode");
                    }
                }
                // reply to the client by writing to the sockets output stream
                out.println("Why did the chicken cross the road? To get to the other side");
            }
            catch (Exception e)
            {
                out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
            }
        }
        catch (IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }

    private void initializeJokes()
    {
        ClientWorker.Jokes.put(1, "JokeA");
        ClientWorker.Jokes.put(2, "JokeB");
        ClientWorker.Jokes.put(3, "JokeC");
        ClientWorker.Jokes.put(4, "JokeD");
    }

    private void initalizeProverbs()
    {
        ClientWorker.Proverbs.put(1, "ProverbA");
        ClientWorker.Proverbs.put(2, "ProverbB");
        ClientWorker.Proverbs.put(3, "ProverbC");
        ClientWorker.Proverbs.put(4, "ProverbD");
    }

    /**
     * Process the request from the client
     *
     * @param request a ; delimted list that can be parsed into the request from the client
     */
    private ServerRequest ProcessRequest(String request)
    {
        ServerRequest serverRequest = new ServerRequest();

        String[] options = request.split(";");

        serverRequest.userId = options[0];
        serverRequest.clientRequest = options[1];

        return serverRequest;
    }
}

class AdminWorker extends Thread
{
    private Socket socket;

    AdminWorker(Socket s)
    {
        this.socket = s;
    }

    public void run()
    {
        System.out.println("Admin Client Connected");

        PrintStream out = null;
        BufferedReader in = null;

        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            // create an output stream on the specified socket
            out = new PrintStream(this.socket.getOutputStream());

            try
            {
                // The admin client only really makes 1 request, Joke Mode or Server Mode so a string is fine
                String request = in.readLine();

                switch (request)
                {
                    case "JOKE_MODE":
                    {
                        ClientWorker.ServerMode = ServerModes.JOKES;
                        System.out.print("Setting server in Joke Mode");
                        out.println("Successfully set server mode to " + request);
                        break;
                    }
                    case "PROVERB_MODE":
                    {
                        ClientWorker.ServerMode = ServerModes.PROVERBS;
                        System.out.print("Setting server in Proverb Mode");
                        out.println("Successfully set server mode to " + request);
                        break;
                    }
                    default:
                    {
                        out.println("Invalid Server Mode.");
                    }
                }
            }
            catch (Exception e)
            {
                out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
            }
        }
        catch (IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }
}

class AdminThread implements Runnable
{
    public void run()
    {
        int q_len = 6;
        // the Admin Client listens on port 5050
        int port = 5050;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("Listening for admin clients on port %s.", port));

            while (true)
            {
                // wait for the next ADMIN client connection:
                socket = serverSocket.accept();
                // Once a connection has come in start the admin worker
                new AdminWorker(socket).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to start client admin worker with exception: " + e);
        }
    }
}

class ServerRequest
{
    public String userId;
    public String clientRequest;
}

/**
 * Joke Server class
 */
public class JokeServer
{
    public static void main(String args[])
    {
        int q_len = 6;
        int port = 1565;
        Socket socket;

        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("No port specified so using default: 1565");
            System.out.println("Usage: java JokeServer -p [port to open]");
            System.out.println("-------------------------------------------------------\n");
        }

        for (int i = 0; i < args.length; ++i)
        {
            if (args[i].contains("-p"))
            {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        if (port == 5050)
        {
            System.out.println("Cannot use admin port, please select another");
            return;
        }

        System.out.println("Tommy Leedberg's Joke server 1.8 starting up");
        try
        {
            // create a new admin client thread to listen on port 5050
            AdminThread adminThread = new AdminThread();
            Thread aThread = new Thread(adminThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create client admin thread with exception: " + e);
        }

        try
        {
            // create a socket on the given port for clients to connect to
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("Listening for clients on port %s.", port));

            while (true)
            {
                // Wait for the incoming connection
                socket = serverSocket.accept();
                // Kick off the client working thread
                new ClientWorker(socket).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to start client worker with exception: " + e);
        }
    }
}
