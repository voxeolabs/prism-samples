package com.micromethod.sipmethod.sample.ar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.ar.SipApplicationRouter;
import javax.servlet.sip.ar.SipApplicationRouterInfo;
import javax.servlet.sip.ar.SipApplicationRoutingDirective;
import javax.servlet.sip.ar.SipApplicationRoutingRegion;
import javax.servlet.sip.ar.SipRouteModifier;
import javax.servlet.sip.ar.SipTargetedRequestInfo;

import org.apache.log4j.Logger;

/**
 * This application router routes the initial requests based on the port number.
 * Each port number is mapped to a specific application based on the
 * configuration file. This can be used to host multiple applications, each with
 * a dedicated port.
 */
public class SipForwardApplicationRouter implements SipApplicationRouter {
  private static final Logger log = Logger.getLogger(SipForwardApplicationRouter.class);

  private final Map<String, String> m_ports = new HashMap<String, String>();

  public void init() {
    init(null);
  }

  public void init(final Properties prop) {
    this.readConfiguration(prop);
  }

  public void applicationDeployed(final List<String> applications) {
    ;
  }

  public void applicationUndeployed(final List<String> applications) {
    ;
  }

  public void destroy() {
    ;
  }

  public SipApplicationRouterInfo getNextApplication(final SipServletRequest initialRequest,
      final SipApplicationRoutingRegion region, final SipApplicationRoutingDirective directive,
      final SipTargetedRequestInfo target, final Serializable stateInfo) {

    final String subscriberURI = initialRequest.getRequestURI().toString();

    SipApplicationRouterInfo info = null;

    final String port = String.valueOf(initialRequest.getLocalPort());
    if (log.isDebugEnabled()) {
      log.debug("Find the port: " + port);
    }
    final String appName = m_ports.get(port);

    if (log.isDebugEnabled()) {
      log.debug("Find next application: " + appName);
    }
    // Determine route info

    if (appName == null || stateInfo != null) {
      // No application found, time to leave the container.
      info = new SipApplicationRouterInfo(null, // next application name,
          null, // routing region, external so null
          null, // served subscriber URI, external so null
          null, // route, modifier set to NO_ROUTE so irrelevant
          SipRouteModifier.NO_ROUTE, null);
    }
    else {
      // Internal application selected. The route modifier is set to
      // NO_ROUTE. This means the container should disregard
      // the route in the info object and inspect the app name instead.
      info = new SipApplicationRouterInfo(appName, // next application name
          region, // region in which app will serve, use unchanged
          subscriberURI, // just use what came in
          null, // route, modifier set to NO_ROUTE so irrelevant
          SipRouteModifier.NO_ROUTE, appName); // application name used as
      // stateinfo
    }
    return info;
  }

  private void readConfiguration(final Properties prop) {
    InputStream is = null;

    String filename = prop == null ? null : prop.getProperty("path");
    if (filename == null) {
      filename = System.getProperty("com.micromethod.sipmethod.sample.ar.configuration");
    }
    if (filename == null || "".equals(filename)) {
      log.error("no configuration found.");
      return;
    }
    File file = new File(filename);
    if (!file.exists()) {
      file = new File(System.getProperty("kernel.home"), filename);
    }
    if (!file.exists()) {
      log.error("no configuration file found.");
      return;
    }

    try {
      is = new FileInputStream(file);
    }
    catch (final FileNotFoundException e) {
      throw new IllegalArgumentException(this + " can't found the properties file.");
    }

    final Properties mapping = new Properties();
    try {
      mapping.load(is);
      loadAppMap(mapping);
    }
    catch (final IOException e) {
      throw new IllegalArgumentException(this + " can't load the properties file.");
    }
    finally {
      try {
        is.close();
      }
      catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }

  private void loadAppMap(final Properties properties) {
    m_ports.clear();
    final Iterator<Object> it = properties.keySet().iterator();
    while (it.hasNext()) {
      final String port = (String) it.next();
      final String appName = properties.getProperty(port);
      m_ports.put(port, appName);
      if (log.isDebugEnabled()) {
        log.debug("load application mapping: " + port + " <-> " + appName);
      }
    }
  }

}
