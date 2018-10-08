/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: October 2, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java WebServer

    Instructions:
    To Compile:
    javac MyWebServer.java

    Notes:
    In order for add nums to work it is expecting the user
    the use two numbers and a name, nothing else.

----------------------------------------------------------*/

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * A message type enum to differentiate between TEXT and HTML responses
 */
enum MessageType
{
    TEXT,
    HTML,
    ERROR
}

/**
 * The message receiver
 */
class MessageReceiver extends Thread
{
    private static String workingDir = ".";
    private static String homePage = "/";
    private Socket socket;

    MessageReceiver(Socket s)
    {
        this.socket = s;
    }

    public void run()
    {
        System.out.println("Client Connected");

        DataOutputStream out;
        BufferedReader in;
        File folder;

        try
        {
            // create an output stream on the specified socket
            out = new DataOutputStream(this.socket.getOutputStream());
            // create an input stream on the specified socket
            in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

            try
            {
                String incoming = in.readLine();

                if (incoming == null || incoming.contains("favicon.ico"))
                {
                    return;
                }

                System.out.println("Incoming request from client: " + incoming);

                // The web server is only allowing GET requests
                if (!incoming.contains("GET"))
                {
                    System.out.println("Illegal request method specified.");
                    WriteResponse( out, "Illegal request method specified", MessageType.ERROR);
                    return;
                }

                // Create the opening of the html file
                // Add a home page link to make traversing back to the root little easier
                String htmlResponse = "<html>" +
                        "<body><br><br><a href=\"" + homePage + "\">Home</a><br><br>";

                if( incoming.contains("?"))
                {
                    // Sample data to test with
                    //String sampeData = "GET /cgi/addnums.fake-cgi?person=Tommy&num1=12&num2=8";
                    String directoryHTML = this.ParseForm(incoming, htmlResponse);
                    WriteResponse(out, directoryHTML, MessageType.HTML);
                    return;
                }

                // If the client is requesting the root directory build it
                if (incoming.compareToIgnoreCase("GET / HTTP/1.1") == 0)
                {
                    workingDir = ".";
                    folder = new File(".");
                    File[] filesInPath = folder.listFiles();

                    String directoryHTML = this.GenerateDirectoryHTML(filesInPath, htmlResponse);

                    try
                    {
                        WriteResponseHeader(out, directoryHTML.length(), MessageType.HTML);
                        out.writeBytes(directoryHTML);
                        return;
                    }
                    catch (IOException ex)
                    {
                        System.out.println("Error opening i/o pipe on the specified socket: " + ex);
                    }

                }
                else
                {
                    // Get the actual request and determine if the user is looking for a file that we have locally
                    String req = incoming.substring(3, incoming.length() - 9).trim();
                    req = "." + req.replace('/', '\\');

                    folder = new File(workingDir);
                    File[] filesInPath = folder.listFiles();
                    for (File file : filesInPath)
                    {
                        // Check the files to see if we have a file name match with the request
                        if (file.getPath().compareToIgnoreCase(req) == 0)
                        {
                            // If the request is for a directory, rebuild the html for all files under that directory
                            if (file.isDirectory())
                            {
                                // Update the working directory and build the new directories HTML
                                workingDir = file.getPath();
                                System.out.println("Request for directory " + req + " has been made");
                                String directoryHTML = this.GenerateDirectoryHTML(file.listFiles(), htmlResponse);

                                // Write the response
                                this.WriteResponse(out, directoryHTML, MessageType.HTML);
                                return;

                            }

                            if( file.isFile())
                            {
                                System.out.println("Request for file " + req + " has been made");

                                String ext = file.getName().substring(file.getName().lastIndexOf("."));
                                if (ext.equalsIgnoreCase(".txt") || ext.equalsIgnoreCase(".java"))
                                {
                                    this.WriteResponse(out, new String(Files.readAllBytes(Paths.get(file.getPath()))), MessageType.TEXT);
                                    return;
                                }
                                if (ext.equalsIgnoreCase(".html"))
                                {
                                    this.WriteResponse(out, new String(Files.readAllBytes(Paths.get(file.getPath()))), MessageType.HTML);
                                    return;
                                }

                                this.WriteResponse(out, "Unsupported File Type Requested", MessageType.ERROR);
                                return;
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                System.out.println("Server error");
                e.printStackTrace();
            }
            finally
            {
                this.socket.close();
                out.flush();
                out.close();
                in.close();
            }
        }
        catch (IOException ex)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + ex);
        }
    }

    /**
     * Write the response to the client
     * @param out The stream to write the response to
     * @param content The content of the response
     * @param contentType The content type for the response
     */
    private void WriteResponse(DataOutputStream out, String content, MessageType contentType)
    {
        try
        {
            this.WriteResponseHeader(out, content.length(), contentType);
            out.writeBytes(content);
        }
        catch( IOException ex)
        {
            System.out.println("Error opening i/o pipe on the specified socket: " + ex);
        }

    }
    /**
     * Write the response header
     * @param out The stream to write the header too
     * @param contentLength The length of the response message
     * @param contentType The content type for the response
     */
    private void WriteResponseHeader(DataOutputStream out, long contentLength, MessageType contentType)
    {
        try
        {
            switch (contentType)
            {
                case TEXT:
                {
                    out.writeBytes("HTTP/1.1 200 OK\n");
                    out.writeBytes("Content-Length: " + contentLength + "\r\n");
                    out.writeBytes("Content-Type: text/plain\r\n\r\n");
                    break;
                }
                case HTML:
                {
                    out.writeBytes("HTTP/1.1 200 OK\n");
                    out.writeBytes("Content-Length: " + contentLength + "\r\n");
                    out.writeBytes("Content-Type: text/html\r\n\r\n");
                    break;
                }
                case ERROR:
                {
                    out.writeBytes("HTTP/1.1 400 BadRequest\n");
                    out.writeBytes("Content-Length: " + contentLength + "\r\n");
                    out.writeBytes("Content-Type: text/html\r\n\r\n");
                    break;
                }
                default:
                {
                    System.out.println("Invalid content type provided.");
                }
            }
        }
        catch (IOException ex)
        {
            System.out.println("Failed to write headers to client. Exception: " + ex);
        }
    }

    /**
     * Create the HTML for the file structure
     *
     * @param filesInPath        the files in the current directory path
     * @param directoryStructure The HTML directory structure
     * @return An HTML string representing the directory structure
     */
    private String GenerateDirectoryHTML(File[] filesInPath, String directoryStructure)
    {
        for (File file : filesInPath)
        {
            System.out.println(file.getPath());
            directoryStructure += "<a href=\"" + file.getPath().replaceFirst(".", "") + "\"> " + file.getName() + "</a><br>";
        }
        directoryStructure += "<body><html>";

        return directoryStructure;
    }

    /**
     * Parse the request string to get the value for the parameters then generate a response HTML
     * @param request The request string
     * @return An HTML string with the results from AddNum and the name supplied
     */
    private String ParseForm(String request, String htmlResponse)
    {
        // The form is hardcoded to specifically add 2 numbers so we can keep this pretty simple
        HashMap<String,String> paramKeyMap = new HashMap<>() ;
        String paramString = request.substring( request.indexOf("?") + 1, request.indexOf("HTTP")) ;
        String[] parameters = paramString.split("&");


        for( String param : parameters)
        {
            String variable = param.substring(0,param.indexOf("=")).trim();
            String value = param.substring(param.indexOf("=") + 1).trim();
            paramKeyMap.put(variable, value);
        }


        int num1 = Integer.parseInt(paramKeyMap.get("num1")) ;
        int num2 = Integer.parseInt(paramKeyMap.get("num2"));
        int sum =  num1 + num2;
        htmlResponse += "<h1>Hello " + paramKeyMap.get("person") + ",<h1>" +
                     "<br>The sum of your two numbers (" + paramKeyMap.get("num1") + ", " +paramKeyMap.get("num2") + ") " +
                    "is " + sum ;

        htmlResponse += "<body><html>";
        return htmlResponse;
    }
}

/**
 * The socket listener clients thread
 */
class SocketListener implements Runnable
{
    public static boolean runReceiver = true;

    public void run()
    {
        int q_len = 6;
        int port = 2540;
        Socket socket;

        try
        {
            ServerSocket serverSocket = new ServerSocket(port, q_len);
            System.out.println(String.format("Listening for the admin client on port %s.", port));

            while (runReceiver)
            {
                // wait for the next ADMIN client connection:
                socket = serverSocket.accept();
                // Once a connection has come in start the admin worker
                new MessageReceiver(socket).start();
            }
        }
        catch (IOException e)
        {
            System.out.println("Failed to message receiver worker with exception: " + e);
        }
    }
}

/**
 * Joke Server class
 */
public class MyWebServer
{
    public static void main(String args[])
    {
        if (args.length == 0)
        {
            System.out.println("\n-------------------------------------------------------");
            System.out.println("Usage: java WebServer");
            System.out.println("-------------------------------------------------------\n");
        }

        System.out.println("Tommy Leedberg's WebServer 1.8 starting up");
        try
        {
            // create a new socket listener thread to listen
            SocketListener listenerThread = new SocketListener();
            Thread aThread = new Thread(listenerThread);
            aThread.start();
        }
        catch (Exception e)
        {
            System.out.println("Failed to create listener thread with exception: " + e);
        }
    }
}
