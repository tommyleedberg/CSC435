/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: October 25, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java HostServer

    Instructions:
    To Compile:
    javac HostServer.java

    High Level Overview:
    A user connects on port 1565 and is then redirected to the
    next available port. They stay on that port acquiring incremental
    state until they enter migrate. This then triggers the AgentWorker
    to request that their port be moved to the next available port

----------------------------------------------------------*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * AgentWorker class listens on ports set up by the agentListener. It finds the next available port and migrates the
 * listener if a request comes in saying to migrate, otherwise it increments the internal state of the listener
 * AgentWorker
 */
class AgentWorker extends Thread
{
    private final String NewHost = "LocalHost";
    private final int NewHostMainPort = 1565;
    Socket socket;
    agentHolder parentAgentHolder;
    int localPort;

    AgentWorker(Socket socket, int port, agentHolder agentHolder)
    {
        this.socket = socket;
        this.localPort = port;
        this.parentAgentHolder = agentHolder;
    }

    public void run()
    {
        //initialize variables
        PrintStream out;
        BufferedReader in;
        String buf;
        int newPort;
        Socket clientSocket;
        BufferedReader fromHostServer;
        PrintStream toHostServer;

        try
        {
            out = new PrintStream(this.socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            String inLine = in.readLine();

            StringBuilder htmlString = new StringBuilder();

            System.out.println();
            System.out.println("Request line: " + inLine);

            // The incoming request contains "migrate" so move the host to a new port
            if (inLine.indexOf("migrate") > -1)
            {
                // create a client connection to the main host on port 1565 and send
                clientSocket = new Socket(NewHost, this.NewHostMainPort);
                fromHostServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                //send a request to port 1565 to receive the next open port
                toHostServer = new PrintStream(clientSocket.getOutputStream());
                toHostServer.println("Please host me. Send my port! [State=" + parentAgentHolder.getAgentState() + "]");
                toHostServer.flush();

                while(true)
                {
                    // Only escape from here if there is a "Port" in the request
                    buf = fromHostServer.readLine();
                    if (buf.indexOf("[Port=") > -1)
                    {
                        break;
                    }
                }

                // Extract the new port from the incomming request
                String tempbuf = buf.substring(buf.indexOf("[Port=") + 6, buf.indexOf("]", buf.indexOf("[Port=")));
                newPort = Integer.parseInt(tempbuf);
                System.out.println("newPort is: " + newPort);

                // Build the new HTML response letting the user know there is migration going on
                htmlString.append(AgentListener.generateHTMLBody(newPort, NewHost, inLine));
                htmlString.append("<h3>We are migrating to host " + newPort + "</h3> \n");
                htmlString.append("<h3>View the source of this page to see how the client is informed of the new location.</h3> \n");
                htmlString.append(AgentListener.generateHTMLForm());


                System.out.println("Killing parent listening loop.");

                // Get the socket saved in the AgentHolder and close it since we're opening a new one
                ServerSocket ss = parentAgentHolder.getSocket();
                ss.close();
            }
            else if (inLine.indexOf("person") > -1)
            {
                // get the parent agents state, increment it and return it to the user via HTML
                int state = parentAgentHolder.getAgentState();
                state++;

                htmlString.append(AgentListener.generateHTMLBody(localPort, NewHost, inLine));
                htmlString.append("<h3>We are having a conversation with state   " + state + "</h3>\n");
                htmlString.append(AgentListener.generateHTMLForm());

                // reset the state
                parentAgentHolder.setAgentState(state);
            }
            else
            {
                // Not a migrate request or a person request so the request must be invalid, let the user know
                htmlString.append(AgentListener.generateHTMLBody(localPort, NewHost, inLine));
                htmlString.append("You have not entered a valid request!\n");
                htmlString.append(AgentListener.generateHTMLForm());
            }

            // Send the user the new html response
            AgentListener.sendHTMLtoStream(htmlString.toString(), out);

            //close the socket
            this.socket.close();
        }
        catch (IOException ioe)
        {
            System.out.println(ioe);
        }
    }
}

/**
 * A container to hold the agents state and socket
 */
class agentHolder
{
    private ServerSocket socket;
    private int agentState;

    agentHolder(ServerSocket socket)
    {
        this.socket = socket;
    }

    /**
     * Get the Agent State from this holder
     * @return The state of the agent
     */
    public int getAgentState()
    {
        return this.agentState;
    }

    /**
     * Set the state of this holder
     * @param state The state to set
     */
    public void setAgentState(int state)
    {
        this.agentState = state;
    }

    /**
     * Get the Server Socket
     * @return
     */
    public ServerSocket getSocket()
    {
        return this.socket;
    }
}

/**
 * AgentListener objects watch individual ports and respond to requests
 * made upon them(in this scenario from a standard web browser); Craeted 
 * by the hostserver when a new request is made to 1565
 *
 */
class AgentListener extends Thread
{
    Socket sock;
    private final String NewHost = "LocalHost";
    int localPort;

    // The state for this particular AgentListener
    int agentState = 0;

    /**
     * The Agent Listener Thread. Used to kick off an AgentWorker whenever a new connection is made to the Host Server
     * on port 1565.
     * @param socket The socket from the Host server which is turned into a local connection on port 1565 for this thread
     * @param port The port to start the AgentWorker on
     */
    AgentListener(Socket socket, int port)
    {
        this.sock = socket;
        this.localPort = port;
    }

    /**
     * The run method for the AgentListener
     */
    public void run()
    {
        BufferedReader in;
        PrintStream out;
        String inLine;

        System.out.println("In AgentListener Thread");
        try
        {
            // Using the socket create an inbound outbound pipe for listening / sending data

            out = new PrintStream(sock.getOutputStream());
            in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            inLine = in.readLine();

            // If the inbound message contains "State=" it means that state is being sent so parse it out and then save it
            if (inLine != null && inLine.indexOf("[State=") > -1)
            {
                // Get the new state from the request, parse it from a String to an Int, and Save it in the AgentState.
                String tempbuf = inLine.substring(inLine.indexOf("[State=") + 7, inLine.indexOf("]", inLine.indexOf("[State=")));
                this.agentState = Integer.parseInt(tempbuf);
                System.out.println("agentState is: " + this.agentState);
            }

            System.out.println(inLine);

            // Using StringBuilder build an HTML response to the request and then display it
            StringBuilder htmlResponse = new StringBuilder();
            htmlResponse.append(generateHTMLBody(localPort, NewHost, inLine));
            htmlResponse.append("Now in Agent Looper starting Agent Listening Loop\n<br />\n");
            htmlResponse.append("[Port=" + localPort + "]<br/>\n");
            htmlResponse.append(generateHTMLForm());
            sendHTMLtoStream(htmlResponse.toString(), out);

            //now open a connection at the port
            ServerSocket servsock = new ServerSocket(localPort, 2);

            // Save the current state of the agent and it's socket in the Agent Holder to be passed into the Agent Worker
            agentHolder agenthold = new agentHolder(servsock);
            agenthold.setAgentState(this.agentState);

            while (true)
            {
                // Update the AgentListeners socket with the new socket using the port supplied by the host server and
                // start the worker passing in the current Agents State and Socket
                this.sock = servsock.accept();
                System.out.println("Got a connection to agent at port " + localPort);
                new AgentWorker(sock, localPort, agenthold).start();
            }
        }
        catch (IOException ioe)
        {
            //this happens when an error occurs OR when we switch port
            System.out.println("Either connection failed, or just killed listener loop for agent at port " + localPort);
            System.out.println(ioe);
        }
    }

    /**
     * Generate the HTML Response
     * @param localPort the Port the response is going to
     * @param NewHost The Host address the response is going to
     * @param inLine The message sent from the host
     * @return
     */
    static String generateHTMLBody(int localPort, String NewHost, String inLine)
    {

        StringBuilder htmlString = new StringBuilder();

        htmlString.append("<html><head> </head><body>\n");
        htmlString.append("<h2>This is for submission to PORT " + localPort + " on " + NewHost + "</h2>\n");
        htmlString.append("<h3>You sent: " + inLine + "</h3>");
        htmlString.append("\n<form method=\"GET\" action=\"http://" + NewHost + ":" + localPort + "\">\n");
        htmlString.append("Enter text or <i>migrate</i>:");
        htmlString.append("\n<input type=\"text\" name=\"person\" size=\"20\" value=\"YourTextInput\" /> <p>\n");

        return htmlString.toString();
    }

    /**
     * Generate a form field for HTML with a submit button
     * @return
     */
    static String generateHTMLForm()
    {
        return "<input type=\"submit\" value=\"Submit\"" + "</p>\n</form></body></html>\n";
    }

    /**
     * Using the given Print stream send the HTML Headers followed by the given HTML body
     * @param html The HTML body to send to the PrintStream
     * @param out The PrintStream to send to
     */
    static void sendHTMLtoStream(String html, PrintStream out)
    {

        out.println("HTTP/1.1 200 OK");
        out.println("Content-Length: " + html.length());
        out.println("Content-Type: text/html");
        out.println("");
        out.println(html);
    }
}

/**
 *  THe host server class. Listens on port 1565 and with every connection starts an Agent Listener that increments the
 *  port number.
 */
public class HostServer
{
    //we start listening on port 3001
    public static int NextPort = 3000;

    public static void main(String[] a) throws IOException
    {
        int q_len = 6;
        int port = 1565;
        Socket sock;

        // The server will listen on port 1565 for incoming requests
        ServerSocket servsock = new ServerSocket(port, q_len);
        System.out.println("Tommy Leedbergs addition of John Reagan's DIA Master receiver started at port 1565.");
        System.out.println("Connect from 1 to 3 browsers using \"http:\\\\localhost:1565\"\n");

        while (true)
        {
            // After every connection is made increment the next port to be sent to the agent listener
            NextPort = NextPort + 1;

            // Someone has connected to port 1565 so start the agent listener passing in the socket and the next port
            sock = servsock.accept();

            // Start the agent listener
            System.out.println("Starting AgentListener at port " + NextPort);
            new AgentListener(sock, NextPort).start();
        }
    }
}