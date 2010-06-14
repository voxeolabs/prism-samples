package com.micromethod.sipmethod.sample.echo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.sip.URI;

@WebService (endpointInterface="com.micromethod.sipmethod.sample.echo.AddressManager")
public class AddressManagerImpl {
  // container will inject application context
  @Resource
  private ServletContext m_context = null;

  public MapWrapper getRegisteredAddresses() {
    MapWrapper mapWrapper = null;
    Map<String, URI> addresses = (Map<String, URI>) m_context.getAttribute("com.micromethod.sample.echoServlet.Addresses");
    if (addresses != null) {
      mapWrapper = new MapWrapper();
      List<MapWrapper.Map.Entry> entryList = new ArrayList<MapWrapper.Map.Entry>(); 
      for (String key : addresses.keySet()) {
        URI uri = (URI) addresses.get(key);
        MapWrapper.Map.Entry entry = new MapWrapper.Map.Entry();
        entry.setKey(key);
        entry.setValue("" + uri);
        entryList.add(entry);
      }
      MapWrapper.Map map = new MapWrapper.Map();
      map.entry = entryList;
      mapWrapper.setMap(map);
    }
    return mapWrapper;
  }

  public boolean removeRegister(String uri) {
    if (uri != null) {
      Map<String, URI> addresses = (Map<String, URI>)m_context.getAttribute("com.micromethod.sample.echoServlet.Addresses");
      if (addresses != null) {
        // remove the name/address mapping.
        Object ret = addresses.remove(uri.trim().toLowerCase());
        
        if (ret != null) {
          // reset addresses map in context attribute for replication.
          m_context.setAttribute("com.micromethod.sample.echoServlet.Addresses", addresses);
          return true;
        }
      }
    }
    return false;
  }
}
