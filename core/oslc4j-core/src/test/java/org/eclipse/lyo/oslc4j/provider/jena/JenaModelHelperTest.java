package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.ResponseInfo; // Needed for createJenaModel

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.xml.datatype.DatatypeConfigurationException; // Needed for createJenaModel
import java.lang.reflect.InvocationTargetException;   // Needed for createJenaModel
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections; // For Collections.emptyMap()
import java.util.HashMap; // For properties map
import java.util.Map; // For properties map


public class JenaModelHelperTest {

    @OslcResourceShape(title = "Test Resource", describes = "test:TestResource")
    @OslcNamespace("test")
    static class MyTestResource extends AbstractResource {
        private String name;

        public MyTestResource() throws URISyntaxException {
            super(new URI("http://example.com/resources/mytestresource"));
        }

        @OslcPropertyDefinition("test:name")
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private Model model;

    @BeforeEach
    void setUp() {
        model = ModelFactory.createDefaultModel();
    }

    @Test
    void testCreateJenaModelWithObjects_delegation() throws Exception {
        MyTestResource resource = new MyTestResource();
        resource.setName("Test");
        Object[] objects = {resource};
        Model resultModel = JenaModelHelper.createJenaModel(objects);
        assertNotNull(resultModel);
        assertFalse(resultModel.isEmpty()); // Basic check that something happened
    }

    @Test
    void testCreateJenaModelWithAllParams_delegation() throws Exception {
        Object[] objects = {};
        Model resultModel = JenaModelHelper.createJenaModel(
            "http://example.com/about",
            "http://example.com/responseInfo",
            new ResponseInfo<>(null, Collections.emptyMap(), 0, null),
            objects,
            new HashMap<>());
        assertNotNull(resultModel);
        // Further checks could verify description resource, etc. if needed to confirm delegation path
    }

    @Test
    void testUnmarshalSingle_delegation_noResource() {
        // Expecting IllegalArgumentException as per JenaUnmarshaller's behavior
        assertThrows(IllegalArgumentException.class, () -> {
            JenaModelHelper.unmarshalSingle(model, MyTestResource.class);
        });
    }

    @Test
    void testUnmarshalSingle_delegation_oneResource() throws Exception {
        Resource r = model.createResource("http://example.com/instance")
                          .addProperty(RDF.type, model.createResource("test:TestResource"))
                          .addProperty(model.createProperty("test:name"), "Instance Name");
        MyTestResource result = JenaModelHelper.unmarshalSingle(model, MyTestResource.class);
        assertNotNull(result);
        assertEquals("Instance Name", result.getName());
    }


    @Test
    void testUnmarshalResource_delegation() throws Exception {
        Resource r = model.createResource("http://example.com/instance")
                          .addProperty(RDF.type, model.createResource("test:TestResource"))
                          .addProperty(model.createProperty("test:name"), "Instance Name");
        MyTestResource result = (MyTestResource) JenaModelHelper.unmarshal(r, MyTestResource.class);
        assertNotNull(result);
        assertEquals("Instance Name", result.getName());
    }

    @Test
    void testUnmarshalModel_delegation() throws Exception {
        model.createResource("http://example.com/instance1")
             .addProperty(RDF.type, model.createResource("test:TestResource"))
             .addProperty(model.createProperty("test:name"), "Instance 1");
        model.createResource("http://example.com/instance2")
             .addProperty(RDF.type, model.createResource("test:TestResource"))
             .addProperty(model.createProperty("test:name"), "Instance 2");

        MyTestResource[] results = JenaModelHelper.unmarshal(model, MyTestResource.class);
        assertNotNull(results);
        assertEquals(2, results.length);
    }

    @Test
    @SuppressWarnings("deprecation")
    void testFromJenaResource_delegation() throws Exception {
        Resource r = model.createResource("http://example.com/instance")
                          .addProperty(RDF.type, model.createResource("test:TestResource"))
                          .addProperty(model.createProperty("test:name"), "Deprecated Instance");
        MyTestResource result = (MyTestResource) JenaModelHelper.fromJenaResource(r, MyTestResource.class);
        assertNotNull(result);
        assertEquals("Deprecated Instance", result.getName());
    }

    @Test
    @SuppressWarnings("deprecation")
    void testFromJenaModel_delegation() throws Exception {
         model.createResource("http://example.com/instance1")
             .addProperty(RDF.type, model.createResource("test:TestResource"))
             .addProperty(model.createProperty("test:name"), "Deprecated Instance 1");
        Object[] results = JenaModelHelper.fromJenaModel(model, MyTestResource.class);
        assertNotNull(results);
        assertEquals(1, results.length);
        assertTrue(results[0] instanceof MyTestResource);
        assertEquals("Deprecated Instance 1", ((MyTestResource)results[0]).getName());
    }

    @Test
    void testOSLC4J_STRICT_DATATYPES_isCorrect() {
        assertEquals(JenaHelperUtils.OSLC4J_STRICT_DATATYPES, JenaModelHelper.OSLC4J_STRICT_DATATYPES);
    }

    @Test
    void testSkolemize_delegates() {
        Resource anonResource = model.createResource(); // Blank node
        assertTrue(anonResource.isAnon());
        JenaModelHelper.skolemize(model);
        // After skolemization, the original anonResource reference might still be anon,
        // but the resource in the model should have been replaced/renamed.
        // We expect no anonymous resources to remain if there was one.
        ResIterator subjects = model.listSubjects();
        boolean foundAnon = false;
        while(subjects.hasNext()) {
            if (subjects.nextResource().isAnon()) {
                foundAnon = true;
                break;
            }
        }
        assertFalse(foundAnon, "Model should not contain anonymous resources after skolemization");
    }

    @Test
    void testFollowLink_delegates() throws Exception {
        String targetUri = "http://example.com/target";
        Resource targetRes = model.createResource(targetUri)
                                  .addProperty(RDF.type, model.createResource("test:TestResource"))
                                  .addProperty(model.createProperty("test:name"), "Linked Resource");
        Link link = new Link(new URI(targetUri));

        MyTestResource result = JenaModelHelper.followLink(model, link, MyTestResource.class);
        assertNotNull(result);
        assertEquals("Linked Resource", result.getName());
        assertEquals(targetUri, result.getAbout().toString());
    }
}
