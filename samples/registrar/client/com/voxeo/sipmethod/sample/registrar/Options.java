package com.voxeo.sipmethod.sample.registrar;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;

public class Options {

  String args[] = null;

  Vector usedArgs = null;

  URL defaultURL = null;

  /**
   * Constructor - just pass in the <b>args</b> from the command line.
   */
  public Options(String _args[]) throws MalformedURLException {
    if (_args == null) {
      _args = new String[] {};
    }
    args = _args;
    usedArgs = null;
    defaultURL = new URL("http://localhost:8080/sipmethod/WSServlet");

    try {
      getURL();
    }
    catch (MalformedURLException e) {
      throw e;
    }
    getUser();
    getPassword();
  }

  public void setDefaultURL(String url) throws MalformedURLException {
    defaultURL = new URL(url);
  }

  public void setDefaultURL(URL url) {
    defaultURL = url;
  }

  public String getDefaultURL() {
    if (defaultURL != null) {
      return defaultURL.toString();
    }
    return null;
  }

  /**
   * Returns an int specifying the number of times that the flag was specified on the command line. Once this flag is
   * looked for you must save the result because if you call it again for the same flag you'll get zero.
   */
  public int isFlagSet(char optChar) {
    int value = 0;
    int loop;
    int i;

    for (loop = 0; usedArgs != null && loop < usedArgs.size(); loop++) {
      String arg = (String) usedArgs.elementAt(loop);
      if (arg.charAt(0) != '-')
        continue;
      for (i = 0; i < arg.length(); i++)
        if (arg.charAt(i) == optChar)
          value++;
    }

    for (loop = 0; loop < args.length; loop++) {
      if (args[loop] == null || args[loop].length() == 0)
        continue;
      if (args[loop].charAt(0) != '-')
        continue;
      while (args[loop] != null && (i = args[loop].indexOf(optChar)) != -1) {
        args[loop] = args[loop].substring(0, i) + args[loop].substring(i + 1);
        if (args[loop].length() == 1)
          args[loop] = null;
        value++;
        if (usedArgs == null)
          usedArgs = new Vector();
        usedArgs.add("-" + optChar);
      }
    }
    return (value);
  }

  /**
   * Returns a string (or null) specifying the value for the passed option. If the option isn't there then null is
   * returned. The option's value can be specified one of two ways: -x value -xvalue Note that: -ax value is not value
   * (meaning flag 'a' followed by option 'x'. Options with values must be the first char after the '-'. If the option
   * is specified more than once then the last one wins.
   */
  public String isValueSet(char optChar) {
    String value = null;
    int loop;
    int i;

    for (loop = 0; usedArgs != null && loop < usedArgs.size(); loop++) {
      String arg = (String) usedArgs.elementAt(loop);
      if (arg.charAt(0) != '-' || arg.charAt(1) != optChar)
        continue;
      value = arg.substring(2);
      if (loop + 1 < usedArgs.size())
        value = (String) usedArgs.elementAt(++loop);
    }

    for (loop = 0; loop < args.length; loop++) {
      if (args[loop] == null || args[loop].length() == 0)
        continue;
      if (args[loop].charAt(0) != '-')
        continue;
      i = args[loop].indexOf(optChar);
      if (i != 1)
        continue;
      if (i != args[loop].length() - 1) {
        // Not at end of arg, so use rest of arg as value
        value = args[loop].substring(i + 1);
        args[loop] = args[loop].substring(0, i);
      }
      else {
        // Remove the char from the current arg
        args[loop] = args[loop].substring(0, i);

        // Nothing after char so use next arg
        if (loop + 1 < args.length && args[loop + 1] != null) {
          // Next arg is there and non-null
          if (args[loop + 1].charAt(0) != '-') {
            value = args[loop + 1];
            args[loop + 1] = null;
          }
        }
        else {
          // Next is null or not there - do nothing
          // value = null ;
        }
      }
      if (args[loop].length() == 1)
        args[loop] = null;
      // For now, keep looping to get that last on there
      // break ;
    }
    if (value != null) {
      if (usedArgs == null)
        usedArgs = new Vector();
      usedArgs.add("-" + optChar);
      if (value.length() > 0)
        usedArgs.add(value);
    }
    return (value);
  }

  /**
   * This just returns a string with the unprocessed flags - mainly for error reporting - so you can report the unknown
   * flags.
   */
  public String getRemainingFlags() {
    StringBuffer sb = null;
    int loop;

    for (loop = 0; loop < args.length; loop++) {
      if (args[loop] == null || args[loop].length() == 0)
        continue;
      if (args[loop].charAt(0) != '-')
        continue;
      if (sb == null)
        sb = new StringBuffer();
      sb.append(args[loop].substring(1));
    }
    return (sb == null ? null : sb.toString());
  }

  /**
   * This returns an array of unused args - these are the non-option args from the command line.
   */
  public String[] getRemainingArgs() {
    ArrayList al = null;
    int loop;

    for (loop = 0; loop < args.length; loop++) {
      if (args[loop] == null || args[loop].length() == 0)
        continue;
      if (args[loop].charAt(0) == '-')
        continue;
      if (al == null)
        al = new ArrayList();
      al.add((String) args[loop]);
    }
    if (al == null)
      return (null);
    String a[] = new String[al.size()];
    for (loop = 0; loop < al.size(); loop++)
      a[loop] = (String) al.get(loop);
    return (a);
  }

  public String getURL() throws MalformedURLException {
    String tmp;
    String host = null; // -h also -l (url)
    String port = null; // -p
    String servlet = null; // -s also -f (file)
    String protocol = null;

    URL url = null;

    if ((tmp = isValueSet('l')) != null) {
      url = new URL(tmp);
      host = url.getHost();
      port = "" + url.getPort();
      servlet = url.getFile();
      protocol = url.getProtocol();
    }

    if ((tmp = isValueSet('f')) != null) {
      host = "";
      port = "-1";
      servlet = tmp;
      protocol = "file";
    }

    tmp = isValueSet('h');
    if (host == null)
      host = tmp;
    tmp = isValueSet('p');
    if (port == null)
      port = tmp;
    tmp = isValueSet('s');
    if (servlet == null)
      servlet = tmp;

    if (host == null)
      host = defaultURL.getHost();
    if (port == null)
      port = "" + defaultURL.getPort();
    if (servlet == null)
      servlet = defaultURL.getFile();
    else if (servlet.length() > 0 && servlet.charAt(0) != '/')
      servlet = "/" + servlet;

    if (url == null) {
      if (protocol == null)
        protocol = defaultURL.getProtocol();
      tmp = protocol + "://" + host;
      if (port != null && !port.equals("-1"))
        tmp += ":" + port;
      if (servlet != null)
        tmp += servlet;
    }
    else
      tmp = url.toString();
    return (tmp);
  }

  public String getHost() {
    try {
      URL url = new URL(getURL());
      return (url.getHost());
    }
    catch (Exception exp) {
      return ("localhost");
    }
  }

  public int getPort() {
    try {
      URL url = new URL(getURL());
      return (url.getPort());
    }
    catch (Exception exp) {
      return (-1);
    }
  }

  public String getUser() {
    return (isValueSet('u'));
  }

  public String getPassword() {
    return (isValueSet('w'));
  }
}
