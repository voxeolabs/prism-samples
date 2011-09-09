package com.voxeo.sipmethod.sample.xmppecho;

public class Item {

  protected String jid;

  protected String name;

  protected String subscription;

  protected String group;

  public Item(String jid, String name, String subscription, String group) {
    super();
    this.jid = jid;
    this.name = name;
    this.subscription = subscription;
    this.group = group;
  }

  public String getJid() {
    return jid;
  }

  public void setJid(String jid) {
    this.jid = jid;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSubscription() {
    return subscription;
  }

  public void setSubscription(String subscription) {
    this.subscription = subscription;
  }

  public String getGroup() {
    return group;
  }

  public void setGroup(String group) {
    this.group = group;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((jid == null) ? 0 : jid.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Item other = (Item) obj;
    if (jid == null) {
      if (other.jid != null)
        return false;
    }
    else if (!jid.equals(other.jid))
      return false;
    return true;
  }
}
