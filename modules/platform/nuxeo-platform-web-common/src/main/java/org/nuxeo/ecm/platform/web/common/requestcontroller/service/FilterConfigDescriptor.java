/*
 * (C) Copyright 2006-2009 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.web.common.requestcontroller.service;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.common.xmap.registry.XRegistry;
import org.nuxeo.common.xmap.registry.XRegistryId;
import org.nuxeo.runtime.api.Framework;

/**
 * Descriptor for {@link RequestFilterConfig}
 *
 * @author tiry
 * @author ldoguin
 */
@XObject(value = "filterConfig")
@XRegistry(compatWarnOnMerge = true)
public class FilterConfigDescriptor {

    public static final String DEFAULT_CACHE_DURATION = "3599";

    @XNode("@name")
    @XRegistryId
    protected String name;

    @XNode("@synchonize")
    protected boolean useSync;

    @XNode("@transactional")
    protected boolean useTx;

    @XNode("@buffered")
    protected boolean useTxBuffered = true;

    @XNode("@cached")
    protected boolean cached;

    @XNode("@cacheTime")
    protected String cacheTime;

    @XNode("@private")
    protected boolean isPrivate;

    @XNode("@grant")
    protected boolean grant = true;

    @XNode("pattern")
    protected String pattern;

    protected Pattern compiledPattern;

    public String getName() {
        if (name == null) {
            return pattern;
        }
        return name;
    }

    public boolean useSync() {
        return useSync;
    }

    public boolean useTx() {
        return useTx;
    }

    public boolean useTxBuffered() {
        return useTxBuffered;
    }

    public boolean isGrantRule() {
        return grant;
    }

    public boolean isCached() {
        return cached;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getCacheTime() {
        if (StringUtils.isBlank(cacheTime)) {
            cacheTime = DEFAULT_CACHE_DURATION;
        }
        return cacheTime;
    }

    public String getPatternStr() {
        return pattern;
    }

    public Pattern getCompiledPattern() {
        if (compiledPattern == null) {
            compiledPattern = Pattern.compile(pattern);
        }
        return compiledPattern;
    }

}
