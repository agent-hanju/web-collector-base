package me.hanju.webcollectorbase.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HtmlParserUtilTest {

  @Nested
  @DisplayName("toPlainText 테스트")
  class ToPlainTextTest {

    @Test
    @DisplayName("null 입력 시 null 반환")
    void nullInput() {
      assertNull(HtmlParserUtil.toPlainText(null));
    }

    @Test
    @DisplayName("일반 HTML 태그 제거")
    void removeBasicTags() {
      String html = "<p>Hello <strong>World</strong></p>";
      assertEquals("Hello World", HtmlParserUtil.toPlainText(html));
    }

    @Test
    @DisplayName("중첩 태그 제거")
    void removeNestedTags() {
      String html = "<div><p><span>Nested</span> <em>Content</em></p></div>";
      assertEquals("Nested Content", HtmlParserUtil.toPlainText(html));
    }

    @Test
    @DisplayName("script 태그 내용 제거")
    void removeScript() {
      String html = "<p>Text</p><script>alert('xss');</script><p>More</p>";
      String result = HtmlParserUtil.toPlainText(html);
      assertFalse(result.contains("alert"));
      assertTrue(result.contains("Text"));
      assertTrue(result.contains("More"));
    }

    @Test
    @DisplayName("style 태그 내용 제거")
    void removeStyle() {
      String html = "<style>.class { color: red; }</style><p>Content</p>";
      String result = HtmlParserUtil.toPlainText(html);
      assertFalse(result.contains("color"));
      assertTrue(result.contains("Content"));
    }

    @Test
    @DisplayName("HTML 엔티티 디코딩")
    void decodeEntities() {
      String html = "<p>&lt;tag&gt; &amp; &quot;quotes&quot;</p>";
      String result = HtmlParserUtil.toPlainText(html);
      assertTrue(result.contains("<tag>"));
      assertTrue(result.contains("&"));
      assertTrue(result.contains("\"quotes\""));
    }

    @Test
    @DisplayName("빈 문자열 처리")
    void emptyString() {
      assertEquals("", HtmlParserUtil.toPlainText(""));
    }

    @Test
    @DisplayName("태그 없는 일반 텍스트")
    void plainText() {
      String text = "Just plain text";
      assertEquals(text, HtmlParserUtil.toPlainText(text));
    }
  }

  @Nested
  @DisplayName("cleanHtml 테스트")
  class CleanHtmlTest {

    @Test
    @DisplayName("null 입력 시 null 반환")
    void nullInput() {
      assertNull(HtmlParserUtil.cleanHtml(null));
    }

    @Test
    @DisplayName("table 태그 보존")
    void preserveTable() {
      String html = "<table><tr><td>Cell</td></tr></table>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("<table>"));
      assertTrue(result.contains("<tr>"));
      assertTrue(result.contains("<td>"));
      assertTrue(result.contains("Cell"));
      assertTrue(result.contains("</table>"));
    }

    @Test
    @DisplayName("img 태그 보존")
    void preserveImg() {
      String html = "<p><img src=\"test.jpg\" alt=\"Test\"></p>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("<img"));
      assertTrue(result.contains("src=\"test.jpg\""));
    }

    @Test
    @DisplayName("script 태그 완전 제거")
    void removeScript() {
      String html = "<div>Text<script>evil();</script>More</div>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertFalse(result.contains("<script>"));
      assertFalse(result.contains("evil"));
    }

    @Test
    @DisplayName("style 태그 완전 제거")
    void removeStyle() {
      String html = "<style>.x{}</style><div>Content</div>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertFalse(result.contains("<style>"));
      assertFalse(result.contains(".x{}"));
    }

    @Test
    @DisplayName("form 관련 태그 완전 제거")
    void removeFormElements() {
      String html = "<form><input type='text'><button>Submit</button><select><option>1</option></select></form>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertFalse(result.contains("<form>"));
      assertFalse(result.contains("<input"));
      assertFalse(result.contains("<button>"));
      assertFalse(result.contains("<select>"));
    }

    @Test
    @DisplayName("일반 태그는 unwrap (텍스트 보존)")
    void unwrapRegularTags() {
      String html = "<div><p><span>Text</span></p></div>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("Text"));
      assertFalse(result.contains("<div>"));
      assertFalse(result.contains("<p>"));
      assertFalse(result.contains("<span>"));
    }

    @Test
    @DisplayName("ul 리스트를 텍스트로 변환")
    void convertUnorderedList() {
      String html = "<ul><li>Item 1</li><li>Item 2</li></ul>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("- Item 1"));
      assertTrue(result.contains("- Item 2"));
      assertFalse(result.contains("<ul>"));
      assertFalse(result.contains("<li>"));
    }

    @Test
    @DisplayName("ol 리스트를 번호 텍스트로 변환")
    void convertOrderedList() {
      String html = "<ol><li>First</li><li>Second</li></ol>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("1. First"));
      assertTrue(result.contains("2. Second"));
    }

    @Test
    @DisplayName("중첩 리스트 변환")
    void convertNestedList() {
      String html = "<ul><li>Parent<ul><li>Child</li></ul></li></ul>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("- Parent"), "부모 항목 포함: " + result);
      assertTrue(result.contains("  - Child"), "중첩 Child 항목 들여쓰기 적용: " + result);
    }

    @Test
    @DisplayName("복합 테이블 보존")
    void preserveComplexTable() {
      String html = """
          <table>
            <thead><tr><th>Header</th></tr></thead>
            <tbody><tr><td>Data</td></tr></tbody>
            <tfoot><tr><td>Footer</td></tr></tfoot>
            <caption>Title</caption>
          </table>
          """;
      String result = HtmlParserUtil.cleanHtml(html);
      String normalized = result.replaceAll("\\s+", " ").trim();
      String expected = "<table> <thead> <tr> <th>Header</th> </tr> </thead> <tbody> <tr> <td>Data</td> </tr> </tbody> <tfoot> <tr> <td>Footer</td> </tr> </tfoot> <caption> Title </caption> </table>";
      assertEquals(expected, normalized);
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCaseTest {

    @Test
    @DisplayName("빈 HTML 처리")
    void emptyHtml() {
      String result = HtmlParserUtil.cleanHtml("");
      assertNotNull(result);
    }

    @Test
    @DisplayName("공백만 있는 HTML")
    void whitespaceOnly() {
      String result = HtmlParserUtil.cleanHtml("   \n\t   ");
      assertNotNull(result);
    }

    @Test
    @DisplayName("잘못된 HTML도 처리 가능")
    void malformedHtml() {
      String html = "<p>Unclosed<div>Mixed</p></div>";
      assertDoesNotThrow(() -> HtmlParserUtil.cleanHtml(html));
      assertDoesNotThrow(() -> HtmlParserUtil.toPlainText(html));
    }

    @Test
    @DisplayName("특수문자 포함 HTML")
    void specialCharacters() {
      String html = "<p>한글 텍스트 & special <chars></p>";
      String result = HtmlParserUtil.toPlainText(html);
      assertTrue(result.contains("한글 텍스트"));
      assertTrue(result.contains("&"));
    }

    @Test
    @DisplayName("긴 HTML 문서 처리")
    void longHtmlDocument() {
      StringBuilder html = new StringBuilder("<html><body>");
      for (int i = 0; i < 1000; i++) {
        html.append("<p>Paragraph ").append(i).append("</p>");
      }
      html.append("</body></html>");

      assertDoesNotThrow(() -> HtmlParserUtil.cleanHtml(html.toString()));
      assertDoesNotThrow(() -> HtmlParserUtil.toPlainText(html.toString()));
    }

    @Test
    @DisplayName("자기닫힘 태그 처리")
    void selfClosingTags() {
      String html = "<p>Text<br/>Line<br>Break<hr/></p>";
      assertDoesNotThrow(() -> HtmlParserUtil.cleanHtml(html));
    }

    @Test
    @DisplayName("인라인 스타일 속성 제거")
    void removeInlineStyles() {
      String html = "<table style=\"width:100%\"><tr><td>Data</td></tr></table>";
      String result = HtmlParserUtil.cleanHtml(html);
      assertTrue(result.contains("<table>"));
      assertTrue(result.contains("Data"));
      assertFalse(result.contains("style="), "인라인 스타일 속성은 제거되어야 함: " + result);
    }
  }
}
