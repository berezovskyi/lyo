package org.eclipse.lyo.oslc4j.provider.jena;

import java.net.URI;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.ReasonerVocabulary;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.junit.Test;
import static org.junit.Assert.*;

public class InferenceTest {

    @OslcNamespace("http://open-services.net/ns/rm#")
    @OslcResourceShape(title = "Requirement", describes = "http://open-services.net/ns/rm#Requirement")
    public static class Requirement extends AbstractResource {
        private String title;

        @OslcName("title")
        @OslcPropertyDefinition(OslcConstants.DCTERMS_NAMESPACE + "title")
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    @Test
    public void testInference() throws Exception {
        // TBox: Define AcmeRequirement as subclass of OSLC Requirement
        Model tbox = ModelFactory.createDefaultModel();
        String acmeNs = "http://acme.com/ns#";
        String oslcRmNs = "http://open-services.net/ns/rm#";

        Resource acmeReqClass = tbox.createResource(acmeNs + "AcmeRequirement");
        Resource oslcReqClass = tbox.createResource(oslcRmNs + "Requirement");

        tbox.add(acmeReqClass, RDFS.subClassOf, oslcReqClass);

        // Data: Instance of AcmeRequirement
        Model data = ModelFactory.createDefaultModel();
        String resourceUri = "http://acme.com/req/1";
        Resource r = data.createResource(resourceUri);
        r.addProperty(RDF.type, acmeReqClass);
        r.addProperty(data.createProperty(OslcConstants.DCTERMS_NAMESPACE + "title"), "My Requirement");
        r.addProperty(data.createProperty(acmeNs + "customProp"), "Custom Value");

        // Use fromJenaModelExact with OntModelSpec
        Requirement req1 = JenaModelHelper.fromJenaModelExact(data, URI.create(resourceUri), Requirement.class, tbox, OntModelSpec.OWL_DL_MEM);

        assertNotNull(req1);
        assertEquals("My Requirement", req1.getTitle());
        assertEquals(URI.create(resourceUri), req1.getAbout());

        // Verify custom property is in extended properties
        Map<QName, Object> props = req1.getExtendedProperties();
        QName customProp = new QName(acmeNs, "customProp");
        // Depending on prefix generation, prefix might vary, but key is QName
        // Actually, JenaModelHelper key generation for extended properties might need attention
        // It uses prefix from model if available.

        // Let's print extended properties if assertion fails
        // System.out.println(props);

        // Note: The extended properties key is constructed using QName(namespace, localName, prefix)
        // If we check with just namespace and localName, equals should work (QName equals checks namespace and localpart)

        assertTrue("Expected custom property in extended properties", props.containsKey(customProp));
        assertEquals("Custom Value", props.get(customProp));

        // Use fromJenaModelExact with ReasonerVocabulary (RDFS inference)
        Requirement req2 = JenaModelHelper.fromJenaModelExact(data, URI.create(resourceUri), Requirement.class, tbox, ReasonerVocabulary.RDFS_SIMPLE);

        assertNotNull(req2);
        assertEquals("My Requirement", req2.getTitle());

        // Verify that the inferred type is present in the unmarshalled resource
        assertTrue("Inferred type should be present", req2.getTypes().contains(URI.create(oslcRmNs + "Requirement")));

        // Verify that the original data did not have this type
        assertFalse("Original data should not have inferred type", data.getResource(resourceUri).hasProperty(RDF.type, oslcReqClass));
    }
}
