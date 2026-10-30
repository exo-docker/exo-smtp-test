package org.exoplatform.smtptest;

import java.util.Map;
import java.util.Properties;

/**
 * Turns the EXO_MAIL_* environment variables of the eXo Platform image into the
 * mail.smtp.* properties handed to the kernel MailServiceImpl.
 *
 * <p>Same chain as the platform: bin/setenv-docker-customize.sh (env to
 * exo.email.smtp.*), then configuration.properties (exo.* to gatein.email.smtp.*,
 * with defaults), then portal-mail-configuration.xml (gatein.* to mail.smtp.*).
 */
final class MailSettings {

  private final Properties exo = new Properties();

  private boolean sslFallback = true;

  MailSettings(Map<String, String> env) {
    String from = envOrDefault(env, "EXO_MAIL_FROM", "noreply@exoplatform.com");
    String port = envOrDefault(env, "EXO_MAIL_SMTP_PORT", "25");
    String username = envOrDefault(env, "EXO_MAIL_SMTP_USERNAME", "-");

    exo.setProperty("exo.email.smtp.from", from);
    exo.setProperty("exo.email.smtp.host", envOrDefault(env, "EXO_MAIL_SMTP_HOST", "localhost"));
    exo.setProperty("exo.email.smtp.port", port);
    exo.setProperty("exo.email.smtp.starttls.enable", envOrDefault(env, "EXO_MAIL_SMTP_STARTTLS", "false"));
    if ("-".equals(username)) {
      exo.setProperty("exo.email.smtp.auth", "false");
    } else {
      exo.setProperty("exo.email.smtp.auth", "true");
      exo.setProperty("exo.email.smtp.username", username);
      exo.setProperty("exo.email.smtp.password", envOrDefault(env, "EXO_MAIL_SMTP_PASSWORD", "-"));
    }
    if ("false".equals(envOrDefault(env, "EXO_SMTP_SSL_ENABLED", "false"))) {
      exo.setProperty("exo.email.smtp.socketFactory.port", "");
      exo.setProperty("exo.email.smtp.socketFactory.class", "");
    } else {
      exo.setProperty("exo.email.smtp.socketFactory.port",
                      envOrDefault(env, "EXO_MAIL_SSL_SOCKETFACTORY_PORT", port));
      exo.setProperty("exo.email.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    }
    String protocols = env.get("EXO_SMTP_SSL_PROTOCOLS");
    if (protocols != null && !protocols.isEmpty()) {
      exo.setProperty("mail.smtp.ssl.protocols", protocols);
    }
  }

  /**
   * JavaMail falls back to plain SMTP when the SSL socket cannot be created
   * (mail.smtp.socketFactory.fallback defaults to true, as in the platform);
   * false makes that a hard failure. Only relevant with EXO_SMTP_SSL_ENABLED=true.
   */
  MailSettings sslFallback(boolean enabled) {
    this.sslFallback = enabled;
    return this;
  }

  /** The "from" address (gatein.email.smtp.from). */
  String from() {
    return exo.getProperty("exo.email.smtp.from");
  }

  /** The properties of the "config" param of the platform's MailService. */
  Properties mailProperties() {
    Properties p = new Properties();
    p.setProperty("mail.smtp.host", exo.getProperty("exo.email.smtp.host"));
    p.setProperty("mail.smtp.port", exo.getProperty("exo.email.smtp.port"));
    p.setProperty("mail.smtp.starttls.enable", exo.getProperty("exo.email.smtp.starttls.enable"));
    p.setProperty("mail.smtp.auth", exo.getProperty("exo.email.smtp.auth"));
    p.setProperty("mail.smtp.auth.username", exo.getProperty("exo.email.smtp.username", ""));
    p.setProperty("mail.smtp.auth.password", exo.getProperty("exo.email.smtp.password", ""));
    // configuration.properties defaults
    p.setProperty("mail.smtp.connectiontimeout", "60000");
    p.setProperty("mail.smtp.timeout", "60000");
    p.setProperty("mail.smtp.writetimeout", "60000");
    p.setProperty("mail.smtp.debug", System.getProperty("exo.email.smtp.debug", "false"));
    String sfPort = exo.getProperty("exo.email.smtp.socketFactory.port");
    String sfClass = exo.getProperty("exo.email.smtp.socketFactory.class");
    if (!sfClass.isEmpty()) {
      p.setProperty("mail.smtp.socketFactory.port", sfPort);
      p.setProperty("mail.smtp.socketFactory.class", sfClass);
      if (!sslFallback) {
        p.setProperty("mail.smtp.socketFactory.fallback", "false");
      }
    }
    String protocols = exo.getProperty("mail.smtp.ssl.protocols");
    if (protocols != null) {
      p.setProperty("mail.smtp.ssl.protocols", protocols);
    }
    p.setProperty("mail.transport.protocol", "smtp");
    return p;
  }

  /** Human-readable settings, the password never included. */
  String describe() {
    Properties p = mailProperties();
    StringBuilder sb = new StringBuilder();
    for (String k : new String[] {"mail.smtp.host", "mail.smtp.port", "mail.smtp.starttls.enable", "mail.smtp.auth",
                                  "mail.smtp.auth.username", "mail.smtp.socketFactory.port",
                                  "mail.smtp.socketFactory.class", "mail.smtp.socketFactory.fallback",
                                  "mail.smtp.ssl.protocols"}) {
      if (p.containsKey(k)) {
        sb.append(String.format("  %-34s %s%n", k, p.getProperty(k)));
      }
    }
    if ("true".equals(p.getProperty("mail.smtp.auth"))) {
      sb.append(String.format("  %-34s %s%n", "mail.smtp.auth.password", "********"));
    }
    return String.format("  %-34s %s%n", "from", from()) + sb;
  }

  private static String envOrDefault(Map<String, String> env, String name, String def) {
    String v = env.get(name);
    return v == null || v.isEmpty() ? def : v;
  }
}
