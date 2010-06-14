package com.micromethod.sipmethod.sample.clicktodial;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WebAction extends HttpServlet {

  private static final long serialVersionUID = 2280878339842233047L;

  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
      ServletException {

    response.setContentType("text/html");
    final PrintWriter out = response.getWriter();
    out.println("<html>");
    out.println("<body>");
    out.println("<head>");

    final String title = "Click-to-Dial";
    out.println("<title>" + title + "</title>");
    out.println("</head>");
    out.println("<body bgcolor=\"white\">");

    final String user1 = request.getParameter("user1");
    final String user2 = request.getParameter("user2");

    out.println("<br><br>" + "Make call:" + "<br><br>");
    if (user1 != null && user2 != null) {
      final SipAgent agent = (SipAgent) getServletContext().getAttribute("SIP_AGENT");
      final Call call = agent.makeCall(user1, user2);
      if (call == null) {
        out.println("Make call failed.");
      }
      else {
        final boolean ret = agent.waitResultFor(call);
        if (ret) {
          out.println("Call[" + user1 + " -> " + user2 + "] established");
        }
        else {
          out.println("Make call failed.");
        }
      }
    }
    else {
      out.print("<form action=\"");
      out.print("WebAction\" ");
      out.println("method=POST>");
      out.println("User1:");
      out.println("<input type=text size=20 name=user1>");
      out.println("<br>");
      out.println("User2:");
      out.println("<input type=text size=20 name=user2>");
      out.println("<br><br>");
      out.println("<input type=submit name=\"Make call\" value=\"Make call\">");
      out.println("</form>");
    }
    out.println("</body>");
    out.println("</html>");
  }

  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
      ServletException {
    doGet(request, response);
  }

}
