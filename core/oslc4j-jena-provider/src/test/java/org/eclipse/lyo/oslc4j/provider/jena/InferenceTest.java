package org.eclipse.lyo.oslc4j.provider.jena;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.namespace.QName;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
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

        // Use fromJenaModelInferred with OntModelSpec (using RDFS_MEM_RDFS_INF for simple subclass inference)
        Requirement[] reqs1 = JenaModelHelper.fromJenaModelInferred(data, Requirement.class, tbox, OntModelSpec.RDFS_MEM_RDFS_INF);

        assertNotNull(reqs1);
        if (reqs1.length == 0) {
            System.err.println("DEBUG: No requirements found with OntModelSpec.RDFS_MEM_RDFS_INF");
            // Debug: print what types the resource actually has in the inferred model
            org.apache.jena.ontology.OntModel inferred = ModelFactory.createOntologyModel(OntModelSpec.RDFS_MEM_RDFS_INF, data);
            inferred.addSubModel(tbox);
            Resource rInf = inferred.getResource(resourceUri);
            System.err.println("DEBUG: Resource types: " + rInf.listProperties(RDF.type).toList());
        }
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

        // Verify that the inferred type is present in the unmarshalled resource
        assertTrue("Inferred type should be present", req2.getTypes().contains(URI.create(oslcRmNs + "Requirement")));

        // Use fromJenaModelExact (no reasoning)
        // Should succeed because we pass the URI explicitly and map to Requirement.class
        // But types will be limited to what's in the model.
        Requirement req3 = JenaModelHelper.fromJenaModelExact(data, URI.create(resourceUri), Requirement.class);
        assertNotNull(req3);
        assertFalse("Inferred type should NOT be present without reasoning", req3.getTypes().contains(URI.create(oslcRmNs + "Requirement")));

        // Use fromJenaModelInferred with EXPLICIT type URI
        // Here we ask for all things that are oslc_rm:Requirement
        Requirement[] reqs4 = JenaModelHelper.fromJenaModelInferred(data, URI.create(oslcRmNs + "Requirement"), Requirement.class, tbox, ReasonerVocabulary.RDFS_SIMPLE);
        assertNotNull(reqs4);
        assertEquals(1, reqs4.length);
        assertEquals(URI.create(resourceUri), reqs4[0].getAbout());

        // Use fromJenaModelInferred with EXPLICIT type URI (OntModelSpec)
        // Using RDFS_MEM_RDFS_INF as it is sufficient for subClassOf inference and less strict than DL
        Requirement[] reqs5 = JenaModelHelper.fromJenaModelInferred(data, URI.create(oslcRmNs + "Requirement"), Requirement.class, tbox, OntModelSpec.RDFS_MEM_RDFS_INF);
        assertNotNull(reqs5);
        assertEquals(1, reqs5.length);
        assertEquals(URI.create(resourceUri), reqs5[0].getAbout());
    }
}
