package org.exoplatform.smtptest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class MailSettingsTest {

  @Test
  void defaultsMatchTheImage() {
    MailSettings s = new MailSettings(Map.of());
    Properties p = s.mailProperties();
    assertEquals("noreply@exoplatform.com", s.from());
    assertEquals("localhost", p.getProperty("mail.smtp.host"));
    assertEquals("25", p.getProperty("mail.smtp.port"));
    assertEquals("false", p.getProperty("mail.smtp.starttls.enable"));
    assertEquals("false", p.getProperty("mail.smtp.auth"));
    assertNull(p.getProperty("mail.smtp.socketFactory.class"));
  }

  @Test
  void dashUsernameMeansNoAuth() {
    Properties p = new MailSettings(Map.of("EXO_MAIL_SMTP_USERNAME", "-", "EXO_MAIL_SMTP_PASSWORD", "x")).mailProperties();
    assertEquals("false", p.getProperty("mail.smtp.auth"));
  }

  @Test
  void usernameEnablesAuthAndPasswordIsMasked() {
    MailSettings s = new MailSettings(Map.of("EXO_MAIL_SMTP_USERNAME", "u", "EXO_MAIL_SMTP_PASSWORD", "topsecret"));
    Properties p = s.mailProperties();
    assertEquals("true", p.getProperty("mail.smtp.auth"));
    assertEquals("u", p.getProperty("mail.smtp.auth.username"));
    assertEquals("topsecret", p.getProperty("mail.smtp.auth.password"));
    assertFalse(s.describe().contains("topsecret"));
  }

  @Test
  void sslUsesSmtpPortUnlessOverridden() {
    Properties p = new MailSettings(Map.of("EXO_SMTP_SSL_ENABLED", "true", "EXO_MAIL_SMTP_PORT", "465",
                                           "EXO_SMTP_SSL_PROTOCOLS", "TLSv1.2")).mailProperties();
    assertEquals("465", p.getProperty("mail.smtp.socketFactory.port"));
    assertEquals("javax.net.ssl.SSLSocketFactory", p.getProperty("mail.smtp.socketFactory.class"));
    assertEquals("TLSv1.2", p.getProperty("mail.smtp.ssl.protocols"));
    p = new MailSettings(Map.of("EXO_SMTP_SSL_ENABLED", "true", "EXO_MAIL_SMTP_PORT", "465",
                                "EXO_MAIL_SSL_SOCKETFACTORY_PORT", "587")).mailProperties();
    assertEquals("587", p.getProperty("mail.smtp.socketFactory.port"));
  }

  @Test
  void sslFallbackIsOnlySetWhenDisabledAndSslEnabled() {
    Map<String, String> ssl = Map.of("EXO_SMTP_SSL_ENABLED", "true");
    assertNull(new MailSettings(ssl).mailProperties().getProperty("mail.smtp.socketFactory.fallback"));
    assertEquals("false", new MailSettings(ssl).sslFallback(false).mailProperties()
                                               .getProperty("mail.smtp.socketFactory.fallback"));
    assertNull(new MailSettings(Map.of()).sslFallback(false).mailProperties()
                                         .getProperty("mail.smtp.socketFactory.fallback"));
  }
}
