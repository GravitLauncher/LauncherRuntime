package pro.gravit.launcher.gui.resources;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxmlContractTest {
    private static final String FX_NAMESPACE = "http://javafx.com/fxml/1";
    private static final Pattern RESOURCE_KEY = Pattern.compile("%([A-Za-z0-9_.-]+)");

    @Test
    void fxmlHasNoEmbeddedControllersScriptsOrEventHandlersAndIncludesResolve()
            throws IOException, ParserConfigurationException, SAXException {
        Path runtime = Path.of("runtime");
        List<Path> files;
        try (Stream<Path> paths = Files.walk(runtime)) {
            files = paths.filter(path -> path.toString().endsWith(".fxml")).toList();
        }
        assertFalse(files.isEmpty(), "No runtime FXML files found");

        for (Path file : files) {
            Document document = parse(file);
            NodeList all = document.getElementsByTagName("*");
            for (int index = 0; index < all.getLength(); index++) {
                Element element = (Element) all.item(index);
                String localName = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
                assertFalse(localName.toLowerCase().contains("script"), file + " embeds a script");
                assertFalse(element.hasAttributeNS(FX_NAMESPACE, "controller"), file + " declares fx:controller");
                NamedNodeMap attributes = element.getAttributes();
                for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                    Node attribute = attributes.item(attributeIndex);
                    String name = attribute.getLocalName() == null ? attribute.getNodeName() : attribute.getLocalName();
                    assertFalse(name.matches("on[A-Z].*"), file + " declares event handler " + name);
                }
                if (FX_NAMESPACE.equals(element.getNamespaceURI()) && "include".equals(localName)) {
                    String source = element.getAttribute("source");
                    assertFalse(source.isBlank(), file + " has an fx:include without source");
                    Path included = file.getParent().resolve(source).normalize();
                    assertTrue(included.startsWith(runtime.normalize()) && Files.isRegularFile(included),
                            file + " includes missing resource " + source);
                }
            }
        }
    }

    @Test
    void englishBundleCoversFxmlResourceKeysAndOtherLocaleGapsAreReported() throws IOException,
            ParserConfigurationException, SAXException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("runtime"))) {
            for (Path file : paths.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                Document document = parse(file);
                NodeList all = document.getElementsByTagName("*");
                for (int index = 0; index < all.getLength(); index++) {
                    NamedNodeMap attributes = all.item(index).getAttributes();
                    for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                        var matcher = RESOURCE_KEY.matcher(attributes.item(attributeIndex).getNodeValue());
                        while (matcher.find()) keys.add(matcher.group(1));
                    }
                }
            }
        }

        Properties english = load(Path.of("runtime/runtime_en.properties"));
        List<String> missingEnglish = keys.stream().filter(key -> !english.containsKey(key)).sorted().toList();
        assertTrue(missingEnglish.isEmpty(), "Missing English FXML translations: " + missingEnglish);

        List<String> localeGaps = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Path.of("runtime"))) {
            for (Path bundle : paths.filter(path -> path.getFileName().toString().matches("runtime_[a-z]{2}\\.properties"))
                    .toList()) {
                if (bundle.equals(Path.of("runtime/runtime_en.properties"))) continue;
                Properties translations = load(bundle);
                keys.stream().filter(key -> !translations.containsKey(key))
                        .forEach(key -> localeGaps.add(bundle.getFileName() + ": " + key));
            }
        }
        if (!localeGaps.isEmpty()) {
            System.err.println("FXML translation gaps (add these keys to the listed bundles):\n"
                    + String.join("\n", localeGaps));
        }
    }

    private static Document parse(Path file) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }
}
