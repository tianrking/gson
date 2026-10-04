/*
 * Copyright (C) 2026 Google LLC
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

package com.google.gson.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.Test;

public final class LinkedTreeMapValuesTest {
  private final Gson gson = new Gson();

  @Test
  public void serializeDeserializedMapValues() {
    Map<String, String> map =
        gson.fromJson(
            "{\"c\":\"same\",\"a\":null,\"b\":\"same\"}",
            new TypeToken<Map<String, String>>() {});
    assertThat(gson.toJson(map.values())).isEqualTo("[\"same\",null,\"same\"]");
    assertThat(gson.toJsonTree(map.values()))
        .isEqualTo(JsonParser.parseString("[\"same\",null,\"same\"]"));
  }

  @Test
  public void serializeEmptyMapValues() {
    Map<String, String> map = gson.fromJson("{}", new TypeToken<Map<String, String>>() {});
    assertThat(gson.toJson(map.values())).isEqualTo("[]");
  }

  @Test
  public void serializeUntypedObjectValues() {
    Object object = gson.fromJson("{\"c\":true,\"a\":[1,2],\"b\":{\"name\":\"value\"}}", Object.class);
    Collection<?> values = ((Map<?, ?>) object).values();
    assertThat(gson.toJsonTree(values))
        .isEqualTo(JsonParser.parseString("[true,[1,2],{\"name\":\"value\"}]"));
  }

  @Test
  public void serializeJsonObjectMapValues() {
    JsonObject object = JsonParser.parseString("{\"c\":null,\"a\":[1],\"b\":\"value\"}").getAsJsonObject();
    assertThat(gson.toJson(object.asMap().values())).isEqualTo("[null,[1],\"value\"]");
  }

  @Test
  public void serializeValuesAfterMapChanges() {
    Map<String, String> map =
        gson.fromJson("{\"c\":\"first\",\"a\":\"second\"}", new TypeToken<Map<String, String>>() {});
    Collection<String> values = map.values();
    map.put("c", "replaced");
    assertThat(map.remove("a")).isEqualTo("second");
    map.put("b", "last");
    assertThat(gson.toJson(values)).isEqualTo("[\"replaced\",\"last\"]");
    values.clear();
    assertThat(map).isEmpty();
    assertThat(gson.toJson(values)).isEqualTo("[]");
  }

  @Test
  public void valuesReflectMapChanges() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    Collection<String> values = map.values();
    assertThat(values).isEmpty();
    map.put("c", "first");
    map.put("a", "second");
    assertThat(values).hasSize(2);
    assertThat(values).containsExactly("first", "second").inOrder();
    map.put("c", null);
    assertThat(map.remove("a")).isEqualTo("second");
    assertThat(values).containsExactly((String) null);
    map.clear();
    assertThat(values).isEmpty();
  }

  @Test
  public void removeValuesRemovesFirstMatchingEntry() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    map.put("c", "same");
    map.put("a", null);
    map.put("b", "same");
    Collection<String> values = map.values();
    assertThat(values.remove("same")).isTrue();
    assertThat(map.keySet()).containsExactly("a", "b").inOrder();
    assertThat(values).containsExactly(null, "same").inOrder();
    assertThat(values.remove(null)).isTrue();
    assertThat(map).containsExactly("b", "same");
    assertThat(values.remove("missing")).isFalse();
    assertThat(values.contains("same")).isTrue();
    assertThat(values.contains(null)).isFalse();
  }

  @Test
  public void bulkRemovalUpdatesMap() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    map.put("c", "keep");
    map.put("a", null);
    map.put("b", "remove");
    map.put("d", "keep");
    Collection<String> values = map.values();
    assertThat(values.removeAll(Arrays.asList("remove", null))).isTrue();
    assertThat(map.keySet()).containsExactly("c", "d").inOrder();
    map.put("e", "other");
    assertThat(values.retainAll(Arrays.asList("keep"))).isTrue();
    assertThat(map.keySet()).containsExactly("c", "d").inOrder();
    values.clear();
    assertThat(map).isEmpty();
    map.put("f", "new");
    assertThat(values).containsExactly("new");
  }

  @Test
  public void iteratorRemovalUpdatesMapAndContinues() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    map.put("c", "first");
    map.put("a", "second");
    Iterator<String> iterator = map.values().iterator();
    assertThrows(IllegalStateException.class, iterator::remove);
    assertThat(iterator.next()).isEqualTo("first");
    iterator.remove();
    assertThat(map).containsExactly("a", "second");
    assertThrows(IllegalStateException.class, iterator::remove);
    assertThat(iterator.next()).isEqualTo("second");
    iterator.remove();
    assertThat(map).isEmpty();
    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
  }

  @Test
  public void iteratorDetectsStructuralChangesButAllowsValueReplacement() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    map.put("c", "first");
    map.put("a", "second");
    Iterator<String> iterator = map.values().iterator();
    map.put("c", "replaced");
    assertThat(iterator.next()).isEqualTo("replaced");
    map.put("b", "third");
    assertThrows(ConcurrentModificationException.class, iterator::next);
    Iterator<String> afterInsertion = map.values().iterator();
    assertThat(map.remove("a")).isEqualTo("second");
    assertThrows(ConcurrentModificationException.class, afterInsertion::next);
  }

  @Test
  public void valuesRejectAdditionAndEmptyIteratorIsExhausted() {
    LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
    Collection<String> values = map.values();
    assertThrows(UnsupportedOperationException.class, () -> values.add("value"));
    assertThat(map).isEmpty();
    Iterator<String> iterator = values.iterator();
    assertThat(iterator.hasNext()).isFalse();
    assertThrows(NoSuchElementException.class, iterator::next);
    assertThrows(IllegalStateException.class, iterator::remove);
  }
}
