package com.oaschool.web;

import com.oaschool.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
  private static final long MAX_REQUIREMENT_FILE_BYTES = 8L * 1024 * 1024;

  private final ProjectController projectController;

  public AiController(ProjectController projectController) {
    this.projectController = projectController;
  }

  @PostMapping("/wbs-generate")
  public ResponseEntity<Map<String, Object>> generateWbs(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    Object rawProjectId = body.containsKey("projectId") ? body.get("projectId") : body.get("project_id");
    if (rawProjectId == null || String.valueOf(rawProjectId).isBlank()) {
      throw new ApiException(422, "VALIDATION_ERROR", "projectId 不能为空");
    }
    return projectController.generateWbs(UUID.fromString(String.valueOf(rawProjectId)), body, request);
  }

  @PostMapping(value = "/wbs-generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> generateWbsWithFile(
      @RequestParam("projectId") String projectId,
      @RequestParam(value = "requirementText", required = false) String requirementText,
      @RequestPart(value = "file", required = false) MultipartFile file,
      HttpServletRequest request
  ) {
    if (projectId == null || projectId.isBlank()) {
      throw new ApiException(422, "VALIDATION_ERROR", "projectId 不能为空");
    }

    String text = requirementText == null ? "" : requirementText.trim();
    String fileText = extractRequirementFile(file);
    String combinedText = joinRequirementText(text, fileText);
    Map<String, Object> body = new HashMap<>();
    body.put("requirementText", combinedText);
    return projectController.generateWbs(UUID.fromString(projectId), body, request);
  }

  private String extractRequirementFile(MultipartFile file) {
    if (file == null || file.isEmpty()) return "";
    if (file.getSize() > MAX_REQUIREMENT_FILE_BYTES) {
      throw new ApiException(422, "VALIDATION_ERROR", "需求文件不能超过 8MB");
    }

    String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String lowerName = filename.toLowerCase(Locale.ROOT);
    try {
      byte[] bytes = file.getBytes();
      if (lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
      }
      if (lowerName.endsWith(".docx")) {
        return extractDocx(bytes);
      }
      if (lowerName.endsWith(".pdf")) {
        return extractPdf(bytes);
      }
    } catch (IOException error) {
      throw new ApiException(422, "VALIDATION_ERROR", "需求文件读取失败");
    }

    throw new ApiException(422, "VALIDATION_ERROR", "仅支持 txt、md、docx、pdf 需求文件");
  }

  private String extractDocx(byte[] bytes) {
    StringBuilder text = new StringBuilder();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!"word/document.xml".equals(entry.getName())) continue;
        Document document = secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(readCurrentZipEntry(zip)));
        NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
          Node node = paragraphs.item(i);
          if (node instanceof Element paragraph) {
            String paragraphText = paragraphText(paragraph);
            if (!paragraphText.isBlank()) text.append(paragraphText).append('\n');
          }
        }
      }
    } catch (IOException | ParserConfigurationException | SAXException error) {
      throw new ApiException(422, "VALIDATION_ERROR", "docx 文件解析失败");
    }

    String result = text.toString().trim();
    if (result.isBlank()) {
      throw new ApiException(422, "VALIDATION_ERROR", "docx 文件没有可读取的正文");
    }
    return result;
  }

  private String extractPdf(byte[] bytes) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      if (document.isEncrypted()) {
        throw new ApiException(422, "VALIDATION_ERROR", "暂不支持加密 PDF 文件");
      }
      String result = new PDFTextStripper().getText(document).trim();
      if (result.isBlank()) {
        throw new ApiException(422, "VALIDATION_ERROR", "PDF 文件没有可读取的正文");
      }
      return result;
    } catch (IOException error) {
      throw new ApiException(422, "VALIDATION_ERROR", "PDF 文件解析失败");
    }
  }

  private byte[] readCurrentZipEntry(ZipInputStream zip) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = zip.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory;
  }

  private String paragraphText(Element paragraph) {
    StringBuilder builder = new StringBuilder();
    NodeList textNodes = paragraph.getElementsByTagNameNS("*", "t");
    for (int i = 0; i < textNodes.getLength(); i++) {
      builder.append(textNodes.item(i).getTextContent());
    }
    return builder.toString().trim();
  }

  private String joinRequirementText(String typedText, String fileText) {
    if (typedText.isBlank()) return fileText;
    if (fileText.isBlank()) return typedText;
    return typedText + "\n\n" + fileText;
  }
}
