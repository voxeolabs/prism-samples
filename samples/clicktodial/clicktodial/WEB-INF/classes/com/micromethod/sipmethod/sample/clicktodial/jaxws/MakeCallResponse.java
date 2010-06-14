
package com.micromethod.sipmethod.sample.clicktodial.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "makeCallResponse", namespace = "http://clicktodial.sample.sipmethod.micromethod.com/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "makeCallResponse", namespace = "http://clicktodial.sample.sipmethod.micromethod.com/")
public class MakeCallResponse {

    @XmlElement(name = "return", namespace = "")
    private String _return;

    /**
     * 
     * @return
     *     returns String
     */
    public String getReturn() {
        return this._return;
    }

    /**
     * 
     * @param _return
     *     the value for the _return property
     */
    public void setReturn(String _return) {
        this._return = _return;
    }

}
