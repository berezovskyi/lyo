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

import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.Map;
import javax.xml.datatype.DatatypeConfigurationException;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.eclipse.lyo.oslc4j.core.exception.LyoModelException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.IResource;
import org.eclipse.lyo.oslc4j.core.model.Link;
import org.eclipse.lyo.oslc4j.core.model.OslcRdfMapper;
import org.eclipse.lyo.oslc4j.core.model.ResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class JenaModelHelper {

  private static final Logger logger = LoggerFactory.getLogger(JenaModelHelper.class);
  // OslcRdfMapper available for future use / alternative implementation
  private static final OslcRdfMapper<Model, Resource, RDFNode, String, Literal> mapper =
        new OslcRdfMapper<>(new JenaOslcRdfApi());

  /**
   * System property {@value} : When "true" (default), fail on when reading a
   * property value that is not a legal instance of a datatype. When "false",
   * skip over invalid values in extended properties.
   */
  @Deprecated
  public static final String OSLC4J_STRICT_DATATYPES = "org.eclipse.lyo.oslc4j.strictDatatypes";

  private JenaModelHelper() {
    super();
  }

  public static Model createJenaModel(final Object[] objects)
      throws DatatypeConfigurationException,
          IllegalAccessException,
          IllegalArgumentException,
          InvocationTargetException,
          OslcCoreApplicationException {
    return LegacyJenaModelHelper.createJenaModel(objects);
  }

  static Model createJenaModel(
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
      return LegacyJenaModelHelper.createJenaModel(descriptionAbout, responseInfoAbout, responseInfo, objects, properties);
  }

  public static <T> T unmarshalSingle(final Model model, Class<T> clazz)
      throws IllegalArgumentException, LyoModelException {
      return LegacyJenaModelHelper.unmarshalSingle(model, clazz);
  }

  public static <T> T unmarshal(final Resource resource, Class<T> clazz) throws LyoModelException {
    return LegacyJenaModelHelper.unmarshal(resource, clazz);
  }

  public static Object fromJenaResource(final Resource resource, Class<?> beanClass)
      throws DatatypeConfigurationException,
          IllegalAccessException,
          IllegalArgumentException,
          InstantiationException,
          InvocationTargetException,
          OslcCoreApplicationException,
          URISyntaxException,
          SecurityException,
          NoSuchMethodException {
      return LegacyJenaModelHelper.fromJenaResource(resource, beanClass);
  }

  public static <T> T[] unmarshal(final Model model, Class<T> clazz) throws LyoModelException {
    return LegacyJenaModelHelper.unmarshal(model, clazz);
  }

  public static Object[] fromJenaModel(final Model model, final Class<?> beanClass)
      throws DatatypeConfigurationException,
          IllegalAccessException,
          IllegalArgumentException,
          InstantiationException,
          InvocationTargetException,
          OslcCoreApplicationException,
          URISyntaxException,
          SecurityException,
          NoSuchMethodException {
       return LegacyJenaModelHelper.fromJenaModel(model, beanClass);
  }

  public static <R extends IResource> R followLink(
      final Model m, final Link l, final Class<R> rClass)
      throws IllegalArgumentException, LyoModelException {
       return LegacyJenaModelHelper.followLink(m, l, rClass);
  }

}
