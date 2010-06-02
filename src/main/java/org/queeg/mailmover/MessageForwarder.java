/**
 * 
 */
package org.queeg.mailmover;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.log4j.Logger;

import com.sun.mail.smtp.SMTPTransport;

public class MessageForwarder extends MessageCountAdapter {
  private final Logger log = Logger.getLogger(getClass());

  private Deque<String> seenIds;
  private int MAX_SEEN_SIZE = 100;

  private String protocol;
  private String mailhost;
  private int port;
  private String username;
  private String password;
  private String tag;
  private String address;
  private boolean auth = false;
  private boolean debug = false;

  public MessageForwarder() {
    seenIds = new ArrayDeque<String>();
  }

  @Override
  public void messagesAdded(MessageCountEvent ev) {
    Properties props = new Properties(System.getProperties());
    if (auth) {
      props.put("mail." + protocol + ".auth", "true");
    }

    if (port > 0) {
      props.put("mail." + protocol + ".port", Integer.toString(port));
    }

    props.put("mail." + protocol + ".host", mailhost);

    // Get a Session object
    Session session = Session.getInstance(props, null);
    session.setDebug(debug);

    Message[] messages = ev.getMessages();
    for (Message imapMessage : messages) {
      try {
        String[] messageIds = imapMessage.getHeader("Message-ID");
        String messageId;

        if (messageIds == null || messageIds.length == 0) {
          log.warn("No Message-ID headers");
          messageId = imapMessage.getSubject();
        } else {
          messageId = messageIds[0];
        }

        if (seenIds.contains(messageId)) {
          log.debug("Already seen " + messageId);
        } else {
          seenIds.addFirst(messageId);
          if (seenIds.size() > MAX_SEEN_SIZE) {
            seenIds.removeLast();
          }

          MimeMessage msg = new MimeMessage((MimeMessage) imapMessage);
          msg.addHeader("X-MailMover-Tag", tag);
          log.info(msg.getSubject());

          Address a = new InternetAddress(getAddress());

          SMTPTransport transport = (SMTPTransport) session.getTransport(protocol);
          if (auth) {
            transport.connect(mailhost, port, username, password);
          } else {
            transport.connect();
          }
          transport.sendMessage(msg, new Address[] { a });
          transport.close();
        }

      } catch (MessagingException e) {
        log.error(e, e);
      }
    }
    try {
      Thread.sleep(60000);
    } catch (InterruptedException e) {
      log.warn("Sleeping after forwarding mail was interrupted", e);
    }
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getMailhost() {
    return mailhost;
  }

  public void setMailhost(String mailhost) {
    this.mailhost = mailhost;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getAddress() {
    return address;
  }

  public boolean isAuth() {
    return auth;
  }

  public void setAuth(boolean auth) {
    this.auth = auth;
  }

  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }
}