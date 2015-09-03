/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.*;

@ThreadSafe
public class ScalarCollectionStrategy extends CollectionStrategy {

    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(List.class)//,
        //ModelType.of(Set.class)
    );

    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store, ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        Class<? super T> rawClass = type.getRawClass();
        ModelType<? super T> rawCollectionType = ModelType.of(rawClass);
        if (TYPES.contains(rawCollectionType)) {
            ModelType<?> elementType = type.getTypeVariables().get(0);
            if (ScalarTypes.isScalarType(elementType)) {
                validateType(rawCollectionType, extractionContext, type);
                return new ModelSchemaExtractionResult<T>(createSchema(store, type, elementType));
            }
        }

        return null;
    }

    private <T, E> ScalarCollectionSchema<T, E> createSchema(ModelSchemaStore store, ModelType<T> type, ModelType<E> elementType) {
        return new ScalarCollectionSchema<T, E>(type, elementType, this.<T, E>getNodeInitializer(store));
    }

    @Override
    protected <T, E> Function<ModelCollectionSchema<T, E>, NodeInitializer> getNodeInitializer(final ModelSchemaStore store) {
        return new Function<ModelCollectionSchema<T, E>, NodeInitializer>() {
            @Override
            public NodeInitializer apply(ModelCollectionSchema<T, E> schema) {
                return new ProjectionOnlyNodeInitializer(
                    TypedModelProjection.of(
                        ModelTypes.list(schema.getElementType()),
                        new ListViewFactory<E>(schema.getElementType())
                    )
                );
            }
        };
    }

    private static class ListViewFactory<T> implements ModelViewFactory<List<T>> {
        private final ModelType<T> elementType;

        public ListViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<List<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<List<T>> listType = ModelTypes.list(elementType);
            DefaultModelViewState state = new DefaultModelViewState(listType, ruleDescriptor, writable, !writable);
            NodeBackedList<T> list = new NodeBackedList<T>(modelNode, state, elementType);
            return InstanceModelView.of(modelNode.getPath(), listType, list, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ListViewFactory<?> that = (ListViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }

    private static class NodeBackedList<T> implements List<T>, ManagedInstance {
        private final MutableModelNode modelNode;
        private final ModelViewState state;
        private final ModelType<T> elementType;

        public NodeBackedList(MutableModelNode modelNode, ModelViewState state, ModelType<T> elementType) {
            this.modelNode = modelNode;
            this.state = state;
            this.elementType = elementType;
        }

        private List<T> getDelegate(boolean write) {
            if (write) {
                state.assertCanMutate();
            }
            List<T> delegate = Cast.uncheckedCast(modelNode.getPrivateData(List.class));
            if (delegate == null) {
                if (write) {
                    delegate = new ArrayList<T>();
                    modelNode.setPrivateData(List.class, delegate);
                } else {
                    delegate = Collections.emptyList();
                }
            }
            return delegate;
        }

        @Override
        public MutableModelNode getBackingNode() {
            return modelNode;
        }

        @Override
        public ModelType<?> getManagedType() {
            return ModelType.of(this.getClass());
        }

        private void validateElementType(Object o) {
            if (o != null) {
                ModelType<?> obType = ModelType.of(o.getClass());
                if (!obType.equals(elementType)) {
                    throw new IllegalArgumentException(String.format("Cannot add an element of type %s to a collection of %s", obType, elementType));
                }
            }
        }

        private void validateCollection(Collection<? extends T> c) {
            for (T element : c) {
                validateElementType(element);
            }
        }

        @Override
        public boolean add(T t) {
            validateElementType(t);
            return getDelegate(true).add(t);
        }

        @Override
        public void add(int index, T element) {
            validateElementType(element);
            getDelegate(true).add(index, element);
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            validateCollection(c);
            return getDelegate(true).addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            validateCollection(c);
            return getDelegate(true).addAll(index, c);
        }

        @Override
        public void clear() {
            getDelegate(true).clear();
        }

        @Override
        public boolean contains(Object o) {
            return getDelegate(false).contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return getDelegate(false).containsAll(c);
        }

        @Override
        public boolean equals(Object o) {
            return getDelegate(false).equals(o);
        }

        @Override
        public T get(int index) {
            return getDelegate(false).get(index);
        }

        @Override
        public int hashCode() {
            return getDelegate(false).hashCode();
        }

        @Override
        public int indexOf(Object o) {
            return getDelegate(false).indexOf(o);
        }

        @Override
        public boolean isEmpty() {
            return getDelegate(false).isEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            return new MutationSafeIterator(getDelegate(false).iterator());
        }

        @Override
        public int lastIndexOf(Object o) {
            return getDelegate(false).lastIndexOf(o);
        }

        @Override
        public ListIterator<T> listIterator() {
            return getDelegate(false).listIterator();
        }

        @Override
        public ListIterator<T> listIterator(int index) {
            return getDelegate(false).listIterator(index);
        }

        @Override
        public T remove(int index) {
            return getDelegate(true).remove(index);
        }

        @Override
        public boolean remove(Object o) {
            return getDelegate(true).remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return getDelegate(true).removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return getDelegate(true).retainAll(c);
        }

        @Override
        public T set(int index, T element) {
            validateElementType(element);
            return getDelegate(true).set(index, element);
        }

        @Override
        public int size() {
            return getDelegate(false).size();
        }

        @Override
        public void sort(Comparator<? super T> c) {
            getDelegate(true).sort(c);
        }

        @Override
        public List<T> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray() {
            return getDelegate(false).toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return getDelegate(false).toArray(a);
        }

        private final class MutationSafeIterator implements Iterator<T> {
            private final Iterator<T> delegate;

            private MutationSafeIterator(Iterator<T> delegate) {
                this.delegate = delegate;
            }

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                return delegate.next();
            }

            @Override
            public void remove() {
                state.assertCanMutate();
                delegate.remove();
            }
        }
    }
}
