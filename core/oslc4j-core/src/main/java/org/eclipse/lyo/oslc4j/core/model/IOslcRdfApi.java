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

public interface IOslcRdfApi<M, R, N, U, L> {

    // M: Model/Graph
    // R: Resource (Named or Blank Node)
    // N: Node (Resource or Literal) - effectively RDFNode in Jena
    // U: URI/IRI type
    // L: Literal type

    M createModel();

    R createResource(M model);
    R createResource(M model, String uri);

    // Properties are also resources/URIs usually
    R createProperty(M model, String namespace, String localName);
    R createProperty(M model, String uri);

    L createLiteral(M model, String value);
    L createTypedLiteral(M model, Object value); // Tries to infer type
    L createTypedLiteral(M model, String value, String datatypeUri);

    void addTriple(M model, R subject, R predicate, N object);

    // Traversal / Query
    Iterable<N> listObjects(M model, R subject, R predicate);
    N getSingleObject(M model, R subject, R predicate); // returns null or object

    boolean isResource(Object node);
    boolean isLiteral(Object node);
    boolean isURI(Object node);

    R asResource(Object node);
    L asLiteral(Object node);

    String getURI(R resource);
    String getLiteralValue(L literal);
    String getLiteralDatatypeURI(L literal);
    String getLiteralLanguage(L literal);

    String getBlankNodeLabel(R resource);

    // For handling collections (List, Alt, Bag, Seq)
    R createCollection(M model, Iterable<N> members, String typeUri);

    // Namespaces
    void setNamespacePrefix(M model, String prefix, String namespaceUri);
    String getNamespacePrefix(M model, String namespaceUri);

    // Reification
    R reifyStatement(M model, R subject, R predicate, N object);
    void removeProperties(M model, R resource);
}
