package com.voxeo.sipmethod.sample.osgi;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class OSGiServlet extends HttpServlet {

  private static final long serialVersionUID = -423961370879245445L;

  /**
   * Delegate to doPost
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
      IOException {

    Map map = new HashMap();

    BundleContext ctx = (BundleContext) getServletContext().getAttribute("osgi-bundlecontext");

    final ServiceReference configurationAdminReference = ctx.getServiceReference(ConfigurationAdmin.class.getName());
    if (configurationAdminReference != null) {
      ConfigurationAdmin configAdmin = (ConfigurationAdmin) ctx.getService(configurationAdminReference);
      Configuration[] configurations = null;
      try {
        configurations = configAdmin.listConfigurations(null);
      }
      catch (InvalidSyntaxException e1) {
        e1.printStackTrace();
      }
      if (configurations != null) {
        for (Configuration configuration : configurations) {
          Dictionary properties = configuration.getProperties();
          if (properties != null) {
            for (Enumeration e = properties.keys(); e.hasMoreElements();) {
              Object key = e.nextElement();
              map.put(key, properties.get(key));
            }
          }
        }
      }
    }

    final PrintWriter out = response.getWriter();
    response.setContentType("text/html");

    out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\r\n");
    out.println("<HTML>\r\n");
    out.println("  <HEAD><TITLE>OSGi Sample Servlet</TITLE></HEAD>\r\n");
    out.println("  <BODY>\r\n");
    out.println("  <br/><br/>");
    out.println("  <CENTER><H3>The Properties of OSGi Server</H3></CENTER>\r\n");

    out.println("  <TABLE BORDER=1 ALIGN=CENTER WIDTH=70%>\r\n");
    out.println("    <TR>\r\n");
    out.println("      <TH>Key</TH><TH>Value</TH>\r\n");
    out.println("    </TR>\r\n");
    final Iterator iter = map.keySet().iterator();
    while (iter.hasNext()) {
      final Object key = iter.next();
      out.println("    <TR>\r\n");
      out.println("      <TD>" + String.valueOf(key) + "</TD>\r\n");
      out.println("      <TD>" + String.valueOf(map.get(key)) + "</TD>\r\n");
      out.println("    </TR>\r\n");
    }
    out.println("  </TABLE>");

    out.println("  </BODY>\r\n");
    out.println("</HTML>\r\n");
    out.flush();
    out.close();

  }

  /**
   * Generate a HTML page to show the current register user agents.
   */
  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
      IOException {
    doGet(request, response);
  }
}
