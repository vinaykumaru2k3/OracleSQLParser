package com.migration.scanner.extractor;

import com.migration.scanner.model.Result;
import com.migration.scanner.sql.SqlDetector;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class XmlExtractor {
    private static final Set<String> SQL_TAGS = Set.of("select", "insert", "update", "delete", "sql", "query", "statement");
    private static final Set<String> SQL_ATTRIBUTES = Set.of("sql", "query", "value", "statement");

    private XmlExtractor() {
    }

    public static List<Result> extract(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Document document = builder.parse(new InputSource(reader));
            Element root = document.getDocumentElement();
            if (root == null) {
                return List.of();
            }

            List<Result> results = new ArrayList<>();
            scanElement(root, file, results);
            return results;
        }
    }

    private static void scanElement(Element element, Path file, List<Result> results) {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        if (SQL_TAGS.contains(tag)) {
            addXmlResult(file, element.getTextContent(), tag, results);
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            org.w3c.dom.Node attr = attributes.item(i);
            if (SQL_ATTRIBUTES.contains(attr.getNodeName().toLowerCase(Locale.ROOT))) {
                addXmlResult(file, attr.getNodeValue(), tag + "@" + attr.getNodeName(), results);
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element) {
                scanElement((Element) child, file, results);
            } else if (child instanceof CDATASection) {
                addXmlResult(file, child.getNodeValue(), tag + "#cdata", results);
            }
        }
    }

    private static void addXmlResult(Path file, String raw, String sourceTag, List<Result> results) {
        if (!SqlDetector.looksLikeSql(raw)) {
            return;
        }
        results.add(Result.build("XML", file.getFileName().toString(), file, 0, "XML:" + sourceTag, raw));
    }
}
