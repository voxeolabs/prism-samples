package com.voxeo.sipmethod.sample.xmppecho;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.voxeo.servlet.xmpp.Provider;
import com.voxeo.servlet.xmpp.ProviderException;

public class RosterProvider implements Provider {

  public Class getClassType() {
    return Roster.class;
  }

  public Element unmarshall(Object object) throws ProviderException {

    if (!(object instanceof Roster)) {
      throw new ProviderException("class didn't match");
    }
    Roster ro = (Roster) object;

    List<Item> items = ro.getItems();

    Element element = null;
    try {
      element = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
          .createElementNS("jabber:iq:roster", "query");
    }
    catch (DOMException e) {
      e.printStackTrace();
    }
    catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
    for (Item item : items) {

      Element itemElement = element.getOwnerDocument().createElement("item");

      itemElement.setAttribute("jid", item.getJid());
      itemElement.setAttribute("name", item.getName());
      itemElement.setAttribute("subscription", item.getSubscription());

      Element group = element.getOwnerDocument().createElement("group");
      group.setTextContent(item.getGroup());
      itemElement.appendChild(group);

      element.appendChild(itemElement);
    }

    return element;
  }

  @Override
  public String getElementLocalName() {
    return "query";
  }

  @Override
  public String getElementNamespaceURI() {
    return "jabber:iq:roster";
  }

  @Override
  public Object unmarshall(Element xml) throws ProviderException {
    if (!xml.getNodeName().equals(getElementLocalName())
        || !xml.getNamespaceURI().equalsIgnoreCase(this.getElementNamespaceURI())) {
      throw new ProviderException("name space didn't match");
    }
    Roster roster = new Roster();

    List<Element> elements = getChildElementsByTagName(xml, "item");

    if (elements != null) {
      for (Element element : elements) {
        Item item = new Item(element.getAttribute("jid"), element.getAttribute("name"),
            element.getAttribute("subscription"), element.getAttribute("group"));
        roster.addItem(item);
      }
    }

    return roster;
  }

  public static List<Element> getChildElementsByTagName(Element parent, String tagName) {
    NodeList nl = parent.getChildNodes();
    List<Element> childEles = new ArrayList<Element>();
    for (int i = 0; i < nl.getLength(); i++) {
      Node node = nl.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
        childEles.add((Element) node);
      }
    }
    return childEles;
  }
}
