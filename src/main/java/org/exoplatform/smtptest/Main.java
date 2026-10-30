package org.exoplatform.smtptest;

import java.io.PrintStream;
import java.util.Map;

import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.PropertiesParam;
import org.exoplatform.services.mail.Message;
import org.exoplatform.services.mail.impl.MailServiceImpl;

/**
 * Sends one test mail through the eXo kernel MailServiceImpl, configured from
 * the same EXO_MAIL_* variables as the eXo Platform image.
 */
public final class Main {

  static final String USAGE = """
      Usage: exo-smtp-test --to a@x.org[,b@y.org] [--subject S] [--body B] [--text] [--no-ssl-fallback] [--cc ..] [--bcc ..] [--reply-to ..]
             exo-smtp-test --show-config

      SMTP settings come from the environment, as in the eXo Platform image:
        EXO_MAIL_FROM (noreply@exoplatform.com)  EXO_MAIL_SMTP_HOST (localhost)  EXO_MAIL_SMTP_PORT (25)
        EXO_MAIL_SMTP_STARTTLS (false)  EXO_MAIL_SMTP_USERNAME (-)  EXO_MAIL_SMTP_PASSWORD (-)
        EXO_SMTP_SSL_ENABLED (false)  EXO_MAIL_SSL_SOCKETFACTORY_PORT (SMTP port)  EXO_SMTP_SSL_PROTOCOLS
      --no-ssl-fallback: with EXO_SMTP_SSL_ENABLED=true, fail instead of falling back to plain SMTP when SSL cannot be set up.
      Exit code: 0 sent, 1 send failed, 2 bad arguments.
      """;

  private Main() {
  }

  public static void main(String[] args) {
    System.exit(run(args, System.getenv(), System.out, System.err));
  }

  static int run(String[] args, Map<String, String> env, PrintStream out, PrintStream err) {
    String to = null, cc = null, bcc = null, replyTo = null;
    String subject = "eXo SMTP configuration test";
    String body = null;
    boolean text = false, showConfig = false, sslFallback = true;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--to" -> to = value(args, ++i);
        case "--cc" -> cc = value(args, ++i);
        case "--bcc" -> bcc = value(args, ++i);
        case "--reply-to" -> replyTo = value(args, ++i);
        case "--subject" -> subject = value(args, ++i);
        case "--body" -> body = value(args, ++i);
        case "--text" -> text = true;
        case "--show-config" -> showConfig = true;
        case "--no-ssl-fallback" -> sslFallback = false;
        case "-h", "--help" -> {
          out.print(USAGE);
          return 0;
        }
        default -> {
          err.println("unknown or incomplete option: " + args[i]);
          err.print(USAGE);
          return 2;
        }
      }
    }
    MailSettings settings = new MailSettings(env).sslFallback(sslFallback);
    out.println("SMTP configuration:");
    out.print(settings.describe());
    if (showConfig) {
      return 0;
    }
    if (to == null || to.isBlank()) {
      err.println("--to is required");
      err.print(USAGE);
      return 2;
    }
    try {
      InitParams params = new InitParams();
      PropertiesParam config = new PropertiesParam();
      config.setName("config");
      settings.mailProperties().forEach((k, v) -> config.setProperty((String) k, (String) v));
      params.addParameter(config);
      MailServiceImpl service = new MailServiceImpl(params, new ExoContainerContext(null, "exo-smtp-test"));

      Message message = new Message();
      message.setFrom(settings.from());
      message.setTo(to);
      message.setCC(cc);
      message.setBCC(bcc);
      message.setReplyTo(replyTo);
      message.setSubject(subject);
      message.setMimeType(text ? "text/plain" : "text/html");
      message.setBody(body != null ? body : "This is a test mail sent by exo-smtp-test with the SMTP settings above.");
      service.sendMessage(message);
      out.println("OK: mail sent to " + to + " via " + service.getOutgoingMailServer());
      return 0;
    } catch (Exception e) {
      err.println("FAILED: " + e);
      for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
        err.println("  caused by: " + t);
      }
      return 1;
    }
  }

  private static String value(String[] args, int i) {
    return i < args.length ? args[i] : "";
  }
}
