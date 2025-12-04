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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.lyo.oslc4j.core.model.IOslcRdfApi;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.util.RDFCollections;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;

// M: Model
// R: Resource (IRI or BNode)
// N: Value (Resource or Literal)
// U: IRI
// L: Literal
public class Rdf4jOslcRdfApi implements IOslcRdfApi<Model, Resource, Value, IRI, Literal> {

    private final ValueFactory vf = SimpleValueFactory.getInstance();

    @Override
    public Model createModel() {
        return new LinkedHashModel();
    }

    @Override
    public Resource createResource(Model model) {
        return vf.createBNode();
    }

    @Override
    public Resource createResource(Model model, String uri) {
        return vf.createIRI(uri);
    }

    @Override
    public Resource createProperty(Model model, String namespace, String localName) {
        return vf.createIRI(namespace, localName);
    }

    @Override
    public Resource createProperty(Model model, String uri) {
        return vf.createIRI(uri);
    }

    @Override
    public Literal createLiteral(Model model, String value) {
        return vf.createLiteral(value);
    }

    @Override
    public Literal createTypedLiteral(Model model, Object value) {
        return vf.createLiteral(value.toString(), vf.createIRI("http://www.w3.org/2001/XMLSchema#string")); // Fallback
        // Actually vf.createLiteral(Object) handles many types?
        // No, vf has overloads for boolean, int, etc.
    }

    @Override
    public Literal createTypedLiteral(Model model, String value, String datatypeUri) {
        return vf.createLiteral(value, vf.createIRI(datatypeUri));
    }

    @Override
    public void addTriple(Model model, Resource subject, Resource predicate, Value object) {
        if (predicate instanceof IRI) {
            model.add(subject, (IRI) predicate, object);
        } else {
            throw new IllegalArgumentException("Predicate must be an IRI");
        }
    }

    @Override
    public Iterable<Value> listObjects(Model model, Resource subject, Resource predicate) {
        IRI pred = (predicate != null && predicate instanceof IRI) ? (IRI) predicate : null;
        // If predicate is not null but not IRI? (Can happen if generic allows Resource)
        // But RDF requires Predicate to be IRI.

        return model.filter(subject, pred, null).objects();
    }

    @Override
    public Value getSingleObject(Model model, Resource subject, Resource predicate) {
        IRI pred = (predicate instanceof IRI) ? (IRI) predicate : null;
        Iterator<Statement> iter = model.filter(subject, pred, null).iterator();
        return iter.hasNext() ? iter.next().getObject() : null;
    }

    @Override
    public boolean isResource(Object node) {
        return node instanceof Resource;
    }

    @Override
    public boolean isLiteral(Object node) {
        return node instanceof Literal;
    }

    @Override
    public boolean isURI(Object node) {
        return node instanceof IRI;
    }

    @Override
    public Resource asResource(Object node) {
        return (Resource) node;
    }

    @Override
    public Literal asLiteral(Object node) {
        return (Literal) node;
    }

    @Override
    public String getURI(Resource resource) {
        if (resource instanceof IRI) {
            return ((IRI) resource).stringValue();
        }
        return null;
    }

    @Override
    public String getLiteralValue(Literal literal) {
        return literal.getLabel();
    }

    @Override
    public String getLiteralDatatypeURI(Literal literal) {
        return literal.getDatatype().stringValue();
    }

    @Override
    public String getLiteralLanguage(Literal literal) {
        return literal.getLanguage().orElse(null);
    }

    @Override
    public String getBlankNodeLabel(Resource resource) {
        if (resource instanceof BNode) {
            return ((BNode) resource).getID();
        }
        return null;
    }

    @Override
    public Resource createCollection(Model model, Iterable<Value> members, String typeUri) {
        // RDF4J RDFCollections utility
        // But it requires a head resource.
        Resource head = vf.createBNode();
        // If typeUri is List
        if (typeUri.endsWith("List")) {
            RDFCollections.asRDF(members, head, model);
            return head;
        }

        // For Bag, Seq, Alt (Containers)
        // RDF4J doesn't have direct helper for creating Bag/Seq/Alt easily?
        // We can do it manually.
        IRI type = vf.createIRI(typeUri);
        model.add(head, RDF.TYPE, type);
        int i = 1;
        for (Value v : members) {
            model.add(head, vf.createIRI(RDF.NAMESPACE + "_" + i), v);
            i++;
        }
        return head;
    }

    @Override
    public void setNamespacePrefix(Model model, String prefix, String namespaceUri) {
        model.setNamespace(prefix, namespaceUri);
    }

    @Override
    public String getNamespacePrefix(Model model, String namespaceUri) {
        return model.getNamespace(namespaceUri).map(ns -> ns.getPrefix()).orElse(null);
    }

    @Override
    public Resource reifyStatement(Model model, Resource subject, Resource predicate, Value object) {
        // Reification in RDF4J?
        // Standard reification: create a node, add rdf:subject, rdf:predicate, rdf:object, rdf:type Statement
        BNode reifiedNode = vf.createBNode();
        if (predicate instanceof IRI) {
             model.add(reifiedNode, RDF.TYPE, RDF.STATEMENT);
             model.add(reifiedNode, RDF.SUBJECT, subject);
             model.add(reifiedNode, RDF.PREDICATE, predicate);
             model.add(reifiedNode, RDF.OBJECT, object);
             return reifiedNode;
        }
        throw new IllegalArgumentException("Predicate must be IRI for reification");
    }

    @Override
    public void removeProperties(Model model, Resource resource) {
        model.remove(resource, null, null);
    }

    // Improved createTypedLiteral to handle basic types
    private Literal literalOf(Object value) {
        if (value instanceof String) return vf.createLiteral((String) value);
        if (value instanceof Boolean) return vf.createLiteral((Boolean) value);
        if (value instanceof Integer) return vf.createLiteral((Integer) value);
        if (value instanceof Long) return vf.createLiteral((Long) value);
        if (value instanceof Float) return vf.createLiteral((Float) value);
        if (value instanceof Double) return vf.createLiteral((Double) value);
        if (value instanceof Date) return vf.createLiteral((Date) value);
        // ...
        return vf.createLiteral(value.toString());
    }
}
