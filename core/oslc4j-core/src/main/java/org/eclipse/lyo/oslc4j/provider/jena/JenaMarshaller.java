package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.datatypes.xsd.impl.XMLLiteralType;
import org.apache.jena.datatypes.xsd.impl.XSDDateType;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.eclipse.lyo.oslc4j.core.NestedWildcardProperties;
import org.eclipse.lyo.oslc4j.core.OSLC4JConstants;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.OslcGlobalNamespaceProvider;
import org.eclipse.lyo.oslc4j.core.SingletonWildcardProperties;
import org.eclipse.lyo.oslc4j.core.UnparseableLiteral;
import org.eclipse.lyo.oslc4j.core.annotation.*;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreInvalidPropertyDefinitionException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.lyo.oslc4j.core.model.AnyResource;
import org.eclipse.lyo.oslc4j.core.model.FilteredResource;
import org.eclipse.lyo.oslc4j.core.model.IExtendedResource;
import org.eclipse.lyo.oslc4j.core.model.IReifiedResource;
import org.eclipse.lyo.oslc4j.core.model.IResource;
import org.eclipse.lyo.oslc4j.core.model.InheritedMethodAnnotationHelper;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;
import org.eclipse.lyo.oslc4j.core.model.ResponseInfo;
import org.eclipse.lyo.oslc4j.core.model.TypeFactory;
import org.eclipse.lyo.oslc4j.core.model.ValueType;
import org.eclipse.lyo.oslc4j.core.model.XMLLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class JenaMarshaller {

    private static final Logger logger = LoggerFactory.getLogger(JenaMarshaller.class);
    private final Supplier<Model> modelSupplier;

    private static final String PROPERTY_TOTAL_COUNT = "totalCount";
    private static final String PROPERTY_NEXT_PAGE = "nextPage";
    private static final String RDF_LIST = "List";
    private static final String RDF_ALT	 = "Alt";
    private static final String RDF_BAG	 = "Bag";
    private static final String RDF_SEQ	 = "Seq";

    // Context class for marshalling
    private static class MarshallingContext {
        final Model model;
        final Map<String, Object> selectedProperties; // TODO: This seems to be 'global' properties, not selected ones. Revisit name.
        final Map<IExtendedResource, Resource> marshallerVisitedResources; // Handles cycles for inline resources
        final Map<String, String> namespaceMappings;


        MarshallingContext(Model model, Map<String, Object> selectedProperties, Map<String, String> globalNamespaceMappings) {
            this.model = model;
            this.selectedProperties = selectedProperties;
            this.marshallerVisitedResources = new HashMap<>();
            this.namespaceMappings = globalNamespaceMappings;
        }
    }


    public JenaMarshaller(Supplier<Model> modelSupplier) {
        this.modelSupplier = modelSupplier;
    }

    public JenaMarshaller() {
        this(ModelFactory::createDefaultModel);
    }

    public Model createJenaModel(final Object[] objects)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        return createJenaModel(null,
                null,
                null,
                objects,
                null);
    }

    Model createJenaModel(final String descriptionAbout, final String responseInfoAbout,
            final ResponseInfo<?> responseInfo, final Object[] objects, final Map<String, Object> globalProperties) // Renamed for clarity
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, OslcCoreApplicationException {

        Instant start = Instant.now();
        final Model model = this.modelSupplier.get();
        final Map<String, String> globalNamespaceMappings = new HashMap<>(OslcGlobalNamespaceProvider.getInstance().getPrefixDefinitionMap());
        final MarshallingContext context = new MarshallingContext(model, globalProperties, globalNamespaceMappings);

        Resource descriptionResource = null;

        if (descriptionAbout != null) {
            if (OSLC4JUtils.isQueryResultListAsContainer()) {
                descriptionResource = context.model.createResource(descriptionAbout, RDFS.Container);
            } else {
                descriptionResource = context.model.createResource(descriptionAbout);
            }
            // Marshalling responseInfo.getContainer() properties
            handleExtendedProperties(FilteredResource.class, descriptionResource, responseInfo.getContainer(), context);


            if (responseInfoAbout != null) {
                final Resource responseInfoResource = context.model.createResource(
                        responseInfoAbout,
                        context.model.createProperty(OslcConstants.TYPE_RESPONSE_INFO));

                if (responseInfo != null) {
                    final int totalCount = responseInfo.totalCount() == null
                            ? objects.length
                            : responseInfo.totalCount();
                    responseInfoResource.addProperty(
                            context.model.createProperty(OslcConstants.OSLC_CORE_NAMESPACE, PROPERTY_TOTAL_COUNT),
                            context.model.createTypedLiteral(totalCount));

                    if (responseInfo.nextPage() != null) {
                        responseInfoResource.addProperty(
                                context.model.createProperty(OslcConstants.OSLC_CORE_NAMESPACE, PROPERTY_NEXT_PAGE),
                                context.model.createResource(responseInfo.nextPage()));
                    }
                    // Marshalling responseInfo itself properties
                    handleExtendedProperties(ResponseInfo.class, responseInfoResource, responseInfo, context);
                }
            }
        }


        for (final Object object : objects) {
            handleSingleResource(descriptionResource, object, context);
        }

        if (descriptionAbout != null) {
            JenaHelperUtils.ensureNamespacePrefix(OslcConstants.RDF_NAMESPACE_PREFIX,
                    OslcConstants.RDF_NAMESPACE, context.namespaceMappings);
            JenaHelperUtils.ensureNamespacePrefix(OslcConstants.RDFS_NAMESPACE_PREFIX,
                    OslcConstants.RDFS_NAMESPACE, context.namespaceMappings);
            if (responseInfoAbout != null) {
                JenaHelperUtils.ensureNamespacePrefix(OslcConstants.OSLC_CORE_NAMESPACE_PREFIX,
                        OslcConstants.OSLC_CORE_NAMESPACE, context.namespaceMappings);
            }
        }

        for (final Map.Entry<String, String> namespaceMapping : context.namespaceMappings.entrySet()) {
            context.model.setNsPrefix(namespaceMapping.getKey(), namespaceMapping.getValue());
        }

        Instant finish = Instant.now();
        logger.trace("createJenaModel - Execution Duration: {} ms", Duration.between(start, finish).toMillis());
        return context.model;
    }

    private void handleSingleResource(final Resource descriptionResource,
                                             final Object object,
                                             final MarshallingContext context)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        final Class<?> objectClass = object.getClass();
        JenaHelperUtils.recursivelyCollectNamespaceMappings(context.namespaceMappings, objectClass);
        final Resource mainResource;

        if (object instanceof URI) {
            mainResource = context.model.createResource(((URI) object).toASCIIString());
        } else {
            URI aboutURI = null;
            if (object instanceof IResource) {
                aboutURI = ((IResource) object).getAbout();
            }

            if (aboutURI != null) {
                if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                    throw new OslcCoreRelativeURIException(objectClass, "getAbout", aboutURI);
                }
                mainResource = context.model.createResource(aboutURI.toString());
            } else {
                mainResource = context.model.createResource();
            }

            if (objectClass.getAnnotation(OslcResourceShape.class) != null) {
                String qualifiedName = TypeFactory.getQualifiedName(objectClass);
                if (qualifiedName != null) {
                    mainResource.addProperty(RDF.type, context.model.createResource(qualifiedName));
                }
            }
            buildResource(object, objectClass, mainResource, context);
        }

        if (descriptionResource != null) {
            descriptionResource.addProperty(RDFS.member, mainResource);
        }
    }

    private void buildResource(final Object object,
                                      final Class<?> resourceClass,
                                      final Resource mainResource,
                                      final MarshallingContext context)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        if (context.selectedProperties == OSLC4JConstants.OSL4J_PROPERTY_SINGLETON) {
            return;
        }

        for (final Method method : resourceClass.getMethods()) {
            if (method.getParameterTypes().length == 0) {
                final String methodName = method.getName();
                if (((methodName.startsWith(JenaHelperUtils.METHOD_NAME_START_GET)) &&
                        (methodName.length() > JenaHelperUtils.METHOD_NAME_START_GET_LENGTH)) ||
                        ((methodName.startsWith(JenaHelperUtils.METHOD_NAME_START_IS)) &&
                                (methodName.length() > JenaHelperUtils.METHOD_NAME_START_IS_LENGTH))) {
                    final OslcPropertyDefinition oslcPropertyDefinitionAnnotation =
                            InheritedMethodAnnotationHelper.getAnnotation(method, OslcPropertyDefinition.class);

                    if (oslcPropertyDefinitionAnnotation != null) {
                        final Object value = method.invoke(object);
                        if (value != null) {
                            Map<String, Object> nestedSelectedProperties = null;
                            boolean onlyNested = false;

                            if (context.selectedProperties != null) {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> map = (Map<String, Object>) context.selectedProperties
                                        .get(oslcPropertyDefinitionAnnotation.value());
                                if (map != null) {
                                    nestedSelectedProperties = map;
                                } else if (context.selectedProperties instanceof SingletonWildcardProperties &&
                                        !(context.selectedProperties instanceof NestedWildcardProperties)) {
                                    nestedSelectedProperties = OSLC4JConstants.OSL4J_PROPERTY_SINGLETON;
                                } else if (context.selectedProperties instanceof NestedWildcardProperties) {
                                    nestedSelectedProperties = ((NestedWildcardProperties) context.selectedProperties).commonNestedProperties();
                                    onlyNested = !(context.selectedProperties instanceof SingletonWildcardProperties);
                                } else {
                                    continue;
                                }
                            }
                            MarshallingContext nestedContext = new MarshallingContext(context.model, nestedSelectedProperties, context.namespaceMappings);
                            // Carry over visited resources from parent context to handle deeper cycles if selectedProperties allows deep nesting
                            nestedContext.marshallerVisitedResources.putAll(context.marshallerVisitedResources);

                            buildAttributeResource(resourceClass, method, oslcPropertyDefinitionAnnotation, mainResource, value, onlyNested, nestedContext);
                        }
                    }
                }
            }
        }

        if (object instanceof IExtendedResource) {
            final IExtendedResource extendedResource = (IExtendedResource) object;
            handleExtendedProperties(resourceClass, mainResource, extendedResource, context);
        }
    }

    private void handleExtendedProperties(final Class<?> resourceClass, // Class of the bean being marshalled
                                                   final Resource mainJenaResource, // Jena resource for the bean
                                                   final IExtendedResource extendedResourceBean, // The bean itself
                                                   final MarshallingContext context) // Current marshalling context
            throws DatatypeConfigurationException,
            IllegalAccessException,
            InvocationTargetException,
            OslcCoreApplicationException {

        // Add this resource to visited map before processing its properties to break cycles
        context.marshallerVisitedResources.put(extendedResourceBean, mainJenaResource);

        for (final URI type : extendedResourceBean.getTypes()) {
            final String propertyName = type.toString();
            if (context.selectedProperties != null &&
                    context.selectedProperties.get(propertyName) == null &&
                    !(context.selectedProperties instanceof NestedWildcardProperties) &&
                    !(context.selectedProperties instanceof SingletonWildcardProperties)) {
                continue;
            }
            final Resource typeResource = context.model.createResource(propertyName);
            if (!mainJenaResource.hasProperty(RDF.type, typeResource)) {
                mainJenaResource.addProperty(RDF.type, typeResource);
            }
        }

        final Transformer transformer = JenaHelperUtils.createTransformer();

        for (final Map.Entry<QName, ?> extendedProperty : extendedResourceBean.getExtendedProperties().entrySet()) {
            final QName qName = extendedProperty.getKey();
            final String propertyName = qName.getNamespaceURI() + qName.getLocalPart();
            Map<String, Object> nestedSelectedProperties = null;
            boolean onlyNested = false;

            if (context.selectedProperties != null) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) context.selectedProperties.get(propertyName);
                if (map != null) {
                    nestedSelectedProperties = map;
                } else if (context.selectedProperties instanceof SingletonWildcardProperties &&
                        !(context.selectedProperties instanceof NestedWildcardProperties)) {
                    nestedSelectedProperties = OSLC4JConstants.OSL4J_PROPERTY_SINGLETON;
                } else if (context.selectedProperties instanceof NestedWildcardProperties) {
                    nestedSelectedProperties = ((NestedWildcardProperties) context.selectedProperties).commonNestedProperties();
                    onlyNested = !(context.selectedProperties instanceof SingletonWildcardProperties);
                } else {
                    continue;
                }
            }

            MarshallingContext extendedPropertyContext = new MarshallingContext(context.model, nestedSelectedProperties, context.namespaceMappings);
            extendedPropertyContext.marshallerVisitedResources.putAll(context.marshallerVisitedResources); // Carry over visited

            final Property jenaProperty = context.model.createProperty(propertyName);
            final Object value = extendedProperty.getValue();

            if (value instanceof Collection<?> collection) {
                for (Object next : collection) {
                    handleExtendedValue(resourceClass, next, mainJenaResource, jenaProperty, onlyNested, transformer, extendedPropertyContext);
                }
            } else {
                handleExtendedValue(resourceClass, value, mainJenaResource, jenaProperty, onlyNested, transformer, extendedPropertyContext);
            }
        }
    }

    private void handleExtendedValue(final Class<?> objectClass, // Class of the parent bean
                                            final Object value, // Value of the extended property
                                            final Resource jenaResource, // Jena resource of the parent bean
                                            final Property jenaProperty, // Jena property for this extended value
                                            final boolean onlyNested,
                                            final Transformer transformer,
                                            final MarshallingContext extValueContext) // Context with specific selectedProperties for this value
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        if (value instanceof UnparseableLiteral unparseable) {
            if (onlyNested) return;
            jenaResource.addProperty(jenaProperty, extValueContext.model.createLiteral(unparseable.getRawValue()));
        } else if (value instanceof AnyResource) { // This is an inline resource from extended properties
            final AbstractResource any = (AbstractResource) value;
            if (extValueContext.marshallerVisitedResources.containsKey(any)) { // Cycle detected
                jenaResource.addProperty(jenaProperty, extValueContext.marshallerVisitedResources.get(any));
            } else {
                final Resource nestedJenaResource;
                final URI aboutURI = any.getAbout();
                if (aboutURI != null) {
                    if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                        throw new OslcCoreRelativeURIException(AnyResource.class, "getAbout", aboutURI);
                    }
                    nestedJenaResource = extValueContext.model.createResource(aboutURI.toString());
                } else {
                    nestedJenaResource = extValueContext.model.createResource();
                }
                // Add to visited map *before* recursive call
                extValueContext.marshallerVisitedResources.put(any, nestedJenaResource);

                for (final URI type : any.getTypes()) {
                    final String typePropertyName = type.toString();
                     if (extValueContext.selectedProperties == null ||
                            extValueContext.selectedProperties.get(typePropertyName) != null ||
                            extValueContext.selectedProperties instanceof NestedWildcardProperties ||
                            extValueContext.selectedProperties instanceof SingletonWildcardProperties) {
                        nestedJenaResource.addProperty(RDF.type, extValueContext.model.createResource(typePropertyName));
                    }
                }
                handleExtendedProperties(AnyResource.class, nestedJenaResource, any, extValueContext);
                jenaResource.addProperty(jenaProperty, nestedJenaResource);
            }
        } else if (value.getClass().getAnnotation(OslcResourceShape.class) != null || value instanceof URI) {
            boolean xmlliteral = false;
            handleLocalResource(objectClass, null, xmlliteral, value, jenaResource, jenaProperty, onlyNested, null, extValueContext);
        } else if (value instanceof Date) { // Other literal types
            if (onlyNested) return;
            addDateLiteral(jenaResource, jenaProperty, (Date) value, null, extValueContext);
        } else if (value instanceof XMLLiteral) {
            if (onlyNested) return;
            addStringLiteral(jenaResource, jenaProperty, ((XMLLiteral)value).getValue(), true, null, extValueContext);
        } else if (value instanceof Element) {
            if (onlyNested) return;
            final StreamResult result = new StreamResult(new StringWriter());
            final DOMSource source = new DOMSource((Element)value);
            try {
                transformer.transform(source, result);
            } catch (TransformerException e) { throw new RuntimeException(e); }
            jenaResource.addProperty(jenaProperty, extValueContext.model.createLiteral(result.getWriter().toString(), true));
        } else if (value instanceof String) {
            if (onlyNested) return;
            addStringLiteral(jenaResource, jenaProperty, (String) value, false, null, extValueContext);
        } else if (value instanceof Float) {
            if (onlyNested) return;
            addFloatLiteral(jenaResource, jenaProperty, (Float) value, null, extValueContext);
        } else if (value instanceof Double) {
            if (onlyNested) return;
            addDoubleLiteral(jenaResource, jenaProperty, (Double) value, null, extValueContext);
        } else { // Boolean or other Number
            if (onlyNested) return;
            if (value instanceof Boolean) {
                 addBooleanLiteral(jenaResource, jenaProperty, (Boolean)value, null, extValueContext);
            } else if (value instanceof Number) {
                 addNumericLiteral(jenaResource, jenaProperty, (Number)value, null, extValueContext);
            } else {
                 jenaResource.addProperty(jenaProperty, extValueContext.model.createTypedLiteral(value));
            }
        }
    }

    private Literal toLiteral(final Model model, final Float f) { /* unchanged */ return JenaHelperUtils.floatToLiteral(f,model); } // Delegate to JenaHelperUtils
    private Literal toLiteral(final Model model, final Double d) { /* unchanged */ return JenaHelperUtils.doubleToLiteral(d,model); } // Delegate to JenaHelperUtils


    private void buildAttributeResource(final Class<?> resourceClass,
                                               final Method method,
                                               final OslcPropertyDefinition propertyDefinitionAnnotation,
                                               final Resource jenaResource, // Changed from 'resource' to 'jenaResource'
                                               final Object value, // Value of the Java property
                                               final boolean onlyNested,
                                               final MarshallingContext context)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        final String propertyDefinition = propertyDefinitionAnnotation.value();
        final OslcName nameAnnotation = InheritedMethodAnnotationHelper.getAnnotation(method, OslcName.class);
        final String name = (nameAnnotation != null) ? nameAnnotation.value() : JenaHelperUtils.getDefaultPropertyName(method);

        if (!propertyDefinition.endsWith(name)) {
            throw new OslcCoreInvalidPropertyDefinitionException(resourceClass, method, propertyDefinitionAnnotation);
        }

        final OslcValueType valueTypeAnnotation = InheritedMethodAnnotationHelper.getAnnotation(method, OslcValueType.class);
        final boolean xmlLiteral = valueTypeAnnotation != null && ValueType.XMLLiteral.equals(valueTypeAnnotation.value());
        final Property attributeProperty = context.model.createProperty(propertyDefinition); // Changed from 'attribute' to 'attributeProperty'
        final Class<?> returnType = method.getReturnType();
        final OslcRdfCollectionType collectionType = InheritedMethodAnnotationHelper.getAnnotation(method, OslcRdfCollectionType.class);
        final List<RDFNode> rdfNodeContainer;

        if (collectionType != null &&
                OslcConstants.RDF_NAMESPACE.equals(collectionType.namespaceURI()) &&
                (RDF_LIST.equals(collectionType.collectionType())
                        || RDF_ALT.equals(collectionType.collectionType())
                        || RDF_BAG.equals(collectionType.collectionType())
                        || RDF_SEQ.equals(collectionType.collectionType()))) {
            rdfNodeContainer = new ArrayList<>();
        } else {
            rdfNodeContainer = null;
        }

        if (returnType.isArray()) {
            final int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                final Object object = Array.get(value, index);
                handleLocalResource(resourceClass, method, xmlLiteral, object, jenaResource, attributeProperty, onlyNested, rdfNodeContainer, context);
            }
            if (rdfNodeContainer != null) {
                RDFNode container = createRdfContainer(collectionType, rdfNodeContainer, context.model);
                jenaResource.addProperty(attributeProperty, container);
            }
        } else if (Collection.class.isAssignableFrom(returnType)) {
            @SuppressWarnings("unchecked")
            final Collection<Object> collection = (Collection<Object>) value;
            for (final Object object : collection) {
                handleLocalResource(resourceClass, method, xmlLiteral, object, jenaResource, attributeProperty, onlyNested, rdfNodeContainer, context);
            }
            if (rdfNodeContainer != null) {
                RDFNode container = createRdfContainer(collectionType, rdfNodeContainer, context.model);
                jenaResource.addProperty(attributeProperty, container);
            }
        } else {
            handleLocalResource(resourceClass, method, xmlLiteral, value, jenaResource, attributeProperty, onlyNested, null, context);
        }
    }

    private RDFNode createRdfContainer(final OslcRdfCollectionType collectionType,
                                              final List<RDFNode> rdfNodeContainer, final Model model) {
        if (RDF_LIST.equals(collectionType.collectionType())) {
            return model.createList(rdfNodeContainer.iterator());
        }
        Container container;
        if (RDF_ALT.equals(collectionType.collectionType())) {
            container = model.createAlt();
        } else if (RDF_BAG.equals(collectionType.collectionType())) {
            container = model.createBag();
        } else { // SEQ
            container = model.createSeq();
        }
        for (RDFNode node : rdfNodeContainer) {
            container.add(node);
        }
        return container;
    }

    private void handleLocalResource(final Class<?> parentResourceClass,
                                            final Method parentMethod,
                                            final boolean xmlLiteral,
                                            final Object object, // This is the original object from the getter
                                            final Resource jenaResource,
                                            final Property attributeProperty, // Changed from 'attribute'
                                            final boolean onlyNested,
                                            final List<RDFNode> rdfNodeContainer,
                                            final MarshallingContext context)
            throws DatatypeConfigurationException,
            IllegalAccessException, IllegalArgumentException, InvocationTargetException,
            OslcCoreApplicationException {
        if (object == null) return;

        final IReifiedResource<?> reifiedResource = (object instanceof IReifiedResource) ? (IReifiedResource<?>) object : null;
        final Object value = (reifiedResource == null) ? object : reifiedResource.getValue();
        if (value == null) return;

        if (reifiedResource != null) {
            addReifiedOslcResource(jenaResource, attributeProperty, reifiedResource, parentResourceClass, rdfNodeContainer, value, xmlLiteral, onlyNested, parentMethod, context);
        } else if (value instanceof String) {
            if (onlyNested) return;
            addStringLiteral(jenaResource, attributeProperty, (String) value, xmlLiteral, rdfNodeContainer, context);
        } else if (value instanceof Float) {
            if (onlyNested) return;
            addFloatLiteral(jenaResource, attributeProperty, (Float) value, rdfNodeContainer, context);
        } else if (value instanceof Double) {
            if (onlyNested) return;
            addDoubleLiteral(jenaResource, attributeProperty, (Double) value, rdfNodeContainer, context);
        } else if (value instanceof Boolean) {
            if (onlyNested) return;
            addBooleanLiteral(jenaResource, attributeProperty, (Boolean) value, rdfNodeContainer, context);
        } else if (value instanceof Date) {
            if (onlyNested) return;
            addDateLiteral(jenaResource, attributeProperty, (Date) value, rdfNodeContainer, context);
        } else if (value instanceof URI) {
            if (onlyNested) return;
            addUriResource(jenaResource, attributeProperty, (URI) value, parentResourceClass, parentMethod, rdfNodeContainer, context);
        } else if (value.getClass().getAnnotation(OslcResourceShape.class) != null) { // OSLC Resource
            addOslcResource(jenaResource, attributeProperty, value, value.getClass(), rdfNodeContainer, context);
        } else if (value instanceof Number) {
            if (onlyNested) return;
            addNumericLiteral(jenaResource, attributeProperty, (Number) value, rdfNodeContainer, context);
        } else if (logger.isWarnEnabled()) {
            String subjectClassName = parentResourceClass.getSimpleName();
            if ("".equals(subjectClassName)) subjectClassName = parentResourceClass.getName();
            String objectClassName = value.getClass().getSimpleName();
            if ("".equals(objectClassName)) objectClassName = value.getClass().getName();
            logger.warn("Could not serialize {} because it does not have an OslcResourceShape annotation or is an unhandled type (class: {}, method: {})",
                    objectClassName, subjectClassName, (parentMethod != null ? parentMethod.getName() : "extended property"));
        }
    }

    private void addStringLiteral(Resource jenaResource, Property attribute, String value, boolean xmlLiteral, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        RDFNode nestedNode = xmlLiteral ? context.model.createTypedLiteral(value, XMLLiteralType.theXMLLiteralType) : context.model.createLiteral(value);
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addFloatLiteral(Resource jenaResource, Property attribute, Float value, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        RDFNode nestedNode = toLiteral(context.model, value);
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addDoubleLiteral(Resource jenaResource, Property attribute, Double value, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        RDFNode nestedNode = toLiteral(context.model, value);
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addBooleanLiteral(Resource jenaResource, Property attribute, Boolean value, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        RDFNode nestedNode = context.model.createTypedLiteral(value);
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addNumericLiteral(Resource jenaResource, Property attribute, Number value, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        RDFNode nestedNode = context.model.createTypedLiteral(value);
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addUriResource(Resource jenaResource, Property attribute, URI value, Class<?> parentResourceClass, Method parentMethod, List<RDFNode> rdfNodeContainer, MarshallingContext context) throws OslcCoreRelativeURIException {
        if (OSLC4JUtils.relativeURIsAreDisabled() && !value.isAbsolute()) {
            throw new OslcCoreRelativeURIException(parentResourceClass, (parentMethod == null) ? "<none>" : parentMethod.getName(), value);
        }
        RDFNode nestedNode = context.model.createResource(value.toString());
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addDateLiteral(Resource jenaResource, Property attribute, Date value, List<RDFNode> rdfNodeContainer, MarshallingContext context) {
        final GregorianCalendar calendar = new GregorianCalendar(); calendar.setTime(value);
        RDFDatatype dataType = null;
        if (OSLC4JUtils.inferTypeFromShape()) {
            HashSet<String> rdfTypes = new HashSet<>();
            rdfTypes = JenaHelperUtils.getTypesFromResource(jenaResource, rdfTypes);
            dataType = OSLC4JUtils.getDataTypeBasedOnResourceShapeType(rdfTypes, attribute);
        }
        RDFNode nestedNode;
        if (dataType != null && dataType instanceof XSDDateType) {
            XSDDateTime valuec = new XSDDateTime(calendar); valuec.narrowType(XSDDatatype.XSDdate);
            String valueDate = valuec.toString();
            if (valueDate != null && valueDate.endsWith("Z")) valueDate = valueDate.replace("Z", "");
            nestedNode = context.model.createTypedLiteral(valueDate, XSDDatatype.XSDdate);
        } else {
            nestedNode = context.model.createTypedLiteral(calendar);
        }
        if (rdfNodeContainer != null) rdfNodeContainer.add(nestedNode); else jenaResource.addProperty(attribute, nestedNode);
    }

    private void addOslcResource(Resource jenaResource, Property attribute, Object nestedObjectValue, Class<?> nestedObjectClass, List<RDFNode> rdfNodeContainer, MarshallingContext context)
            throws DatatypeConfigurationException, IllegalAccessException, InvocationTargetException, OslcCoreApplicationException {
        if (nestedObjectValue instanceof IExtendedResource && context.marshallerVisitedResources.containsKey(nestedObjectValue)) {
            // Cycle detected for inline resource
            RDFNode visitedJenaNode = context.marshallerVisitedResources.get(nestedObjectValue);
             if (rdfNodeContainer != null) rdfNodeContainer.add(visitedJenaNode); else jenaResource.addProperty(attribute, visitedJenaNode);
            return;
        }

        final String namespace = TypeFactory.getNamespace(nestedObjectClass);
        final String name = TypeFactory.getName(nestedObjectClass);
        URI aboutURI = null;
        if (nestedObjectValue instanceof IResource) aboutURI = ((IResource) nestedObjectValue).getAbout();

        final Resource newJenaNestedResource;
        if (aboutURI != null) {
            if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                throw new OslcCoreRelativeURIException(nestedObjectClass, "getAbout", aboutURI);
            }
            newJenaNestedResource = context.model.createResource(aboutURI.toString(), context.model.createProperty(namespace, name));
        } else {
            newJenaNestedResource = context.model.createResource(context.model.createProperty(namespace, name));
        }

        if (nestedObjectValue instanceof IExtendedResource) { // Add to visited before recursive call
            context.marshallerVisitedResources.put((IExtendedResource)nestedObjectValue, newJenaNestedResource);
        }

        buildResource(nestedObjectValue, nestedObjectClass, newJenaNestedResource, context); // Pass current context

        if (rdfNodeContainer != null) rdfNodeContainer.add(newJenaNestedResource); else jenaResource.addProperty(attribute, newJenaNestedResource);
    }

    private void addReifiedOslcResource(Resource jenaResource, Property attribute, IReifiedResource<?> reifiedValue, Class<?> parentResourceClass, List<RDFNode> rdfNodeContainer, Object actualValue, boolean xmlLiteral, boolean onlyNested, Method parentMethod, MarshallingContext context)
        throws DatatypeConfigurationException, IllegalAccessException, InvocationTargetException, OslcCoreApplicationException {

        if (rdfNodeContainer != null) throw new IllegalStateException("Reified resource is not supported for rdf collection resources");

        RDFNode valueNode = null;
        // Simplified dispatch for actualValue; assumes it's a literal or URI, not another reified/nested OSLC resource
        if (actualValue instanceof String) valueNode = xmlLiteral ? context.model.createTypedLiteral(actualValue.toString(), XMLLiteralType.theXMLLiteralType) : context.model.createLiteral(actualValue.toString());
        else if (actualValue instanceof Float) valueNode = toLiteral(context.model, (Float) actualValue);
        else if (actualValue instanceof Double) valueNode = toLiteral(context.model, (Double) actualValue);
        else if (actualValue instanceof Boolean) valueNode = context.model.createTypedLiteral(actualValue);
        else if (actualValue instanceof Date) {
            final GregorianCalendar calendar = new GregorianCalendar(); calendar.setTime((Date) actualValue);
            // Simplified date handling for this dispatch, full version in addDateLiteral
            valueNode = context.model.createTypedLiteral(calendar);
        } else if (actualValue instanceof URI) {
             final URI uri = (URI) actualValue;
            if (OSLC4JUtils.relativeURIsAreDisabled() && !uri.isAbsolute()) {
                throw new OslcCoreRelativeURIException(parentResourceClass, (parentMethod == null) ? "<none>" : parentMethod.getName(), uri);
            }
            valueNode = context.model.createResource(actualValue.toString());
        } else if (actualValue instanceof Number) valueNode = context.model.createTypedLiteral(actualValue);
        else { logger.warn("Unhandled type for reified resource's actual value: {}", actualValue.getClass().getName()); return; }

        if (valueNode == null) { logger.warn("Value node for reified resource was null for value type: {}", actualValue.getClass().getName()); return; }

        Statement statement = context.model.createStatement(jenaResource, attribute, valueNode);

        if (context.selectedProperties != OSLC4JConstants.OSL4J_PROPERTY_SINGLETON) { // nestedProperties equivalent
            ReifiedStatement reifiedStatement = statement.createReifiedStatement();
            // Pass the existing context, but buildResource might need a more specific selectedProperties map for the reifiedValue itself
            // For now, pass the parent's selectedProperties context; refinement needed if selectedProperties should apply to reification annotations
            buildResource(reifiedValue, reifiedValue.getClass(), reifiedStatement, context);

            boolean hasOnlyImplicitTriples = true; int count = 0;
            StmtIterator iter = reifiedStatement.listProperties();
            while(iter.hasNext()) {
                Statement stmt = iter.next(); Property p = stmt.getPredicate();
                if (!(p.equals(RDF.subject) || p.equals(RDF.predicate) || p.equals(RDF.object) || p.equals(RDF.type))) {
                    hasOnlyImplicitTriples = false; break;
                }
                count++;
            }
            if (hasOnlyImplicitTriples && count <= 4) {
                if (!context.model.contains(statement)) context.model.add(statement);
                reifiedStatement.remove();
                logger.debug("An empty reified statement was stripped from the model, base statement ensured.");
                return;
            }
        }
        context.model.add(statement);
    }
}
