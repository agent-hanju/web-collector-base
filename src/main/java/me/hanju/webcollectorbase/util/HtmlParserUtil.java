package me.hanju.webcollectorbase.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Jsoup 기반 HTML 파싱 및 정제 유틸리티.
 * <p>
 * HTML 태그 제거, 리스트 변환, 불필요한 요소 정리 등의 기능을 제공합니다.
 * 테이블과 이미지 태그는 보존하고, 스크립트/스타일/폼 요소는 완전히 제거합니다.
 * </p>
 */
@Slf4j
@UtilityClass
public class HtmlParserUtil {
  /**
   * HTML 태그를 모두 제거하여 순수 텍스트로 반환
   *
   * @param htmlString HTML 문자열
   * @return 모든 태그를 unwrap한 순수 텍스트
   */
  public String toPlainText(final String htmlString) {
    if (htmlString == null) {
      return null;
    }

    try {
      Document doc = Jsoup.parse(htmlString);
      return doc.text();
    } catch (Exception e) {
      log.warn("Failed to convert HTML to plain text: {}", e.getMessage());
      return htmlString;
    }
  }

  /**
   * HTML 정제
   * - 보존: table 관련 태그, img, pre
   * - 리스트: 텍스트로 변환 (ul → "- 항목", ol → "1. 항목")
   * - 완전 제거: script, style, form, input, button
   * - 태그만 제거 (텍스트 보존): 나머지 모든 태그
   *
   * @param htmlString HTML 문자열
   * @return 정제 결과 문자열
   */
  public String cleanHtml(final String htmlString) {
    if (htmlString == null) {
      return null;
    }

    try {
      Document doc = Jsoup.parse(htmlString);
      convertListsToText(doc);
      doc.select("script, style, form, input, button, select, textarea").remove();

      Elements allElements = doc.body().getAllElements();
      for (Element element : allElements) {
        String tagName = element.tagName();

        // 일부 태그는 보존, 그 외는 unwrap
        if (!(isPreservedTag(tagName) || tagName.equals("body") || tagName.equals("#root"))) {
          element.unwrap();
        } else if (isPreservedTag(tagName)) {
          removeInlineAttributes(element);
        }
      }

      return doc.body().html();
    } catch (Exception e) {
      log.warn("Failed to clean content HTML: {}", e.getMessage());
      return htmlString;
    }
  }

  /** 보존할 태그인지 확인(테이블 태그들 및 img, pre) */
  private boolean isPreservedTag(final String tagName) {
    return switch (tagName) {
      case "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption", "img", "pre" -> true;
      default -> false;
    };
  }

  /** 인라인 속성 제거 (img는 src, alt만 유지) */
  private void removeInlineAttributes(final Element element) {
    if (element.tagName().equals("img")) {
      String src = element.attr("src");
      String alt = element.attr("alt");
      element.clearAttributes();
      if (!src.isEmpty()) {
        element.attr("src", src);
      }
      if (!alt.isEmpty()) {
        element.attr("alt", alt);
      }
    } else {
      element.clearAttributes();
    }
  }

  /** 리스트 태그를 재귀적으로 문자열로 변환 */
  private void convertListsToText(final Document doc) {
    // 최상위 리스트만 선택하여 재귀 처리
    Elements topLevelLists = doc.select("ul, ol");
    for (Element list : topLevelLists) {
      // 이미 처리되어 부모가 없거나 부모가 li이면 스킵
      if (list.parent() == null || list.parent().tagName().equals("li")) {
        continue;
      }

      String text = convertListsToTextInner(list, 0);
      // pre 태그로 감싸서 개행/공백 보존
      Element pre = new Element("pre");
      pre.text(text.stripTrailing());
      list.before(pre);
      list.remove();
    }
  }

  private String convertListsToTextInner(final Element list, final int depth) {
    StringBuilder result = new StringBuilder();
    String indent = "  ".repeat(depth);
    boolean isOrdered = list.tagName().equals("ol");

    Elements items = list.select("> li");
    int index = 1;

    for (Element li : items) {
      // li의 직접 텍스트만 추출 (하위 요소 제외)
      StringBuilder itemText = new StringBuilder();
      for (var node : li.childNodes()) {
        if (node instanceof org.jsoup.nodes.TextNode textNode) {
          String text = textNode.text().trim();
          if (!text.isEmpty()) {
            if (!itemText.isEmpty()) {
              itemText.append(" ");
            }
            itemText.append(text);
          }
        } else if (node instanceof Element elem && !elem.tagName().matches("ul|ol")) {
          String text = elem.text().trim();
          if (!text.isEmpty()) {
            if (!itemText.isEmpty()) {
              itemText.append(" ");
            }
            itemText.append(text);
          }
        }
      }

      // 리스트 아이템 추가
      String prefix = isOrdered ? (index++ + ". ") : "- ";
      result.append(indent).append(prefix).append(itemText).append("\n");

      // nested list 재귀 처리
      Elements nestedLists = li.select("> ul, > ol");
      for (Element nestedList : nestedLists) {
        result.append(convertListsToTextInner(nestedList, depth + 1));
      }
    }

    return result.toString();
  }
}
