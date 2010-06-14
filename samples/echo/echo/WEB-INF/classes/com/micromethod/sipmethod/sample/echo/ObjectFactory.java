
package com.micromethod.sipmethod.sample.echo;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.micromethod.sipmethod.sample.echo package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _RemoveRegister_QNAME = new QName("http://echo.sample.sipmethod.micromethod.com/", "removeRegister");
    private final static QName _RemoveRegisterResponse_QNAME = new QName("http://echo.sample.sipmethod.micromethod.com/", "removeRegisterResponse");
    private final static QName _GetRegisteredAddresses_QNAME = new QName("http://echo.sample.sipmethod.micromethod.com/", "getRegisteredAddresses");
    private final static QName _GetRegisteredAddressesResponse_QNAME = new QName("http://echo.sample.sipmethod.micromethod.com/", "getRegisteredAddressesResponse");
    private final static QName _MapWrapper_QNAME = new QName("http://echo.sample.sipmethod.micromethod.com/", "mapWrapper");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.micromethod.sipmethod.sample.echo
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link RemoveRegister }
     * 
     */
    public RemoveRegister createRemoveRegister() {
        return new RemoveRegister();
    }

    /**
     * Create an instance of {@link MapWrapper.Map.Entry }
     * 
     */
    public MapWrapper.Map.Entry createMapWrapperMapEntry() {
        return new MapWrapper.Map.Entry();
    }

    /**
     * Create an instance of {@link GetRegisteredAddressesResponse }
     * 
     */
    public GetRegisteredAddressesResponse createGetRegisteredAddressesResponse() {
        return new GetRegisteredAddressesResponse();
    }

    /**
     * Create an instance of {@link GetRegisteredAddresses }
     * 
     */
    public GetRegisteredAddresses createGetRegisteredAddresses() {
        return new GetRegisteredAddresses();
    }

    /**
     * Create an instance of {@link MapWrapper.Map }
     * 
     */
    public MapWrapper.Map createMapWrapperMap() {
        return new MapWrapper.Map();
    }

    /**
     * Create an instance of {@link RemoveRegisterResponse }
     * 
     */
    public RemoveRegisterResponse createRemoveRegisterResponse() {
        return new RemoveRegisterResponse();
    }

    /**
     * Create an instance of {@link MapWrapper }
     * 
     */
    public MapWrapper createMapWrapper() {
        return new MapWrapper();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RemoveRegister }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://echo.sample.sipmethod.micromethod.com/", name = "removeRegister")
    public JAXBElement<RemoveRegister> createRemoveRegister(RemoveRegister value) {
        return new JAXBElement<RemoveRegister>(_RemoveRegister_QNAME, RemoveRegister.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link RemoveRegisterResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://echo.sample.sipmethod.micromethod.com/", name = "removeRegisterResponse")
    public JAXBElement<RemoveRegisterResponse> createRemoveRegisterResponse(RemoveRegisterResponse value) {
        return new JAXBElement<RemoveRegisterResponse>(_RemoveRegisterResponse_QNAME, RemoveRegisterResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GetRegisteredAddresses }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://echo.sample.sipmethod.micromethod.com/", name = "getRegisteredAddresses")
    public JAXBElement<GetRegisteredAddresses> createGetRegisteredAddresses(GetRegisteredAddresses value) {
        return new JAXBElement<GetRegisteredAddresses>(_GetRegisteredAddresses_QNAME, GetRegisteredAddresses.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link GetRegisteredAddressesResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://echo.sample.sipmethod.micromethod.com/", name = "getRegisteredAddressesResponse")
    public JAXBElement<GetRegisteredAddressesResponse> createGetRegisteredAddressesResponse(GetRegisteredAddressesResponse value) {
        return new JAXBElement<GetRegisteredAddressesResponse>(_GetRegisteredAddressesResponse_QNAME, GetRegisteredAddressesResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MapWrapper }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://echo.sample.sipmethod.micromethod.com/", name = "mapWrapper")
    public JAXBElement<MapWrapper> createMapWrapper(MapWrapper value) {
        return new JAXBElement<MapWrapper>(_MapWrapper_QNAME, MapWrapper.class, null, value);
    }

}
