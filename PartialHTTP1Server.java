package partialhttp1server;

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
		BlockingQueue < Runnable > threadQueue = new SynchronousQueue < > ();
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
				clientSocket.setSoTimeout(3000);
				clientThreadPool.execute(new ServerThread(clientSocket));
			} catch (RejectedExecutionException rej) {
				PrintStream outToClient = new PrintStream(clientSocket.getOutputStream());
				outToClient.println("HTTP/1.0 503 Service Unavailable\r\n");
				outToClient.println("\r\n");
				outToClient.flush();
				outToClient.close();
				clientSocket.close();
			} catch (SocketTimeoutException e) {
				PrintStream outToClient = new PrintStream(clientSocket.getOutputStream());
				outToClient.println("HTTP/1.0 408 Request Timeout\r\n");
				outToClient.println("\r\n");
				outToClient.flush();
				outToClient.close();
				clientSocket.close();
			} catch (IOException e) {
				PrintStream outToClient = new PrintStream(clientSocket.getOutputStream());
				outToClient.println("HTTP/1.0 500 Internal Error\r\n");
				outToClient.println("\r\n");
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
	PrintStream outToClient;
	DataOutputStream outToClient2;
	PrintStream outToClient3;
	//Setup Thread input and output to client
	public ServerThread(Socket clientSocket) throws IOException {
		this.clientSocket = clientSocket;
		try {
			inFromClient = new BufferedReader(
			new InputStreamReader(clientSocket.getInputStream()));
			outToClient = new PrintStream(clientSocket.getOutputStream());
			outToClient2 = new DataOutputStream(clientSocket.getOutputStream());
			outToClient3 = new PrintStream(clientSocket.getOutputStream());
		} catch (IOException e) {
			//print error message and exit program
			outToClient.println("HTTP/1.0 500 Internal Error\r\n");
			outToClient.println("\r\n");
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
		Integer ctLength = null;
		String[] tokens = null;
		String[] lineTokens = null;
		String input = "";
		String[] lines = null;
		String temp = "";
                String contentType2 = "";
		float HTTP;
		String[] HTTPversion;
		int ifModLine = 0;
		Boolean printHeader = true;
		// Regular expressions for valid commands
		String pattern = "\\bGET\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern2 = "\\b(LINK|UNLINK|DELETE|PUT)\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern3 = "\\bHEAD\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern4 = "\\bGET\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
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
			boolean matchesPOST = Pattern.matches(pattern4, lines[0]);
			boolean matchesIfMod = false;
			boolean postErrorCheck = true;
			boolean postErrorCheck2 = true;
                        

			for (String s: lines) {
				lineTokens = s.split(" ");
				ifModLine++;
				if (lineTokens[0].equals("If-Modified-Since:")) {
					matchesIfMod = true;
					break;
				}
                                if (lineTokens[0].equals("Content-Type:")) {
                                        contentType2 = tokens[1];
                                }

			}
			//matches valid HTTP command
			if (matchesPOST || matchesGET || matchesHEAD) {
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
						String contentType = getContentType(dir + tokens[1]);
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
									outToClient.println("HTTP/1.0 304 Not Modified\r");
									outToClient.println("Last-Modified: " + lastModified + "\r");
									outToClient.println("Expires: " + expireDate + "\r");
									outToClient.println("\r");
									printHeader = false;
									fileReader.close();
								}
							} catch (ParseException pe) {}
						}

						//checks if post should throw an error immediately
						if (matchesPOST) {
							if (file.canExecute()) {
								if (ctLength == null && (!contentType2.equals("application/x-www-form-urlencoded"))) {
									outToClient.println("HTTP/1.0 411 Length Required");
									postErrorCheck = false;
									postErrorCheck2 = false;
								} else {
									if (!contentType2.equals("application/x-www-form-urlencoded")) {
										outToClient.println("HTTP/1.0 500 Internal Server Error\r\n");
										outToClient.println("\r\n");
										postErrorCheck = false;
									}
									if (ctLength == null) {
										outToClient.println("HTTP/1.0 411 Length Required");
										outToClient.println("\r\n");
										postErrorCheck2 = false;
									}
								}
							} else {
								outToClient.println("HTTP/1.0 403 Forbidden\r\n");
								outToClient.println("\r\n");
							}
						}

						if (printHeader && postErrorCheck && postErrorCheck2) {

							// print Header information
							outToClient.println("HTTP/1.0 200 OK" + "\r");
							outToClient.println("Content-Type: " + contentType + "\r");
							outToClient.println("Content-Length: " + fileLength + "\r");
							outToClient.println("Last-Modified: " + lastModified + "\r");
							outToClient.println("Content-Encoding: identity\r");
							outToClient.println("Allow: GET, POST, HEAD\r");
							outToClient.println("Expires: " + expireDate + "\r");
							outToClient.println("\r");
							// if command/request is get or post get file contents
							if (matchesGET) {
								if (contentType.equals("text/plain")) {
									for (String s: list) {
										outToClient2.writeBytes(s);
									}
								} else {
									byte[] bytes = convertDocToByteArray(dir + tokens[1]);
									outToClient.write(bytes);
								}
							}

						}
						fileReader.close();
					} catch (IOException e) {
						outToClient.println("HTTP/1.0 404 Not Found\r\n");
						outToClient.println("\r\n");
					}
				} else {
					if (!file.exists()) {
						outToClient.println("HTTP/1.0 404 Not Found\r\n");
						outToClient.println("\r\n");
					} else {
						outToClient.println("HTTP/1.0 403 Forbidden\r\n");
						outToClient.println("\r\n");
					}
				}
			} else if (matchesNotImplemented) {
				outToClient.println("HTTP/1.0 501 Not Implemented\r\n");
				outToClient.println("\r\n");
			} else {
				outToClient.println("HTTP/1.0 400 Bad Request\r\n");
				outToClient.println("\r\n");
			}
		} catch (UnsupportedOperationException e) {
			outToClient3.print("HTTP/1.0 505 HTTP Version Not Supported\r\n");
			outToClient3.print("\r\n");
		} catch (SocketTimeoutException e) {
			outToClient3.print("HTTP/1.0 408 Request Timeout\r\n");
			outToClient3.print("\r\n");
		} catch (IOException e) {
			outToClient3.print("HTTP/1.0 500 Internal Error\r\n");
			outToClient3.print("\r");
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
	// find type of file
	private static String getContentType(String fileName) {
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
                if (fileName.endsWith(".cgi")) {
			return "application/x-www-form-urlencoded";
		}
		return "application/octet-stream";
	}

	public static byte[] convertDocToByteArray(String sourcePath) throws IOException {

		File file = new File(sourcePath);
		byte[] bFile = new byte[(int) file.length()];

		FileInputStream fileInputStream = new FileInputStream(file);
		fileInputStream.read(bFile);
		fileInputStream.close();
                
                String decoded = new String(bFile, "UTF-8");
                String recode = "";
                recode = decodemyURL(decoded);
                
		return bFile;
	}
        
        public static byte[] convertDocToByteArray2(String sourcePath) throws IOException {
                String decoded = "";
		File file = new File(sourcePath);
		byte[] bFile = new byte[(int) file.length()];

		FileInputStream fileInputStream = new FileInputStream(file);
		fileInputStream.read(bFile);
		fileInputStream.close();
                
                String makestring = new String(bFile, "UTF-8");
                decoded = decodemyURL(makestring);
                byte[] b = decoded.getBytes("UTF-8");
                
		return b;
	}
        
        
        public static String decodemyURL(String url) throws UnsupportedEncodingException {
            String result = "";
            return result = java.net.URLDecoder.decode(url, "UTF-8");
        }
}