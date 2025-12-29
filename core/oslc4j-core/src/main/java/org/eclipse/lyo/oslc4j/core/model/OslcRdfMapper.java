/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Eclipse Distribution License 1.0
 * which is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package org.eclipse.lyo.oslc4j.core.model;

import java.io.StringWriter;
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
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractSequentialList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.eclipse.lyo.oslc4j.core.NestedWildcardProperties;
import org.eclipse.lyo.oslc4j.core.OSLC4JConstants;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.OslcGlobalNamespaceProvider;
import org.eclipse.lyo.oslc4j.core.SingletonWildcardProperties;
import org.eclipse.lyo.oslc4j.core.UnparseableLiteral;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespaceDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcPropertyDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcRdfCollectionType;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.annotation.OslcSchema;
import org.eclipse.lyo.oslc4j.core.annotation.OslcValueType;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreInvalidPropertyDefinitionException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMissingSetMethodException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreMisusedOccursException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreRelativeURIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public final class OslcRdfMapper<M, R, N, U, L> {

    private static final String PROPERTY_TOTAL_COUNT = "totalCount";
    private static final String PROPERTY_NEXT_PAGE = "nextPage";

    private static final String RDF_TYPE_URI = OslcConstants.RDF_NAMESPACE + "type";

    private static final String RDF_LIST = "List";
    private static final String RDF_ALT = "Alt";
    private static final String RDF_BAG = "Bag";
    private static final String RDF_SEQ = "Seq";

    private static final String METHOD_NAME_START_GET = "get";
    private static final String METHOD_NAME_START_IS = "is";
    private static final String METHOD_NAME_START_SET = "set";

    private static final int METHOD_NAME_START_GET_LENGTH = METHOD_NAME_START_GET.length();
    private static final int METHOD_NAME_START_IS_LENGTH = METHOD_NAME_START_IS.length();

    private static final Logger logger = LoggerFactory.getLogger(OslcRdfMapper.class);

    private final IOslcRdfApi<M, R, N, U, L> rdfApi;

    public OslcRdfMapper(IOslcRdfApi<M, R, N, U, L> rdfApi) {
        this.rdfApi = rdfApi;
    }

    public M createModel(final Object[] objects)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        return createModel(null, null, null, objects, null);
    }

    public M createModel(
            final String descriptionAbout,
            final String responseInfoAbout,
            final ResponseInfo<?> responseInfo,
            final Object[] objects,
            final Map<String, Object> properties)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {

        Instant start = Instant.now();

        final M model = rdfApi.createModel();

        R descriptionResource = null;

        if (descriptionAbout != null) {
            // TODO: handle container logic abstraction?
            if (OSLC4JUtils.isQueryResultListAsContainer()) {
                 // For now, assuming createResource creates a resource
                 // This part needs specific handling for RDFS.Container if needed by the provider
                 // But RDFS.Container is just a type usually.
                 descriptionResource = rdfApi.createResource(model, descriptionAbout);
                 // We should add type RDFS.Container
                 R containerType = rdfApi.createResource(model, OslcConstants.RDFS_NAMESPACE + "Container");
                 R typePred = rdfApi.createResource(model, RDF_TYPE_URI);
                 rdfApi.addTriple(model, descriptionResource, typePred, (N) containerType);
            } else {
                descriptionResource = rdfApi.createResource(model, descriptionAbout);
            }

            Map<IExtendedResource, R> visitedResources = new HashMap<>();
            handleExtendedProperties(
                    FilteredResource.class,
                    model,
                    descriptionResource,
                    responseInfo.getContainer(),
                    properties,
                    visitedResources);

            if (responseInfoAbout != null) {
                final R responseInfoResource = rdfApi.createResource(model, responseInfoAbout);

                R typePred = rdfApi.createResource(model, RDF_TYPE_URI);
                R responseInfoType = rdfApi.createResource(model, OslcConstants.TYPE_RESPONSE_INFO);
                rdfApi.addTriple(model, responseInfoResource, typePred, (N) responseInfoType);

                if (responseInfo != null) {
                    final int totalCount =
                            responseInfo.totalCount() == null ? objects.length : responseInfo.totalCount();

                    R totalCountProp = rdfApi.createProperty(model, OslcConstants.OSLC_CORE_NAMESPACE, PROPERTY_TOTAL_COUNT);
                    L totalCountLit = rdfApi.createTypedLiteral(model, totalCount);
                    rdfApi.addTriple(model, responseInfoResource, totalCountProp, (N) totalCountLit);


                    if (responseInfo.nextPage() != null) {
                         R nextPageProp = rdfApi.createProperty(model, OslcConstants.OSLC_CORE_NAMESPACE, PROPERTY_NEXT_PAGE);
                         R nextPageRes = rdfApi.createResource(model, responseInfo.nextPage());
                         rdfApi.addTriple(model, responseInfoResource, nextPageProp, (N) nextPageRes);
                    }

                    visitedResources = new HashMap<>();
                    handleExtendedProperties(
                            ResponseInfo.class,
                            model,
                            responseInfoResource,
                            responseInfo,
                            properties,
                            visitedResources);
                }
            }
        }

        // add global namespace mappings
        final Map<String, String> namespaceMappings =
                new HashMap<>(OslcGlobalNamespaceProvider.getInstance().getPrefixDefinitionMap());

        for (final Object object : objects) {
            handleSingleResource(descriptionResource, object, model, namespaceMappings, properties);
        }

        if (descriptionAbout != null) {
            ensureNamespacePrefix(
                    OslcConstants.RDF_NAMESPACE_PREFIX, OslcConstants.RDF_NAMESPACE, namespaceMappings);

            ensureNamespacePrefix(
                    OslcConstants.RDFS_NAMESPACE_PREFIX, OslcConstants.RDFS_NAMESPACE, namespaceMappings);

            if (responseInfoAbout != null) {
                ensureNamespacePrefix(
                        OslcConstants.OSLC_CORE_NAMESPACE_PREFIX,
                        OslcConstants.OSLC_CORE_NAMESPACE,
                        namespaceMappings);
            }
        }

        for (final Map.Entry<String, String> namespaceMapping : namespaceMappings.entrySet()) {
            rdfApi.setNamespacePrefix(model, namespaceMapping.getKey(), namespaceMapping.getValue());
        }

        Instant finish = Instant.now();
        logger.trace(
                "createJenaModel - Execution Duration: {} ms", Duration.between(start, finish).toMillis());
        return model;
    }

    private void handleSingleResource(
            final R descriptionResource,
            final Object object,
            final M model,
            final Map<String, String> namespaceMappings,
            final Map<String, Object> properties)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        final Class<?> objectClass = object.getClass();

        recursivelyCollectNamespaceMappings(namespaceMappings, objectClass);

        final R mainResource;

        if (object instanceof URI) {
            mainResource = rdfApi.createResource(model, ((URI) object).toASCIIString());
        } else {
            URI aboutURI = null;
            if (object instanceof IResource) {
                aboutURI = ((IResource) object).getAbout();
            }

            if (aboutURI != null) {
                if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                    throw new OslcCoreRelativeURIException(objectClass, "getAbout", aboutURI);
                }

                mainResource = rdfApi.createResource(model, aboutURI.toString());
            } else {
                mainResource = rdfApi.createResource(model);
            }

            if (objectClass.getAnnotation(OslcResourceShape.class) != null) {
                String qualifiedName = TypeFactory.getQualifiedName(objectClass);
                if (qualifiedName != null) {
                    R typePred = rdfApi.createResource(model, RDF_TYPE_URI);
                    R typeRes = rdfApi.createResource(model, qualifiedName);
                    rdfApi.addTriple(model, mainResource, typePred, (N) typeRes);
                }
            }

            buildResource(object, objectClass, model, mainResource, properties);
        }

        if (descriptionResource != null) {
            R memberPred = rdfApi.createResource(model, OslcConstants.RDFS_NAMESPACE + "member");
            rdfApi.addTriple(model, descriptionResource, memberPred, (N) mainResource);
        }
    }

    // ... Copying and adapting helper methods ...

    private void recursivelyCollectNamespaceMappings(
            final Map<String, String> namespaceMappings, final Class<?> resourceClass) {
        final OslcSchema oslcSchemaAnnotation =
                resourceClass.getPackage().getAnnotation(OslcSchema.class);

        if (oslcSchemaAnnotation != null) {
            final OslcNamespaceDefinition[] oslcNamespaceDefinitionAnnotations =
                    oslcSchemaAnnotation.value();

            for (final OslcNamespaceDefinition oslcNamespaceDefinitionAnnotation :
                    oslcNamespaceDefinitionAnnotations) {
                final String prefix = oslcNamespaceDefinitionAnnotation.prefix();
                final String namespaceURI = oslcNamespaceDefinitionAnnotation.namespaceURI();

                namespaceMappings.put(prefix, namespaceURI);
            }
            // Adding custom prefixes obtained from an implementation, if there is an implementation.
            Class<? extends IOslcCustomNamespaceProvider> customNamespaceProvider =
                    oslcSchemaAnnotation.customNamespaceProvider();
            if (!customNamespaceProvider.isInterface()) {
                try {
                    IOslcCustomNamespaceProvider customNamespaceProviderImpl =
                            customNamespaceProvider.getDeclaredConstructor().newInstance();
                    Map<String, String> customNamespacePrefixes =
                            customNamespaceProviderImpl.getCustomNamespacePrefixes();
                    if (null != customNamespacePrefixes) {
                        namespaceMappings.putAll(customNamespacePrefixes);
                    }
                } catch (Exception e) {
                   // ignoring for brevity in this initial port
                   throw new RuntimeException(e);
                }
            }
        }

        final Class<?> superClass = resourceClass.getSuperclass();

        if (superClass != null) {
            recursivelyCollectNamespaceMappings(namespaceMappings, superClass);
        }

        final Class<?>[] interfaces = resourceClass.getInterfaces();

        for (final Class<?> iface : interfaces) {
            recursivelyCollectNamespaceMappings(namespaceMappings, iface);
        }
    }

    private void ensureNamespacePrefix(
        final String prefix, final String namespace, final Map<String, String> namespaceMappings) {
        if (!namespaceMappings.containsValue(namespace)) {
            if (!namespaceMappings.containsKey(prefix)) {
                namespaceMappings.put(prefix, namespace);
            } else {
                int index = 1;
                while (true) {
                    final String newPrefix = prefix + index;
                    if (!namespaceMappings.containsKey(newPrefix)) {
                        namespaceMappings.put(newPrefix, namespace);
                        return;
                    }
                    index++;
                }
            }
        }
    }

    private void buildResource(
            final Object object,
            final Class<?> resourceClass,
            final M model,
            final R mainResource,
            final Map<String, Object> properties)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        if (properties == OSLC4JConstants.OSL4J_PROPERTY_SINGLETON) {
            return;
        }

        for (final Method method : resourceClass.getMethods()) {
            if (method.getParameterTypes().length == 0) {
                final String methodName = method.getName();

                if (((methodName.startsWith(METHOD_NAME_START_GET))
                        && (methodName.length() > METHOD_NAME_START_GET_LENGTH))
                        || ((methodName.startsWith(METHOD_NAME_START_IS))
                        && (methodName.length() > METHOD_NAME_START_IS_LENGTH))) {
                    final OslcPropertyDefinition oslcPropertyDefinitionAnnotation =
                            InheritedMethodAnnotationHelper.getAnnotation(method, OslcPropertyDefinition.class);

                    if (oslcPropertyDefinitionAnnotation != null) {
                        final Object value = method.invoke(object);

                        if (value != null) {
                            Map<String, Object> nestedProperties = null;
                            boolean onlyNested = false;

                            if (properties != null) {
                                @SuppressWarnings("unchecked")
                                final Map<String, Object> map =
                                        (Map<String, Object>) properties.get(oslcPropertyDefinitionAnnotation.value());

                                if (map != null) {
                                    nestedProperties = map;
                                } else if (properties instanceof SingletonWildcardProperties
                                        && !(properties instanceof NestedWildcardProperties)) {
                                    nestedProperties = OSLC4JConstants.OSL4J_PROPERTY_SINGLETON;
                                } else if (properties instanceof NestedWildcardProperties) {
                                    nestedProperties =
                                            ((NestedWildcardProperties) properties).commonNestedProperties();
                                    onlyNested = !(properties instanceof SingletonWildcardProperties);
                                } else {
                                    continue;
                                }
                            }

                            buildAttributeResource(
                                    resourceClass,
                                    method,
                                    oslcPropertyDefinitionAnnotation,
                                    model,
                                    mainResource,
                                    value,
                                    nestedProperties,
                                    onlyNested);
                        }
                    }
                }
            }
        }

        // Handle any extended properties.
        if (object instanceof IExtendedResource) {
            final IExtendedResource extendedResource = (IExtendedResource) object;
            Map<IExtendedResource, R> visitedResources = new HashMap<>();
            handleExtendedProperties(
                    resourceClass, model, mainResource, extendedResource, properties, visitedResources);
        }
    }

    protected void handleExtendedProperties(
            final Class<?> resourceClass,
            final M model,
            final R mainResource,
            final IExtendedResource extendedResource,
            final Map<String, Object> properties,
            Map<IExtendedResource, R> visitedResources)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            InvocationTargetException,
            OslcCoreApplicationException {
        visitedResources.put(extendedResource, mainResource);

        R typePred = rdfApi.createResource(model, RDF_TYPE_URI);

        for (final URI type : extendedResource.getTypes()) {
            final String propertyName = type.toString();

            if (properties != null
                    && properties.get(propertyName) == null
                    && !(properties instanceof NestedWildcardProperties)
                    && !(properties instanceof SingletonWildcardProperties)) {
                continue;
            }

            final R typeResource = rdfApi.createResource(model, propertyName);

            // TODO: check if already exists?
            // if (!mainResource.hasProperty(RDF.type, typeResource)) {
            rdfApi.addTriple(model, mainResource, typePred, (N) typeResource);
            // }
        }

        final Transformer transformer = createTransformer();

        for (final Map.Entry<QName, ?> extendedProperty :
                extendedResource.getExtendedProperties().entrySet()) {
            final QName qName = extendedProperty.getKey();
            final String propertyName = qName.getNamespaceURI() + qName.getLocalPart();
            Map<String, Object> nestedProperties = null;
            boolean onlyNested = false;

            if (properties != null) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> map = (Map<String, Object>) properties.get(propertyName);

                if (map != null) {
                    nestedProperties = map;
                } else if (properties instanceof SingletonWildcardProperties
                        && !(properties instanceof NestedWildcardProperties)) {
                    nestedProperties = OSLC4JConstants.OSL4J_PROPERTY_SINGLETON;
                } else if (properties instanceof NestedWildcardProperties) {
                    nestedProperties = ((NestedWildcardProperties) properties).commonNestedProperties();
                    onlyNested = !(properties instanceof SingletonWildcardProperties);
                } else {
                    continue;
                }
            }

            final R property = rdfApi.createProperty(model, propertyName);
            final Object value = extendedProperty.getValue();

            if (value instanceof Collection<?> collection) {
                for (Object next : collection) {
                    handleExtendedValue(
                            resourceClass,
                            next,
                            model,
                            mainResource,
                            property,
                            nestedProperties,
                            onlyNested,
                            visitedResources,
                            transformer);
                }
            } else {
                handleExtendedValue(
                        resourceClass,
                        value,
                        model,
                        mainResource,
                        property,
                        nestedProperties,
                        onlyNested,
                        visitedResources,
                        transformer);
            }
        }
    }

    private void handleExtendedValue(
            final Class<?> objectClass,
            final Object value,
            final M model,
            final R resource,
            final R property,
            final Map<String, Object> nestedProperties,
            final boolean onlyNested,
            final Map<IExtendedResource, R> visitedResources,
            final Transformer transformer)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        if (value instanceof UnparseableLiteral unparseable) {
            if (onlyNested) {
                return;
            }

            // resource.addProperty(property, model.createLiteral(unparseable.getRawValue()));
            L literal = rdfApi.createLiteral(model, unparseable.getRawValue()); // Untyped string
            // TODO: UnparseableLiteral has datatypeURI, should use it
             if (unparseable.getDatatype() != null) {
                  literal = rdfApi.createTypedLiteral(model, unparseable.getRawValue(), unparseable.getDatatype());
             }
             rdfApi.addTriple(model, resource, property, (N) literal);
        } else if (value instanceof AnyResource) {
            final AbstractResource any = (AbstractResource) value;

            // create a new resource in the model if we have not visited it yet
            if (!visitedResources.containsKey(any)) {
                final R nestedResource;
                final URI aboutURI = any.getAbout();
                if (aboutURI != null) {
                    if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                        throw new OslcCoreRelativeURIException(AnyResource.class, "getAbout", aboutURI);
                    }

                    nestedResource = rdfApi.createResource(model, aboutURI.toString());
                } else {
                    nestedResource = rdfApi.createResource(model);
                }

                R typePred = rdfApi.createResource(model, RDF_TYPE_URI);

                for (final URI type : any.getTypes()) {
                    final String propertyName = type.toString();

                    if (nestedProperties == null
                            || nestedProperties.get(propertyName) != null
                            || nestedProperties instanceof NestedWildcardProperties
                            || nestedProperties instanceof SingletonWildcardProperties) {
                        // nestedResource.addProperty(RDF.type, model.createResource(propertyName));
                        R typeRes = rdfApi.createResource(model, propertyName);
                        rdfApi.addTriple(model, nestedResource, typePred, (N) typeRes);
                    }
                }

                handleExtendedProperties(
                        AnyResource.class, model, nestedResource, any, nestedProperties, visitedResources);
                // resource.addProperty(property, nestedResource);
                rdfApi.addTriple(model, resource, property, (N) nestedResource);
            } else {
                // We've already added the inline resource, add a reference to it for this property
                // resource.addProperty(property, visitedResources.get(any));
                rdfApi.addTriple(model, resource, property, (N) visitedResources.get(any));
            }

        } else if (value.getClass().getAnnotation(OslcResourceShape.class) != null
                || value instanceof URI) {
            boolean xmlliteral = false;
            handleLocalResource(
                    objectClass,
                    null,
                    xmlliteral,
                    value,
                    model,
                    resource,
                    property,
                    nestedProperties,
                    onlyNested,
                    null);
        } else if (value instanceof Date) {
            if (onlyNested) {
                return;
            }

            final Calendar cal = Calendar.getInstance();
            cal.setTime((Date) value);
            // TODO: Logic for inferTypeFromShape

            L literal = rdfApi.createTypedLiteral(model, cal);
             rdfApi.addTriple(model, resource, property, (N) literal);

        } else if (value instanceof XMLLiteral) {
            final XMLLiteral xmlLiteral = (XMLLiteral) value;
            // TODO: XMLLiteral type constant
             L literal = rdfApi.createTypedLiteral(model, xmlLiteral.getValue(), "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral");
             rdfApi.addTriple(model, resource, property, (N) literal);
        } else if (value instanceof Element) {
            final StreamResult result = new StreamResult(new StringWriter());

            final DOMSource source = new DOMSource((Element) value);

            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }

            // TODO: re-check rdf:XMLLiteral generation.
            L literal = rdfApi.createTypedLiteral(model, result.getWriter().toString(), "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral");
            rdfApi.addTriple(model, resource, property, (N) literal);
        } else if (value instanceof String) {
            if (onlyNested) {
                return;
            }
            L literal = rdfApi.createLiteral(model, (String) value);
            rdfApi.addTriple(model, resource, property, (N) literal);
        } else if (value instanceof Float) {
             if (onlyNested) {
                return;
            }
             L literal = rdfApi.createTypedLiteral(model, value);
             rdfApi.addTriple(model, resource, property, (N) literal);
        } else if (value instanceof Double) {
             if (onlyNested) {
                return;
            }
             L literal = rdfApi.createTypedLiteral(model, value);
             rdfApi.addTriple(model, resource, property, (N) literal);
        } else {
            if (onlyNested) {
                return;
            }
            L literal = rdfApi.createTypedLiteral(model, value);
             rdfApi.addTriple(model, resource, property, (N) literal);
        }
    }

    private void buildAttributeResource(
            final Class<?> resourceClass,
            final Method method,
            final OslcPropertyDefinition propertyDefinitionAnnotation,
            final M model,
            R resource,
            final Object value,
            final Map<String, Object> nestedProperties,
            final boolean onlyNested)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        final String propertyDefinition = propertyDefinitionAnnotation.value();

        final OslcName nameAnnotation =
                InheritedMethodAnnotationHelper.getAnnotation(method, OslcName.class);

        final String name;
        if (nameAnnotation != null) {
            name = nameAnnotation.value();
        } else {
            name = getDefaultPropertyName(method);
        }

        if (!propertyDefinition.endsWith(name)) {
            throw new OslcCoreInvalidPropertyDefinitionException(
                    resourceClass, method, propertyDefinitionAnnotation);
        }

        final OslcValueType valueTypeAnnotation =
                InheritedMethodAnnotationHelper.getAnnotation(method, OslcValueType.class);

        final boolean xmlLiteral =
                valueTypeAnnotation != null && ValueType.XMLLiteral.equals(valueTypeAnnotation.value());

        final R attribute = rdfApi.createProperty(model, propertyDefinition);

        final Class<?> returnType = method.getReturnType();
        final OslcRdfCollectionType collectionType =
                InheritedMethodAnnotationHelper.getAnnotation(method, OslcRdfCollectionType.class);
        final List<N> rdfNodeContainer;

        if (collectionType != null
                && OslcConstants.RDF_NAMESPACE.equals(collectionType.namespaceURI())
                && (RDF_LIST.equals(collectionType.collectionType())
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

                handleLocalResource(
                        resourceClass,
                        method,
                        xmlLiteral,
                        object,
                        model,
                        resource,
                        attribute,
                        nestedProperties,
                        onlyNested,
                        rdfNodeContainer);
            }

            if (rdfNodeContainer != null) {
                String typeUri = collectionType.namespaceURI() + collectionType.collectionType();
                R container = rdfApi.createCollection(model, rdfNodeContainer, typeUri);
                rdfApi.addTriple(model, resource, attribute, (N) container);
            }
        } else if (Collection.class.isAssignableFrom(returnType)) {
            @SuppressWarnings("unchecked")
            final Collection<Object> collection = (Collection<Object>) value;

            for (final Object object : collection) {
                handleLocalResource(
                        resourceClass,
                        method,
                        xmlLiteral,
                        object,
                        model,
                        resource,
                        attribute,
                        nestedProperties,
                        onlyNested,
                        rdfNodeContainer);
            }

            if (rdfNodeContainer != null) {
                 String typeUri = collectionType.namespaceURI() + collectionType.collectionType();
                 R container = rdfApi.createCollection(model, rdfNodeContainer, typeUri);
                 rdfApi.addTriple(model, resource, attribute, (N) container);
            }
        } else {
            handleLocalResource(
                    resourceClass,
                    method,
                    xmlLiteral,
                    value,
                    model,
                    resource,
                    attribute,
                    nestedProperties,
                    onlyNested,
                    null);
        }
    }

    private void handleLocalResource(
            final Class<?> resourceClass,
            final Method method,
            final boolean xmlLiteral,
            final Object object,
            final M model,
            final R resource,
            final R attribute,
            final Map<String, Object> nestedProperties,
            final boolean onlyNested,
            final List<N> rdfNodeContainer)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        if (object == null) {
            return;
        }
        final Class<?> objectClass = object.getClass();

        N nestedNode = null;
        final IReifiedResource<?> reifiedResource =
                (object instanceof IReifiedResource) ? (IReifiedResource<?>) object : null;
        final Object value = (reifiedResource == null) ? object : reifiedResource.getValue();
        if (value == null) {
            return;
        }

        if (value instanceof String) {
            if (onlyNested) {
                return;
            }

            if (xmlLiteral) {
                nestedNode = (N) rdfApi.createTypedLiteral(model, value.toString(), "http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral");
            } else {
                nestedNode = (N) rdfApi.createLiteral(model, value.toString());
            }
        }
        // Floats need special handling for infinite values.
        else if (value instanceof Float) {
            if (onlyNested) {
                return;
            }

            nestedNode = (N) rdfApi.createTypedLiteral(model, (Float) value);
        }
        // Doubles need special handling for infinite values.
        else if (value instanceof Double) {
            if (onlyNested) {
                return;
            }

            nestedNode = (N) rdfApi.createTypedLiteral(model, (Double) value);
        } else if ((value instanceof Boolean) || (value instanceof Number)) {
            if (onlyNested) {
                return;
            }

            nestedNode = (N) rdfApi.createTypedLiteral(model, value);
        } else if (value instanceof URI) {
            if (onlyNested) {
                return;
            }

            final URI uri = (URI) value;

            if (OSLC4JUtils.relativeURIsAreDisabled() && !uri.isAbsolute()) {
                throw new OslcCoreRelativeURIException(
                        resourceClass, (method == null) ? "<none>" : method.getName(), uri);
            }

            nestedNode = (N) rdfApi.createResource(model, value.toString());
        } else if (value instanceof Date) {
            if (onlyNested) {
                return;
            }

            final GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTime((Date) value);
            // TODO: datatype logic
            nestedNode = (N) rdfApi.createTypedLiteral(model, calendar);

        } else if (objectClass.getAnnotation(OslcResourceShape.class) != null) {
            final String namespace = TypeFactory.getNamespace(objectClass);
            final String name = TypeFactory.getName(objectClass);

            URI aboutURI = null;
            if (value instanceof IResource) {
                aboutURI = ((IResource) value).getAbout();
            }

            final R nestedResource;
            if (aboutURI != null) {
                if (OSLC4JUtils.relativeURIsAreDisabled() && !aboutURI.isAbsolute()) {
                    throw new OslcCoreRelativeURIException(objectClass, "getAbout", aboutURI);
                }
                nestedResource = rdfApi.createResource(model, aboutURI.toString());
            } else {
                nestedResource = rdfApi.createResource(model);
            }

            // Add type?
            // Original code: nestedResource = model.createResource(aboutURI.toString(), model.createProperty(namespace, name));
            // createResource with type? No, Jena createResource(uri, type) adds type.

            R typeRes = rdfApi.createResource(model, namespace + name);
            R typePred = rdfApi.createResource(model, RDF_TYPE_URI);
            rdfApi.addTriple(model, nestedResource, typePred, (N) typeRes);

            // ignore nested resource, which points to same resource, otherwise infinite loop
            String resUri = rdfApi.getURI(resource);
            String nestedResUri = rdfApi.getURI(nestedResource);

            if (resUri == null
                    || (resUri != null
                    && !resUri.equalsIgnoreCase(nestedResUri))) {
                buildResource(value, objectClass, model, nestedResource, nestedProperties);
            }

            nestedNode = (N) nestedResource;
        } else if (logger.isWarnEnabled()) {
             logger.warn("Could not serialize " + objectClassName(objectClass));
        }

        if (nestedNode != null) {
            if (rdfNodeContainer != null) {
                if (reifiedResource != null) {
                    throw new IllegalStateException(
                            "Reified resource is not supported for rdf collection resources");
                }

                rdfNodeContainer.add(nestedNode);
            } else {
                // Statement statement = model.createStatement(resource, attribute, nestedNode);
                // rdfApi.addTriple(model, resource, attribute, nestedNode);

                if (reifiedResource != null
                        && nestedProperties != OSLC4JConstants.OSL4J_PROPERTY_SINGLETON) {
                    addReifiedStatements(model, resource, attribute, nestedNode, reifiedResource, nestedProperties);
                } else {
                    rdfApi.addTriple(model, resource, attribute, nestedNode);
                }
            }
        }
    }

    private void addReifiedStatements(
            final M model,
            final R subject,
            final R predicate,
            final N object,
            final IReifiedResource<?> reifiedResource,
            final Map<String, Object> nestedProperties)
            throws IllegalArgumentException,
            IllegalAccessException,
            InvocationTargetException,
            DatatypeConfigurationException,
            OslcCoreApplicationException {

        R reifiedJenaResource = rdfApi.reifyStatement(model, subject, predicate, object);

        buildResource(
                reifiedResource, reifiedResource.getClass(), model, reifiedJenaResource, nestedProperties);

        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=526188
        // If the resulting reifiedStatement only contain the 4 statements about its subject,
        // predicate object, & type, then there are no additional statements on the statement. Hence,
        // remove the newly created reifiedStatement.
        // TODO: check this is a correct removal
        // TODO: check if we still need to do this in Jena 5
        // How to count properties?
        // rdfApi.listObjects(model, reifiedJenaResource, null); ?
        // We need listProperties (resource as subject).
        // listObjects(model, reifiedJenaResource, null) should return all objects.
        // But we need to know how many triples have reifiedJenaResource as subject.

        Iterable<N> objects = rdfApi.listObjects(model, reifiedJenaResource, null);
        int size = 0;
        for (N obj : objects) {
            size++;
        }

//        if (size == 4) {
//            rdfApi.removeProperties(model, reifiedJenaResource);
//            logger.debug("An empty reified statement was stripped from the model");
//        }
    }

    private String objectClassName(Class<?> clazz) {
        return clazz.getName();
    }

    private String getDefaultPropertyName(final Method method) {
        final String methodName = method.getName();
        final int startingIndex =
                methodName.startsWith(METHOD_NAME_START_GET)
                        ? METHOD_NAME_START_GET_LENGTH
                        : METHOD_NAME_START_IS_LENGTH;
        final int endingIndex = startingIndex + 1;

        final String lowercasedFirstCharacter =
                methodName.substring(startingIndex, endingIndex).toLowerCase(Locale.ENGLISH);

        if (methodName.length() == endingIndex) {
            return lowercasedFirstCharacter;
        }

        return lowercasedFirstCharacter + methodName.substring(endingIndex);
    }

    private static Transformer createTransformer() {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            return transformer;
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

}
