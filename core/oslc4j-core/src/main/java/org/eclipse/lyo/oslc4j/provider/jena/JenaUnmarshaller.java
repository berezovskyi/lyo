package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.impl.ReifierStd;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.UnparseableLiteral;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMissingSetMethodException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMisusedOccursException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.core.model.AnyResource;
import org.eclipse.lyo.oslc4j.core.model.IExtendedResource;
import org.eclipse.lyo.oslc4j.core.model.IReifiedResource;
import org.eclipse.lyo.oslc4j.core.model.IResource;
import org.eclipse.lyo.oslc4j.core.model.InheritedMethodAnnotationHelper;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.TypeFactory;
import org.eclipse.lyo.oslc4j.core.model.XMLLiteral;
import org.eclipse.lyo.oslc4j.provider.jena.ordfm.ResourcePackages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;


public class JenaUnmarshaller {

    private static final Logger logger = LoggerFactory.getLogger(JenaUnmarshaller.class);
    private static final String RDF_TYPE_URI = OslcConstants.RDF_NAMESPACE + "type";
    private static final String METHOD_NAME_START_GET = "get";
    private static final String METHOD_NAME_START_IS  = "is";
    private static final String METHOD_NAME_START_SET = "set";
    // private static final int METHOD_NAME_START_GET_LENGTH = METHOD_NAME_START_GET.length(); // Moved to JenaHelperUtils or unused
    // private static final int METHOD_NAME_START_IS_LENGTH  = METHOD_NAME_START_IS.length(); // Moved to JenaHelperUtils or unused

    // Context class for unmarshalling
    private static class UnmarshallingContext {
        final Map<Class<?>, Map<String, Method>> classPropertyDefinitionsToSetMethods;
        final Map<String, Object> visitedResources; // Handles cycles

        UnmarshallingContext() {
            this.classPropertyDefinitionsToSetMethods = new HashMap<>();
            this.visitedResources = new HashMap<>();
        }
    }

    public JenaUnmarshaller() {
        // Default constructor
    }

    public <T> T unmarshalSingle(final Model model, Class<T> clazz)
            throws IllegalArgumentException, LyoModelException {
        final T[] ts = unmarshal(model, clazz);
        if (ts.length != 1) {
            throw new IllegalArgumentException("Model shall contain exactly 1 instance of the "
                    + "class");
        }
        return ts[0];
    }

    @SuppressWarnings({"unchecked"})
    public <T> T unmarshal(final Resource resource, Class<T> clazz) throws LyoModelException {
        try {
            UnmarshallingContext context = new UnmarshallingContext();
            return (T)fromJenaResource(resource, clazz, context);
        } catch (DatatypeConfigurationException | IllegalAccessException |
                InvocationTargetException | InstantiationException | OslcCoreApplicationException
                | NoSuchMethodException | URISyntaxException e) {
            throw new LyoModelException(e);
        }
    }

    @SuppressWarnings({"deprecation", "JavaReflectionMemberAccess"})
    private Object fromJenaResource(final Resource resource, Class<?> beanClass, UnmarshallingContext context) // Added context
            throws DatatypeConfigurationException, IllegalAccessException,
            IllegalArgumentException,
            InstantiationException, InvocationTargetException, OslcCoreApplicationException,
            URISyntaxException, SecurityException, NoSuchMethodException {
        ResourcePackages.mapPackage(beanClass.getPackage());
        Optional<Class<?>> mostConcreteResourceClass = ResourcePackages.getClassOf(resource, beanClass);
        if (mostConcreteResourceClass.isPresent()) {
            beanClass = mostConcreteResourceClass.get();
        }
        final Object   newInstance = beanClass.getDeclaredConstructor().newInstance();
        final HashSet<String> rdfTypes = new HashSet<>();
        JenaHelperUtils.getTypesFromResource(resource, rdfTypes);

        fromResource(beanClass, newInstance, resource, context, rdfTypes);
        return newInstance;
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public <T> T[] unmarshal(final Model model, Class<T> clazz) throws LyoModelException {
        try {
            UnmarshallingContext context = new UnmarshallingContext();
            final Object[] objects = fromJenaModel(model, clazz, context);
            return (T[]) objects;
        } catch (DatatypeConfigurationException | IllegalAccessException |
                InvocationTargetException | InstantiationException | OslcCoreApplicationException
                | NoSuchMethodException | URISyntaxException e) {
            throw new LyoModelException(e);
        }
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private Object[] fromJenaModel(final Model model, final Class<?> beanClass, UnmarshallingContext context) // Added context
            throws DatatypeConfigurationException, IllegalAccessException,
            IllegalArgumentException,
            InstantiationException, InvocationTargetException, OslcCoreApplicationException,
            URISyntaxException, SecurityException, NoSuchMethodException {

        Instant start = Instant.now();
        final List<Object> results = new ArrayList<>();

        if (beanClass.getAnnotation(OslcResourceShape.class) != null) {
            ResIterator listSubjects;
            if (!OSLC4JUtils.useBeanClassForParsing()) {
                final String qualifiedName = TypeFactory.getQualifiedName(beanClass);
                listSubjects = model.listSubjectsWithProperty(RDF.type, model.getResource(qualifiedName));
                List<Resource> resourceList = listSubjects.toList();
                createObjectResultList(beanClass, results, resourceList, context);
            } else {
                listSubjects = model.listSubjectsWithProperty(RDF.type);
                List<Resource> resourceList = new ArrayList<>();
                while (listSubjects.hasNext()) {
                    final Resource resource = listSubjects.next();
                    SimpleSelector selector = new SimpleSelector(null, null, resource);
                    StmtIterator listStatements = model.listStatements(selector);
                    if (!listStatements.hasNext()) {
                        resourceList.add(resource);
                    }
                }
                createObjectResultList(beanClass, results, resourceList, context);
            }
        } else if (URI.class.equals(beanClass)) {
            StmtIterator memberIterator = model.listStatements(null, RDFS.member, (RDFNode) null);
            while (memberIterator.hasNext()) {
                Statement memberStatement = memberIterator.next();
                RDFNode memberObject = memberStatement.getObject();
                if (memberObject.isURIResource()) {
                    URI memberURI = URI.create(memberObject.asResource().getURI());
                    results.add(memberURI);
                }
            }
        }

        Instant finish = Instant.now();
        logger.trace("fromJenaModel - Execution Duration: {} ms", Duration.between(start, finish).toMillis());
        return results.toArray((Object[]) Array.newInstance(beanClass, results.size()));
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private List<Object> createObjectResultList(Class<?> beanClass, List<Object> results, List<Resource> listSubjects, UnmarshallingContext context) // Added context
            throws IllegalAccessException, InstantiationException,
            DatatypeConfigurationException, InvocationTargetException,
            OslcCoreApplicationException, URISyntaxException,
            NoSuchMethodException {
        if (null != listSubjects) {
            ResourcePackages.mapPackage(beanClass.getPackage());
            Class<?> originalBeanClass = beanClass;
            for (final Resource resource : listSubjects) {
                beanClass = originalBeanClass;
                Optional<Class<?>> mostConcreteResourceClass = ResourcePackages.getClassOf(resource, beanClass);
                if (mostConcreteResourceClass.isPresent()) {
                    beanClass = mostConcreteResourceClass.get();
                    if (!originalBeanClass.isAssignableFrom(beanClass)) {
                        continue;
                    }
                }
                final Object newInstance = beanClass.getDeclaredConstructor().newInstance();
                final HashSet<String> rdfTypes = new HashSet<>();
                JenaHelperUtils.getTypesFromResource(resource, rdfTypes);
                fromResource(beanClass, newInstance, resource, context, rdfTypes);
                results.add(newInstance);
            }
        }
        return results;
    }

    @SuppressWarnings({"unchecked", "JavaReflectionMemberAccess"})
    private void fromResource(final Class<?> beanClass, final Object bean, final Resource resource, UnmarshallingContext context, HashSet<String> rdfTypes)
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException, InstantiationException,
            InvocationTargetException, OslcCoreApplicationException, URISyntaxException, SecurityException, NoSuchMethodException {

        Map<String, Method> setMethodMap = context.classPropertyDefinitionsToSetMethods.get(beanClass);
        if (setMethodMap == null) {
            setMethodMap = createPropertyDefinitionToSetMethods(beanClass);
            context.classPropertyDefinitionsToSetMethods.put(beanClass, setMethodMap);
        }

        context.visitedResources.put(JenaHelperUtils.getVisitedResourceName(resource),bean);

        if (bean instanceof IResource) { /* ... unchanged ... */ }

        final Map<String, List<Object>> propertyDefinitionsToArrayValues = new HashMap<>();
        final Set<Method> singleValueMethodsUsed = new HashSet<>();
        final StmtIterator listProperties = resource.listProperties();
        final IExtendedResource extendedResource = (bean instanceof IExtendedResource) ? (IExtendedResource) bean : null;
        final Map<QName, Object> extendedProperties = (extendedResource != null) ? new HashMap<>() : null;
        if (extendedResource != null) extendedResource.setExtendedProperties(extendedProperties);

        // rdfTypes passed in

        while (listProperties.hasNext()) {
            final Statement statement = listProperties.next();
            final Property predicate = statement.getPredicate();
            final RDFNode object = statement.getObject();
            final String uri = predicate.getURI();
            final Method setMethod = setMethodMap.get(uri);

            if (setMethod == null) {
                if (RDF_TYPE_URI.equals(uri)) {
                    if (extendedResource != null && object.isResource()) {
                        extendedResource.addType(new URI(object.asResource().getURI()));
                    }
                } else {
                    if (extendedProperties != null) {
                        String prefix = resource.getModel().getNsURIPrefix(predicate.getNameSpace());
                        if (prefix == null) prefix = JenaHelperUtils.generatePrefix(resource.getModel(), predicate.getNameSpace());
                        final QName key = new QName(predicate.getNameSpace(), predicate.getLocalName(), prefix);
                        final Object value = handleExtendedPropertyValue(beanClass, object, context, key, rdfTypes); // Pass context
                        final Object previous = extendedProperties.get(key);
                        if (previous == null) extendedProperties.put(key, value);
                        else {
                            final Collection<Object> collection = (previous instanceof Collection) ? (Collection<Object>) previous : new ArrayList<>();
                            if (!(previous instanceof Collection)) collection.add(previous);
                            collection.add(value);
                            extendedProperties.put(key, collection);
                        }
                    } else {
                        logger.debug("Set method not found for object type: {}, uri: {}", beanClass.getName(), uri);
                    }
                }
            } else { // setMethod is not null
                Class<?> setMethodRawParameterClass = setMethod.getParameterTypes()[0];
                Class<?> setMethodComponentParameterClass = getSetMethodComponentParameterClass(setMethod);
                final List<RDFNode> objects;
                boolean isMultiple = setMethodRawParameterClass.isArray() || Collection.class.isAssignableFrom(setMethodRawParameterClass);

                if (isMultiple && object.isResource() && ( (object.asResource().hasProperty(RDF.first) && object.asResource().hasProperty(RDF.rest)) || (RDF.nil.equals(object)) || object.canAs(RDFList.class))) {
                    objects = new ArrayList<>(); // process RDF List
                    Resource listNode = object.asResource();
                    while (listNode != null && !RDF.nil.getURI().equals(listNode.getURI())) {
                        context.visitedResources.put(JenaHelperUtils.getVisitedResourceName(listNode), new Object());
                        RDFNode o = listNode.getPropertyResourceValue(RDF.first);
                        if (o != null) objects.add(o);
                        listNode = listNode.getPropertyResourceValue(RDF.rest);
                    }
                    context.visitedResources.put(JenaHelperUtils.getVisitedResourceName(object.asResource()), objects);
                } else {
                    final Class<? extends Container> collectionResourceClass = JenaHelperUtils.getRdfCollectionResourceClass(object.getModel(), object);
                    if (isMultiple && collectionResourceClass != null) { // process RDF Bag, Seq, Alt
                        objects = new ArrayList<>(); Container container = object.as(collectionResourceClass);
                        NodeIterator iterator = container.iterator();
                        while (iterator.hasNext()) { RDFNode oNode = iterator.next();
                            if (oNode.isResource()) context.visitedResources.put(JenaHelperUtils.getVisitedResourceName(oNode.asResource()), new Object());
                            objects.add(oNode);
                        }
                        context.visitedResources.put(JenaHelperUtils.getVisitedResourceName(object.asResource()), objects);
                    } else { objects = Collections.singletonList(object); } // Single value or multiple statements for same predicate
                }

                @SuppressWarnings("unchecked")
                Class<? extends IReifiedResource<?>> reifiedClass = null;
                Class<?> reifiedValueClass = setMethodComponentParameterClass;

                if (IReifiedResource.class.isAssignableFrom(setMethodRawParameterClass)) {
                     reifiedClass = (Class<? extends IReifiedResource<?>>) setMethodRawParameterClass;
                     reifiedValueClass = getReifiedValueClass(reifiedClass);
                } else if (Collection.class.isAssignableFrom(setMethodRawParameterClass) && IReifiedResource.class.isAssignableFrom(setMethodComponentParameterClass)) {
                    reifiedClass = (Class<? extends IReifiedResource<?>>) setMethodComponentParameterClass;
                    reifiedValueClass = getReifiedValueClass(reifiedClass);
                }

                for (RDFNode o : objects) {
                    Object parameter;
                    if (o.isLiteral()) parameter = getLiteralValue(o.asLiteral(), reifiedValueClass);
                    else if (o.isResource()) {
                        if (URI.class == reifiedValueClass) parameter = getUriFromResource(o.asResource(), beanClass, setMethod.getName());
                        else {
                            HashSet<String> nestedRdfTypes = new HashSet<>();
                            JenaHelperUtils.getTypesFromResource(o.asResource(), nestedRdfTypes);
                            parameter = resolveResourceReference(o.asResource(), reifiedValueClass, context, nestedRdfTypes);
                        }
                    } else { logger.warn("RDFNode is neither Literal nor Resource: {}", o); continue; }

                    if (parameter != null) {
                        if (reifiedClass != null) {
                            parameter = createReifiedResource(statement, reifiedClass, reifiedValueClass, parameter, context, rdfTypes);
                        }
                        if (isMultiple) propertyDefinitionsToArrayValues.computeIfAbsent(uri, k -> new ArrayList<>()).add(parameter);
                        else {
                            if (singleValueMethodsUsed.contains(setMethod)) throw new OslcCoreMisusedOccursException(beanClass, setMethod);
                            setMethod.invoke(bean, parameter); singleValueMethodsUsed.add(setMethod);
                        }
                    }
                }
            }
        }
        for (final Map.Entry<String, List<Object>> entry : propertyDefinitionsToArrayValues.entrySet()) { /* ... unchanged ... */ }
    }

    private Class<?> getReifiedValueClass(Class<? extends IReifiedResource<?>> reifiedClass) { /* ... unchanged ... */ return Object.class; }
    private Collection<?> newCollectionInstance(Class<?> pc) throws Exception { /* ... unchanged ... */ return new ArrayList<>(); }
    private Class<?> getSetMethodComponentParameterClass(Method setMethod) { /* ... unchanged ... */ return Object.class; }

    private String getStringFromLiteral(Literal literal) { return literal.getString(); }
    private Boolean getBooleanFromLiteral(Literal literal) { /* ... unchanged ... */ return false; }
    private Byte getByteFromLiteral(Literal literal) { /* ... unchanged ... */ return 0; }
    private Short getShortFromLiteral(Literal literal) { /* ... unchanged ... */ return 0; }
    private Integer getIntegerFromLiteral(Literal literal) { /* ... unchanged ... */ return 0; }
    private Long getLongFromLiteral(Literal literal) { /* ... unchanged ... */ return 0L; }
    private BigInteger getBigIntegerFromLiteral(Literal literal) { /* ... unchanged ... */ return BigInteger.ZERO; }
    private Float getFloatFromLiteral(Literal literal) { /* ... unchanged ... */ return 0f; }
    private Double getDoubleFromLiteral(Literal literal) { /* ... unchanged ... */ return 0.0; }
    private Date getDateFromLiteral(Literal literal) throws DatatypeConfigurationException { /* ... unchanged ... */ return null; }

    private Object getLiteralValue(Literal literal, Class<?> targetType) throws DatatypeConfigurationException { /* ... unchanged ... */ return null; }
    private URI getUriFromResource(Resource res, Class<?> beanClass, String meth) throws URISyntaxException, OslcCoreRelativeURIException { /* ... unchanged ... */ return null; }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private Object resolveResourceReference(Resource nestedJenaResource, Class<?> targetType, UnmarshallingContext context, HashSet<String> rdfTypesForNested)
            throws DatatypeConfigurationException, IllegalAccessException, InvocationTargetException, InstantiationException, OslcCoreApplicationException, NoSuchMethodException, URISyntaxException {
        String visitedResourceKey = JenaHelperUtils.getVisitedResourceName(nestedJenaResource);
        if (context.visitedResources.containsKey(visitedResourceKey)) return context.visitedResources.get(visitedResourceKey);
        Optional<Class<?>> optionalResourceClass = ResourcePackages.getClassOf(nestedJenaResource, targetType);
        Class<?> actualNestedBeanClass = optionalResourceClass.orElse(targetType);
        if (!targetType.isAssignableFrom(actualNestedBeanClass) && !actualNestedBeanClass.isAssignableFrom(targetType)) {
             logger.warn("Actual type {} of nested resource {} is not assignable to property type {}", actualNestedBeanClass, nestedJenaResource.getURI(), targetType);
             return null;
        }
        final Object nestedBean = actualNestedBeanClass.getDeclaredConstructor().newInstance();
        context.visitedResources.put(visitedResourceKey, nestedBean);
        fromResource(actualNestedBeanClass, nestedBean, nestedJenaResource, context, rdfTypesForNested);
        return nestedBean;
    }

    @SuppressWarnings("unchecked")
    private <T extends IReifiedResource<?>> T createReifiedResource(Statement statement, Class<T> reifiedClass, Class<?> valueClass, Object actualValue, UnmarshallingContext context, HashSet<String> rdfTypesForReifiedProps)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, DatatypeConfigurationException, OslcCoreApplicationException, URISyntaxException {
        T reifiedResourceInstance = reifiedClass.getDeclaredConstructor().newInstance();
        // Set value... (simplified, full logic needed)
        Method setValueMethod = null;
        try { setValueMethod = reifiedClass.getMethod("setValue", actualValue.getClass()); }
        catch (NoSuchMethodException e) { /* ... find compatible ... */ }
        if (setValueMethod == null) throw new NoSuchMethodException("setValue");
        setValueMethod.invoke(reifiedResourceInstance, actualValue);

        Graph stmtGraph = statement.getModel().getGraph();
        ExtendedIterator<Node> reifiedTriplesIter = ReifierStd.allNodes(stmtGraph, statement.asTriple());
        while (reifiedTriplesIter.hasNext()) {
            Node reifiedNodeJena = reifiedTriplesIter.next();
            Resource reifiedJenaResource = JenaHelperUtils.getResource(statement.getModel(), reifiedNodeJena);
            HashSet<String> currentReifiedNodeTypes = new HashSet<>();
            JenaHelperUtils.getTypesFromResource(reifiedJenaResource, currentReifiedNodeTypes);
            fromResource(reifiedClass, reifiedResourceInstance, reifiedJenaResource, context, currentReifiedNodeTypes);
        }
        return reifiedResourceInstance;
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    private Object handleExtendedPropertyValue(final Class<?> beanClass, final RDFNode object, UnmarshallingContext context, final QName propertyQName, final HashSet<String> rdfTypes)
            throws URISyntaxException, IllegalArgumentException, SecurityException, DatatypeConfigurationException, IllegalAccessException, InstantiationException, InvocationTargetException, OslcCoreApplicationException, NoSuchMethodException {
        if (object.isLiteral()) { /* ... (adapt to use context.visitedResources) ... */ }
        final Resource nestedResource = object.asResource();
        if ((nestedResource.getURI() == null || nestedResource.listProperties().hasNext()) && (!context.visitedResources.containsKey(JenaHelperUtils.getVisitedResourceName(nestedResource)))) {
            final AbstractResource any = new AnyResource();
            HashSet<String> nestedRdfTypes = new HashSet<>(); JenaHelperUtils.getTypesFromResource(nestedResource, nestedRdfTypes);
            fromResource(AnyResource.class, any, nestedResource, context, nestedRdfTypes);
            return any;
        }
        if (nestedResource.getURI() == null || nestedResource.listProperties().hasNext()) return context.visitedResources.get(JenaHelperUtils.getVisitedResourceName(nestedResource));
        else { /* ... URI creation ... */ }
        return null; // Placeholder
    }

    private Map<String, Method> createPropertyDefinitionToSetMethods(final Class<?> beanClass) throws OslcCoreApplicationException { /* ... unchanged ... */ return new HashMap<>(); }

    public <R extends IResource> R followLink(final Model m, final Link l, final Class<R> rClass)
            throws IllegalArgumentException, LyoModelException {
        final R[] rs = this.unmarshal(m, rClass); // Call instance method
        for (R r : rs) {
            if (l.getValue().equals(r.getAbout())) {
                return r;
            }
        }
        throw new IllegalArgumentException("Link cannot be followed in this model");
    }
}
