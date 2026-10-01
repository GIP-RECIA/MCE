/*
 * Copyright (C) 2023 GIP-RECIA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.recia.mce.api.escomceapi.configuration.cas;

import fr.recia.mce.api.escomceapi.configuration.bean.CasProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jasig.cas.client.authentication.AttributePrincipalImpl;
import org.jasig.cas.client.ssl.HttpURLConnectionFactory;
import org.jasig.cas.client.ssl.HttpsURLConnectionFactory;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.AssertionImpl;
import org.jasig.cas.client.validation.TicketValidationException;
import org.jasig.cas.client.validation.TicketValidator;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Classe custom qui permet de faire valider un ticket CAS en prenant en compte la gestion du multidomaine
 * On est obligé de recopier toutes les méthodes des classes de TicketValidator jusqu'à Cas30ServiceTicketValidator
 * car les seules méthodes qu'on voudrait surcharger (constructValidationUrl ou validate) sont final...
 */
@Data
@Slf4j
public class CustomCas30ServiceTicketValidator implements TicketValidator {

    private HttpURLConnectionFactory urlConnectionFactory;
    private final String casServerUrlPrefix;
    private boolean renew;
    private String encoding;
    private CasProperties casProperties;

    public CustomCas30ServiceTicketValidator(String casServerUrlPrefix, boolean renew, CasProperties casProperties){
        this.urlConnectionFactory = new HttpsURLConnectionFactory();
        this.casServerUrlPrefix = casServerUrlPrefix;
        this.renew = renew;
        this.casProperties = casProperties;
    }

    /**
     * The endpoint of the validation URL.  Should be relative (i.e. not start with a "/"). I.e. validate or serviceValidate.
     * @return the endpoint of the validation URL.
     */
    protected String getUrlSuffix() {
        return "/p3/serviceValidate";
    }

    /**
     * Parses the response from the server into a CAS Assertion.
     *
     * @param response the response from the server, in any format.
     * @return the CAS assertion if one could be parsed from the response.
     * @throws TicketValidationException if an Assertion could not be created.
     *
     */
    protected Assertion parseResponseFromServer(final String response) throws TicketValidationException {
        final String error = parseAuthenticationFailureFromResponse(response);

        if (CommonUtils.isNotBlank(error)) {
            throw new TicketValidationException(error);
        }

        final String principal = parsePrincipalFromResponse(response);
        if (CommonUtils.isEmpty(principal)) {
            throw new TicketValidationException("No principal was found in the response from the CAS server.");
        }

        final Map<String, Object> attributes = extractCustomAttributes(response);
        return new AssertionImpl(new AttributePrincipalImpl(principal, attributes));
    }

    /**
     * Default attribute parsing for CAS XML response when validating a ticket (see Cas20ServiceTicketValidator)
     *
     * @param xml the XML to parse.
     * @return the map of attributes.
     */
    protected Map<String, Object> extractCustomAttributes(final String xml) {
        final SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setValidating(false);
        try {
            final SAXParser saxParser = spf.newSAXParser();
            final XMLReader xmlReader = saxParser.getXMLReader();
            final CustomAttributeHandler handler = new CustomAttributeHandler();
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(new StringReader(xml)));
            return handler.getAttributes();
        } catch (final Exception e) {
            log.error(e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    protected String parsePrincipalFromResponse(final String response) {
        return XmlUtils.getTextForElement(response, "user");
    }

    protected String parseAuthenticationFailureFromResponse(final String response) {
        return XmlUtils.getTextForElement(response, "authenticationFailure");
    }

    /**
     * Encodes a URL using the URLEncoder format.
     *
     * @param url the url to encode.
     * @return the encoded url, or the original url if "UTF-8" character encoding could not be found.
     */
    protected final String encodeUrl(final String url) {
        if (url == null) {
            return null;
        }
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
    }
    /**
     * Constructs the URL to send the validation request to.
     *
     * @param ticket the ticket to be validated.
     * @param serviceUrl the service identifier.
     * @return the fully constructed URL.
     */
    protected final String constructValidationUrl(final String ticket, final String serviceUrl) {
        final Map<String, String> urlParameters = new HashMap<String, String>();

        log.debug("Placing URL parameters in map.");
        urlParameters.put("ticket", ticket);

        // Multidomain customisation
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        final String url = request.getRequestURL().toString();
        final String uri = request.getRequestURI();
        urlParameters.put("service", url.substring(0, url.length() - uri.length()) + serviceUrl);

        if (this.renew) {
            urlParameters.put("renew", "true");
        }

        final String suffix = getUrlSuffix();
        final StringBuilder buffer = new StringBuilder(urlParameters.size() * 10 + this.casServerUrlPrefix.length() + suffix.length() + 1);
        int i = 0;
        buffer.append(this.casServerUrlPrefix);
        buffer.append(suffix);

        for (final Map.Entry<String, String> entry : urlParameters.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            if (value != null) {
                buffer.append(i++ == 0 ? "?" : "&");
                buffer.append(key);
                buffer.append("=");
                final String encodedValue = encodeUrl(value);
                buffer.append(encodedValue);
            }
        }

        return buffer.toString();
    }

    /**
     * Contacts the CAS Server to retrieve the response for the ticket validation.
     *
     * @param validationUrl the url to send the validation request to.
     * @return the response from the CAS server.
     */
    protected String retrieveResponseFromServer(final URL validationUrl) {
        return CommonUtils.getResponseFromServer(validationUrl, getUrlConnectionFactory(), getEncoding());
    }

    @Override
    public Assertion validate(final String ticket, final String service) throws TicketValidationException {
        final String validationUrl = constructValidationUrl(ticket, service);
        log.debug("Constructing validation url: {}", validationUrl);
        try {
            log.debug("Retrieving response from server.");
            final String serverResponse = retrieveResponseFromServer(new URL(validationUrl));
            log.debug("Server response: {}", serverResponse);
            return parseResponseFromServer(serverResponse);
        } catch (final MalformedURLException e) {
            throw new TicketValidationException(e);
        }
    }

}
