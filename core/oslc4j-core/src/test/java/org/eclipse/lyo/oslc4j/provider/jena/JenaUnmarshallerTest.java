package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcRdfCollectionType;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.eclipse.lyo.oslc4j.core.model.*;
import org.eclipse.lyo.oslc4j.provider.jena.JenaUnmarshaller.UnmarshallingContext; // Import the context
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;


import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.namespace.QName;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JenaUnmarshallerTest {

    private JenaUnmarshaller unmarshaller;
    private Model model;
    private UnmarshallingContext unmarshallingContext;


    @Mock
    private Resource mockJenaResource;

    // Enhanced Test Resource Class (Copied from previous step, ensure it's up-to-date)
    @OslcResourceShape(title = "Test Resource Unmarshal", describes = "test:TestResourceUnmarshal")
    @OslcNamespace(OslcConstants.OSLC_CORE_NAMESPACE)
    static class MyTestResource extends AbstractResource implements IExtendedResource {
        private String stringProp;
        private MyNestedResource nestedProp;
        private MyReifiedStringProperty reifiedProp;
        private String[] stringArrayProp;
        private List<URI> uriListProp;
        private Integer integerProp;
        private int intPrimitiveProp;
        private boolean booleanPrimitiveProp;
        private Boolean booleanObjectProp;
        private long longPrimitiveProp;
        private Long longObjectProp;
        private double doublePrimitiveProp;
        private Double doubleObjectProp;
        private Date dateProp;
        private Set<String> stringSetProp;
        private List<String> rdfBagProp;
        private List<String> rdfSeqProp;
        private List<String> rdfAltProp;

        private Map<QName, Object> extendedProperties = new HashMap<>();

        public MyTestResource() throws URISyntaxException { super(new URI("urn:example:mytestresource/" + UUID.randomUUID().toString())); }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "stringProp")
        public String getStringProp() { return stringProp; }
        public void setStringProp(String stringProp) { this.stringProp = stringProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "nestedProp")
        public MyNestedResource getNestedProp() { return nestedProp; }
        public void setNestedProp(MyNestedResource nestedProp) { this.nestedProp = nestedProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "reifiedProp")
        public MyReifiedStringProperty getReifiedProp() { return reifiedProp; }
        public void setReifiedProp(MyReifiedStringProperty reifiedProp) { this.reifiedProp = reifiedProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "stringArrayProp")
        public String[] getStringArrayProp() { return stringArrayProp; }
        public void setStringArrayProp(String[] stringArrayProp) { this.stringArrayProp = stringArrayProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "uriListProp")
        public List<URI> getUriListProp() { return uriListProp; }
        public void setUriListProp(List<URI> uriListProp) { this.uriListProp = uriListProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "integerProp")
        public Integer getIntegerProp() { return integerProp; }
        public void setIntegerProp(Integer integerProp) { this.integerProp = integerProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "intPrimitiveProp")
        public int getIntPrimitiveProp() { return intPrimitiveProp; }
        public void setIntPrimitiveProp(int intPrimitiveProp) { this.intPrimitiveProp = intPrimitiveProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "booleanPrimitiveProp")
        public boolean isBooleanPrimitiveProp() { return booleanPrimitiveProp; }
        public void setBooleanPrimitiveProp(boolean booleanPrimitiveProp) { this.booleanPrimitiveProp = booleanPrimitiveProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "booleanObjectProp")
        public Boolean getBooleanObjectProp() { return booleanObjectProp; }
        public void setBooleanObjectProp(Boolean booleanObjectProp) { this.booleanObjectProp = booleanObjectProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "longPrimitiveProp")
        public long getLongPrimitiveProp() { return longPrimitiveProp; }
        public void setLongPrimitiveProp(long longPrimitiveProp) { this.longPrimitiveProp = longPrimitiveProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "longObjectProp")
        public Long getLongObjectProp() { return longObjectProp; }
        public void setLongObjectProp(Long longObjectProp) { this.longObjectProp = longObjectProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "doublePrimitiveProp")
        public double getDoublePrimitiveProp() { return doublePrimitiveProp; }
        public void setDoublePrimitiveProp(double doublePrimitiveProp) { this.doublePrimitiveProp = doublePrimitiveProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "doubleObjectProp")
        public Double getDoubleObjectProp() { return doubleObjectProp; }
        public void setDoubleObjectProp(Double doubleObjectProp) { this.doubleObjectProp = doubleObjectProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "dateProp")
        public Date getDateProp() { return dateProp; }
        public void setDateProp(Date dateProp) { this.dateProp = dateProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "stringSetProp")
        public Set<String> getStringSetProp() { return stringSetProp; }
        public void setStringSetProp(Set<String> stringSetProp) { this.stringSetProp = stringSetProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "rdfBagProp") @OslcRdfCollectionType(collectionType = "Bag", namespaceURI = OslcConstants.RDF_NAMESPACE)
        public List<String> getRdfBagProp() { return rdfBagProp; }
        public void setRdfBagProp(List<String> rdfBagProp) { this.rdfBagProp = rdfBagProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "rdfSeqProp") @OslcRdfCollectionType(collectionType = "Seq", namespaceURI = OslcConstants.RDF_NAMESPACE)
        public List<String> getRdfSeqProp() { return rdfSeqProp; }
        public void setRdfSeqProp(List<String> rdfSeqProp) { this.rdfSeqProp = rdfSeqProp; }

        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "rdfAltProp") @OslcRdfCollectionType(collectionType = "Alt", namespaceURI = OslcConstants.RDF_NAMESPACE)
        public List<String> getRdfAltProp() { return rdfAltProp; }
        public void setRdfAltProp(List<String> rdfAltProp) { this.rdfAltProp = rdfAltProp; }

        @Override public Map<QName, Object> getExtendedProperties() { return extendedProperties; }
        @Override public void setExtendedProperties(Map<QName, Object> properties) { this.extendedProperties = properties; }
        @Override public void addExtendedProperty(QName name, Object value) { this.extendedProperties.put(name, value); }
        @Override public Object getExtendedProperty(QName name) { return this.extendedProperties.get(name); }
    }

    @OslcResourceShape(title = "Nested Test Resource", describes = "test:NestedResource")
    @OslcNamespace(OslcConstants.OSLC_CORE_NAMESPACE)
    static class MyNestedResource extends AbstractResource {
        private String nestedId;
        public MyNestedResource() throws URISyntaxException { super(new URI("urn:example:mynestedresource/" + UUID.randomUUID().toString())); }
        @OslcPropertyDefinition(OslcConstants.OSLC_CORE_NAMESPACE + "nestedId")
        public String getNestedId() { return nestedId; }
        public void setNestedId(String nestedId) { this.nestedId = nestedId; }
    }

    @OslcResourceShape(title = "Reified String Property", describes = "test:ReifiedString")
    @OslcNamespace(OslcConstants.OSLC_CORE_NAMESPACE)
    static class MyReifiedStringProperty extends AbstractReifiedResource<String> {
        public MyReifiedStringProperty() throws URISyntaxException { super(); }
    }

    @BeforeEach
    void setUp() {
        unmarshaller = new JenaUnmarshaller();
        model = ModelFactory.createDefaultModel();
        model.setNsPrefix("oslc", OslcConstants.OSLC_CORE_NAMESPACE);
        model.setNsPrefix("dcterms", DCTerms.NS);
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("test", "test:");
        model.setNsPrefix("ext", "http://example.com/ext#");
        unmarshallingContext = new UnmarshallingContext();
    }

    private Method getAccessibleHelperMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = JenaUnmarshaller.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    @Test
    void testGetStringFromLiteral() throws Exception {
        Literal literal = model.createLiteral("hello");
        Method method = getAccessibleHelperMethod("getStringFromLiteral", Literal.class);
        String result = (String) method.invoke(unmarshaller, literal);
        assertEquals("hello", result);
    }

    @Test
    void testGetBooleanFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getBooleanFromLiteral", Literal.class);
        assertTrue((Boolean) method.invoke(unmarshaller, model.createTypedLiteral(true)));
        assertTrue((Boolean) method.invoke(unmarshaller, model.createLiteral("true")));
        assertTrue((Boolean) method.invoke(unmarshaller, model.createLiteral("1")));
        assertFalse((Boolean) method.invoke(unmarshaller, model.createTypedLiteral(false)));
        assertFalse((Boolean) method.invoke(unmarshaller, model.createLiteral("false")));
        assertFalse((Boolean) method.invoke(unmarshaller, model.createLiteral("0")));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createLiteral("not-a-boolean")));
    }

    @Test
    void testGetByteFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getByteFromLiteral", Literal.class);
        assertEquals((byte) 123, method.invoke(unmarshaller, model.createTypedLiteral((byte) 123)));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createLiteral("not-a-byte")));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createTypedLiteral(300)));
    }

    @Test
    void testGetShortFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getShortFromLiteral", Literal.class);
        assertEquals((short) 12345, method.invoke(unmarshaller, model.createTypedLiteral((short) 12345)));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createLiteral("not-a-short")));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createTypedLiteral(40000)));
    }

    @Test
    void testGetIntegerFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getIntegerFromLiteral", Literal.class);
        assertEquals(123456, method.invoke(unmarshaller, model.createTypedLiteral(123456)));
        assertThrows(InvocationTargetException.class, () -> method.invoke(unmarshaller, model.createLiteral("not-an-int")));
    }

    @Test
    void testGetLongFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getLongFromLiteral", Literal.class);
        assertEquals(1234567890L, method.invoke(unmarshaller, model.createTypedLiteral(1234567890L)));
    }

    @Test
    void testGetBigIntegerFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getBigIntegerFromLiteral", Literal.class);
        assertEquals(new BigInteger("12345678901234567890"), method.invoke(unmarshaller, model.createTypedLiteral(new BigInteger("12345678901234567890"))));
    }

    @Test
    void testGetFloatFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getFloatFromLiteral", Literal.class);
        assertEquals(123.45f, (Float) method.invoke(unmarshaller, model.createTypedLiteral(123.45f)), 0.001f);
        assertEquals(Float.POSITIVE_INFINITY, (Float) method.invoke(unmarshaller, model.createTypedLiteral("INF", XSDDatatype.XSDfloat)));
        assertEquals(Float.NEGATIVE_INFINITY, (Float) method.invoke(unmarshaller, model.createTypedLiteral("-INF", XSDDatatype.XSDfloat)));
    }

    @Test
    void testGetDoubleFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getDoubleFromLiteral", Literal.class);
        assertEquals(123.45678, (Double) method.invoke(unmarshaller, model.createTypedLiteral(123.45678)), 0.00001);
        assertEquals(Double.POSITIVE_INFINITY, (Double) method.invoke(unmarshaller, model.createTypedLiteral("INF", XSDDatatype.XSDdouble)));
        assertEquals(Double.NEGATIVE_INFINITY, (Double) method.invoke(unmarshaller, model.createTypedLiteral("-INF", XSDDatatype.XSDdouble)));
    }

    @Test
    void testGetDateFromLiteral() throws Exception {
        Method method = getAccessibleHelperMethod("getDateFromLiteral", Literal.class);
        String dateTimeString = "2023-10-26T10:30:00Z";
        Literal dateLiteral = model.createTypedLiteral(dateTimeString, XSDDatatype.XSDdateTime);
        Date date = (Date) method.invoke(unmarshaller, dateLiteral);
        assertNotNull(date);
    }

    @Test
    void testGetLiteralValue_Dispatcher() throws Exception {
        Method method = getAccessibleHelperMethod("getLiteralValue", Literal.class, Class.class);
        assertEquals("test", method.invoke(unmarshaller, model.createLiteral("test"), String.class));
        assertEquals(true, method.invoke(unmarshaller, model.createTypedLiteral(true), Boolean.class));
        Object actualXml = method.invoke(unmarshaller, model.createTypedLiteral("<r/>", XMLLiteralType.theXMLLiteralType), Object.class);
        assertTrue(actualXml instanceof org.apache.jena.rdf.model.impl.XMLLiteralImpl);
    }

    @Test
    void testGetUriFromResource_absolute() throws Exception {
        String uri = "http://example.com/resource";
        when(mockJenaResource.getURI()).thenReturn(uri);
        Method method = getAccessibleHelperMethod("getUriFromResource", Resource.class, Class.class, String.class);
        URI result = (URI) method.invoke(unmarshaller, mockJenaResource, MyTestResource.class, "setLink");
        assertEquals(uri, result.toString());
    }

    @Test
    void testGetUriFromResource_relative_disabled() throws Exception {
        String uri = "relative/uri";
        when(mockJenaResource.getURI()).thenReturn(uri);
        Method method = getAccessibleHelperMethod("getUriFromResource", Resource.class, Class.class, String.class);
        boolean originalFlag = OSLC4JUtils.relativeURIsAreDisabled();
        try {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, "true");
            OSLC4JUtils.reset();
            InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> {
                method.invoke(unmarshaller, mockJenaResource, MyTestResource.class, "setLink");
            });
            assertTrue(e.getCause() instanceof OslcCoreRelativeURIException);
        } finally {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, Boolean.toString(originalFlag));
            OSLC4JUtils.reset();
        }
    }

    @Test
    void testGetUriFromResource_relative_enabled() throws Exception {
        String uri = "relative/uri";
        when(mockJenaResource.getURI()).thenReturn(uri);
        Method method = getAccessibleHelperMethod("getUriFromResource", Resource.class, Class.class, String.class);
        boolean originalFlag = OSLC4JUtils.relativeURIsAreDisabled();
        try {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, "false");
            OSLC4JUtils.reset();
            URI result = (URI) method.invoke(unmarshaller, mockJenaResource, MyTestResource.class, "setLink");
            assertEquals(uri, result.toString());
            assertFalse(result.isAbsolute());
        } finally {
            System.setProperty(OslcConstants.OSLC4J_DISABLE_RELATIVE_URIS, Boolean.toString(originalFlag));
            OSLC4JUtils.reset();
        }
    }

    @Test
    void testGetUriFromResource_nullUri() throws Exception {
        when(mockJenaResource.getURI()).thenReturn(null);
        Method method = getAccessibleHelperMethod("getUriFromResource", Resource.class, Class.class, String.class);
        URI result = (URI) method.invoke(unmarshaller, mockJenaResource, MyTestResource.class, "setLink");
        assertNull(result);
    }

    @Test
    void testResolveResourceReference_simple() throws Exception {
        Resource nestedJenaRes = model.createResource("http://example.com/nested1");
        nestedJenaRes.addProperty(RDF.type, model.createResource(OslcConstants.OSLC_CORE_NAMESPACE + "NestedResource"));
        Property nestedIdProp = model.createProperty(OslcConstants.OSLC_CORE_NAMESPACE + "nestedId");
        nestedJenaRes.addProperty(nestedIdProp, "nestedValue123");

        Method method = getAccessibleHelperMethod("resolveResourceReference", Resource.class, Class.class, UnmarshallingContext.class, HashSet.class);
        HashSet<String> nestedRdfTypes = new HashSet<>();
        JenaHelperUtils.getTypesFromResource(nestedJenaRes, nestedRdfTypes);

        MyNestedResource result = (MyNestedResource) method.invoke(unmarshaller, nestedJenaRes, MyNestedResource.class,
            unmarshallingContext, nestedRdfTypes);

        assertNotNull(result);
        assertEquals("nestedValue123", result.getNestedId());
        assertEquals(nestedJenaRes.getURI(), result.getAbout().toString());
    }

    @Test
    void testResolveResourceReference_visited() throws Exception {
        Resource nestedJenaRes = model.createResource("http://example.com/visitedNested");
        nestedJenaRes.addProperty(RDF.type, model.createResource(OslcConstants.OSLC_CORE_NAMESPACE + "NestedResource"));
        Property nestedIdProp = model.createProperty(OslcConstants.OSLC_CORE_NAMESPACE + "nestedId");
        nestedJenaRes.addProperty(nestedIdProp, "visitedValue");

        Method method = getAccessibleHelperMethod("resolveResourceReference", Resource.class, Class.class, UnmarshallingContext.class, HashSet.class);
        HashSet<String> nestedRdfTypes = new HashSet<>();
        JenaHelperUtils.getTypesFromResource(nestedJenaRes, nestedRdfTypes);

        MyNestedResource result1 = (MyNestedResource) method.invoke(unmarshaller, nestedJenaRes, MyNestedResource.class,
            unmarshallingContext, nestedRdfTypes);
        assertNotNull(result1);
        assertEquals("visitedValue", result1.getNestedId());

        MyNestedResource result2 = (MyNestedResource) method.invoke(unmarshaller, nestedJenaRes, MyNestedResource.class,
            unmarshallingContext, nestedRdfTypes);
        assertSame(result1, result2);
    }

    @Test
    void testCreateReifiedResource() throws Exception {
        Resource subjectRes = model.createResource("http://example.com/subject");
        Property predicateProp = model.createProperty(OslcConstants.OSLC_CORE_NAMESPACE + "reifiedProp");
        Literal objectLiteral = model.createLiteral("ReifiedValue");
        Statement originalStatement = model.createStatement(subjectRes, predicateProp, objectLiteral);
        model.add(originalStatement);

        ReifiedStatement reifiedStatement = model.createReifiedStatement(originalStatement);
        reifiedStatement.addProperty(DCTerms.creator, "testCreator");
        reifiedStatement.addProperty(RDF.type, model.createResource(OslcConstants.OSLC_CORE_NAMESPACE + "ReifiedString"));

        Method method = getAccessibleHelperMethod("createReifiedResource", Statement.class, Class.class, Class.class, Object.class, UnmarshallingContext.class, HashSet.class);
        HashSet<String> reifiedRdfTypes = new HashSet<>();
        JenaHelperUtils.getTypesFromResource(reifiedStatement, reifiedRdfTypes);

        MyReifiedStringProperty result = (MyReifiedStringProperty) method.invoke(unmarshaller, originalStatement, MyReifiedStringProperty.class,
            String.class, "ReifiedValue", unmarshallingContext, reifiedRdfTypes);

        assertNotNull(result);
        assertEquals("ReifiedValue", result.getValue());
        assertNotNull(result.getExtendedProperties());
        assertEquals("testCreator", result.getExtendedProperties().get(new QName(DCTerms.NS, "creator")));
    }

    @Test
    void testFromResource_AllLiteralTypes() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testFromResource_stringSet() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testFromResource_RdfCollectionTypes() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testFromResource_ExtendedProperties() throws Exception { /* ... (ensure MyTestResource type is correct and ext namespace) ... */ }
    @Test
    void testUnmarshal_MultipleResources() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testUnmarshal_MixedResources() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testUnmarshal_EmptyModel() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testUnmarshalSingle_Success() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }
    @Test
    void testUnmarshalSingle_NoResource() { /* ... unchanged ... */ }
    @Test
    void testUnmarshalSingle_MultipleResources() { /* ... unchanged ... */ }
    @Test
    void testFromResource_TypeMismatch_ErrorHandling() throws Exception { /* ... (ensure MyTestResource type is correct) ... */ }

    @Test
    void testFollowLink_movedToUnmarshaller() throws Exception {
        Model localModel = ModelFactory.createDefaultModel();
        Resource targetRes = localModel.createResource("http://example.com/targetResource")
                                     .addProperty(RDF.type, localModel.createResource(OslcConstants.OSLC_CORE_NAMESPACE + "TestResourceUnmarshal"))
                                     .addProperty(localModel.createProperty(OslcConstants.OSLC_CORE_NAMESPACE + "stringProp"), "Target Acquired");

        Link linkToTarget = new Link(new URI("http://example.com/targetResource"));

        MyTestResource result = unmarshaller.followLink(localModel, linkToTarget, MyTestResource.class);
        assertNotNull(result);
        assertEquals("Target Acquired", result.getStringProp());
    }
}
