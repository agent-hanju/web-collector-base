package me.hanju.webcollectorbase.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractDomainEntityTest {

  static class TestEntity extends AbstractDomainEntity {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }

  @Test
  @DisplayName("unexpectedFieldMap 초기값은 null")
  void initialUnexpectedFieldMapIsNull() {
    TestEntity entity = new TestEntity();
    assertNull(entity.getUnexpectedFieldMap());
    assertFalse(entity.getUnexpectedFieldMap() != null && !entity.getUnexpectedFieldMap().isEmpty());
  }

  @Test
  @DisplayName("putUnexpectedField로 필드 추가")
  void putUnexpectedField() {
    TestEntity entity = new TestEntity();

    entity.putUnexpectedField("unknownField", "someValue");

    assertTrue(entity.getUnexpectedFieldMap() != null && !entity.getUnexpectedFieldMap().isEmpty());
    assertEquals("someValue", entity.getUnexpectedFieldMap().get("unknownField"));
  }

  @Test
  @DisplayName("여러 필드 추가")
  void putMultipleUnexpectedFields() {
    TestEntity entity = new TestEntity();

    entity.putUnexpectedField("field1", "value1");
    entity.putUnexpectedField("field2", "value2");

    assertEquals(2, entity.getUnexpectedFieldMap().size());
    assertEquals("value1", entity.getUnexpectedFieldMap().get("field1"));
    assertEquals("value2", entity.getUnexpectedFieldMap().get("field2"));
  }
}
