/*
 *
 * Copyright 2020 Wei-Ming Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.github.wnameless.spring.boot.up.data.mongodb;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.util.ReflectionUtils;

public class ParentReferenceCallback implements ReflectionUtils.FieldCallback {

  private final Object source;
  private final MongoOperations mongoOperations;

  ParentReferenceCallback(final Object source,
      final MongoOperations mongoOperations) {
    this.source = source;
    this.mongoOperations = mongoOperations;
  }

  @Override
  public void doWith(final Field field)
      throws IllegalArgumentException, IllegalAccessException {
    ReflectionUtils.makeAccessible(field);

    if (field.isAnnotationPresent(DBRef.class)
        && field.isAnnotationPresent(Cascade.class)) {
      Cascade cascade = AnnotationUtils.getAnnotation(field, Cascade.class);
      if (!(Arrays.asList(cascade.value()).contains(CascadeType.ALL)
          || Arrays.asList(cascade.value()).contains(CascadeType.SAVE))) {
        return;
      }

      final Object fieldValue = field.get(source);

      if (fieldValue != null) {
        IdFieldCallback callback = new IdFieldCallback();
        ReflectionUtils.doWithFields(fieldValue.getClass(), callback);

        if (callback.isIdFound()) {
          for (Field f : fieldValue.getClass().getDeclaredFields()) {
            ReflectionUtils.makeAccessible(f);

            ParentReference parentRef =
                AnnotationUtils.findAnnotation(f, ParentReference.class);
            if (parentRef != null) {
              String refFieldName = parentRef.value();

              if (refFieldName.isEmpty()) {
                f.set(fieldValue, source);
                mongoOperations.save(fieldValue);
              } else {
                Field srcField =
                    ReflectionUtils.findField(source.getClass(), refFieldName);
                f.set(fieldValue, ReflectionUtils.getField(srcField, source));
                mongoOperations.save(fieldValue);
              }
            }
          }
        }
      }
    }
  }

}