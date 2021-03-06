/*
 * The JASDB software and code is Copyright protected 2011 and owned by Renze de Vries
 * 
 * All the code and design principals in the codebase are also Copyright 2011 
 * protected and owned Renze de Vries. Any unauthorized usage of the code or the 
 * design and principals as in this code is prohibited.
 */
package com.oberasoftware.jasdb.rest.client;

import com.google.common.collect.Range;
import com.oberasoftware.jasdb.api.exceptions.ConfigurationException;
import com.oberasoftware.jasdb.api.exceptions.RuntimeJasDBException;
import com.oberasoftware.jasdb.api.model.NodeInformation;
import nl.renarj.jasdb.remote.RemoteConnector;
import nl.renarj.jasdb.remote.RemotingContext;
import nl.renarj.jasdb.remote.exceptions.RemoteException;
import com.oberasoftware.jasdb.api.exceptions.RestException;
import com.oberasoftware.jasdb.rest.model.ErrorEntity;
import com.oberasoftware.jasdb.rest.model.RestEntity;
import com.oberasoftware.jasdb.rest.model.serializers.json.JsonRestResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * User: renarj
 * Date: 1/23/12
 * Time: 9:39 PM
 */
public class RemoteRestConnector implements RemoteConnector {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteRestConnector.class);

    protected static final String CHARACTER_ENCODING = "UTF8";
    protected enum REQUEST_MODE {
        GET,
        POST,
        DELETE,
        PUT
    }
    public static final String REQUESTCONTEXT = "requestcontext";

    
    private static final String CONNECTION_PROTOCOL_PROPERTY = "protocol";
    public static final String CONNECTION_HOST_PROPERTY = "host";
    public static final String CONNECTION_PORT_PROPERTY = "port";

    /* Connection properties */
    private String baseUrl;

    public RemoteRestConnector(NodeInformation nodeInformation) throws ConfigurationException {
        loadHostAddress(nodeInformation);
    }
    
    private void loadHostAddress(NodeInformation nodeInformation) throws ConfigurationException {
        Map<String, ?> remoteProperties = nodeInformation.getServiceInformation("rest").getNodeProperties();
        if(remoteProperties.containsKey(CONNECTION_HOST_PROPERTY) &&
                remoteProperties.containsKey(CONNECTION_PORT_PROPERTY) &&
                remoteProperties.containsKey(CONNECTION_PROTOCOL_PROPERTY)) {
            String host = (String)remoteProperties.get(CONNECTION_HOST_PROPERTY);
            String port = (String)remoteProperties.get(CONNECTION_PORT_PROPERTY);
            String protocol = (String) remoteProperties.get(CONNECTION_PROTOCOL_PROPERTY);

            if(remoteProperties.containsKey("verifyCert") && remoteProperties.get("verifyCert").equals("false")) {
                disableCertificationValidation();
            }

            if(protocol.equals("http") || protocol.equals("https")) {
                try {
                    int portNumber = Integer.parseInt(port);

                    this.baseUrl = protocol + "://" + host + ":" + portNumber + "/";
                    LOG.debug("Loaded rest connector with baseUrl: {}", baseUrl);
                } catch(NumberFormatException e) {
                    throw new ConfigurationException("Invalid Rest client port number: " + port);
                }
            } else {
                throw new ConfigurationException("Unsupported Rest client protocol: " + protocol);
            }
            
        } else {
            throw new ConfigurationException("Unable to load remote connection properties to establish rest client connection");
        }
    }

    private void disableCertificationValidation() throws ConfigurationException {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch(NoSuchAlgorithmException | KeyManagementException e) {
            throw new ConfigurationException("Unable to disable SSL verification", e);
        }

        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
    }
    
    @Override
	public void close() {
	}

    private URL getUrl(String resource, Map<String, String> params) throws MalformedURLException, UnsupportedEncodingException {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(baseUrl).append(resource);

        boolean first = true;
        for(Map.Entry<String, String> param : params.entrySet()) {
            char queryChar = first ? '?' : '&';
            first = false;
            urlBuilder.append(queryChar).append(param.getKey()).append("=").append(URLEncoder.encode(param.getValue(), "UTF8"));
        }
        return new URL(urlBuilder.toString());
    }

    protected byte[] toBytes(RestEntity entity) throws RestException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new JsonRestResponseHandler().serialize(entity, bos);
        return bos.toByteArray();
    }

    protected ClientResponse doRequest(RemotingContext context, String connectionString) throws RemoteException {
        return doRequest(context, connectionString, new HashMap<>());
    }

    protected ClientResponse doRequest(RemotingContext context, String connectionString, Map<String, String> params) throws RemoteException {
        return doInternalRequest(context, connectionString, params, null, REQUEST_MODE.GET);
    }

    protected ClientResponse doRequest(RemotingContext context, String connectionString, Map<String, String> params, String postBody, REQUEST_MODE mode) throws RemoteException {
        try {
            return doInternalRequest(context, connectionString, params, postBody != null ? postBody.getBytes("UTF8") : null, mode);
        } catch(UnsupportedEncodingException e) {
            throw new RemoteException("", e);
        }
    }
    
    protected ClientResponse doInternalRequest(RemotingContext context, String connectionString, Map<String, String> params, byte[] postStream, REQUEST_MODE mode) throws RemoteException {
        HttpURLConnection urlConnection = null;
        try {
            URL url = getUrl(connectionString, params);
            LOG.debug("Doing request to resource: {}", url);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod(mode.toString());
            urlConnection.setRequestProperty("Accept-Charset", "UTF-8");
            urlConnection.addRequestProperty("content-type", "application/json");
            urlConnection.setRequestProperty(REQUESTCONTEXT, getRequestContext(context));
            if(context.getUserSession() != null) {
                urlConnection.setRequestProperty("oauth_token", context.getUserSession().getAccessToken());
                urlConnection.setRequestProperty("sessionid", context.getUserSession().getSessionId());
            }

            if(postStream != null) {
                urlConnection.setDoOutput(true);
                urlConnection.getOutputStream().write(postStream);
            } else {
                urlConnection.connect();
            }
            return handleResponse(new ClientResponse(urlConnection.getInputStream(), urlConnection.getResponseCode()));
        } catch(MalformedURLException e) {
            throw new RuntimeJasDBException("The remote client url is invalid", e);
        } catch(UnsupportedEncodingException e) {
            throw new RemoteException("Unsupported encoding", e);
        } catch(IOException e) {
            if(urlConnection != null) {
                try {
                    return handleResponse(new ClientResponse(urlConnection.getErrorStream(), urlConnection.getResponseCode()));
                } catch(IOException ex) {
                    throw new RemoteException("Unable to connect to client", ex);
                }
            } else {
                throw new RemoteException("Unable to remote, fatal exception on connection", e);
            }
        }
    }

    private ClientResponse handleResponse(ClientResponse response) throws RemoteException {
        if(Range.closedOpen(200, 400).contains(response.getStatus())) {
            return response;
        } else if(Range.closedOpen(400, 500).contains(response.getStatus())) {
            String responseEntity = response.getEntityAsString();
            if(LOG.isDebugEnabled()) {
                LOG.debug("Response: {}", responseEntity);
            }

            try {
                String message = "";
                if(responseEntity != null) {
                    ErrorEntity errorEntity = new JsonRestResponseHandler().deserialize(ErrorEntity.class, responseEntity);
                    message = errorEntity.getMessage();
                }

                if(response.getStatus() == HttpStatus.NOT_FOUND.value()) {
                    throw new ResourceNotFoundException("No resource was found, " + message);
                } else {
                    LOG.error("Raw response: {}", responseEntity);
                    throw new RemoteException("Unable to execute remote operation: " + message + " statuscode: " + response.getStatus());
                }
            } catch(RestException e) {
                String reason = HttpStatus.valueOf(response.getStatus()).getReasonPhrase();
                throw new RemoteException("Unable to execute remote operation: " + response.getStatus() + "(" + reason + ")");
            }
        } else {
            String responseEntity = response.getEntityAsString();
            LOG.error("Remote server response with an error: {}", responseEntity);
            throw new RemoteException("Unable to execute remote operation: " + response.getStatus());
        }
    }

    private String getRequestContext(RemotingContext context) {
        if(context.isClientRequest()) {
            return "client";
        } else {
            return "grid";
        }
    }

    public String toString() {
        return baseUrl;
    }
}
