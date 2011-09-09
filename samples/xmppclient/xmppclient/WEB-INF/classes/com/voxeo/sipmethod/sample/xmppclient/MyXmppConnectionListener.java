package com.voxeo.sipmethod.sample.xmppclient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.dom4j.DocumentHelper;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.w3c.dom.DOMException;

import com.micromethod.sipmethod.server.xmpp.inf.MechanismFeature;
import com.micromethod.sipmethod.server.xmpp.inf.XmppConnectionEvent;
import com.voxeo.servlet.xmpp.Feature;
import com.voxeo.servlet.xmpp.IQResponse;
import com.voxeo.servlet.xmpp.XmppServletMessage;
import com.voxeo.servlet.xmpp.XmppSessionEvent;
import com.voxeo.servlet.xmpp.XmppSessionListener;

public class MyXmppConnectionListener implements XmppSessionListener {

  public void onFeature(XmppSessionEvent event, List<Feature> features) {

    for (Feature feature : features) {
      if (feature.toXML().getNodeName().equalsIgnoreCase("session")
          && feature.toXML().getNamespaceURI().equalsIgnoreCase("urn:ietf:params:xml:ns:xmpp-session")) {

        try {
          Future<IQResponse> resp = event
              .getSession()
              .createIQ(
                  null,
                  XmppServletMessage.TYPE_SET,
                  DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                      .createElementNS("urn:ietf:params:xml:ns:xmpp-session", "session")).sendIQ();

          resp.get();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
        catch (ExecutionException e) {
          e.printStackTrace();
        }
        catch (DOMException e) {
          e.printStackTrace();
        }
        catch (ParserConfigurationException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Override
  public void sessionCreated(XmppSessionEvent xmppsessionevent) {

  }

  @Override
  public void sessionDestroyed(XmppSessionEvent xmppsessionevent) {

  }
}
