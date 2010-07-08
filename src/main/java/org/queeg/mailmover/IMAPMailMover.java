package org.queeg.mailmover;

import java.io.FileInputStream;
import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.log4j.Logger;

public class IMAPMailMover implements Runnable {
  private final Logger log = Logger.getLogger(getClass());

  private String imapHost;
  private String username;
  private String password;
  private String folderName;
  private int freq = 60000;

  private MessageForwarder messageListener;

  public void run() {
    try {
      Properties props = System.getProperties();

      // Get a Session object
      Session session = Session.getInstance(props, null);
      // session.setDebug(true);

      // Get a Store object
      Store store = session.getStore("imap");

      log.info("Mail Mover starting");

      // Connect
      store.connect(imapHost, username, password);

      // Open a Folder
      Folder folder = store.getFolder(folderName);
      if (folder == null || !folder.exists()) {
        log.fatal("Invalid folder");
        System.exit(1);
      }
      
      log.info("Checking for existing unread mail on IMAP server");
      folder.open(Folder.READ_WRITE);
      Message[] unread = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
      getMessageListener().forwardMessages(unread);
      folder.close(false);
      log.info("Done with existing unread mail. Waitingf for new mail.");
      
      for (;;) {
        try {
          folder.open(Folder.READ_WRITE);

          // Add messageCountListener to listen for new messages
          folder.addMessageCountListener(getMessageListener());

          // Check mail once in "freq" MILLIseconds
          for (;;) {
            Thread.sleep(freq); // sleep for freq milliseconds

            // This is to force the IMAP server to send us
            // EXISTS notifications.
            folder.getMessageCount();
          }
        } catch (FolderClosedException e) {
          // Ignore to work around idle timeouts on Exchange
          log.debug("Folder closed. Reopening");
        }
      }
    } catch (Exception e) {
      log.fatal(e, e);
    }
  }

  public String getImapHost() {
    return imapHost;
  }

  public void setImapHost(String imapHost) {
    this.imapHost = imapHost;
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

  public String getFolderName() {
    return folderName;
  }

  public void setFolderName(String folderName) {
    this.folderName = folderName;
  }

  public int getFreq() {
    return freq;
  }

  public void setFreq(int freq) {
    this.freq = freq;
  }

  public void setMessageListener(MessageForwarder messageListener) {
    this.messageListener = messageListener;
  }

  public MessageForwarder getMessageListener() {
    return messageListener;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: IMAPMailMover <properties file>");
      System.exit(1);
    }

    FileInputStream is = new FileInputStream(args[0]);
    Properties props = new Properties();
    props.load(is);
    is.close();

    String imapHost = props.getProperty("mailmover.imap.host");
    String imapUsername = props.getProperty("mailmover.imap.username");
    String imapPassword = props.getProperty("mailmover.imap.password");
    String imapFolder = props.getProperty("mailmover.imap.folder", "INBOX");
    int imapFrequency = Integer.parseInt(props.getProperty("mailmover.imap.frequency", "60000"));

    String outputProtocol = props.getProperty("mailmover.output.protocol");
    String outputMailHost = props.getProperty("mailmover.output.mailhost");
    int outputPort = Integer.parseInt(props.getProperty("mailmover.output.port", "25"));
    String outputUsername = props.getProperty("mailmover.output.username");
    String outputPassword = props.getProperty("mailmover.output.password");
    String outputTag = props.getProperty("mailmover.output.tag", "Queeg");
    String outputAddress = props.getProperty("mailmover.output.address");
    boolean outputAuth = Boolean.parseBoolean(props.getProperty("mailmover.output.auth", "false"));
    boolean outputDebug = Boolean.parseBoolean(props.getProperty("mailmover.output.debug", "false"));

    MessageForwarder listener = new MessageForwarder();
    listener.setProtocol(outputProtocol);
    listener.setMailhost(outputMailHost);
    listener.setPort(outputPort);
    listener.setUsername(outputUsername);
    listener.setPassword(outputPassword);
    listener.setTag(outputTag);
    listener.setAddress(outputAddress);
    listener.setAuth(outputAuth);
    listener.setDebug(outputDebug);

    IMAPMailMover t = new IMAPMailMover();
    t.setMessageListener(listener);
    t.setImapHost(imapHost);
    t.setUsername(imapUsername);
    t.setPassword(imapPassword);
    t.setFolderName(imapFolder);
    t.setFreq(imapFrequency);

    t.run();
  }
}
