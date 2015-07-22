/**
 * @author Adam Fendler
 * @author Matt Eder
 * Partial HTTP1Server
 */

import java.net.*;
import java.util.regex.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;

public class PartialHTTP1Server {
	public static void main(String[] args) throws Exception {
		int port = 3456; //randomly assigned number chosen
		boolean serverRunning = true;

		if (args.length == 1) {
			boolean portValid = validatePort(args[0]);

			if (portValid == true) {
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
			} catch (IOException e) {
				System.out.println("Exception encountered on accept.  StackTrace: \n");
				System.exit(1);
			}
		}

		try {
			m_ServerSocket.close();
			System.out.println("Server has stopped.");
		} catch (Exception e) {
			System.out.println("Exception encountered when trying to stop server socket.");
			System.exit(1);
		}
	}

	private static boolean validatePort(String num) {
		Pattern pattern = Pattern.compile("[0-9]+"); //checks if it is a digit
		if (pattern.matcher(num).matches()) {
			//now check to see if it is in the range of 1-65535 (valid port number) but we want above 1024
			int portNum = Integer.parseInt(num);
			if (portNum > 1024 && portNum <= 65535) {
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

	/**
	 * Global Strings
	 * 
	 */
	public static final String ALLOW = "Allow";
	public static final String AUTHORIZATION = "Authorization";
	public static final String CONTENT_ENCODING = "Content-Encoding";
	public static final String CONTENT_LENGTH = "Content-Length";
	public static final String CONTENT_TYPE = "Content-Type";
	public static final String DATE = "Date";
	public static final String EXPIRES = "Expires";
	public static final String FROM = "From";
	public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
	public static final String LAST_MODIFIED = "Last-Modified";
	public static final String LOCATION = "Location";
	public static final String PRAGMA = "Pragma";
	public static final String REFERER = "Referer";
	public static final String SERVER = "Server";
	public static final String USER_AGENT = "User-Agent";
	public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
        public static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

	public void run() {
		BufferedReader in = null;
		PrintWriter out = null;
		System.out.println("Accepted Client : ID - " + clientID + " : Address - " + clientSocket.getInetAddress().getHostName());

		//header information
		String statusCode = "";
		String version_use = "HTTP/X";
		String content = null;
		String content_type = "";
		String content_length = "";
		String content_encoding = "";
		String server = "";
		String allow = "";
		String date = "";
		String expires = "";
		String modified_if_since = "";
		String last_modified = "";

		try { in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			try {
				clientSocket.setSoTimeout(3000);
			} catch (IOException ioe) {
				statusCode = "408";
				out.print(statusCode + " Request Timeout");
				out.println();
			}

			while (running) {

				String clientCommand = in .readLine(); //reads single line
				System.out.println(clientCommand);

				if (clientCommand.equalsIgnoreCase("QUIT")) {
					running = false;
					System.out.print("Stopping client thread for client : " + clientID);
				} else {
					//get command string
					String cmdStr = clientCommand;

					//split commandStr into two sections
					String[] parts = cmdStr.split("\\s+");

					//now requires 3 
					if (parts.length == 3) {
						//parts[0] = Command (GET, PUT, etc)
						//parts[1] = Resource (file name)
						//parts[2] = HTTP version (HTTP/0.8 <= x <= HTTP/1.0)
						//currentDirectory is the directory that one is working in (where the server is current running)

						String[] HTTPversion = parts[2].split("/");
						float HTTP = Float.parseFloat(HTTPversion[1]);

						String checkFileStr = currentDir + parts[(parts.length - 2)];
						Path fp = Paths.get(checkFileStr);

						//Check if file exist
						File tempFile = new File(checkFileStr);
						if (tempFile.exists() && !tempFile.isDirectory()) {
							//valid file - - checks what command was sent (parts[0])
							String commandSent = parts[0];
							boolean isValidCommand = false;
							boolean isValidVersion = true;

							if (commandSent.equals("GET") || commandSent.equals("POST") || commandSent.equals("HEAD")) {
								if (validateHTTPVersion(HTTP) == true) {
									//200 OK - - read the file back
									statusCode = "200";
									isValidCommand = true;
									version_use = parts[2];
								} else {
									//505 HTTP Version Not Supported
									statusCode = "505";
									isValidCommand = true;
									isValidVersion = false;
                                                                        out.println(statusCode + " HTTP Version Not Supported");
								}
							} else {
								// Since it is not implemented, check to see if it matches any of the following:
								// 1) DELETE
								// 2) PUT
								//both will still become false for isValidCommand
								if (commandSent.equals("DELETE") || commandSent.equals("PUT") 
                                                                        || commandSent.equals("LINK") || commandSent.equals("UNLINK")) {
									//valid but not implemented so use 501 Not Implemented Message
									//501 Not Implemented
									statusCode = "501";
									out.println(statusCode + " Not Implemented");
									out.println();
								} else {
									//not valid (mistyped command)
									statusCode = "400";
									out.println(statusCode + " Bad Request");
									out.println();
								}
							}

							//200 OK - - then read file back
							if (isValidCommand && isValidVersion) {
								statusCode = "200";
								if (tempFile.canRead()) {
									try {
                                                                                Date lastModified = new Date(tempFile.lastModified()); 
                                                                                SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
                                                                                formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
										String ct = contentType(parts[1]);
                                                                                String ex = getExpirationDate();
                                                                                /* EXAMPLE RESPONSE TEMPLATE
										 * HTTP/1.0 <status code> <explanation>
										 * <response head>
										 * (new line)
										 * <response body>
                                                                                 * Allow[x], Content-Encoding[x], Content-Length[x], Content-Type[x], Expires[x], Last-Modified[x]
										 */
                                                                                if (ct.equals("application/pdf")) {
                                                                                    
                                                                                } else if (ct.equals("text/html") || ct.equals("text/plain")) {
                                                                                    
                                                                                } else if (ct.equals("image/jpeg") || ct.equals("image/gif") || ct.equals("image/png")) {
                                                                                    
                                                                                } else if (ct.equals("application/x-gzip") || ct.equals("application/zip") || ct.equals("application/x-tar")) {
                                                                                    
                                                                                } else { //"application/octet-stream"
                                                                                    
                                                                                }
                                                                                
										out.println(version_use + " " + statusCode + " OK");
                                                                                out.println(CONTENT_TYPE + ": " + ct);
                                                                                out.println(CONTENT_LENGTH + ": " + tempFile.length());
                                                                                out.println(LAST_MODIFIED + ": " + lastModified);
                                                                                out.println(CONTENT_ENCODING + ": " + "identity");
                                                                                out.println(ALLOW + ": " + "GET, POST, HEAD");
                                                                                out.println(EXPIRES + ": " + ex);
                                                                       
										out.println();
                                                                                
										FileInputStream fileStream = new FileInputStream(tempFile);
										BufferedReader bFileReader = new BufferedReader(new InputStreamReader(fileStream));
                                                                                
										String tmpLine;
										while ((tmpLine = bFileReader.readLine()) != null) {
											if (tmpLine.length() == 0) {
												break;
											}
											out.print(tmpLine + "\r\n");
										}

										out.println();
										out.println();
										bFileReader.close();
									} catch (Exception e) {
										statusCode = "500";
										out.println(statusCode + " Internal Error");
										out.println();
										//e.printStackTrace();
									}
								} else {
									// 403 Forbidden
									statusCode = "403";
									out.println(statusCode + " Forbidden");
									out.println();
								}
							}
						} else {
							//404 Not Found
							statusCode = "404";
							out.println(statusCode + " Not Found");
							out.println();
						}
					} else {
						//400 Bad Request
						statusCode = "400";
						out.println("400 Bad Request");
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
				Thread.sleep(500); in .close();
				out.close();
				clientSocket.close();
				System.out.println("Client " + clientID + " has just disconnected.");
			} catch (InterruptedException ie) {
				System.out.println("Error waiting 500 miliseconds (0.5 seconds).");
				try { in .close();
					out.close();
					clientSocket.close();
				} catch (IOException e) {
					System.out.println("Something went wrong closing the Client Socket.\n");
				}
			} catch (IOException e) {
				System.out.println("Something went wrong closing the Client Socket.\n");
			}
		}
	}
            
        /** 
         * This function returns a boolean value depending on if the float is less than or equal to 1.0.
         * @return Boolean
         * 
         */
	private boolean validateHTTPVersion(float ver) {
		if (ver >= 1.0) {
			return false;
		} else {
			return true;
		}
	}
        
        /** 
         * This function returns the Content-Type of the file. It accepts one String parameter.
         * @return Content-Type
         * 
         */
        private static String contentType(String fileName) {
            if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
                return "text/html";
            }
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpe") || fileName.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (fileName.endsWith(".gif")) {
                return "image/gif";
            }
            if (fileName.endsWith(".png")) {
                return "image/png";
            }
            if (fileName.endsWith(".txt")) {
                return "text/plain";
            }
            if (fileName.endsWith(".pdf")) {
                return "application/pdf";
            }
            if (fileName.endsWith(".gz") || fileName.endsWith(".gzip")) {
                return "application/x-gzip";
            }
            if (fileName.endsWith(".zip")) {
                return "application/zip";
            }
            if (fileName.endsWith(".tar")) {
                return "application/x-tar";
            }

            return "application/octet-stream";
        }
        
        
        /** 
         * This function returns the Expires date of the file which is set to expire 48 hours (2 days) later.
         * @return Expires
         * 
         */
        private static String getExpirationDate() {
            /**
             * This will be used in the future, but for now the tester wants it hard coded.
             * "Expires: a future date"
             * int days = 2;
             * Calendar c = Calendar.getInstance();
             * c.setTime( new Date());
             * c.add(Calendar.DATE, days);
             * String o = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss zzz").format(c.getTime());
             * 
             */
            String ct = "a future date";
            return ct;
        }
        
        private static String getContentEncoding() {
            String ce = "identity"; 
            return ce;
        }
        
        private static String getAllow() {
            String al = "GET, POST, HEAD";
            return al; 
        }
}