/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class DefaultComponentMetadataHandlerTest extends Specification {
    def handler = new DefaultComponentMetadataHandler(new DirectInstantiator())

    def "processing fails when status is not present in status scheme"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "green"
            getStatusScheme() >> ["alpha", "beta"]
        }

        when:
        handler.process(metadata)

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }
}
