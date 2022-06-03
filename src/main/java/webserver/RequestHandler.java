package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;

import static util.HttpRequestUtils.*;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String firstLine = br.readLine();
            String requestMethod = firstLine.split(" ")[0];
            String requestURL = firstLine.split(" ")[1];

            List<Pair> headers = parseHeaders(br);

            if (requestMethod.equals("POST") && requestURL.startsWith("/user/create")) {
                Map<String, String> queryString = createQueryString(headers, br);
                User user = new User(queryString.get("userId"),
                        queryString.get("password"),
                        queryString.get("name"),
                        queryString.get("email"));
                DataBase.addUser(user);

                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
                return;
            }

            if (requestMethod.equals("POST") && requestURL.startsWith("/user/login")) {
                Map<String, String> queryString = createQueryString(headers, br);
                String userId = queryString.get("userId");
                User user = DataBase.findUserById(userId);
                if (user == null || !user.matchPassword(queryString.get("password"))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302HeaderWithCookie(dos, "/user/login_failed.html", "logined=false");
                } else {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302HeaderWithCookie(dos, "/index.html", "logined=true");
                    addCookie(dos, "logined=true");
                }
                return;
            }

            if (requestMethod.equals("GET") && requestURL.equals("/user/list")) {
                String cookies = headers.stream()
                        .filter(header -> header.getKey().equals("Cookie"))
                        .map(Pair::getValue)
                        .findAny()
                        .orElse("");

                boolean logined = Boolean.parseBoolean(parseCookies(cookies).get("logined"));
                if (logined) {
                    DataOutputStream dos = new DataOutputStream(out);
                    StringBuilder sb = new StringBuilder();
                    sb.append("<ul>");
                    DataBase.findAll()
                            .stream()
                            .map(user -> String.format("<li>%s</li>", user.getName()))
                            .forEach(sb::append);
                    sb.append("</ul>");
                    response200Header(dos, sb.length());
                    responseBody(dos, sb.toString().getBytes());
                } else {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos, "/user/login.html");
                }
                return;
            }

            if (requestMethod.equals("GET")) {
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + requestURL).toPath());
                response200Header(dos, body.length);
                responseBody(dos, body);
                return;
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = "Hello World!!".getBytes();
            response200Header(dos, body.length);
            responseBody(dos, body);

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private List<Pair> parseHeaders(BufferedReader br) throws IOException {
        List<Pair> headers = new ArrayList<>();

        String headerLine = br.readLine();
        while (headerLine != null && !headerLine.equals("")) {
            log.info(headerLine);
            headers.add(parseHeader(headerLine));
            headerLine = br.readLine();
        }

        return headers;
    }

    private Map<String, String> createQueryString(List<Pair> headers, BufferedReader br) throws IOException {
        int contentLength = headers.stream()
                .filter(header -> header.getKey().equals("Content-Length"))
                .map(header -> Integer.parseInt(header.getValue()))
                .findAny()
                .orElse(0);

        String bodyData = IOUtils.readData(br, contentLength);
        Map<String, String> queryString = parseQueryString(bodyData);
        return queryString;
    }

    private void addCookie(DataOutputStream dos, String cookie) {
        try {
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String location) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302HeaderWithCookie(DataOutputStream dos, String location, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
