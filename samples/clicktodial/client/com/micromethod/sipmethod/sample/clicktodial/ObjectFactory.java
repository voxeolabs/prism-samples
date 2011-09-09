
package com.micromethod.sipmethod.sample.clicktodial;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.micromethod.sipmethod.sample.clicktodial package. 
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

    private final static QName _MakeCallResponse_QNAME = new QName("http://clicktodial.sample.sipmethod.micromethod.com/", "makeCallResponse");
    private final static QName _MakeCall_QNAME = new QName("http://clicktodial.sample.sipmethod.micromethod.com/", "makeCall");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.micromethod.sipmethod.sample.clicktodial
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link MakeCallResponse }
     * 
     */
    public MakeCallResponse createMakeCallResponse() {
        return new MakeCallResponse();
    }

    /**
     * Create an instance of {@link MakeCall }
     * 
     */
    public MakeCall createMakeCall() {
        return new MakeCall();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MakeCallResponse }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://clicktodial.sample.sipmethod.micromethod.com/", name = "makeCallResponse")
    public JAXBElement<MakeCallResponse> createMakeCallResponse(MakeCallResponse value) {
        return new JAXBElement<MakeCallResponse>(_MakeCallResponse_QNAME, MakeCallResponse.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link MakeCall }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://clicktodial.sample.sipmethod.micromethod.com/", name = "makeCall")
    public JAXBElement<MakeCall> createMakeCall(MakeCall value) {
        return new JAXBElement<MakeCall>(_MakeCall_QNAME, MakeCall.class, null, value);
    }

}
