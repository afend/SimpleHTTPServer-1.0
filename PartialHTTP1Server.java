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

public class HTTP1Server {
	public static void main(String[] args) throws Exception{
		//get port # from args[0] and start the server
		int port = Integer.parseInt(args[0]);
		HTTP1Server server = new HTTP1Server(port);
		server.startServer();
	}
//end of main
//========================================================================================================
//Server class
	ServerSocket welcomeSocket = null;
	Socket clientSocket = null;
	int port; 

	public HTTP1Server(int port){
		this.port = port;
	}

	public void startServer() throws IOException{
		//open server on specified port
		try{
			welcomeSocket = new ServerSocket(port);
		} catch(IOException e){
			System.err.println(e);
		}
		//create blocking queue and thread pool for server
		int  corePoolSize  =    5; // always have 5 idle threads at minimum
		int  maxPoolSize   =   50; // can hold up to 50 threads before 503
		long keepAliveTime = 0; 
		BlockingQueue<Runnable> threadQueue = new SynchronousQueue<Runnable>();
		ThreadPoolExecutor clientThreadPool = 
					 new ThreadPoolExecutor(
		                					corePoolSize,
		                					maxPoolSize,
		                					keepAliveTime,
		    								TimeUnit.MILLISECONDS,
		               						threadQueue);
		clientThreadPool.prestartAllCoreThreads();
		//accept new socket connections from client and create a new thread to handle request
		while(true){
			try{
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
			} catch(IOException e){	
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
	HTTP1Server server;
	BufferedReader inFromClient;
	DataOutputStream outToClient;
	//Setup Thread input and output to client
	public ServerThread(Socket clientSocket) throws IOException{
		this.clientSocket = clientSocket;
		try{ 
			inFromClient = new BufferedReader(
						   new InputStreamReader(clientSocket.getInputStream())); 
			outToClient = new DataOutputStream(clientSocket.getOutputStream());
		} catch(IOException e){
			//print error message and exit program
			outToClient.writeBytes("HTTP/1.0 500 Internal Error\r\n");
			outToClient.writeBytes("\r\n");
			System.out.println(e);
			outToClient.flush();
			outToClient.close();
			return;
		}
		
	}
	
	public void run() {
		//initialize variables instantiated in the try/catch block	
		// tokens[0] HTTP command
		// tokens[1] file
		// tokens[2] HTTP version
		String[] tokens = null;      
		String[] lines = null;       // every line of input from client separated by \r\n
		String[] lineTokens =null;   // tokens in lines[] separated by space    
		String input = "";           // all the input put into one string
		String temp = "";            // used to help make input string
		String userAgent = null;     // content header for User-Agent
		String from = null;			 // content header for From
		float HTTP;                  // HTTP version as float
		String[] HTTPversion;        // HTTP version as string
		int ifModLine = 0;           // line of header where If-Modified-Since: is sent from client
        String contentType2 = "";    // content-type given by client
		Integer ctLength = null;     // length of content-type
		Boolean printHeader = true;  // Used to check if Header needs to printed when If-Modified-Since: is sent from client
		String urlDecoded = ""; 	 // decoded URL 
		String urlEncoded = "";      // message from client percent-encoded
		// Regular expressions for valid commands
		String pattern = "\\bGET\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern2 = "\\b(LINK|UNLINK|DELETE|PUT)\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern3 = "\\bHEAD\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		String pattern4 = "\\bPOST\\b /\\S* \\bHTTP\\b/[0-2].[0-9]";
		
		// read input/request from client 
		try{
			while(true){
				temp = inFromClient.readLine();
				if(temp == null || temp.isEmpty())
					break;
				input = input + temp + "\r\n";
			}
			
			try{
				urlEncoded = inFromClient.readLine();
			}catch(Exception e){
			}
			
			// Parse input into separate lines
			lines = input.split("\\r\\n");
			// Parse 1st line of input by spaces to get command, file, version
			tokens = lines[0].split(" ");
			// Variables for regular expression matches
			boolean matchesGET = Pattern.matches(pattern, lines[0]);
			boolean matchesNotImplemented = Pattern.matches(pattern2, lines[0]);
			boolean matchesHEAD = Pattern.matches(pattern3, lines[0]);
			boolean matchesPOST = Pattern.matches(pattern4, lines[0]);
			
			boolean matchesIfMod = false;
			boolean postErrorCheck = true;
			// check header from client for If-Modified-Since
			for(String s: lines){
				lineTokens = s.split(" ");
				ifModLine++;
				if (lineTokens[0].equals("If-Modified-Since:")){
					matchesIfMod = true;
					break;
				}
			}
			// check header from client for Content-Length
			for(String s: lines){
				lineTokens = s.split(" ");
				if(lineTokens[0].equals("Content-Length:")){
					try{
						ctLength = Integer.parseInt(lineTokens[1]);
					} catch(Exception e){
					}
					break;
				}
			}
			// check header from client for Content-Type
			for(String s: lines){
				lineTokens = s.split(" ");
				if(lineTokens[0].equals("Content-Type:")){
					try{
						contentType2 = lineTokens[1];
					} catch(Exception e){
					}
					break;
				}
			}	
			for(String s: lines){
				lineTokens = s.split(" ");
				if(lineTokens[0].equals("From:")){
					try{
						from = lineTokens[1];
					} catch(Exception e){
					}
					break;
				}
			}	
			for(String s: lines){
				lineTokens = s.split(" ");
				if(lineTokens[0].equals("User-Agent:")){
					try{
						userAgent = lineTokens[1];
					} catch(Exception e){
					}
					break;
				}
			}	
			
			// matches valid HTTP command POST, GET or HEAD
			if(matchesPOST || matchesGET || matchesHEAD){
				// check version of HTTP version is supported
				HTTPversion = tokens[2].split("/");
				HTTP = Float.parseFloat(HTTPversion[1]);
				if(HTTP > 1){
				     throw new UnsupportedOperationException();
				}		
		
				// find the path to the file
				File curDir = new File(".");
				String dir = curDir.getAbsolutePath().substring(0, curDir.getAbsolutePath().length()-2);				
				File file = new File(dir + tokens[1]);
				
				// initialize variables instantiated in the try/catch block	
				BufferedReader fileReader;
				ArrayList<String> list;
				String line;
				
				if (file.canRead()) {
					try{
						// read from file and print contents to client
						fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
						list = new ArrayList<String>();
				    
						while ((line = fileReader.readLine()) != null){
							list.add(line);
						}
						
						// find all header information
						String contentType = getContentType(dir + tokens[1]); // Content-Type get mime type of the file
						
						long fileLength = file.length();                      // Content-Length: length of the file 
						
						// find all date information
						Date dateModified = new Date(file.lastModified()); 	  
						SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
						formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
						String Date2Str = formatter.format(dateModified);
						String lastModified = Date2Str;                       // Last-Modified: when the file was last modified
						
						Calendar ex = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
					    ex.add(Calendar.DAY_OF_MONTH, 10);
						String expireDate = formatter.format(ex.getTime());   // Expires: set some future expiration date
						Date ifModDate = null;                                // Date sent with If-Modified-Since header
						
						// check if If-Modified-Since: Header information was sent 
						if(!matchesHEAD && matchesIfMod){
							String ifModDateStr = lines[ifModLine-1].substring(19); // 19 = length of If-Modified-Since:
							Calendar ifModCalDate = Calendar.getInstance();
							try{
								ifModCalDate.setTime(formatter.parse(ifModDateStr));
								ifModDate = ifModCalDate.getTime();
								// If file has not been modified since date specified send 304 message
								if(dateModified.before(ifModDate)){
									outToClient.writeBytes("HTTP/1.0 304 Not Modified\r\n");
									outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
									outToClient.writeBytes("Expires: " + expireDate + "\r\n");
									outToClient.writeBytes("\r\n");
									printHeader = false;
									fileReader.close();
								}
							}
							catch(ParseException pe) {
							}
						}
						
						// if command matches POST check Content-Type and Content-Length and if file has execute permissions
						if (matchesPOST) {
							if (file.canExecute()) {
								if (ctLength == null) {
									outToClient.writeBytes("HTTP/1.0 411 Length Required\r\n");
									outToClient.writeBytes("\r\n");
									postErrorCheck = false;
								}
								// Content-Type given in header by client
								else if (!contentType2.equals("application/x-www-form-urlencoded")) {
									outToClient.writeBytes("HTTP/1.0 500 Internal Server Error\r\n");
									outToClient.writeBytes("\r\n");
									postErrorCheck = false;
								}
								// Content-Type of the file 
								else if (!contentType.equals("application/x-www-form-urlencoded")) {
									outToClient.writeBytes("HTTP/1.0 405 Method Not Allowed\r\n");
									outToClient.writeBytes("\r\n");
									postErrorCheck = false;
								}
							} else {
								// 405 is given before 403 in test cases
								if(!contentType.equals("application/x-www-form-urlencoded")){
									outToClient.writeBytes("HTTP/1.0 405 Method Not Allowed\r\n");
									outToClient.writeBytes("\r\n");
									postErrorCheck = false;
								} else{
									outToClient.writeBytes("HTTP/1.0 403 Forbidden\r\n");
									outToClient.writeBytes("\r\n");
									postErrorCheck = false;
								}
							}
						}
                         
						if (printHeader && postErrorCheck) {
							// print Header information
							outToClient.writeBytes("HTTP/1.0 200 OK" + "\r\n"); 
							outToClient.writeBytes("Content-Type: " + contentType + "\r\n");
							outToClient.writeBytes("Content-Length: " + fileLength + "\r\n");
							outToClient.writeBytes("Last-Modified: " + lastModified + "\r\n");
							outToClient.writeBytes("Content-Encoding: identity\r\n");
							outToClient.writeBytes("Allow: GET, POST, HEAD\r\n");
							outToClient.writeBytes("Expires: " + expireDate + "\r\n");
							outToClient.writeBytes("\r\n");
							// if command/request is GET send file contents
							if(matchesGET){
								if(contentType.equals("text/plain")){
									for(String s: list){
										outToClient.writeBytes(s);
									}
								}
								else{
									byte[] bytes = convertDocToByteArray(dir + tokens[1]); 
									outToClient.write(bytes);
								}
							}
							else{ // matchesPOST
								int rawByte;
								// System.out.println("Payload: " + urlEncoded);
								urlDecoded = URLDecoder.decode(urlEncoded, "UTF-8");
								// System.out.println("Payload decode: " + urlDecoded);
								//byte[] urlBytes = urlDecoded.getBytes();
								
								// set environment variables
								String[] environment = new String[6]; 
								for(int i = 0; i < environment.length; i++)
									environment[i] = ""; 
								
								environment[0] = ("CONTENT_LENGTH=" + urlDecoded.getBytes().length);
								environment[1] = ("SRCIPT_NAME=" + tokens[1]);
								environment[2] = ("SERVER_NAME=" + clientSocket.getInetAddress());
								environment[3] = ("SERVER_PORT=" + clientSocket.getPort());
								if (from != null)
									environment[4] = ("HTTP_FROM="+from);
								if (userAgent != null)
									environment[5] = ("HTTP_USER_AGENT=" + userAgent);
								
								Process process = Runtime.getRuntime().exec((dir + tokens[1]), environment);
								InputStream stdout = process.getInputStream();
								OutputStream stdin = process.getOutputStream();
								stdin.write(urlDecoded.getBytes());
								
								byte[] b = new byte[urlDecoded.getBytes().length]; 
						
								while((rawByte = stdout.read(b, 0, b.length)) != -1) {
									stdin.write(b, 0, rawByte);
								}
								
								for(int j=0; j < b.length; j++) {
									System.out.println(b[j]);
								}
								//outToClient.write();
							}
							fileReader.close();
						}
					} catch(IOException e){
						outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
						outToClient.writeBytes("\r\n");
					}
					
				}
				else{
					if(!file.exists()){
						outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
						outToClient.writeBytes("\r\n");
					}
					else{
						outToClient.writeBytes("HTTP/1.0 403 Forbidden\r\n");
						outToClient.writeBytes("\r\n");
					}
				}
			}
			else if(matchesNotImplemented){
				outToClient.writeBytes("HTTP/1.0 501 Not Implemented\r\n");
				outToClient.writeBytes("\r\n");
			}
			else{
				outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
				outToClient.writeBytes("\r\n");
			}
		} catch(UnsupportedOperationException e){
			try {
				outToClient.writeBytes("HTTP/1.0 505 HTTP Version Not Supported\r\n");
				outToClient.writeBytes("\r\n");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (SocketTimeoutException e) {
			try {
				outToClient.writeBytes("HTTP/1.0 408 Request Timeout\r\n");
				outToClient.writeBytes("\r\n");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch(IOException e){
			try {
				outToClient.writeBytes("HTTP/1.0 500 Internal Error\r\n");
				outToClient.writeBytes("\r\n");
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.out.println(e);
		}finally{
			//flush to make sure output is sent before closing
			//wait from half a second
			//close everything
			try {
				outToClient.flush();
				Thread.sleep(500);
				inFromClient.close();
				clientSocket.close();
				return;
	         	} catch(InterruptedException ie) {
					try {
						inFromClient.close();
						outToClient.close();
						clientSocket.close();
	            	} catch(IOException e) {
						System.err.println(e);
	            	}
	         } catch(IOException e) {
		         System.err.println(e);
	         }
		}
	}
	// method to find type of file
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
	// method to turn file into array of bytes 
	public static byte[] convertDocToByteArray(String sourcePath) throws IOException 
    { 
        
        File file = new File(sourcePath); 
        byte[] bFile = new byte[(int) file.length()];
 
        FileInputStream fileInputStream = new FileInputStream(file);
	    fileInputStream.read(bFile);
	    fileInputStream.close();
 
        return bFile; 
  }
}



