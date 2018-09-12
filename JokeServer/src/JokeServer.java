/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: September 12, 2018
    Java Version: 1.8.0_181
    
    Command-Line Examples:
    Usage: java JokeServer -p [port to open]
    Note: If no port is selected it uses a default of 1565

    Instructions:
----------------------------------------------------------*/
import java.io.*;
import java.net.*;

/**
 * Worker thread class
 */
class Worker extends Thread
{
    Socket socket;
    Worker (Socket s)
    {
        this.socket = s;
    }

    /**
     * Run the inet server
     */
    public void run()
    {
        System.out.println("Client Connected");

        PrintStream out = null;
        BufferedReader in = null;

        try
        {
            // create an input stream on the specified socket
            in = new BufferedReader( new InputStreamReader(this.socket.getInputStream()));
            // create an output stream on the specified socket
            out = new PrintStream(this.socket.getOutputStream());

            try
            {
                String name;
                // get the name from the input pipe
                //name = in.readLine();
                // reply to the client by writing to the sockets output stream
                out.println("Why did the chicken cross the road? To get to the other side");
            }
            catch(Exception e)
            {
                out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
            }
        }
        catch( IOException e)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + e);
        }
    }

    /**
     * Print the host name and ip address
     * @param name
     * @param out
     *//*
    private void printRemoteAddress(String name, PrintStream out)
    {
        try
        {
            out.println("Looking up " + name + "...");
            InetAddress machine = InetAddress.getByName(name);
            out.println("Host name: " + machine.getHostName());
            out.println("Host IP: " + this.toText(machine.getAddress()));
        }
        catch(UnknownHostException e)
        {
            out.println("Failed to lookup name: " + name);
        }
    }*/

    /**
     * Take the given byte array and turn it into a string
     * @param ip a byte array representation of an ip address
     * @return a string representation of the ip address
     */
    public String toText(byte ip[])
    {
        StringBuffer result = new StringBuffer();
        for( int i = 0; i <ip.length; ++i)
        {
            if(i > 0)
            {
                result.append(".");
            }
            result.append(0xff & ip[i]);
        }
        return result.toString();
    }
}

/**
 * Inet Server class
 */
public class JokeServer
{
    public static void main(String args[])
    {
        int q_len = 6;
        int port = 1565;
        Socket socket;

        if( args.length == 0)
        {
            System.out.println("No port specified so using default: 1565");
            System.out.println("Usage: java JokeServer -p [port to open]");
        }

        for(int i = 0; i < args.length; ++i)
        {
            if( args[i].contains("-p"))
            {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        try
        {
            // create a socket on the given port
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("Tommy Leedberg's Inet server 1.8 starting up, listening at port %s.\n", port));

            while(true)
            {
                socket = serverSocket.accept(); // wait for the next client connection
                new Worker(socket).start(); // Spawn worker to handle it
            }
        }
        catch(IOException e)
        {
            System.out.println( "Failed to start server with exception: " + e);
        }
    }
}
