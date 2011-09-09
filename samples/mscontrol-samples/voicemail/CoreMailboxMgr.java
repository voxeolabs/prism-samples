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

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

/**
 * Mailbox manager. Note: This class is not JSR309-related code. It is provided
 * only for completeness.
 */
class CoreMailboxMgr {
	
	private static HashMap<String, CoreMailbox> mailboxList = new HashMap<String, CoreMailbox>();

	/**
	 * Retrieve the mailbox associated to aUserId If it does not exist, creates
	 * it.
	 */
	static public synchronized CoreMailbox getMailbox(String userId) {
		CoreMailbox mbx = mailboxList.get(userId);
		if (mbx == null) {
			mbx = new CoreMailbox(userId);
			mailboxList.put(userId, mbx);
		} else {
			mbx.addUser();
		}
		return mbx;
	}

	static public synchronized void releaseMailbox(String userId) {
		CoreMailbox mbx = mailboxList.get(userId);
		if (mbx == null)
			return;
		mbx.removeUser();
		if (mbx.canBeRemoved()) {
			mailboxList.remove(userId);
		}
	}

	static public synchronized void copyMessage(URI from, URI to) {
		try {
			FileReader fromReader = new FileReader(from.toString());
			FileWriter toReader = new FileWriter(to.toString());
			char[] content = new char[64];
			while (fromReader.read(content) != -1) {
				// System.out.println("copy content =>"+content);
				toReader.write(content);
			}
			fromReader.close();
			toReader.close();
		} catch (IOException e) {
			System.out.println("error when copying " + from + " to " + to
					+ " " + e);
		}
	}

}
