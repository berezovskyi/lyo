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

import java.net.URI;
import org.eclipse.lyo.oslc4j.core.annotation.OslcName;
import org.eclipse.lyo.oslc4j.core.annotation.OslcNamespace;
import org.eclipse.lyo.oslc4j.core.annotation.OslcResourceShape;
import org.eclipse.lyo.oslc4j.core.model.AbstractResource;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class Rdf4jProviderTest {

    @OslcNamespace("http://example.com/ns#")
    @OslcName("TestResource")
    @OslcResourceShape(title = "Test Resource Shape", describes = "http://example.com/ns#TestResource")
    public static class TestResource extends AbstractResource {
        public TestResource() {}
        public TestResource(URI about) {
            super(about);
        }
    }

    @Test
    public void testMarshalling() throws Exception {
        TestResource resource = new TestResource(URI.create("http://example.com/resource/1"));

        Model model = Rdf4jModelHelper.createRdf4jModel(new Object[]{resource});

        assertThat(model).isNotEmpty();
        Rio.write(model, System.out, RDFFormat.TURTLE);

        // Basic check: subject should be in model
        boolean containsSubject = model.contains(
            org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance().createIRI("http://example.com/resource/1"),
            null, null
        );
        assertThat(containsSubject).isTrue();
    }
}
