package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.core.model.IExtendedResource;
import org.eclipse.lyo.oslc4j.core.model.IReifiedResource;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.provider.jena.JenaMarshaller.MarshallingContext; // Import the context
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.namespace.QName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID; // For test resource URIs
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@OslcNamespace("test")
@ExtendWith(MockitoExtension.class)
public class JenaMarshallerTest {

    @Mock Model mockModel;
    @Mock Resource mockJenaResource;
    @Mock Property mockAttributeProperty;
    @Mock Literal mockLiteral;
    @Mock Resource mockUriResource;
    @Mock Supplier<Model> mockModelSupplier;


    @Captor ArgumentCaptor<Literal> literalCaptor;
    @Captor ArgumentCaptor<Resource> resourceCaptor;
    @Captor ArgumentCaptor<RDFNode> rdfNodeCaptor;


    private JenaMarshaller marshaller;
    private JenaMarshaller marshallerWithMockModel;
    private MarshallingContext marshallingContext;


    @OslcResourceShape(title = "Test Resource", describes = "test:TestResource")
    @OslcNamespace("test")
    static class MyTestResource extends AbstractResource {
        private String name;
        private URI link;
        private String description;
        private MyNestedResource nestedResource;
        private MyReifiedStringProperty reifiedProperty;
        private String[] stringArray;
        private Collection<URI> uriCollection;

        public MyTestResource() throws URISyntaxException {
            super(new URI("http://example.com/resources/mytestresource/" + UUID.randomUUID().toString()));
        }

        @OslcPropertyDefinition("test:name")
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        @OslcPropertyDefinition("test:link")
        public URI getLink() { return link; }
        public void setLink(URI link) { this.link = link; }

        @OslcPropertyDefinition("test:description")
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        @OslcPropertyDefinition("test:nested")
        public MyNestedResource getNestedResource() { return nestedResource; }
        public void setNestedResource(MyNestedResource nestedResource) { this.nestedResource = nestedResource; }

        @OslcPropertyDefinition("test:reifiedProp")
        public MyReifiedStringProperty getReifiedProperty() { return reifiedProperty; }
        public void setReifiedProperty(MyReifiedStringProperty reifiedProperty) { this.reifiedProperty = reifiedProperty; }

        @OslcPropertyDefinition("test:stringArray")
        public String[] getStringArray() { return stringArray; }
        public void setStringArray(String[] stringArray) { this.stringArray = stringArray; }

        @OslcPropertyDefinition("test:uriCollection")
        public Collection<URI> getUriCollection() { return uriCollection; }
        public void setUriCollection(Collection<URI> uriCollection) { this.uriCollection = uriCollection; }
    }

    @OslcResourceShape(title = "Nested Test Resource", describes = "test:NestedResource")
    @OslcNamespace("test")
    static class MyNestedResource extends AbstractResource {
        private String nestedName;

        public MyNestedResource() throws URISyntaxException {
            super(new URI("http://example.com/resources/mynestedresource/" + UUID.randomUUID().toString()));
        }

        @OslcPropertyDefinition("test:nestedName")
        public String getNestedName() { return nestedName; }
        public void setNestedName(String nestedName) { this.nestedName = nestedName; }
    }

    @OslcResourceShape(title = "Reified String Property", describes = "test:ReifiedString")
    @OslcNamespace("test")
    static class MyReifiedStringProperty extends AbstractResource implements IReifiedResource<String>, IExtendedResource {
        private String value;
        private Map<QName, Object> extendedProperties = new HashMap<>();

        public MyReifiedStringProperty() throws URISyntaxException { super(); }

        @Override public String getValue() { return value; }
        @Override public void setValue(String value) { this.value = value; }

        @Override public void setExtendedProperties(Map<QName, Object> properties) { this.extendedProperties = properties; }
        @Override public Map<QName, Object> getExtendedProperties() { return extendedProperties; }
        @Override public void addExtendedProperty(QName name, Object value) { extendedProperties.put(name, value); }
        @Override public Object getExtendedProperty(QName name) { return extendedProperties.get(name); }
    }


    @BeforeEach
    void setUp() {
        Model realModelForContext = ModelFactory.createDefaultModel();
        marshaller = new JenaMarshaller(() -> realModelForContext);
        marshallerWithMockModel = new JenaMarshaller(mockModelSupplier);
        // Initialize context for tests that might need it for reflection calls
        marshallingContext = new MarshallingContext(mockModel, Collections.emptyMap(), new HashMap<>());
    }

    private Method getAccessibleHelperMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = JenaMarshaller.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @Test
    void testAddStringLiteral_plain() throws Exception {
        String testValue = "Hello, OSLC!";
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createLiteral(testValue)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addStringLiteral", Resource.class, Property.class, String.class, boolean.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, false, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
        verify(mockModel).createLiteral(testValue); // From context
    }

    @Test
    void testAddStringLiteral_xml() throws Exception {
        String testValue = "<p>Hello</p>";
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral(testValue, XMLLiteralType.theXMLLiteralType)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addStringLiteral", Resource.class, Property.class, String.class, boolean.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, true, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
        verify(mockModel).createTypedLiteral(testValue, XMLLiteralType.theXMLLiteralType);
    }

    @Test
    void testAddStringLiteral_toContainer() throws Exception {
        String testValue = "Value for container";
        List<RDFNode> rdfNodeContainer = new ArrayList<>();
        when(mockModel.createLiteral(testValue)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addStringLiteral", Resource.class, Property.class, String.class, boolean.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, false, rdfNodeContainer, marshallingContext);
        assertEquals(1, rdfNodeContainer.size());
        assertSame(mockLiteral, rdfNodeContainer.get(0));
        verify(mockJenaResource, never()).addProperty(any(Property.class), any(RDFNode.class));
    }

    @Test
    void testAddFloatLiteral_normal() throws Exception {
        Float testValue = 123.45f;
        List<RDFNode> rdfNodeContainer = null;
        // The toLiteral method will use JenaHelperUtils, which takes the model from context
        when(mockModel.createTypedLiteral(testValue, XSDDatatype.XSDfloat)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addFloatLiteral", Resource.class, Property.class, Float.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddFloatLiteral_infinity() throws Exception {
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral("INF", XSDDatatype.XSDfloat.getURI())).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addFloatLiteral", Resource.class, Property.class, Float.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, Float.POSITIVE_INFINITY, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddFloatLiteral_toContainer() throws Exception {
        Float testValue = 123.45f;
        List<RDFNode> rdfNodeContainer = new ArrayList<>();
        when(mockModel.createTypedLiteral(testValue, XSDDatatype.XSDfloat)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addFloatLiteral", Resource.class, Property.class, Float.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, rdfNodeContainer, marshallingContext);
        assertEquals(1, rdfNodeContainer.size());
        assertSame(mockLiteral, rdfNodeContainer.get(0));
    }


    @Test
    void testAddDoubleLiteral_normal() throws Exception {
        Double testValue = 123.456789;
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral(testValue, XSDDatatype.XSDdouble)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addDoubleLiteral", Resource.class, Property.class, Double.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddBooleanLiteral() throws Exception {
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral(true)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addBooleanLiteral", Resource.class, Property.class, Boolean.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, true, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddNumericLiteral_integer() throws Exception {
        Integer testValue = 123;
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral(testValue)).thenReturn(mockLiteral);
        Method method = getAccessibleHelperMethod("addNumericLiteral", Resource.class, Property.class, Number.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testValue, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddDateLiteral_dateTime() throws Exception {
        Date testDate = new Date();
        List<RDFNode> rdfNodeContainer = null;
        when(mockModel.createTypedLiteral(any(GregorianCalendar.class))).thenReturn(mockLiteral);
        // Mocking the static call to JenaHelperUtils.getTypesFromResource
        // This test will focus on the direct path where XSDDateType is not inferred if getTypesFromResource is hard to mock here
        Method method = getAccessibleHelperMethod("addDateLiteral", Resource.class, Property.class, Date.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testDate, rdfNodeContainer, marshallingContext);
        verify(mockModel).createTypedLiteral(argThat((GregorianCalendar cal) -> cal.getTime().equals(testDate)));
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockLiteral);
    }

    @Test
    void testAddUriResource_absolute() throws Exception {
        URI testUri = new URI("http://example.com/linkedresource");
        List<RDFNode> rdfNodeContainer = null;
        Class<?> dummyParentClass = MyTestResource.class;
        Method dummyParentMethod = MyTestResource.class.getMethod("getLink");
        when(mockModel.createResource(testUri.toString())).thenReturn(mockUriResource);
        Method method = getAccessibleHelperMethod("addUriResource", Resource.class, Property.class, URI.class, Class.class, Method.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testUri, dummyParentClass, dummyParentMethod, rdfNodeContainer, marshallingContext);
        verify(mockJenaResource).addProperty(mockAttributeProperty, mockUriResource);
        verify(mockModel).createResource(testUri.toString());
    }

    @Test
    void testAddUriResource_toContainer() throws Exception {
        URI testUri = new URI("http://example.com/linkedresourceInContainer");
        List<RDFNode> rdfNodeContainer = new ArrayList<>();
        Class<?> dummyParentClass = MyTestResource.class;
        Method dummyParentMethod = MyTestResource.class.getMethod("getLink");
        when(mockModel.createResource(testUri.toString())).thenReturn(mockUriResource);
        Method method = getAccessibleHelperMethod("addUriResource", Resource.class, Property.class, URI.class, Class.class, Method.class, List.class, MarshallingContext.class);
        method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testUri, dummyParentClass, dummyParentMethod, rdfNodeContainer, marshallingContext);
        assertEquals(1, rdfNodeContainer.size());
        assertSame(mockUriResource, rdfNodeContainer.get(0));
        verify(mockJenaResource, never()).addProperty(any(Property.class), any(RDFNode.class));
    }

    @Test
    void testAddUriResource_relative_disabled() throws Exception {
        URI testUri = new URI("relative/link");
        List<RDFNode> rdfNodeContainer = null;
        Class<?> dummyParentClass = MyTestResource.class;
        Method dummyParentMethod = MyTestResource.class.getMethod("getLink");
        boolean originalRelativeUrisDisabled = OSLC4JUtils.relativeURIsAreDisabled();
        try {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, "true");
            OSLC4JUtils.reset();
            Method method = getAccessibleHelperMethod("addUriResource", Resource.class, Property.class, URI.class, Class.class, Method.class, List.class, MarshallingContext.class);
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> {
                method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testUri, dummyParentClass, dummyParentMethod, rdfNodeContainer, marshallingContext);
            });
            assertTrue(thrown.getCause() instanceof OslcCoreRelativeURIException);
        } finally {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, Boolean.toString(originalRelativeUrisDisabled));
            OSLC4JUtils.reset();
        }
    }

    @Test
    void testAddUriResource_relative_enabled() throws Exception {
        URI testUri = new URI("relative/link");
        List<RDFNode> rdfNodeContainer = null;
        Class<?> dummyParentClass = MyTestResource.class;
        Method dummyParentMethod = MyTestResource.class.getMethod("getLink");
        boolean originalRelativeUrisDisabled = OSLC4JUtils.relativeURIsAreDisabled();
        try {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, "false");
            OSLC4JUtils.reset();
            when(mockModel.createResource(testUri.toString())).thenReturn(mockUriResource);
            Method method = getAccessibleHelperMethod("addUriResource", Resource.class, Property.class, URI.class, Class.class, Method.class, List.class, MarshallingContext.class);
            method.invoke(marshaller, mockJenaResource, mockAttributeProperty, testUri, dummyParentClass, dummyParentMethod, rdfNodeContainer, marshallingContext);
            verify(mockJenaResource).addProperty(mockAttributeProperty, mockUriResource);
        } finally {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, Boolean.toString(originalRelativeUrisDisabled));
            OSLC4JUtils.reset();
        }
    }

    @Test
    void testCreateJenaModel_simpleResource() throws Exception { /* ... unchanged, uses real model ... */ }
    @Test
    void testCreateJenaModel_withNestedResource() throws Exception { /* ... unchanged, uses real model ... */ }
    @Test
    void testCreateJenaModel_withReifiedProperty() throws Exception { /* ... unchanged, uses real model ... */ }
    @Test
    void testCreateJenaModel_withStringArray() throws Exception { /* ... unchanged, uses real model ... */ }
    @Test
    void testCreateJenaModel_withUriCollection() throws Exception { /* ... unchanged, uses real model ... */ }

    @Test
    void testCreateJenaModel_usesModelSupplier() throws Exception {
        MyTestResource myResource = new MyTestResource();
        Object[] objects = {myResource};

        when(mockModelSupplier.get()).thenReturn(mockModel);
        when(mockModel.getNsPrefixMap()).thenReturn(new HashMap<>()); // Needed for ensureNamespacePrefix
        when(mockModel.createResource(anyString())).thenReturn(mockJenaResource);
        when(mockModel.createResource()).thenReturn(mockJenaResource);
        // Allow setNsPrefix to be called on the mockModel
        when(mockModel.setNsPrefix(anyString(), anyString())).thenAnswer(invocation -> mockModel);


        Model resultModel = marshallerWithMockModel.createJenaModel(objects);

        assertSame(mockModel, resultModel);
        verify(mockModelSupplier).get();
    }
}
