import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HTTPServer {
	private static final int PORT = 8080;

	private int contentLength;

	public static void main(String[] args) {
		HTTPServer httpServer = new HTTPServer();

		try {
			ServerSocket httpServerSocket= new ServerSocket(PORT);
			ExecutorService executorService = Executors.newFixedThreadPool(10);
			System.out.println("Listening at port " + PORT);

			while (true) {
				Socket socket = httpServerSocket.accept();
				if (socket != null) {
					executorService.submit(() -> {
						try {
							httpServer.handleRequest(socket);
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			}
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	private void handleRequest(Socket socket) throws Exception {
		System.out.println("Received Connection!");

		Map<ContentHeader, String> headerContent = new HashMap<>();
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

		String firstLine = in.readLine();
		if(firstLine == null || firstLine.isEmpty()) {
			return ;
		}

		Method method;
		try {
			method = Method.valueOf(firstLine.split(" ")[0].toUpperCase());
		} catch (IllegalArgumentException e) {
			System.out.println("Illegal Http Method");
			String msg = "Method Not Allowed";
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 405, out, msg.getBytes(StandardCharsets.UTF_8).length);
			return ;
		}

		String content = firstLine.split(" ")[1];

		String line;
		while ((line = in.readLine()) != null) {
			if (line.isEmpty()) {
				break;
			}

			try {
				String headerContentField = line.split(": ")[0];
				String headerContentValue = line.split(": ")[1];
				ContentHeader headerField = ContentHeader.fromString(headerContentField);
				if (headerField != null) {
					headerContent.put(headerField, headerContentValue);
				}
			} catch (IllegalArgumentException ignored) {
			}
		}
		HTTPHeader httpHeader = new HTTPHeader(headerContent.get(ContentHeader.CONTENTTYPE), Integer.parseInt(headerContent.get(ContentHeader.CONTENTLENGTH)), content, method);

		if (httpHeader.getMethod().equals(Method.GET)) {
			handleGet(httpHeader, out);
		} else if (httpHeader.getMethod().equals(Method.POST)) {
			handlePost(httpHeader, in, out);
		} else if (httpHeader.getMethod().equals(Method.PUT)) {
			handlePut(httpHeader, in, out);;
		} else if (httpHeader.getMethod().equals(Method.DELETE)) {
			handleDelete(httpHeader, out);
		} else {
			String msg = "Method Not Allowed";
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 405, out, msg.getBytes(StandardCharsets.UTF_8).length);
		}

		socket.close();
	}

	private void handleDelete(HTTPHeader httpHeader, PrintWriter out) {
		try {
			Path filePath = Paths.get("." + httpHeader.getContent());

			if(!Files.exists(filePath)) {
				String msg = "Not Exists!!";
				sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 404, out, msg.getBytes(StandardCharsets.UTF_8).length);
				return;
			}
			Files.delete(filePath);

			String msg = "Successfully deleted";
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 200, out, msg.getBytes(StandardCharsets.UTF_8).length);
		} catch (Exception ignore) {
		}
	}

	private void handlePut(HTTPHeader httpHeader, BufferedReader in, PrintWriter out) {
		try {
			MIMETypes mimeTypes = MIMETypes.fromString(httpHeader.getContentType());
			if(!mimeTypes.equals(MIMETypes.TEXTPLAIN)) {
				String msg = "Unsupported MIME type for Post Request";
				sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 400, out, msg.getBytes(StandardCharsets.UTF_8).length);
			}

			byte[] bodyContent = getBody(in, httpHeader.getContentLength());
			Path filePath = Paths.get("." + httpHeader.getContent());

			if(!Files.exists(filePath)) {
				Files.createFile(filePath);
			}
			Files.write(filePath, bodyContent);

			String msg = "Successfully put";
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 200, out, msg.getBytes(StandardCharsets.UTF_8).length);
		} catch (Exception e) {
			sendResponse(e.getMessage(), MIMETypes.TEXTPLAIN.getValue(), 400, out, e.getMessage().getBytes(StandardCharsets.UTF_8).length);
		}
	}

	private void handlePost(HTTPHeader httpHeader, BufferedReader in, PrintWriter out) {
		try {
			MIMETypes mimeTypes = MIMETypes.fromString(httpHeader.getContentType());
			if(!mimeTypes.equals(MIMETypes.TEXTPLAIN)) {
				String msg = "Unsupported MIME type for Post Request";
				sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 400, out, msg.getBytes(StandardCharsets.UTF_8).length);
			}
			byte[] bodyContent = getBody(in, httpHeader.getContentLength());
			Files.write(Paths.get("." + httpHeader.getContent()), bodyContent, StandardOpenOption.APPEND);

			String msg = "Successfully posted";
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 200, out, msg.getBytes(StandardCharsets.UTF_8).length);
		} catch (Exception e) {
			sendResponse(e.getMessage(), MIMETypes.TEXTPLAIN.getValue(), 400, out, e.getMessage().getBytes(StandardCharsets.UTF_8).length);
		}
	}

	private byte[] getBody (BufferedReader in, int length) throws IOException {
		byte[] body = new byte[length];

		for(int i = 0; i < length; i++) {
			body[i] = (byte) in.read();
		}

		return body;
	}

	private void handleGet(HTTPHeader httpHeader, PrintWriter out) {
		try {
			MIMETypes mimeTypes = MIMETypes.fromString(httpHeader.getContentType());
			File file = new File("." + httpHeader.getContent());

			FileInputStream fileInputStream = new FileInputStream(file);
			byte[] fileContent = fileInputStream.readAllBytes();
			if(fileContent.length == 0) {
				String msg = "Unable to find indicated file";
				sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 400, out, msg.getBytes(StandardCharsets.UTF_8).length);
			} else {
				assert mimeTypes != null;
				sendResponse(new String(fileContent), mimeTypes.getValue(), 200, out, (int) file.length());
			}

		} catch (Exception e) {
			String msg = "Unsupported MIME type for Get Request";
			System.out.println("Unsupported MIME type for Get Request");
			sendResponse(msg, MIMETypes.TEXTPLAIN.getValue(), 400, out, msg.getBytes(StandardCharsets.UTF_8).length);
		}
	}

	private void sendResponse(String content, String mimeType, int statusCode, PrintWriter out, int length) {
		out.printf("HTTP/1.1 %d \r\n", statusCode);
		out.printf("Content-Type: %s\r\n", mimeType);
		out.printf("Content-Length: %s\r\n", length);
		out.printf("\r\n");
		out.write(content);
		out.flush();
	}

	public static class HTTPHeader {
		private final String contentType;
		private final int contentLength;
		private final String content;
		private final Method method;

		public HTTPHeader(String contentType, int contentLength, String content, Method method) {
			this.contentType = contentType;
			this.contentLength = contentLength;
			this.content = content;
			this.method = method;
		}


		public String getContentType() {
			return contentType;
		}

		public int getContentLength() {
			return contentLength;
		}

		public String getContent() {
			return content;
		}

		public Method getMethod() {
			return method;
		}
	}
}

enum Method {
	POST, GET, PUT, DELETE;
}

enum ContentHeader {
	CONTENTTYPE("Content-Type"),
	CONTENTLENGTH("Content-Length");

	private final String header;

	ContentHeader(String header) {
		this.header = header;
	}

	public String getHeader() {
		return this.header;
	}

	public static ContentHeader fromString(String text) {
		for (ContentHeader header: ContentHeader.values()) {
			if(header.getHeader().equals(text)) {
				return header;
			}
		}
		return null;
	}
}

enum MIMETypes {
	TEXTPLAIN("text/plain"),
	TEXTHTML("text/html"),
	;

	private final String value;

	MIMETypes(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static MIMETypes fromString(String text) {
		for (MIMETypes type: MIMETypes.values()) {
			if(type.getValue().equals(text)) {
				return type;
			}
		}
		return null;
	}
}