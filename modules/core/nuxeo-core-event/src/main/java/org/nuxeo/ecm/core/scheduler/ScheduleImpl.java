/*
 * (C) Copyright 2007-2018 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Florent Guillaume
 *     Florent Munch
 */
package org.nuxeo.ecm.core.scheduler;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.common.xmap.registry.XEnable;
import org.nuxeo.common.xmap.registry.XRegistry;
import org.nuxeo.common.xmap.registry.XRegistryId;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * ScheduleImpl extension definition.
 */
@XObject("schedule")
@XRegistry(enable = false)
public class ScheduleImpl implements Schedule {

    @XNode("@id")
    @XRegistryId
    public String id;

    /**
     * @since 10.2
     */
    @XNode("@jobFactoryClass")
    public Class<? extends EventJobFactory> jobFactoryClass = DefaultEventJobFactory.class;

    @XNode(value = "event", fallback = "eventId")
    public String eventId;

    @XNode("eventCategory")
    public String eventCategory;

    @XNode("cronExpression")
    public String cronExpression;

    /**
     * @since 10.2
     */
    @XNode("timezone")
    public String timeZone;

    @XNode("username")
    public String username;

    /**
     * @since 5.7.3
     */
    @XNode(value = XEnable.ENABLE, fallback = "@enabled", defaultAssignment = "true")
    @XEnable
    public boolean enabled;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public EventJobFactory getJobFactory() {
        try {
            return jobFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException("Failed to instantiate job factory " + jobFactoryClass, e);
        }
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getEventCategory() {
        return eventCategory;
    }

    @Override
    public String getCronExpression() {
        return cronExpression;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "Schedule " + id + " (cron=" + cronExpression + ')';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Schedule)) {
            return false;
        }
        return id.equals(((Schedule) obj).getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @since 10.2
     */
    @Override
    public String getTimeZone() {
        return timeZone;
    }
}
