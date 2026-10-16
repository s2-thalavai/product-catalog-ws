/**
 * JAXB data types of the SOAP contract (become complexTypes in the published XSD).
 * Child elements are unqualified, so requests look like {@code <cat:createProduct><product><sku>..}.
 */
@XmlSchema(namespace = Namespaces.CATALOG_V1)
package com.infy.catalog.ws.dto;

import com.infy.catalog.ws.Namespaces;
import jakarta.xml.bind.annotation.XmlSchema;
