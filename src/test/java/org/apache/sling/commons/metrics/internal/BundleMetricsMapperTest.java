/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.commons.metrics.internal;

import javax.management.ObjectName;

import java.util.Map;

import com.codahale.metrics.MetricRegistry;
import org.apache.sling.testing.mock.osgi.MockBundle;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(OsgiContextExtension.class)
class BundleMetricsMapperTest {

    public final OsgiContext context = new OsgiContext();

    private MetricRegistry registry = new MetricRegistry();

    private BundleMetricsMapper mapper = new BundleMetricsMapper(new MetricsServiceImpl(), registry);

    @Test
    void defaultDomainName() {
        ObjectName name = mapper.createName("counter", "foo", "bar");
        assertEquals("foo", name.getDomain());
    }

    @Test
    void mappedName_SymbolicName() {
        MockBundle bundle = new MockBundle(context.bundleContext());
        bundle.setSymbolicName("com.example");

        mapper.addMapping("bar", bundle);

        ObjectName name = mapper.createName("counter", "foo", "bar");
        assertEquals("com.example", name.getDomain());
    }

    @Test
    void mappedName_Header() {
        MockBundle bundle = new MockBundle(context.bundleContext());
        bundle.setSymbolicName("com.example");
        bundle.setHeaders(Map.of(BundleMetricsMapper.HEADER_DOMAIN_NAME, "com.test"));

        mapper.addMapping("bar", bundle);

        ObjectName name = mapper.createName("counter", "foo", "bar");
        assertEquals("com.test", name.getDomain());
    }
}
