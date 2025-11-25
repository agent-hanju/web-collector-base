package me.hanju.webcollectorbase.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractResponseEntityTest {

  static class TestResponse extends AbstractResponseEntity {
  }

  @Test
  @DisplayName("초기 상태에서 rawData는 null")
  void initialRawDataIsNull() {
    TestResponse response = new TestResponse();
    assertNull(response.getRawData());
  }

  @Test
  @DisplayName("setRawData로 rawData 설정")
  void setRawData() {
    TestResponse response = new TestResponse();
    String json = "{\"key\":\"value\"}";

    response.setRawData(json);

    assertEquals(json, response.getRawData());
  }

  @Test
  @DisplayName("초기 상태에서 id는 null")
  void initialIdIsNull() {
    TestResponse response = new TestResponse();
    assertNull(response.getId());
  }
}
