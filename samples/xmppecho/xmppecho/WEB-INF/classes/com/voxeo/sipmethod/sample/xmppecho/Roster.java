package com.voxeo.sipmethod.sample.xmppecho;

import java.util.LinkedList;
import java.util.List;

public class Roster {

  protected List<Item> items = new LinkedList<Item>();

  public Roster() {
    super();
  }

  public void addItem(Item item) {
    items.add(item);
  }

  public void remove(Item item) {
    items.remove(item);
  }

  public List<Item> getItems() {
    return items;
  }
}
