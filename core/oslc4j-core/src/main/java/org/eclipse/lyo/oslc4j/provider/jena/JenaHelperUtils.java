package org.eclipse.lyo.oslc4j.provider.jena;

import org.apache.jena.graph.BlankNodeId;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Alt;
import org.apache.jena.rdf.model.Bag;
import org.apache.jena.rdf.model.Container;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Seq;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.AnonId; // Added for getResource
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lyo.oslc4j.core.OSLC4JUtils;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespaceDefinition;
import org.eclipse.lyo.oslc4j.core.annotation.OslcSchema;
// import org.eclipse.lyo.oslc4j.core.exception.LyoModelException; // No longer needed for followLink
import org.eclipse.lyo.oslc4j.core.model.IOslcCustomNamespaceProvider;
// import org.eclipse.lyo.oslc4j.core.model.IResource; // No longer needed for followLink
// import org.eclipse.lyo.oslc4j.core.model.Link; // No longer needed for followLink
import org.eclipse.lyo.oslc4j.core.model.OslcConstants;


import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class JenaHelperUtils {

    @Deprecated
    public static final String OSLC4J_STRICT_DATATYPES = "org.eclipse.lyo.oslc4j.strictDatatypes";

    private static final String GENERATED_PREFIX_START = "j.";
    public static final String METHOD_NAME_START_GET = "get"; // Made public for JenaMarshaller
    public static final String METHOD_NAME_START_IS  = "is";  // Made public for JenaMarshaller
    public static final int METHOD_NAME_START_GET_LENGTH = METHOD_NAME_START_GET.length(); // Made public
    public static final int METHOD_NAME_START_IS_LENGTH  = METHOD_NAME_START_IS.length();   // Made public

    private JenaHelperUtils() {
    }

    public static void skolemize(final Model m) {
        skolemize(m, blankNodeId -> "urn:skolem:" + blankNodeId.getLabelString());
    }

    public static void skolemize(final Model m, Function<BlankNodeId, String> skolemUriFunction) {
        final ResIterator resIterator = m.listSubjects();
        while (resIterator.hasNext()) {
            final Resource resource = resIterator.nextResource();
            if (resource != null && resource.isAnon()) {
                final String skolemURI = skolemUriFunction.apply(resource.getId().getBlankNodeId());
                ResourceUtils.renameResource(resource, skolemURI);
            }
        }
    }

    public static Transformer createTransformer() {
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

    public static String getVisitedResourceName(Resource resource) {
        String visitedResourceName;
        if (resource.getURI() != null) {
            visitedResourceName = resource.getURI();
        } else {
            visitedResourceName = resource.getId().toString();
        }
        return visitedResourceName;
    }

    public static String generatePrefix(Model model, String namespace) {
        final Map<String, String> map = model.getNsPrefixMap();
        int i = 0;
        String candidatePrefix;
        do {
            candidatePrefix = GENERATED_PREFIX_START + i;
            ++i;
        } while (map.containsKey(candidatePrefix));
        model.setNsPrefix(candidatePrefix, namespace);
        return candidatePrefix;
    }

    public static HashSet<String> getTypesFromResource(final Resource resource, HashSet<String> types) {
        if (OSLC4JUtils.inferTypeFromShape() && types.isEmpty()) {
            StmtIterator rdfTypesIterator = resource.listProperties(RDF.type);
            while (rdfTypesIterator.hasNext()) {
                Statement rdfTypeStmt = rdfTypesIterator.next();
                RDFNode object = rdfTypeStmt.getObject();
                if (object.isResource()) {
                    String rdfType = object.asResource().getURI();
                    types.add(rdfType);
                }
            }
        }
        return types;
    }

    public static String getDefaultPropertyName(final Method method) {
        final String methodName = method.getName();
        final int startingIndex = methodName.startsWith(METHOD_NAME_START_GET)
                ? METHOD_NAME_START_GET_LENGTH
                : METHOD_NAME_START_IS_LENGTH;
        final int endingIndex = startingIndex + 1;
        final String lowercasedFirstCharacter = methodName.substring(startingIndex, endingIndex).toLowerCase(Locale.ENGLISH);
        if (methodName.length() == endingIndex) {
            return lowercasedFirstCharacter;
        }
        return lowercasedFirstCharacter + methodName.substring(endingIndex);
    }

    public static void recursivelyCollectNamespaceMappings(final Map<String, String> namespaceMappings,
                                                            final Class<?> resourceClass) {
        final OslcSchema oslcSchemaAnnotation = resourceClass.getPackage().getAnnotation(OslcSchema.class);
        if (oslcSchemaAnnotation != null) {
            final OslcNamespaceDefinition[] oslcNamespaceDefinitionAnnotations = oslcSchemaAnnotation.value();
            for (final OslcNamespaceDefinition oslcNamespaceDefinitionAnnotation : oslcNamespaceDefinitionAnnotations) {
                final String prefix = oslcNamespaceDefinitionAnnotation.prefix();
                final String namespaceURI = oslcNamespaceDefinitionAnnotation.namespaceURI();
                namespaceMappings.put(prefix, namespaceURI);
            }
            Class<? extends IOslcCustomNamespaceProvider> customNamespaceProvider = oslcSchemaAnnotation.customNamespaceProvider();
            if (!customNamespaceProvider.isInterface()) {
                try {
                    IOslcCustomNamespaceProvider customNamespaceProviderImpl = customNamespaceProvider.getDeclaredConstructor().newInstance();
                    Map<String, String> customNamespacePrefixes = customNamespaceProviderImpl.getCustomNamespacePrefixes();
                    if (null != customNamespacePrefixes) {
                        namespaceMappings.putAll(customNamespacePrefixes);
                    }
                } catch (IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                    throw new RuntimeException("Error initializing custom namespace provider: " + customNamespaceProvider.getName(), e);
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

    public static void ensureNamespacePrefix(final String prefix,
                                              final String namespace,
                                              final Map<String, String> namespaceMappings) {
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

    public static Resource getResource(Model model, Node node) {
        if (node.isURI()) {
            return model.createResource(node.getURI());
        } else if (node.isBlank()) {
            return model.createResource(new AnonId(node.getBlankNodeId()));
        } else {
            throw new IllegalArgumentException("Only returning nodes for URI or bnode subjects. Node: " + node);
        }
    }

    public static Class<? extends Container> getRdfCollectionResourceClass(Model model, RDFNode object) {
        if (object.isResource()) {
            Resource resource = object.asResource();
            if (resource.hasProperty(RDF.type, model.getResource(OslcConstants.RDF_NAMESPACE + "Alt"))) return Alt.class;
            if (resource.hasProperty(RDF.type, model.getResource(OslcConstants.RDF_NAMESPACE + "Bag"))) return Bag.class;
            if (resource.hasProperty(RDF.type, model.getResource(OslcConstants.RDF_NAMESPACE + "Seq"))) return Seq.class;
        }
        return null;
    }

    // floatToLiteral and doubleToLiteral can be added here if needed by JenaMarshaller's toLiteral methods
    public static Literal floatToLiteral(final Float f, final Model model) {
        // Copied from JenaMarshaller, can be used if toLiteral methods are moved here or delegated
        if (f.compareTo(Float.POSITIVE_INFINITY) == 0) {
            // logger.warn("+INF float is serialised to the model"); // Logger not available here directly
            return model.createTypedLiteral("INF", XSDDatatype.XSDfloat.getURI());
        } else if (f.compareTo(Float.NEGATIVE_INFINITY) == 0) {
            // logger.warn("-INF float is serialised to the model");
            return model.createTypedLiteral("-INF", XSDDatatype.XSDfloat.getURI());
        }
        return model.createTypedLiteral(f, XSDDatatype.XSDfloat);
    }

    public static Literal doubleToLiteral(final Double d, final Model model) {
        // Copied from JenaMarshaller
        if (d.compareTo(Double.POSITIVE_INFINITY) == 0) {
            // logger.warn("+INF double is serialised to the model");
            return model.createTypedLiteral("INF", XSDDatatype.XSDdouble.getURI());
        } else if (d.compareTo(Double.NEGATIVE_INFINITY) == 0) {
            // logger.warn("-INF double is serialised to the model");
            return model.createTypedLiteral("-INF", XSDDatatype.XSDdouble.getURI());
        }
        return model.createTypedLiteral(d, XSDDatatype.XSDdouble);
    }
}
