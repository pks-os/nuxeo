/*
 * (C) Copyright 2006-2020 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Bogdan Stefanescu
 *     Anahide Tchertchian
 */
package org.nuxeo.runtime.services.resource;

import java.net.URL;

import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Service handling resources retrieved from contributor bundle.
 */
public class ResourceService extends DefaultComponent {

    public static final String XP_RESOURCES = "resources";

    public URL getResource(String name) {
        return this.<ResourceDescriptor> getRegistryContribution(XP_RESOURCES, name)
                   .map(ResourceDescriptor::getUrl)
                   .orElse(null);
    }

}
