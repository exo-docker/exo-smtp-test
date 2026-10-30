package org.exoplatform.smtptest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal single-connection-at-a-time SMTP server recording what it receives. */
final class StubSmtpServer implements AutoCloseable {

  final List<String> commands = new CopyOnWriteArrayList<>();
  final List<String> messages = new CopyOnWriteArrayList<>();
  private final ServerSocket server;
  private final boolean requireAuth;

  StubSmtpServer(boolean requireAuth) throws IOException {
    this.requireAuth = requireAuth;
    this.server = new ServerSocket(0);
    Thread t = new Thread(this::serve, "stub-smtp");
    t.setDaemon(true);
    t.start();
  }

  int port() {
    return server.getLocalPort();
  }

  private void serve() {
    while (!server.isClosed()) {
      try (Socket s = server.accept()) {
        session(s);
      } catch (IOException e) {
        // closed
      }
    }
  }

  private void session(Socket s) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
    out.print("220 stub ESMTP\r\n");
    out.flush();
    boolean authed = !requireAuth;
    String line;
    while ((line = in.readLine()) != null) {
      commands.add(line);
      String u = line.toUpperCase();
      if (u.startsWith("EHLO")) {
        out.print("250-stub\r\n" + (requireAuth ? "250 AUTH PLAIN\r\n" : "250 OK\r\n"));
      } else if (u.startsWith("AUTH PLAIN")) {
        String initial = line.length() > 10 ? line.substring(10).trim() : "";
        if (initial.isEmpty()) {
          out.print("334 \r\n");
          out.flush();
          initial = in.readLine();
        }
        String cred = new String(Base64.getDecoder().decode(initial), StandardCharsets.UTF_8);
        commands.add("CRED " + cred.replace('\0', '|'));
        authed = cred.endsWith("\0user\0secret");
        out.print(authed ? "235 ok\r\n" : "535 bad credentials\r\n");
      } else if (u.startsWith("MAIL FROM") || u.startsWith("RCPT TO")) {
        out.print(authed ? "250 OK\r\n" : "530 auth required\r\n");
      } else if (u.equals("DATA")) {
        out.print("354 go\r\n");
        out.flush();
        List<String> data = new ArrayList<>();
        while (!(line = in.readLine()).equals(".")) {
          data.add(line);
        }
        messages.add(String.join("\n", data));
        out.print("250 queued\r\n");
      } else if (u.equals("QUIT")) {
        out.print("221 bye\r\n");
        out.flush();
        return;
      } else {
        out.print("250 OK\r\n");
      }
      out.flush();
    }
  }

  @Override
  public void close() throws IOException {
    server.close();
  }
}
