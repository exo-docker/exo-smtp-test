# exo-smtp-test

Single-jar SMTP configuration tester for eXo Platform. It reads the **same `EXO_MAIL_*` variables as the
[exo-enterprise](https://github.com/exo-docker/exo-enterprise) image**, maps them to `mail.smtp.*` the same way the
platform does, and sends a test mail through the eXo kernel `MailServiceImpl` (`io.meeds.kernel:exo.kernel.component.common`,
a Maven dependency, not a copy). It is independent from the eXo image: use it to validate host, port, TLS and credentials
before putting them in a real deployment.

## Usage

```sh
docker build -t exoplatform/exo-smtp-test .

docker run --rm \
  -e EXO_MAIL_SMTP_HOST=smtp.example.org -e EXO_MAIL_SMTP_PORT=587 -e EXO_MAIL_SMTP_STARTTLS=true \
  -e EXO_MAIL_SMTP_USERNAME=user -e EXO_MAIL_SMTP_PASSWORD=secret \
  -e EXO_MAIL_FROM=noreply@example.org \
  exoplatform/exo-smtp-test --to me@example.org
```

Or without Docker: `mvn package && java -jar target/exo-smtp-test.jar --to me@example.org`.

Options: `--to` (comma separated, required), `--subject`, `--body`, `--text` (default is HTML like the platform),
`--no-ssl-fallback`, `--cc`, `--bcc`, `--reply-to`, `--show-config` (print the resolved settings and exit). The password is never printed.
Exit code: `0` sent, `1` send failed (the cause chain is printed), `2` bad arguments.

## Variables

| Variable | Default | Meaning |
| --- | --- | --- |
| `EXO_MAIL_FROM` | `noreply@exoplatform.com` | "from" of the mail |
| `EXO_MAIL_SMTP_HOST` | `localhost` | SMTP host |
| `EXO_MAIL_SMTP_PORT` | `25` | SMTP port |
| `EXO_MAIL_SMTP_STARTTLS` | `false` | enable STARTTLS |
| `EXO_MAIL_SMTP_USERNAME` | `-` | `-` disables authentication, any other value enables it |
| `EXO_MAIL_SMTP_PASSWORD` | `-` | password (when authenticating) |
| `EXO_SMTP_SSL_ENABLED` | `false` | `true` uses `javax.net.ssl.SSLSocketFactory` (implicit TLS, e.g. port 465) |
| `EXO_MAIL_SSL_SOCKETFACTORY_PORT` | SMTP port | SSL socket factory port |
| `EXO_SMTP_SSL_PROTOCOLS` | - | e.g. `TLSv1.2` (`mail.smtp.ssl.protocols`) |

Timeouts are the platform defaults (60 s). `-Dexo.email.smtp.debug=true` (via `JAVA_TOOL_OPTIONS`) turns on JavaMail debug.

## TLS notes (checked against a real TLS server)

- STARTTLS (`EXO_MAIL_SMTP_STARTTLS=true`, port 587) and implicit SSL (`EXO_SMTP_SSL_ENABLED=true`, port 465, optionally
  `EXO_SMTP_SSL_PROTOCOLS=TLSv1.2`) both work. A certificate that the JVM does not trust is rejected; for a private CA
  pass `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/path/ts.jks -Djavax.net.ssl.trustStorePassword=..."`.
- Same JavaMail behaviour as the platform: with implicit SSL an untrusted certificate shows up after the 60 s read
  timeout (`Read timed out`), not as a certificate error, and if the SSL handshake fails JavaMail falls back to plain SMTP
  (`mail.smtp.socketFactory.fallback` defaults to true). A pass over `EXO_SMTP_SSL_ENABLED=true` does not by itself prove
  the connection was encrypted; check the server log, or pass `--no-ssl-fallback`
  (`mail.smtp.socketFactory.fallback=false`) to make a failed SSL setup a hard failure.

## Development

`mvn verify` (also run by `docker build`) runs the unit tests and an SMTP round trip against an in-process stub server.
