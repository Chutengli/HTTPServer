import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class HTTPServer {
	private static final int PORT = 8080;

	public static void main(String[] args) {
		try {
			HttpServer httpServer = HttpServer.create(new InetSocketAddress(PORT), -1);
			System.out.println("HTTP server started: listening on port " + PORT);

			HttpContext context = httpServer.createContext("/");
			context.setHandler(new HTTPHandler());
			httpServer.start();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	public static class HTTPHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String method = exchange.getRequestMethod().toUpperCase();
			String path = exchange.getRequestURI().getPath();

			switch (method) {
				case "GET" -> handleGet(exchange, path);
				case "POST" -> handlePost(exchange, path);
				case "PUT" -> handlePut(exchange, path);
				case "DELETE" -> handleDelete(exchange, path);
				default -> sendResponse(exchange, "INVALID METHOD".getBytes(StandardCharsets.UTF_8), 400, 0);
			}
		}

		private void handleDelete(HttpExchange exchange, String path) throws IOException {
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");
			if(!checkRequestHeader(contentType, contentLengthStr, exchange)) {
				return ;
			}

			try {
				Path filePath = Paths.get("." + path);

				if(!Files.exists(filePath)) {
					sendResponse(exchange, "File Not Found".getBytes(StandardCharsets.UTF_8), 404, 0);
					return ;
				}
				Files.delete(filePath);
				sendResponse(exchange, "Successfully Deleted".getBytes(StandardCharsets.UTF_8), 200, 0);
			} catch (IOException e) {
				sendResponse(exchange, "Server Error".getBytes(StandardCharsets.UTF_8), 500, 0);
			}
		}

		private void handlePut(HttpExchange exchange, String path) throws IOException {
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");
			if(!checkRequestHeader(contentType, contentLengthStr, exchange)) {
				return ;
			}

			if(Objects.equals(contentType, "image/png")) {
				sendResponse(exchange, "Bad Content-Type".getBytes(StandardCharsets.UTF_8), 400, 0);
				return ;
			}

			try {
				Path filePath = Paths.get("." + path);

				byte[] bodyContent = exchange.getRequestBody().readAllBytes();
				if(!Files.exists(filePath)) {
					Files.createFile(filePath);
				}
				Files.write(filePath, bodyContent);
				sendResponse(exchange, "Successfully put".getBytes(StandardCharsets.UTF_8), 200, "Successfully put".length());
			} catch (IOException e) {
				sendResponse(exchange, "Server Error".getBytes(StandardCharsets.UTF_8), 500, 0);
			}
		}

		private void handlePost(HttpExchange exchange, String path) throws IOException {
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");

			if(!checkRequestHeader(contentType, contentLengthStr, exchange)) {
				return ;
			}

			if(!Objects.equals(contentType, "text/plain")) {
				sendResponse(exchange, "Bad Content-Type".getBytes(StandardCharsets.UTF_8), 400, 0);
				return ;
			}

			try {
				String fileMIMEType = Files.probeContentType(new File(path).toPath());

				if(!fileMIMEType.equals("text/plain")) {
					sendResponse(exchange, "Bad Content-Type".getBytes(StandardCharsets.UTF_8), 400, 0);
				} else {
					byte[] bodyContent = exchange.getRequestBody().readAllBytes();
					Files.write(Paths.get("." + path), bodyContent, StandardOpenOption.APPEND);

					sendResponse(exchange, "Successfully Posted".getBytes(StandardCharsets.UTF_8), 200, "Successfully Posted".length());
				}
			} catch (IOException e) {
				sendResponse(exchange, "File Not Found".getBytes(StandardCharsets.UTF_8), 404, 0);
			}
		}

		private void handleGet(HttpExchange exchange, String path) throws IOException {
			String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
			String contentLengthStr = exchange.getRequestHeaders().getFirst("Content-Length");

			if(!checkRequestHeader(contentType, contentLengthStr, exchange)) {
				return ;
			}

			try {
				String fileMIMEType = Files.probeContentType(new File(path).toPath());

				if(!fileMIMEType.equals(contentType)) {
					sendResponse(exchange, "Bad Content-Type".getBytes(StandardCharsets.UTF_8), 400, 0);
				} else {
					byte[] fileContent = Files.readAllBytes(Paths.get("." + path));
					if(fileContent == null || fileContent.length == 0) {
						sendResponse(exchange, "Server Error".getBytes(StandardCharsets.UTF_8), 500, "Server Error".length());
					} else {
						exchange.getResponseHeaders().set("Content-Type", contentType);

						sendResponse(exchange, fileContent, 200, fileContent.length);
					}
				}
			} catch (IOException e) {
				sendResponse(exchange, "File Not Found".getBytes(StandardCharsets.UTF_8), 404, 0);
			}
		}

		private boolean checkRequestHeader(String contentType, String contentLengthStr, HttpExchange exchange) throws IOException {
			if(contentType == null || contentType.matches("-?\\d+")) {
				sendResponse(exchange, "Bad Content-Type".getBytes(StandardCharsets.UTF_8), 400, 0);
				return false;
			}

			if(contentLengthStr == null || !contentLengthStr.matches("-?\\d+") || Integer.parseInt(contentLengthStr) == 0) {
				sendResponse(exchange, "Bad Content-Length".getBytes(StandardCharsets.UTF_8), 400, 0);
				return false;
			}
			return true;
		}


		private void sendResponse(HttpExchange exchange, byte[] message, int code, int length) throws IOException {
			exchange.sendResponseHeaders(code, length);
			OutputStream outputStream = exchange.getResponseBody();
			outputStream.write(message);
			outputStream.close();
		}
	}
}
