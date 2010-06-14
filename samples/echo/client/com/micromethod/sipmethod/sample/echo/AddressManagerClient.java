package com.micromethod.sipmethod.sample.echo;

import java.net.URL;
import java.util.List;

import javax.xml.namespace.QName;

public class AddressManagerClient {
  
  public static void main(String[] args) {
    new AddressManagerClient().invoke(args);
  }

  public void usage() {
    System.out.println("Usage: java -cp %JAXWSRILIB% com.micromethod.sipmethod.sample.echo.AddressManagerClient <list | remove <uri>>");
    System.out.println("   required parameters:");
    System.out.println("       list          -- list all registered addresses.");
    System.out.println("       remove <uri>  -- remove uri from addresses map.");
    System.out.println("   optional parameters:");
    System.out.println("       -l<url>  -- service url (eg: -lhttp://localhost:8080/echo/services/manager).");
  }

  public void invoke(String[] args) {
    try {
      Options opts = new Options(args);
      AddressManager port = null;

      String action = null;
      String uri = null;
      args = opts.getRemainingArgs();
      if (args != null && args.length >= 1) {
        if (!opts.getURL().equalsIgnoreCase(opts.getDefaultURL())) {
          port = new AddressManagerService(new URL(opts.getURL() +  "?wsdl"), new QName("http://echo.sample.sipmethod.micromethod.com/", "AddressManagerService")).getAddressManagerPort();
        }
        else {
          port = new AddressManagerService().getAddressManagerPort();
        }
        action = args[0];
        if ("list".equalsIgnoreCase(action)) {
          list(port);
        }
        else if ("remove".equalsIgnoreCase(action) && args.length == 2) {
          uri = args[1];
          boolean ret = removeRegister(port, uri);
          if (ret) {
            System.out.println("Remote '" + uri + "' success.\r\n");
          }
          else {
            System.out.println("Remote '" + uri + "' failed.\r\n");
          }
        }
        else {
          usage();
        }
      }
      else {
        usage();
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void list(AddressManager port) {
    MapWrapper mapWrapper = port.getRegisteredAddresses();
    if (mapWrapper != null) {
      MapWrapper.Map addresses = mapWrapper.getMap();
      List<MapWrapper.Map.Entry> addList =  addresses.getEntry();
      if (addList.size() != 0) {
        System.out.println("                            Registered Addresses                             ");
        System.out.println("No.         From                                             Contact               ");
        int i = 1;
        for (MapWrapper.Map.Entry entry : addList) {
          System.out.println(i++ + "   " + entry.getKey() + "            " + entry.getValue() + "            ");        
        }
      }
      else {
        System.out.println("Empty.");
      }
    }
    else {
      System.out.println("Can't get soap message.");
    }
  }

  private boolean removeRegister(AddressManager port, String uri) {
    return port.removeRegister(uri);
  }
}
