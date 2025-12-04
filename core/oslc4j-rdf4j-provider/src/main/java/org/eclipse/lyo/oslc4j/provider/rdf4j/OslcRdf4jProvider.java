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

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Map;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.lyo.oslc4j.core.exception.OslcCoreApplicationException;
import org.eclipse.lyo.oslc4j.core.model.OslcMediaType;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import javax.xml.datatype.DatatypeConfigurationException;

@Provider
@Produces({OslcMediaType.APPLICATION_RDF_XML, OslcMediaType.TEXT_TURTLE})
public class OslcRdf4jProvider implements MessageBodyWriter<Object> {

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true; // Simplified for basic testing, should verify OslcResource
    }

    @Override
    public void writeTo(Object object, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        try {
            Model model;
            if (object instanceof Object[]) {
                model = Rdf4jModelHelper.createRdf4jModel((Object[]) object);
            } else {
                model = Rdf4jModelHelper.createRdf4jModel(new Object[]{object});
            }

            RDFFormat format = RDFFormat.RDFXML;
            if (mediaType.isCompatible(MediaType.valueOf(OslcMediaType.TEXT_TURTLE))) {
                format = RDFFormat.TURTLE;
            }

            Rio.write(model, entityStream, format);

        } catch (DatatypeConfigurationException | IllegalAccessException | InvocationTargetException | OslcCoreApplicationException e) {
            throw new WebApplicationException(e);
        }
    }
}
