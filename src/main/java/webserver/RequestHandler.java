package webserver;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                queryString.forEach((k, v) -> {
                    try {
                        queryString.put(k, URLDecoder.decode(v, "utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        log.error(e.getMessage());
                    }
                });
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

            if (requestMethod.equals("GET") && requestURL.equals("/user/list.html")) {
                String cookies = headers.stream()
                        .filter(header -> header.getKey().equals("Cookie"))
                        .map(Pair::getValue)
                        .findAny()
                        .orElse("");

                boolean logined = Boolean.parseBoolean(parseCookies(cookies).get("logined"));
                if (logined) {
                    DataOutputStream dos = new DataOutputStream(out);
                    StringBuilder sb = new StringBuilder();
                    sb.append("<!DOCTYPE html>\n" +
                            "<html lang=\"kr\">\n" +
                            "<head>\n" +
                            "    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n" +
                            "    <meta charset=\"utf-8\">\n" +
                            "    <title>SLiPP Java Web Programming</title>\n" +
                            "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\">\n" +
                            "    <link href=\"../css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
                            "    <!--[if lt IE 9]>\n" +
                            "    <script src=\"//html5shim.googlecode.com/svn/trunk/html5.js\"></script>\n" +
                            "    <![endif]-->\n" +
                            "    <link href=\"../css/styles.css\" rel=\"stylesheet\">\n" +
                            "</head>\n" +
                            "<body>\n" +
                            "<nav class=\"navbar navbar-fixed-top header\">\n" +
                            "    <div class=\"col-md-12\">\n" +
                            "        <div class=\"navbar-header\">\n" +
                            "\n" +
                            "            <a href=\"../index.html\" class=\"navbar-brand\">SLiPP</a>\n" +
                            "            <button type=\"button\" class=\"navbar-toggle\" data-toggle=\"collapse\" data-target=\"#navbar-collapse1\">\n" +
                            "                <i class=\"glyphicon glyphicon-search\"></i>\n" +
                            "            </button>\n" +
                            "\n" +
                            "        </div>\n" +
                            "        <div class=\"collapse navbar-collapse\" id=\"navbar-collapse1\">\n" +
                            "            <form class=\"navbar-form pull-left\">\n" +
                            "                <div class=\"input-group\" style=\"max-width:470px;\">\n" +
                            "                    <input type=\"text\" class=\"form-control\" placeholder=\"Search\" name=\"srch-term\" id=\"srch-term\">\n" +
                            "                    <div class=\"input-group-btn\">\n" +
                            "                        <button class=\"btn btn-default btn-primary\" type=\"submit\"><i class=\"glyphicon glyphicon-search\"></i></button>\n" +
                            "                    </div>\n" +
                            "                </div>\n" +
                            "            </form>\n" +
                            "            <ul class=\"nav navbar-nav navbar-right\">\n" +
                            "                <li>\n" +
                            "                    <a href=\"#\" class=\"dropdown-toggle\" data-toggle=\"dropdown\"><i class=\"glyphicon glyphicon-bell\"></i></a>\n" +
                            "                    <ul class=\"dropdown-menu\">\n" +
                            "                        <li><a href=\"https://slipp.net\" target=\"_blank\">SLiPP</a></li>\n" +
                            "                        <li><a href=\"https://facebook.com\" target=\"_blank\">Facebook</a></li>\n" +
                            "                    </ul>\n" +
                            "                </li>\n" +
                            "                <li><a href=\"../user/list.html\"><i class=\"glyphicon glyphicon-user\"></i></a></li>\n" +
                            "            </ul>\n" +
                            "        </div>\n" +
                            "    </div>\n" +
                            "</nav>\n" +
                            "<div class=\"navbar navbar-default\" id=\"subnav\">\n" +
                            "    <div class=\"col-md-12\">\n" +
                            "        <div class=\"navbar-header\">\n" +
                            "            <a href=\"#\" style=\"margin-left:15px;\" class=\"navbar-btn btn btn-default btn-plus dropdown-toggle\" data-toggle=\"dropdown\"><i class=\"glyphicon glyphicon-home\" style=\"color:#dd1111;\"></i> Home <small><i class=\"glyphicon glyphicon-chevron-down\"></i></small></a>\n" +
                            "            <ul class=\"nav dropdown-menu\">\n" +
                            "                <li><a href=\"../user/profile.html\"><i class=\"glyphicon glyphicon-user\" style=\"color:#1111dd;\"></i> Profile</a></li>\n" +
                            "                <li class=\"nav-divider\"></li>\n" +
                            "                <li><a href=\"#\"><i class=\"glyphicon glyphicon-cog\" style=\"color:#dd1111;\"></i> Settings</a></li>\n" +
                            "            </ul>\n" +
                            "            \n" +
                            "            <button type=\"button\" class=\"navbar-toggle\" data-toggle=\"collapse\" data-target=\"#navbar-collapse2\">\n" +
                            "            \t<span class=\"sr-only\">Toggle navigation</span>\n" +
                            "            \t<span class=\"icon-bar\"></span>\n" +
                            "            \t<span class=\"icon-bar\"></span>\n" +
                            "            \t<span class=\"icon-bar\"></span>\n" +
                            "            </button>            \n" +
                            "        </div>\n" +
                            "        <div class=\"collapse navbar-collapse\" id=\"navbar-collapse2\">\n" +
                            "            <ul class=\"nav navbar-nav navbar-right\">\n" +
                            "                <li class=\"active\"><a href=\"../index.html\">Posts</a></li>\n" +
                            "                <li><a href=\"../user/login.html\" role=\"button\">로그인</a></li>\n" +
                            "                <li><a href=\"../user/form.html\" role=\"button\">회원가입</a></li>\n" +
                            "                <li><a href=\"#\" role=\"button\">로그아웃</a></li>\n" +
                            "                <li><a href=\"#\" role=\"button\">개인정보수정</a></li>\n" +
                            "            </ul>\n" +
                            "        </div>\n" +
                            "    </div>\n" +
                            "</div>\n" +
                            "\n" +
                            "<div class=\"container\" id=\"main\">\n" +
                            "   <div class=\"col-md-10 col-md-offset-1\">\n" +
                            "      <div class=\"panel panel-default\">\n" +
                            "          <table class=\"table table-hover\">\n" +
                            "              <thead>\n" +
                            "                <tr>\n" +
                            "                    <th>#</th> <th>사용자 아이디</th> <th>이름</th> <th>이메일</th><th></th>\n" +
                            "                </tr>\n" +
                            "              </thead>\n" +
                            "              <tbody>");

                    int number = 1;
                    for (User user : DataBase.findAll()) {
                        sb.append(String.format("<tr>\n" +
                                "                    <th scope=\"row\">%d</th> <td>%s</td> <td>%s</td> <td>%s</td><td><a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a></td>\n" +
                                "                </tr>", number++, user.getUserId(), user.getName(), user.getEmail()));
                    }

                    sb.append("</tbody>\n" +
                            "          </table>\n" +
                            "        </div>\n" +
                            "    </div>\n" +
                            "</div>\n" +
                            "\n" +
                            "<!-- script references -->\n" +
                            "<script src=\"../js/jquery-2.2.0.min.js\"></script>\n" +
                            "<script src=\"../js/bootstrap.min.js\"></script>\n" +
                            "<script src=\"../js/scripts.js\"></script>\n" +
                            "\t</body>\n" +
                            "</html>");
                    response200Header(dos, sb.length(), "text/html");
                    responseBody(dos, sb.toString().getBytes());
                } else {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos, "/user/login.html");
                }
                return;
            }

            String accept = headers.stream()
                    .filter(header -> header.getKey().equals("Accept"))
                    .map(Pair::getValue)
                    .findAny()
                    .orElse("");

            if (requestMethod.equals("GET") && accept.startsWith("text/css")) {
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + requestURL).toPath());
                response200Header(dos, body.length, "text/css");
                responseBody(dos, body);
                return;
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./webapp" + requestURL).toPath());
            response200Header(dos, body.length, "text/html");
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
        return parseQueryString(bodyData);
    }

    private void addCookie(DataOutputStream dos, String cookie) {
        try {
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String contentType) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: " + contentType + ";charset=utf-8\r\n");
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
