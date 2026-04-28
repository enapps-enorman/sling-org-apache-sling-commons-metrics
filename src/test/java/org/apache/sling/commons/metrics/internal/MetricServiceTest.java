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

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Set;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import org.apache.sling.commons.metrics.Counter;
import org.apache.sling.commons.metrics.Gauge;
import org.apache.sling.commons.metrics.Histogram;
import org.apache.sling.commons.metrics.Meter;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.commons.metrics.Timer;
import org.apache.sling.testing.mock.osgi.MapUtil;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContext;
import org.apache.sling.testing.mock.osgi.junit5.OsgiContextExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.ServiceRegistration;

import static org.apache.sling.commons.metrics.internal.BundleMetricsMapper.JMX_TYPE_METRICS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OsgiContextExtension.class)
class MetricServiceTest {
    public final OsgiContext context = new OsgiContext();

    private MetricsServiceImpl service = new MetricsServiceImpl();

    @AfterEach
    void deactivate() {
        MockOsgi.deactivate(service, context.bundleContext());
    }

    @Test
    void defaultSetup() {
        activate();

        assertNotNull(context.getService(MetricRegistry.class));
        assertNotNull(context.getService(MetricsService.class));

        assertNotNull(service.adaptTo(MetricRegistry.class));

        MockOsgi.deactivate(service, context.bundleContext());

        assertNull(context.getService(MetricRegistry.class));
        assertNull(context.getService(MetricsService.class));
    }

    @Test
    void meter() {
        activate();
        Meter meter = service.meter("test");

        assertNotNull(meter);
        assertTrue(getRegistry().getMeters().containsKey("test"));

        assertSame(meter, service.meter("test"));
    }

    @Test
    void counter() {
        activate();
        Counter counter = service.counter("test");

        assertNotNull(counter);
        assertTrue(getRegistry().getCounters().containsKey("test"));

        assertSame(counter, service.counter("test"));
    }

    @Test
    void timer() {
        activate();
        Timer timer = service.timer("test");

        assertNotNull(timer);
        assertTrue(getRegistry().getTimers().containsKey("test"));

        assertSame(timer, service.timer("test"));
    }

    @Test
    void histogram() {
        activate();
        Histogram histo = service.histogram("test");

        assertNotNull(histo);
        assertTrue(getRegistry().getHistograms().containsKey("test"));

        assertSame(histo, service.histogram("test"));
    }

    @Test
    void gaugeRegistration() {
        activate();
        Gauge<Long> gauge = service.gauge("gauge", () -> 42L);
        assertNotNull(gauge);
        assertTrue(getRegistry().getGauges().containsKey("gauge"));
        assertEquals(Long.valueOf(42L), gauge.getValue());

        // Just the name matters, not the supplier
        Gauge<?> gauge2 = service.gauge("gauge", () -> 43L);
        assertSame(gauge, gauge2);
    }

    @Test
    void sameNameDifferentTypeMetric() {
        activate();
        service.histogram("test");
        assertThrows(IllegalArgumentException.class, () -> service.timer("test"));
    }

    @Test
    void jmxRegistration() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        activate();
        Meter meter = service.meter("test");
        assertNotNull(meter);
        QueryExp q = Query.isInstanceOf(Query.value(JmxReporter.JmxMeterMBean.class.getName()));
        Set<ObjectName> names =
                server.queryNames(new ObjectName("org.apache.sling:name=*,type=" + JMX_TYPE_METRICS), q);
        assertThat(names, is(not(empty())));

        MockOsgi.deactivate(service, context.bundleContext());

        names = server.queryNames(new ObjectName("org.apache.sling:name=*"), q);
        assertThat(names, is(empty()));
    }

    @Test
    void gaugeRegistrationViaWhiteboard() {
        activate();
        @SuppressWarnings("rawtypes")
        ServiceRegistration<Gauge> reg = context.bundleContext()
                .registerService(Gauge.class, new TestGauge(42), MapUtil.toDictionary(Gauge.NAME, "foo"));

        assertTrue(getRegistry().getGauges().containsKey("foo"));
        assertEquals(42, getRegistry().getGauges().get("foo").getValue());

        reg.unregister();
        assertFalse(getRegistry().getGauges().containsKey("foo"));
    }

    @Test
    void unregisterMetric() {
        activate();
        Gauge<Long> gauge = service.gauge("gauge", () -> 42L);
        assertNotNull(gauge);
        assertTrue(getRegistry().getGauges().containsKey("gauge"));
        service.unregister("gauge");
        assertFalse(getRegistry().getGauges().containsKey("gauge"));
    }

    private MetricRegistry getRegistry() {
        return context.getService(MetricRegistry.class);
    }

    private void activate() {
        MockOsgi.activate(service, context.bundleContext(), Collections.<String, Object>emptyMap());
    }

    private static class TestGauge implements Gauge<Object> {
        int value;

        public TestGauge(int value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }
}
