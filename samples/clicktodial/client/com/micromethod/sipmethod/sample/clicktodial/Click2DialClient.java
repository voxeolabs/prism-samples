package com.micromethod.sipmethod.sample.clicktodial;

import java.net.URL;

import javax.xml.namespace.QName;

public class Click2DialClient {

  public static void main(String[] args) {
    new Click2DialClient().makeCall(args);
  }

  public void makeCall(String[] args) {
    try {
      Options opts = new Options(args);
      String user1 = null;
      String user2 = null;
      args = opts.getRemainingArgs();
      if (args != null && args.length == 2) {
        user1 = args[0];
        user2 = args[1];
      }
      else {
        usage();
        return;
      }
      Click2DialImpl port = null;
      if (!opts.getURL().equalsIgnoreCase(opts.getDefaultURL())) {
        port = new Click2DialImplService(new URL(opts.getURL() + "?wsdl"), new QName("http://clicktodial.sample.sipmethod.micromethod.com/", "Click2DialImplService")).getClick2DialImplPort();
      }
      else {
        port = new Click2DialImplService().getClick2DialImplPort();
      }
      System.out.println(port.makeCall(user1, user2));
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void usage() {
    System.out.println("Usage: java -cp %JAXWSRILIB% com.micromethod.sipmethod.sample.clicktodial.Click2DialClient <user1> <user2>");
    System.out.println("   required parameters:");
    System.out.println("       user1    -- registered user1 (eg:  sip:user1@domain.com).");
    System.out.println("       user2    -- registered user2 (eg:  sip:user2@domain.com).");
    System.out.println("   optional parameters:");
    System.out.println("       -l<url>  -- service url (eg: -lhttp://localhost:8080/clicktodial/makecall).");
  }
}
