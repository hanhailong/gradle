/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.attributes;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Comparator;
import java.util.List;

public class DefaultCompatibilityRuleChain<T> implements CompatibilityRuleChainInternal<T> {

    private final List<Action<? super CompatibilityCheckDetails<T>>> rules = Lists.newArrayList();

    private boolean assumeCompatibleWhenMissing;

    @Override
    public void ordered(Comparator<? super T> comparator) {
        Action<? super CompatibilityCheckDetails<T>> rule = AttributeMatchingRules.orderedCompatibility(comparator, false);
        rules.add(rule);
    }

    @Override
    public void reverseOrdered(Comparator<? super T> comparator) {
        Action<? super CompatibilityCheckDetails<T>> rule = AttributeMatchingRules.orderedCompatibility(comparator, true);
        rules.add(rule);
    }

    @Override
    public void add(final Class<? extends AttributeCompatibilityRule<T>> rule) {
        rules.add(new Action<CompatibilityCheckDetails<T>>() {
            @Override
            public void execute(CompatibilityCheckDetails<T> details) {
                try {
                    AttributeCompatibilityRule<T> instance = DirectInstantiator.INSTANCE.newInstance(rule);
                    instance.execute(details);
                } catch (Throwable t) {
                    throw new AttributeMatchException(String.format("Could not determine whether value %s is compatible with value %s using %s.", details.getProducerValue(), details.getConsumerValue(), ModelType.of(rule).getDisplayName()), t);
                }
            }
        });
    }

    @Override
    public void assumeCompatibleWhenMissing() {
        assumeCompatibleWhenMissing = true;
    }

    @Override
    public boolean isCompatibleWhenMissing() {
        return assumeCompatibleWhenMissing;
    }

    @Override
    public void execute(CompatibilityCheckDetails<T> details) {
        State<T> state = new State<T>(details);
        for (Action<? super CompatibilityCheckDetails<T>> rule : rules) {
            rule.execute(state);
            if (state.determined) {
                return;
            }
        }
        if (!state.determined) {
            AttributeMatchingRules.<T>equalityCompatibility().execute(state);
            if (state.determined) {
                return;
            }
        }
        // Eventually fail, always
        details.incompatible();
    }

    private static class State<T> implements CompatibilityCheckDetails<T> {
        private final CompatibilityCheckDetails<T> delegate;
        private boolean determined;

        private State(CompatibilityCheckDetails<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T getConsumerValue() {
            return delegate.getConsumerValue();
        }

        @Override
        public T getProducerValue() {
            return delegate.getProducerValue();
        }

        @Override
        public void compatible() {
            determined = true;
            delegate.compatible();
        }

        @Override
        public void incompatible() {
            determined = true;
            delegate.incompatible();
        }
    }
}