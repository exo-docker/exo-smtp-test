package org.exoplatform.smtptest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MainTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int run(Map<String, String> env, String... args) {
    return Main.run(args, env, new PrintStream(out, true), new PrintStream(err, true));
  }

  @Test
  void sendsThroughTheKernelMailServiceWithoutAuth() throws Exception {
    try (StubSmtpServer smtp = new StubSmtpServer(false)) {
      int code = run(Map.of("EXO_MAIL_SMTP_HOST", "127.0.0.1", "EXO_MAIL_SMTP_PORT", String.valueOf(smtp.port()),
                            "EXO_MAIL_FROM", "eXo <from@example.org>"),
                     "--to", "a@example.org,b@example.org", "--subject", "Hello", "--body", "Hi there");
      assertEquals(0, code, err.toString());
      assertTrue(smtp.commands.stream().anyMatch(c -> c.contains("RCPT TO:<b@example.org>")));
      String mail = smtp.messages.get(0);
      assertTrue(mail.contains("Subject: Hello"));
      assertTrue(mail.contains("From: eXo <from@example.org>"));
      assertTrue(mail.contains("Hi there"));
      assertTrue(out.toString().startsWith("SMTP configuration:"));
    }
  }

  @Test
  void authenticatesWhenUsernameIsSet() throws Exception {
    try (StubSmtpServer smtp = new StubSmtpServer(true)) {
      int code = run(Map.of("EXO_MAIL_SMTP_HOST", "127.0.0.1", "EXO_MAIL_SMTP_PORT", String.valueOf(smtp.port()),
                            "EXO_MAIL_SMTP_USERNAME", "user", "EXO_MAIL_SMTP_PASSWORD", "secret"),
                     "--to", "a@example.org");
      assertEquals(0, code, err + " " + smtp.commands);
      assertTrue(smtp.commands.stream().anyMatch(c -> c.endsWith("|user|secret")));
      assertFalse(out.toString().contains("secret"));
    }
  }

  @Test
  void failedAuthenticationExitsWithOne() throws Exception {
    try (StubSmtpServer smtp = new StubSmtpServer(true)) {
      int code = run(Map.of("EXO_MAIL_SMTP_HOST", "127.0.0.1", "EXO_MAIL_SMTP_PORT", String.valueOf(smtp.port()),
                            "EXO_MAIL_SMTP_USERNAME", "user", "EXO_MAIL_SMTP_PASSWORD", "wrong"),
                     "--to", "a@example.org");
      assertEquals(1, code);
      assertTrue(err.toString().startsWith("FAILED"));
    }
  }

  @Test
  void unreachableServerExitsWithOne() {
    assertEquals(1, run(Map.of("EXO_MAIL_SMTP_HOST", "127.0.0.1", "EXO_MAIL_SMTP_PORT", "1"), "--to", "a@example.org"));
  }

  @Test
  void toIsRequiredButShowConfigNeedsNothing() {
    assertEquals(2, run(Map.of()));
    assertEquals(0, run(Map.of(), "--show-config"));
    assertEquals(2, run(Map.of(), "--bogus"));
  }
}
