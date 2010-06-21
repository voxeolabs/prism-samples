package com.voxeo.sipmethod.sample.convergence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpClient {

  public static String postData(String posturl, String content, String contentType) throws IOException {
    URL url = new URL(posturl);
    URLConnection conn = url.openConnection();
    conn.setDoOutput(true);
    if (contentType != null) {
      conn.setRequestProperty("Content-Type", contentType);
    }
    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
    wr.write(content);
    wr.flush();
    wr.close();
    if (conn instanceof HttpURLConnection) {
      HttpURLConnection httpConnection = (HttpURLConnection) conn;
      if (httpConnection.getResponseCode() != 200) {
        throw new IOException("Status " + httpConnection.getResponseCode());
      }
    }

    return getContentFromConn(conn);
  }

  private static String getContentFromConn(URLConnection conn) throws IOException {
    String response = null;

    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
    StringBuffer buf = new StringBuffer();
    String line;
    while (null != (line = br.readLine())) {
      buf.append(line).append("\n");
    }
    response = buf.toString();
    br.close();
    return response.trim();
  }
}
