package org.eclipse.lyo.oslc4j.provider.jena;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntModel;
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
import org.eclipse.lyo.oslc4j.core.model.GenericOslcResource;
import org.eclipse.lyo.oslc4j.provider.jena.helpers.JenaAssert;
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
        String acmeNs = "http://acme.com/ns#";
        String oslcRmNs = "http://open-services.net/ns/rm#";

        // TBox: Define AcmeRequirement as subclass of OSLC Requirement using Turtle
        String tboxTurtle = """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix acme: <http://acme.com/ns#> .
            @prefix oslc_rm: <http://open-services.net/ns/rm#> .

            acme:AcmeRequirement a rdfs:Class, owl:Class ;
                rdfs:subClassOf oslc_rm:Requirement .
            """;

        Model tbox = ModelFactory.createDefaultModel();
        tbox.read(new ByteArrayInputStream(tboxTurtle.getBytes(StandardCharsets.UTF_8)), null, "TURTLE");

        // Data: Instance of AcmeRequirement
        Model data = ModelFactory.createDefaultModel();
        String resourceUri = "http://acme.com/req/1";
        Resource r = data.createResource(resourceUri);
        Resource acmeReqClass = tbox.getResource(acmeNs + "AcmeRequirement");

        r.addProperty(RDF.type, acmeReqClass);
        r.addProperty(data.createProperty(OslcConstants.DCTERMS_NAMESPACE + "title"), "My Requirement");
        r.addProperty(data.createProperty(acmeNs + "customProp"), "Custom Value");

        // Use fromJenaModelInferred with OntSpecification (using RDFS_MEM_RDFS_INF equivalent)
        Requirement[] reqs1 = JenaModelHelper.fromJenaModelInferred(data, Requirement.class, tbox, OntSpecification.RDFS_MEM_RDFS_INF);

        assertNotNull(reqs1);
        assertEquals(1, reqs1.length);
        Requirement req1 = reqs1[0];

        assertEquals("My Requirement", req1.getTitle());
        assertEquals(URI.create(resourceUri), req1.getAbout());

        // Verify custom property is in extended properties
        Map<QName, Object> props = req1.getExtendedProperties();
        QName customProp = new QName(acmeNs, "customProp");

        assertTrue("Expected custom property in extended properties", props.containsKey(customProp));
        assertEquals("Custom Value", props.get(customProp));

        // Use fromJenaModelInferred with ReasonerVocabulary (RDFS inference)
        Requirement[] reqs2 = JenaModelHelper.fromJenaModelInferred(data, Requirement.class, tbox, ReasonerVocabulary.RDFS_SIMPLE);

        assertNotNull(reqs2);
        assertEquals(1, reqs2.length);
        Requirement req2 = reqs2[0];

        assertEquals("My Requirement", req2.getTitle());

        // Use fromJenaModelExact (no reasoning)
        Requirement req3 = JenaModelHelper.fromJenaModelExact(data, URI.create(resourceUri), Requirement.class);
        assertNotNull(req3);
        assertFalse("Inferred type should NOT be present without reasoning", req3.getTypes().contains(URI.create(oslcRmNs + "Requirement")));

        // Use fromJenaModelInferred with EXPLICIT type URI
        Requirement[] reqs4 = JenaModelHelper.fromJenaModelInferred(data, URI.create(oslcRmNs + "Requirement"), Requirement.class, tbox, ReasonerVocabulary.RDFS_SIMPLE);
        assertNotNull(reqs4);
        assertEquals(1, reqs4.length);
        assertEquals(URI.create(resourceUri), reqs4[0].getAbout());

        // Use fromJenaModelInferred with EXPLICIT type URI (OntSpecification)
        Requirement[] reqs5 = JenaModelHelper.fromJenaModelInferred(data, URI.create(oslcRmNs + "Requirement"), Requirement.class, tbox, OntSpecification.RDFS_MEM_RDFS_INF);
        assertNotNull(reqs5);
        assertEquals(1, reqs5.length);
        assertEquals(URI.create(resourceUri), reqs5[0].getAbout());
    }

    @Test
    public void testGenericResourceRoundTrip() throws Exception {
        String resourceUri = "http://example.com/r1";
        String acmeReqType = "http://acme.com/ns#AcmeRequirement";
        Model originalModel = ModelFactory.createDefaultModel();
        Resource r = originalModel.createResource(resourceUri);
        // Use acme:AcmeRequirement as the resource type
        r.addProperty(RDF.type, originalModel.createResource(acmeReqType));
        r.addProperty(originalModel.createProperty("http://example.com/ns#prop1"), "Value 1");
        r.addProperty(originalModel.createProperty("http://example.com/ns#prop2"), "Value 2");

        // Unmarshal into GenericOslcResource
        GenericOslcResource generic = JenaModelHelper.fromJenaModelExact(originalModel, URI.create(resourceUri), GenericOslcResource.class);

        assertNotNull(generic);
        assertEquals(URI.create(resourceUri), generic.getAbout());
        assertTrue("Generic resource should capture custom type", generic.getTypes().contains(URI.create(acmeReqType)));

        // Verify extended properties
        Map<QName, Object> props = generic.getExtendedProperties();
        assertTrue(props.containsKey(new QName("http://example.com/ns#", "prop1")));
        assertEquals("Value 1", props.get(new QName("http://example.com/ns#", "prop1")));

        // Marshal back to Model
        Model marshalledModel = JenaModelHelper.createJenaModel(new Object[]{generic});

        // The marshalled model will include:
        // 1. The original triples (properties and acme:AcmeRequirement type).
        // 2. rdf:type rdfs:Resource (added by GenericOslcResource constructor).
        // 3. rdf:type <java:GenericOslcResource> (derived from class annotation/structure by JenaModelHelper).

        // To verify isomorphism, we add these expected types to the original model expectation.
        originalModel.add(r, RDF.type, originalModel.createResource("http://www.w3.org/2000/01/rdf-schema#GenericOslcResource"));

        JenaAssert.assertThat(marshalledModel).isomorphicWith(originalModel);
    }
}
