For this project I used Googles gson library instead of using XML. Inorder to compile the project you need to run
javac -cp "gson-2.8.4.jar" *.java which will compile the blockchain project with the gson jar file.

The above compiles successfully but after testing( unfortunatly right befoe it was time to tern in the assignment ) it turns out you cannot run the program as expected, it actually fails outright. I was testing the entire time in an IDE
that handles the compilation for you. 

If I'm allowed I can take this out and resubmit at a later date using the built in XML libs(I could probably have it done sometime this weekend ).

Also, instead of each process having it's own private/public key pair I created a KeyManager service. This service listens on port 1524
which we have used in the past. Essentially, on startup of process 2 this thread is kicked off and creates the private/public key pair.
Once it has created it is sends the public key to the other running processes publickey socket where it is stored in a keymanager and used to verify blocks.