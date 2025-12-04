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
package org.eclipse.lyo.oslc4j.provider.jena;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.impl.ReifierStd;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.oslc4j.core.model.IOslcRdfApi;
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;

public class JenaOslcRdfApi implements IOslcRdfApi<Model, Resource, RDFNode, String, Literal> {

    @Override
    public Model createModel() {
        return ModelFactory.createDefaultModel();
    }

    @Override
    public Resource createResource(Model model) {
        return model.createResource();
    }

    @Override
    public Resource createResource(Model model, String uri) {
        return model.createResource(uri);
    }

    @Override
    public Resource createProperty(Model model, String namespace, String localName) {
        return model.createProperty(namespace, localName);
    }

    @Override
    public Resource createProperty(Model model, String uri) {
        return model.createProperty(uri);
    }

    @Override
    public Literal createLiteral(Model model, String value) {
        return model.createLiteral(value);
    }

    @Override
    public Literal createTypedLiteral(Model model, Object value) {
        if (value instanceof Float) {
             Float f = (Float) value;
             if (f.compareTo(Float.POSITIVE_INFINITY) == 0) {
                  return model.createTypedLiteral("INF", XSDDatatype.XSDfloat.getURI());
             } else if (f.compareTo(Float.NEGATIVE_INFINITY) == 0) {
                  return model.createTypedLiteral("-INF", XSDDatatype.XSDfloat.getURI());
             }
             return model.createTypedLiteral(f, XSDDatatype.XSDfloat);
        }
        if (value instanceof Double) {
             Double d = (Double) value;
             if (d.compareTo(Double.POSITIVE_INFINITY) == 0) {
                  return model.createTypedLiteral("INF", XSDDatatype.XSDdouble.getURI());
             } else if (d.compareTo(Double.NEGATIVE_INFINITY) == 0) {
                  return model.createTypedLiteral("-INF", XSDDatatype.XSDdouble.getURI());
             }
             return model.createTypedLiteral(d, XSDDatatype.XSDdouble);
        }
        return model.createTypedLiteral(value);
    }

    @Override
    public Literal createTypedLiteral(Model model, String value, String datatypeUri) {
        return model.createTypedLiteral(value, datatypeUri);
    }

    @Override
    public void addTriple(Model model, Resource subject, Resource predicate, RDFNode object) {
        model.add(subject, model.createProperty(predicate.getURI()), object);
    }

    @Override
    public Iterable<RDFNode> listObjects(Model model, Resource subject, Resource predicate) {
        List<RDFNode> objects = new ArrayList<>();
        Property prop = (predicate != null) ? model.createProperty(predicate.getURI()) : null;
        Iterator<Statement> stmtIt = model.listStatements(subject, prop, (RDFNode) null);
        while (stmtIt.hasNext()) {
            objects.add(stmtIt.next().getObject());
        }
        return objects;
    }

    @Override
    public RDFNode getSingleObject(Model model, Resource subject, Resource predicate) {
        Property prop = model.createProperty(predicate.getURI());
        Statement stmt = model.getProperty(subject, prop);
        return stmt != null ? stmt.getObject() : null;
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
        return node instanceof Resource && ((Resource) node).isURIResource();
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
        return resource.getURI();
    }

    @Override
    public String getLiteralValue(Literal literal) {
        return literal.getString();
    }

    @Override
    public String getLiteralDatatypeURI(Literal literal) {
        return literal.getDatatypeURI();
    }

    @Override
    public String getLiteralLanguage(Literal literal) {
        return literal.getLanguage();
    }

    @Override
    public String getBlankNodeLabel(Resource resource) {
        if (resource.isAnon()) {
             return resource.getId().getLabelString();
        }
        return null;
    }

    @Override
    public Resource createCollection(Model model, Iterable<RDFNode> members, String typeUri) {
        List<RDFNode> memberList = new ArrayList<>();
        members.forEach(memberList::add);

        if (typeUri.endsWith("List")) { // RDF List
            return model.createList(memberList.iterator());
        }

        Container container = null;
        if (typeUri.endsWith("Alt")) {
             container = model.createAlt();
        } else if (typeUri.endsWith("Bag")) {
             container = model.createBag();
        } else {
             container = model.createSeq();
        }

        for (RDFNode n : memberList) {
             container.add(n);
        }
        return container;
    }

    @Override
    public void setNamespacePrefix(Model model, String prefix, String namespaceUri) {
         model.setNsPrefix(prefix, namespaceUri);
    }

    @Override
    public String getNamespacePrefix(Model model, String namespaceUri) {
         return model.getNsURIPrefix(namespaceUri);
    }

    @Override
    public Resource reifyStatement(Model model, Resource subject, Resource predicate, RDFNode object) {
        Statement statement = model.createStatement(subject, model.createProperty(predicate.getURI()), object);
        Node reifiedNode = ReifierStd.reifyAs(model.getGraph(), null, statement.asTriple());
        return model.createResource(new AnonId(reifiedNode.getBlankNodeLabel()));
    }

    @Override
    public void removeProperties(Model model, Resource resource) {
        resource.removeProperties();
    }
}
