For this project I used Googles gson library instead of using XML. Inorder to compile the project you need to run
javac -cp "gson-2.8.4.jar" *.java which will compile the blockchain project with the gson jar file.

Also, instead of each process having it's own private/public key pair I created a KeyManager service. This service listens on port 1524
which we have used in the past. Essentially, on startup of process 2 this thread is kicked off and creates the private/public key pair.
Once it has created it is sends the public key to the other running processes publickey socket where it is stored in a keymanager and used to verify blocks.