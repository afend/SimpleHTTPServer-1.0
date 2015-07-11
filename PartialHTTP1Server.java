/*
 * Adam Fendler & Matt Eder
 * Internet Technology
 * PartialHTTP1Server
 */
 
import java.net.*;
import java.util.regex.*;
import java.io.*;
import java.util.*;

public class PartialHTTP1Server {
	public static void main(String[] args) throws Exception {
		boolean serverRunning = true;
		
		if(args.length == 1) {
			boolean portValid = validatePort(args[0]);
			
			if(portValid == true) {
				port = Integer.parseInt(args[0]);
			}
    	}
		
		ServerSocket m_ServerSocket = new ServerSocket(port);
		int id = 0;
		
		while (serverRunning) {
			try {
				Socket clientSocket = m_ServerSocket.accept();
				ClientServiceThread cliThread = new ClientServiceThread(clientSocket, id++);
				cliThread.start();
      		} catch(IOException e) {
	  			System.out.println("Exception encountered on accept.  StackTrace: \n");
	  			System.exit(1);
	  		}
    	}
		
		try {
			m_ServerSocket.close();
			System.out.println("Server has stopped.");
    	} catch(Exception e) {
			System.out.println("Exception encountered when trying to stop server socket.");
			System.exit(1);
    	}
  	}

  	private static boolean validatePort(String num) {
    	Pattern pattern = Pattern.compile("[0-9]+"); //checks if it is a digit
		if(!pattern.matcher(num).matches()) {
        	//now check to see if it is in the range of 1-65535 (valid port number) but we want above 1024
			int portNum = Integer.parseInt(num);
			if(portNum > 1024 && portNum <= 65535) {
	        	return true;
        	} else {
	        	return false;
        	}
		}
      
		return !pattern.matcher(num).matches();
  	}
}

class ClientServiceThread extends Thread {
	Socket clientSocket;
	int clientID = -1;
	boolean running = true;
	String currentDir = ".";
  
	public ClientServiceThread() {
      super();
  	}
  
  	ClientServiceThread(Socket s, int i) {
		clientSocket = s;
		clientID = i;
  	}

  	public void run() {
    	BufferedReader in = null;
		PrintWriter out = null;
		System.out.println("Accepted Client : ID - " + clientID + " : Address - " + clientSocket.getInetAddress().getHostName());
      
		//Status code (200, 404, etc)
		String statusCode = "";
		//HTTP protocol (version)
		String versionUse = "HTTP/0.8";
		//Type of Data needed (text/plain, etc
		String contentType = ""; //example can be text/plain 
		
		try {
        	in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
         
			try {
            	clientSocket.setSoTimeout(3000);
        	} catch(IOException ioe) {
            	statusCode = "408";
				out.print(statusCode + " Request Timeout");
				out.println();
        	}

			while (running) {
            
            	String clientCommand = in.readLine(); //reads single line
				System.out.println("Client Says :" + clientCommand);
        
				if (clientCommand.equalsIgnoreCase("QUIT")) {
                	running = false;
					System.out.print("Stopping client thread for client : " + clientID);
            	} else {
	            	//get command string
					String cmdStr = clientCommand;
               
					//split commandStr into two sections
					String[] parts = cmdStr.split("\\s+");
               
					if(parts.length == 2) {
						//parts[0] = Command (GET, PUT, etc)
						//parts[1] = Resource (file name)
						//currentDirectory is the directory that one is working in (where the server is current running)
						String checkFileStr = currentDir + parts[(parts.length-1)];
                  
						//Check if file exist
						File tempFile = new File(checkFileStr);
						if(tempFile.exists() && !tempFile.isDirectory()) {
							//valid file - - checks what command was sent (parts[0])
							String commandSent = parts[0];
							boolean isValidCommand = false;
					
							if(commandSent.equals("GET")) {
								//200 OK - - read the file back
								statusCode = "200";
								isValidCommand = true;
                    		} else {
							/*
								Since it is not implemented, check to see if it matches any of the following:
								1) POST
								2) DELETE
								3) PUT
                        	*/
							//both will still become false for isValidCommand
								if (commandSent.equals("POST") || commandSent.equals("DELETE") || commandSent.equals("PUT")) {
									//valid but not implemented so use 501 Not Implemented Message
									//501 Not Implemented
									statusCode = "501";
									out.print(statusCode + " Not Implemented");
									out.println();
                           		} else {
						   			//not valid (mistyped command)
						   			statusCode = "400";
						   			out.print(statusCode + " Bad Request");
						   			out.println();
						   		}
                     		}
                     
					 		//200 OK - - then read file back
					 		if(isValidCommand) {
					 			statusCode = "200";
					 			if(tempFile.canRead()) {
					 				try {
					 					String content = null;
					 					out.print(statusCode + " OK");
					 					out.println();
					 					out.println();
					 					FileInputStream fileStream = new FileInputStream(tempFile);
					 					BufferedReader bFileReader = new BufferedReader(new InputStreamReader(fileStream));
                              
					 					String tmpLine;
					 					while ((tmpLine = bFileReader.readLine()) != null) {
					 						if(tmpLine.length() == 0) {
					 							break;
                                 			} 			
								 			out.print(tmpLine + "\r\n");
                              			}
							  			
							  			out.println();
							  			out.println();
							  			bFileReader.close();
                           			} catch (Exception e) {
						   				statusCode = "500";
						   				out.print(statusCode + " Internal Error");
						   				out.println();
						   				//e.printStackTrace();
                           			}
                        		} else {
									statusCode = "500";
									out.print(statusCode + " Internal Error");
									out.println();
                        		}
                     		}
                  		} else {
				  			//404 Not Found
				  			statusCode = "404";
				  			out.print(statusCode + " Not Found");
				  			out.println();
                  		}
               		} else {
			   			//400 Bad Request
			   			statusCode = "400";
			   			out.print("400 Bad Request");
			   			out.println();
               		}
			   		
			   		//out.println(clientCommand);
			   		out.flush();
            	}
            }
        } catch (Exception e) {
	      	System.out.println("Error when using buffer reader and writer.");
		  	e.printStackTrace();
      	} finally {
	      	try {
            	Thread.sleep(500);
				in.close();
				out.close();
				clientSocket.close();
				System.out.println("Client " + clientID + " has just disconnected.");
         	} catch(InterruptedException ie) {
            	System.out.println("Error waiting 500 miliseconds (0.5 seconds).");
				try {
					in.close();
					out.close();
					clientSocket.close();
            	} catch(IOException e) {
					System.out.println("Something went wrong closing the Client Socket.\n");
            	}
         	} catch(IOException e) {
	         	System.out.println("Something went wrong closing the Client Socket.\n");
         	}
      	}
   	}
}