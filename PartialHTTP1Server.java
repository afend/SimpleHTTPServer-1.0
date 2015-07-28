/* Adam Fendler & Matthew Eder 
 * Internet Technology CS352 Summer 2015 Rutgers
 * Project 1: HTTP 1.0 Webserver
 */
import java.io.*;
import java.net.*;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.*;

import javax.activation.MimetypesFileTypeMap;

public class PartialHTTP1Server {
	public static void main(String[] args) throws Exception {
		//get port # from args[0] and start the server
		int port = Integer.parseInt(args[0]);
		PartialHTTP1Server server = new PartialHTTP1Server(port);
		server.startServer();
	}
	//end of main
	//========================================================================================================
	//Server class
	ServerSocket welcomeSocket = null;
	Socket clientSocket = null;
	int port;

	public PartialHTTP1Server(int port) {
		this.port = port;
	}

	public void startServer() throws IOException {
		//open server on specified port
		try {
			welcomeSocket = new ServerSocket(port);
		} catch (IOException e) {
			System.err.println(e);
		}
		//create blocking queue and thread pool for server
		int corePoolSize = 5; // always have 5 idle threads at minimum
		int maxPoolSize = 50; // can hold up to 50 threads before 503
		long keepAliveTime = 0;
		BlockingQueue < Runnable > threadQueue = new ArrayBlockingQueue < Runnable > (50);
		//ThreadFactory threadFactory = new threadFactory();
		ThreadPoolExecutor clientThreadPool = new ThreadPoolExecutor(
		corePoolSize,
		maxPoolSize,
		keepAliveTime,
		TimeUnit.MILLISECONDS,
		threadQueue);
		clientThreadPool.prestartAllCoreThreads();
		//accept new socket connections from client and create a new thread to handle request
		while (true) {
			try {
				clientSocket = welcomeSocket.accept();
				//clientThreadPool.setThreadFactory((ThreadFactory)new ServerThread(clientSocket)); // casting no no
				clientSocket.setSoTimeout(10000); //Timeout mechanism change to 3000 is 3 seconds
				clientThreadPool.execute(new ServerThread(clientSocket));
			} catch (RejectedExecutionException rej) {
				DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
				outToClient.writeChars("HTTP/1.0 503 Service Unavailable");
				outToClient.flush();
				outToClient.close();
				clientSocket.close();
			} catch (SocketTimeoutException e) {
				DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
				outToClient.writeChars("HTTP/1.0 408 Request Timeout");
				outToClient.flush();
				outToClient.close();
				clientSocket.close();
			} catch (IOException e) {
				DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
				outToClient.writeChars("HTTP/1.0 500 Internal Error");
				System.err.println(e);
				outToClient.flush();
				outToClient.close();
				clientSocket.close();
			}
		}
	}
}

class ServerThread implements Runnable {
	//initialize variables instantiated in the try/catch block	
	Socket clientSocket;
	PartialHTTP1Server server;
	BufferedReader inFromClient;
	DataOutputStream outToClient;
	PrintStream outToClient2;
	//Setup Thread input and output to client
	public ServerThread(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;
		try {
			inFromClient = new BufferedReader(
			new InputStreamReader(clientSocket.getInputStream()));
			outToClient = new DataOutputStream(clientSocket.getOutputStream());
			//Used only in try/catch blocks in run() where writeChars throws IOException for whatever reason
			outToClient2 = new PrintStream(clientSocket.getOutputStream());

		} catch (IOException e) {
			//print error message and exit program
			outToClient.writeChars("HTTP/1.0 500 Internal Error");
			System.out.println(e);
			outToClient.flush();
			return;
		}
	}

	public void run() {
		//initialize variables instantiated in the try/catch block	
		// tokens[0] HTTP command
		// tokens[1] file
		// tokens[2] HTTP version
		String[] tokens = null;
		String[] lineTokens = null;
		String input = "";
		String[] lines = null;
		String temp = "";
		float HTTP;
		String[] HTTPversion;
		int ifModLine = 0;
		// Regular expressions for valid commands
		String pattern = "\\b(GET|POST)\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern2 = "\\b(LINK|UNLINK|DELETE|PUT)\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern3 = "\\bHEAD\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		//String pattern4 = "\\bIf\\b-\\bModified\\b-\\bSince\\b:\\S.*";
		try {
			// read input/request from client 
			while (true) {
				temp = inFromClient.readLine();
				if (temp == null || temp.isEmpty()) break;
				input = input + temp + "\r\n";
			}
			//Parse input into separate lines
			lines = input.split("\\r\\n");
			//Parse 1st line of input by spaces to get command, file, version
			tokens = lines[0].split(" ");
			//Variables for regular expression matches
			boolean matchesGET = Pattern.matches(pattern, lines[0]); // also matches POST
			boolean matchesNotImplemented = Pattern.matches(pattern2, lines[0]);
			boolean matchesHEAD = Pattern.matches(pattern3, lines[0]);
			boolean matchesIfMod = false;
			for (String s: lines) {
				lineTokens = s.split(" ");
				ifModLine++;
				if (lineTokens[0].equals("If-Modified-Since:")) {
					matchesIfMod = true;
					break;
				}
			}
			//matches valid HTTP command
			if (matchesGET || matchesHEAD) {
				//check version of HTTP version is supported
				HTTPversion = tokens[2].split("/");
				HTTP = Float.parseFloat(HTTPversion[1]);
				if (HTTP > 1) {
					throw new UnsupportedOperationException();
				}
				//find the path to the file
				File curDir = new File(".");
				String dir = curDir.getAbsolutePath().substring(0, curDir.getAbsolutePath().length() - 2);
				File file = new File(dir + tokens[1]);

				//initialize variables instantiated in the try/catch block	
				BufferedReader fileReader;
				ArrayList < String > list;
				String line;
				if (file.canRead()) {
					try {
						// read from file and print contents to client
						fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
						list = new ArrayList < String > ();
						while ((line = fileReader.readLine()) != null) {
							list.add(line);
						}
						// find header information
						// Content-Type get mime type of the file
						String contentType = new MimetypesFileTypeMap().getContentType(dir + tokens[1]);
						// check for supported file type 
						if (!(contentType.equals("text/html") || contentType.equals("text/plain") || contentType.equals("image/gif") || contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("application/octet-stream") || contentType.equals("application/pdf") || contentType.equals("application/x-gzip") || contentType.equals("application/zip"))) {
							contentType = "application/octet-stream";
						}

						// Content-Length: length of the file 
						long fileLength = file.length();
						// Last-Modified: when the file was last modified
						Date dateModified = new Date(file.lastModified());
						SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
						formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
						String Date2Str = formatter.format(dateModified);
						String lastModified = Date2Str;
						// Expires: set some future expiration date
						Calendar ex = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
						ex.add(Calendar.DAY_OF_MONTH, 10);
						String expireDate = formatter.format(ex.getTime());
						//check if modified condition
						Date ifModDate = null;
						if (!matchesHEAD && matchesIfMod) {
							String ifModDateStr = lines[ifModLine - 1].substring(19); //length of If-Modified-Since:
							Calendar ifModCalDate = Calendar.getInstance();
							try {
								ifModCalDate.setTime(formatter.parse(ifModDateStr));
								ifModDate = ifModCalDate.getTime();
								if (dateModified.before(ifModDate)) {
									outToClient.writeChars("HTTP/1.0 304 Not Modified\r");
									outToClient.writeChars("Last-Modified: " + lastModified + "\r");
									outToClient.writeChars("Expires: " + expireDate + "\r");
									fileReader.close();
								}
							} catch (ParseException pe) {}
						}

						if (!dateModified.before(ifModDate)) {
							// print Header information
							outToClient.writeChars("HTTP/1.0 200 OK" + "\r"); // header to be added
							outToClient.writeChars("Content-Type: " + contentType + "\r");
							outToClient.writeChars("Content-Length: " + fileLength + "\r");
							outToClient.writeChars("Last-Modified: " + lastModified + "\r");
							outToClient.writeChars("Content-Encoding: identity\r");
							outToClient.writeChars("Allow: GET, POST, HEAD\r");
							outToClient.writeChars("Expires: " + expireDate + "\r");
							outToClient.writeChars("\r");
							// if command/request is get or post get file contents
							if (matchesGET) {
								for (String s: list) {
									outToClient.writeBytes(s);
								}
							}
							fileReader.close();
						}
					} catch (IOException e) {
						outToClient.writeChars("HTTP/1.0 404 Not Found");
					}
				} else {
					outToClient.writeChars("HTTP/1.0 403 Forbidden");
				}
			} else if (matchesNotImplemented) {
				outToClient.writeChars("HTTP/1.0 501 Not Implemented");
			} else {
				outToClient.writeChars("HTTP/1.0 400 Bad Request");
			}
		} catch (UnsupportedOperationException e) {
			outToClient2.println("HTTP/1.0 505 HTTP Version Not Supported");
		} catch (SocketTimeoutException e) {
			outToClient2.println("HTTP/1.0 408 Request Timeout");
		} catch (IOException e) {
			outToClient2.println("HTTP/1.0 500 Internal Error");
			System.out.println(e);
		} finally {
			//flush to make sure output is sent before closing
			//wait from half a second
			//close everything
			try {
				outToClient.flush();
				Thread.sleep(500);
				inFromClient.close();
				outToClient.close();
				clientSocket.close();
				return;
			} catch (InterruptedException ie) {
				try {
					inFromClient.close();
					outToClient.close();
					clientSocket.close();
				} catch (IOException e) {
					System.err.println(e);
				}
			} catch (IOException e) {
				System.err.println(e);
			}
		}
	}
}