package com.voxeo.sipmethod.sample.registrar;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.sip.URI;

public class QueryAddressServlet extends HttpServlet {

  private static final long serialVersionUID = -3531017675570494090L;

  /**
   * Delegate to doPost
   */
  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
      IOException {
    final ServletContext context = getServletContext();
    final Map addresses = (Map) context.getAttribute("com.micromethod.sipmethod.sample.register.Addresses");
    final PrintWriter out = response.getWriter();

    if (request.getParameter("fromOtherSample") != null) {
      response.setContentType("text/html");

      if (addresses != null) {

        final Iterator iter = addresses.keySet().iterator();
        while (iter.hasNext()) {
          final String from = (String) iter.next();
          final URI uri = (URI) addresses.get(from);

          out.print(from + " ");
          out.print(uri.toString() + "\r\n");
        }
      }
      out.flush();
      out.close();
    }

    else {
      response.setContentType("text/html");

      out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\r\n");
      out.println("<HTML>\r\n");
      out.println("  <HEAD><TITLE>Address Servlet</TITLE></HEAD>\r\n");
      out.println("  <BODY>\r\n");
      out.println("  <br/><br/>");
      out.println("  <CENTER><H3>Registered Addresses</H3></CENTER>\r\n");

      if (addresses != null) {
        out.println("  <TABLE BORDER=1 ALIGN=CENTER WIDTH=70%>\r\n");
        out.println("    <TR>\r\n");
        out.println("      <TH>From</TH><TH>Contact</TH>\r\n");
        out.println("    </TR>\r\n");
        final Iterator iter = addresses.keySet().iterator();
        while (iter.hasNext()) {
          final String from = (String) iter.next();
          final URI uri = (URI) addresses.get(from);
          out.println("    <TR>\r\n");
          out.println("      <TD>" + HTMLFilter.filter(from) + "</TD>\r\n");
          out.println("      <TD>" + HTMLFilter.filter(uri.toString()) + "</TD>\r\n");
          out.println("    </TR>\r\n");
        } 
        out.println("  </TABLE>");
      }
      out.println("  </BODY>\r\n");
      out.println("</HTML>\r\n");
      out.flush();
      out.close();
    }
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
