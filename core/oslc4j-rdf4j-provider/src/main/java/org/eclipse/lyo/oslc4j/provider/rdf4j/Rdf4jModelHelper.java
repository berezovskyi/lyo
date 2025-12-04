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
package org.eclipse.lyo.oslc4j.provider.rdf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import javax.xml.datatype.DatatypeConfigurationException;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.OslcRdfMapper;
import org.eclipse.lyo.oslc4j.core.model.ResponseInfo;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

public final class Rdf4jModelHelper {

    private static final OslcRdfMapper<Model, Resource, Value, IRI, Literal> mapper =
            new OslcRdfMapper<>(new Rdf4jOslcRdfApi());

    private Rdf4jModelHelper() {
        super();
    }

    public static Model createRdf4jModel(final Object[] objects)
            throws DatatypeConfigurationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException,
            OslcCoreApplicationException {
        return mapper.createModel(objects);
    }

    public static Model createRdf4jModel(
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
        return mapper.createModel(descriptionAbout, responseInfoAbout, responseInfo, objects, properties);
    }
}
