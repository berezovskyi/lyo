/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.graph.BlankNodeId;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.IResource;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.ResponseInfo;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.transform.Transformer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides helper methods for working with Jena models.
 * <p>
 * This class acts as a facade for backward compatibility, delegating calls
 * to the refactored {@link JenaMarshaller}, {@link JenaUnmarshaller},
 * and {@link JenaHelperUtils} classes. It should not contain complex logic itself.
 * </p>
 */
public final class JenaModelHelper {

    /**
     * Private constructor to prevent instantiation.
     */
    private JenaModelHelper() {
        // Utility class should not be instantiated.
    }

    // Facade methods delegating to JenaMarshaller instances
    public static Model createJenaModel(final Object[] objects)
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, OslcCoreApplicationException {
        return new JenaMarshaller().createJenaModel(objects);
    }

    public static Model createJenaModel(final String descriptionAbout, final String responseInfoAbout,
            final ResponseInfo<?> responseInfo, final Object[] objects, final Map<String, Object> properties)
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException,
            InvocationTargetException, OslcCoreApplicationException {
        return new JenaMarshaller().createJenaModel(descriptionAbout, responseInfoAbout, responseInfo, objects, properties);
    }

    // Facade methods delegating to JenaUnmarshaller instances
    public static <T> T unmarshalSingle(final Model model, Class<T> clazz)
            throws IllegalArgumentException, LyoModelException {
        return new JenaUnmarshaller().unmarshalSingle(model, clazz);
    }

    public static <T> T unmarshal(final Resource resource, Class<T> clazz) throws LyoModelException {
        return new JenaUnmarshaller().unmarshal(resource, clazz);
    }

    @Deprecated
    public static Object fromJenaResource(final Resource resource, Class<?> beanClass)
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException,
            InstantiationException, InvocationTargetException, OslcCoreApplicationException,
            URISyntaxException, SecurityException, NoSuchMethodException {
        try {
            return new JenaUnmarshaller().unmarshal(resource, beanClass);
        } catch (LyoModelException e) {
            // Unwrap LyoModelException to maintain original exception signature compatibility
            Throwable cause = e.getCause();
            if (cause instanceof DatatypeConfigurationException) throw (DatatypeConfigurationException) cause;
            if (cause instanceof IllegalAccessException) throw (IllegalAccessException) cause;
            if (cause instanceof InvocationTargetException) throw (InvocationTargetException) cause;
            if (cause instanceof InstantiationException) throw (InstantiationException) cause;
            if (cause instanceof OslcCoreApplicationException) throw (OslcCoreApplicationException) cause;
            if (cause instanceof NoSuchMethodException) throw (NoSuchMethodException) cause;
            if (cause instanceof URISyntaxException) throw (URISyntaxException) cause;
            if (cause instanceof SecurityException) throw (SecurityException) cause;
            if (cause instanceof IllegalArgumentException) throw (IllegalArgumentException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause; // e.g. IllegalArgumentException from Jena
            throw new OslcCoreApplicationException("Unmarshalling failed, LyoModelException cause: " + cause.getMessage(), cause);
        }
    }

    public static <T> T[] unmarshal(final Model model, Class<T> clazz) throws LyoModelException {
        return new JenaUnmarshaller().unmarshal(model, clazz);
    }

    @Deprecated
    public static Object[] fromJenaModel(final Model model, final Class<?> beanClass)
            throws DatatypeConfigurationException, IllegalAccessException, IllegalArgumentException,
            InstantiationException, InvocationTargetException, OslcCoreApplicationException,
            URISyntaxException, SecurityException, NoSuchMethodException {
         try {
            return new JenaUnmarshaller().unmarshal(model, beanClass);
        } catch (LyoModelException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DatatypeConfigurationException) throw (DatatypeConfigurationException) cause;
            if (cause instanceof IllegalAccessException) throw (IllegalAccessException) cause;
            if (cause instanceof InvocationTargetException) throw (InvocationTargetException) cause;
            if (cause instanceof InstantiationException) throw (InstantiationException) cause;
            if (cause instanceof OslcCoreApplicationException) throw (OslcCoreApplicationException) cause;
            if (cause instanceof NoSuchMethodException) throw (NoSuchMethodException) cause;
            if (cause instanceof URISyntaxException) throw (URISyntaxException) cause;
            if (cause instanceof SecurityException) throw (SecurityException) cause;
            if (cause instanceof IllegalArgumentException) throw (IllegalArgumentException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new OslcCoreApplicationException("Unmarshalling failed, LyoModelException cause: " + cause.getMessage(), cause);
        }
    }

    public static <R extends IResource> R followLink(final Model m, final Link l, final Class<R> rClass)
            throws IllegalArgumentException, LyoModelException {
        return new JenaUnmarshaller().followLink(m, l, rClass);
    }

    // Delegating methods to JenaHelperUtils (static calls)
    public static final String OSLC4J_STRICT_DATATYPES = JenaHelperUtils.OSLC4J_STRICT_DATATYPES;

    public static void skolemize(final Model m) {
        JenaHelperUtils.skolemize(m);
    }

    public static void skolemize(final Model m, Function<BlankNodeId, String> skolemUriFunction) {
        JenaHelperUtils.skolemize(m, skolemUriFunction);
    }

    public static Transformer createTransformer() {
        return JenaHelperUtils.createTransformer();
    }

    public static String getVisitedResourceName(Resource resource) {
        return JenaHelperUtils.getVisitedResourceName(resource);
    }

    public static String generatePrefix(Model model, String namespace) {
        return JenaHelperUtils.generatePrefix(model, namespace);
    }

    public static HashSet<String> getTypesFromResource(final Resource resource, HashSet<String> types) {
        return JenaHelperUtils.getTypesFromResource(resource, types);
    }

    public static String getDefaultPropertyName(final Method method) {
        return JenaHelperUtils.getDefaultPropertyName(method);
    }

    public static void recursivelyCollectNamespaceMappings(final Map<String, String> namespaceMappings, final Class<?> resourceClass) {
        JenaHelperUtils.recursivelyCollectNamespaceMappings(namespaceMappings, resourceClass);
    }

    public static void ensureNamespacePrefix(final String prefix, final String namespace, final Map<String, String> namespaceMappings) {
        JenaHelperUtils.ensureNamespacePrefix(prefix, namespace, namespaceMappings);
    }

    public static Resource getResource(Model model, Node node) {
        return JenaHelperUtils.getResource(model, node);
    }

    public static Class<? extends Container> getRdfCollectionResourceClass(Model model, RDFNode object) {
        return JenaHelperUtils.getRdfCollectionResourceClass(model, object);
    }
}
