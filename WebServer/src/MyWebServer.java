/*--------------------------------------------------------
    Name: Tommy Leedberg
    Date: October 2, 2018
    Java Version: 1.8.0_181

    Command-Line Examples:
    Usage: java WebServer

    Instructions:
    To Compile:
    javac MyWebServer.java

----------------------------------------------------------*/

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A message type enum to differentiate between TEXT and HTML responses
 */
enum MessageType
{
    TEXT,
    HTML
}
    /**
     * The message receiver
     */
    class MessageReceiver extends Thread
    {
        private Socket socket;
        private static String workingDir = ".";
        private static String homePage = "/";

        MessageReceiver(Socket s)
        {
            this.socket = s;
        }

        public void run()
        {
            System.out.println("Client Connected");

            DataOutputStream out = null;
            BufferedReader in = null;
            File folder = null;

            try
            {
                // create an output stream on the specified socket
                out = new DataOutputStream(this.socket.getOutputStream());
                // create an input stream on the specified socket
                in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

                try
                {
                    String incoming = in.readLine();

                    if (incoming != null)
                    {
                        System.out.println(incoming);
                        String directoryStructure = "<html><body>";

                        // create a home page button to make traversing the directories a little easier
                        if( incoming.compareToIgnoreCase("GET / HTTP/1.1") == 0)
                        {
                            workingDir = ".";
                            folder = new File(".");
                        }
                        else
                        {
                            folder = new File(workingDir);
                        }

                        File[] filesInPath = folder.listFiles();

                        if(incoming.compareToIgnoreCase("GET / HTTP/1.1") == 0)
                        {

                            String directoryHTML = this.GenerateDirectoryHTML(filesInPath, directoryStructure);

                            try
                            {
                                WriteHeaderResponse(out, directoryHTML.length(), MessageType.HTML);
                                out.writeBytes(directoryHTML);
                            }
                            catch( IOException ex)
                            {
                                System.out.println("Error opening i/o pipe on the specified socket: " + ex);
                            }

                        }
                        else if( incoming.contains("POST") || incoming.contains("PUT") || incoming.contains("DELETE"))
                        {
                            System.out.println( "Illegal request type specified");
                            out.writeBytes("HTTP/1.1 400 Bad request");
                        }
                        else
                        {
                            //Get the actual request and determine if the user is looking for a file that we have locally
                            String req = incoming.substring(3, incoming.length()-9).trim();
                            req = "." + req.replace('/', '\\');

                            for(File file : filesInPath)
                            {
                                // check the files to see if we have a file name match with the request
                                if(file.getPath().compareToIgnoreCase(req) == 0)
                                {
                                    // if the request is for a directory, rebuild the html for all files under that directory
                                    if(file.isDirectory())
                                    {
                                        workingDir = file.getPath();
                                        System.out.println("Request for directory " + req + " has been made");
                                        String directoryHTML = this.GenerateDirectoryHTML(file.listFiles(), directoryStructure);

                                        try
                                        {
                                            WriteHeaderResponse(out, directoryHTML.length(), MessageType.HTML);
                                            out.writeBytes(directoryHTML);
                                        }
                                        catch( IOException ex)
                                        {
                                            System.out.println("Error opening i/o pipe on the specified socket: " + ex);
                                        }
                                    }
                                    else
                                    {
                                        System.out.println("Request for file " + req + " has been made");
                                        String ext = file.getName().substring(file.getName().lastIndexOf("."));
                                        if (ext.equalsIgnoreCase(".txt") || ext.equalsIgnoreCase(".java"))
                                        {
                                            WriteHeaderResponse(out, file.length(), MessageType.TEXT);
                                        }
                                        else if (ext.equalsIgnoreCase(".html"))
                                        {
                                            WriteHeaderResponse(out, file.length(), MessageType.HTML);

                                        }
                                        out.writeBytes(new String(Files.readAllBytes(Paths.get(file.getPath()))));
                                    }

                                    // no reason to continue in this loop we found what we're looking for
                                    break;
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

        private void WriteHeaderResponse(DataOutputStream out, long contentLength, MessageType type)
        {
            try
            {
                out.writeBytes("HTTP/1.1 200 OK\n");
                out.writeBytes("Content-Length: " + contentLength + "\r\n");
                switch (type)
                {
                    case TEXT:
                    {
                        out.writeBytes("Content-Type: text/plain\r\n\r\n");
                        break;
                    }
                    case HTML:
                    {
                        out.writeBytes("Content-Type: text/html\r\n\r\n");
                        break;
                    }
                    default:
                    {
                        System.out.println( "Invalid content type provided.")  ;
                    }
                }
            }
           catch( IOException ex)
           {
               System.out.println( "Failed to write headers to client. Exception: " +ex);
           }
        }

        private String GenerateDirectoryHTML(File[] filesInPath, String directoryStructure )
        {
            for( File file : filesInPath)
            {
                System.out.println(file.getPath());
                directoryStructure += "<a href=\"" + file.getPath().replaceFirst(".", "") + "\"> " + file.getName() + "</a><br>";
            }
            directoryStructure += "<br><br><a href=\"" + homePage + "\">Home<body></html>";

            return directoryStructure;
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
