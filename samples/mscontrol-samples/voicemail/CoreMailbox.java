/* ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Copyright (c) 2008 Hewlett-Packard, Inc. All rights reserved.
 * Copyright (c) 2008 Oracle and/or its affiliates. All rights reserved.
 *
 * Use is subject to license terms.
 * 
 * This code should only be used for further understanding of the
 * specifications and is not of production quality in terms of robustness,
 * scalability etc.
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 */
package voicemail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.net.URI;

import org.apache.log4j.Logger;

/**
 * Handle a voice mailbox. Note: This class is not JSR309-related code. It is
 * provided only for completeness.
 */
class CoreMailbox {
	
	private static Logger log = Logger.getLogger(CoreMailbox.class);
	private File path;
	private File mailboxPath;
	private String absolutePath;
	private File info;
	private int nextMsgId;
	private int msgNb;
	private static final String MESSAGE_PREFIX = "message";
	private int userNb;

	public CoreMailbox(String uid) {
		userNb = 0;
		
		String root = CoreMailbox.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		root = root.substring(0, root.indexOf("WEB-INF"));
    log.debug("mailbox root:" + root);
		String pathName = root + "/mediafiles/voicemailboxes/";
		
		path = new File(pathName);
		if (!path.exists())
			path.mkdir();
		pathName = pathName + uid + "/";
		path = new File(pathName);
		if (!path.exists())
			path.mkdir();
		mailboxPath = new File(pathName, "messages");
		if (!mailboxPath.exists())
			mailboxPath.mkdir();
		absolutePath = mailboxPath.getAbsolutePath() + "/";
		msgNb = mailboxPath.list().length;
		info = new File(pathName, "info");
		try {
			StreamTokenizer f = new StreamTokenizer(new FileReader(info));
			f.parseNumbers();
			f.nextToken();
			if (f.ttype != StreamTokenizer.TT_EOF) {
				nextMsgId = (int) f.nval;
				return;
			}
		} catch (FileNotFoundException e) {
		} catch (IOException ex) {
		}
		// exception processing or empty file
		nextMsgId = 0;
	}

	public int getMessageNumber() {
		return msgNb;
	}

	public URI[] getMessages() {
		String[] msgs = mailboxPath.list();
		URI[] m = new URI[msgs.length];
		for (int ii = 0, jj = 0; ii < msgs.length; ii++) {
			if (msgs[ii].startsWith(MESSAGE_PREFIX)) {
				m[jj] = URI.create(absolutePath + msgs[ii]);
				jj++;
			}
			log.debug("getMessages " + m[ii]);
		}
		return m;
	}

	public synchronized URI newMessage() // throws TooManyMessagesException
	{
		// if (msgNb > MAX_MESSAGES)
		// throw new TooManyMessagesException();
		msgNb++;
		nextMsgId++;
		return URI.create(mailboxPath + "/" + MESSAGE_PREFIX
				+ Integer.toString(nextMsgId));
	}

	public synchronized void removeMessage(URI fileName) {
		log.debug("remove " + fileName);
		msgNb--;
		new File(fileName.toString()).delete();
	}

	public synchronized void update() {
		String s = Integer.toString(nextMsgId);
		log.debug("write in file " + s);
		try {
			FileWriter f = new FileWriter(info);
			f.write(s, 0, s.length());
			f.close();
		} catch (IOException e) {
			log.error("update mailbox info exception " + e, e);
		}
	}

	public synchronized void addUser() {
		userNb++;
	}

	public synchronized void removeUser() {
		userNb--;
	}

	public boolean canBeRemoved() {
		return (userNb == 0);
	}

}
