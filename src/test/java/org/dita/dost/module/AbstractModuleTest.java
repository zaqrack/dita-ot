package org.dita.dost.module;

import org.apache.commons.io.FilenameUtils;
import org.custommonkey.xmlunit.XMLUnit;
import org.dita.dost.TestUtils;
import org.dita.dost.TestUtils.CachingLogger;
import org.dita.dost.TestUtils.CachingLogger.Message;
import org.dita.dost.pipeline.AbstractPipelineInput;
import org.dita.dost.util.Job;
import org.junit.After;
import org.junit.Before;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

public abstract class AbstractModuleTest {

    private final File resourceDir = TestUtils.getResourceDir(getClass());
    private final File expBaseDir = new File(resourceDir, "exp");
    private File tempBaseDir;
    private final DocumentBuilder builder;

    public AbstractModuleTest() {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private Document getDocument(final File file) {
        try {
            final Document doc = builder.parse(file);
            normalizeSpace(doc.getDocumentElement());
            return doc;
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void normalizeSpace(final Node node) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                final NodeList ns = node.getChildNodes();
                for (int i = 0; i < ns.getLength(); i++) {
                    normalizeSpace(ns.item(i));
                }
                break;
            case Node.TEXT_NODE:
                final String v = node.getNodeValue().replaceAll("\\s+", " ");
                node.setNodeValue(v);
                break;
        }
    }

    @Before
    public void setUp() throws Exception {
        tempBaseDir = TestUtils.createTempDir(getClass());
        TestUtils.copy(new File(resourceDir, "src"), tempBaseDir);

        TestUtils.resetXMLUnit();
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreComments(true);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.forceDelete(tempBaseDir);
    }

    public void test(final String testCase) {
        final String testName = FilenameUtils.getBaseName(testCase);
        final File tempDir = new File(tempBaseDir, testName);
        final File expDir = new File(expBaseDir, testName);
        try {
            final AbstractPipelineModule chunkModule = getModule(tempDir);
            final Job job = new Job(tempDir);
            chunkModule.setJob(job);
            final CachingLogger logger = new CachingLogger();
            chunkModule.setLogger(logger);

            final AbstractPipelineInput input = getAbstractPipelineInput();
            chunkModule.execute(input);

            compare(tempDir, expDir);

            for (Message m : logger.getMessages()) {
                assertEquals(false, m.level == Message.Level.ERROR);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    abstract AbstractPipelineInput getAbstractPipelineInput();

    abstract AbstractPipelineModule getModule(File tempDir);

    private void compare(File actDir, File expDir) throws SAXException, IOException {
        final File[] exps = expDir.listFiles();
        for (final File exp : exps) {
            if (exp.isDirectory()) {
                compare(new File(expDir, exp.getName()), new File(actDir, exp.getName()));
            } else if (exp.getName().equals(".job.xml")) {
                // skip
            } else {
                final Document expDoc = getDocument(exp);
                final Document actDoc = getDocument(new File(actDir, exp.getName()));
                assertXMLEqual("Comparing " + exp + " to " + new File(actDir, exp.getName()) + ":",
                        expDoc, actDoc);
            }
        }
    }

}
