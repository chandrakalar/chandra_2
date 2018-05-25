package com.dxc.nifi.custom;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * 
 * @author cramakri
 *
 */

@SideEffectFree
@Tags({ "Replace XML Content", "XML", "Replace" })
@CapabilityDescription("Replace XML Content.")

public class ReplaceXMLContent extends AbstractProcessor {

	public static final Relationship SUCCESS = new Relationship.Builder().name("SUCCESS")
			.description("Succes relationship").build();

	public static final PropertyDescriptor QUERY_PARTIAL = new PropertyDescriptor.Builder().name("Partial Query")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor FROM_DATE = new PropertyDescriptor.Builder().name("From Date").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor XPATH = new PropertyDescriptor.Builder().name("XPath").required(true)
			.addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	public static final PropertyDescriptor CONF_FILE_NAME = new PropertyDescriptor.Builder().name("Conf File Name")
			.required(true).addValidator(StandardValidators.NON_EMPTY_VALIDATOR).build();

	@Override
	public void init(final ProcessorInitializationContext context) {

		Set<Relationship> relationships = new HashSet<>();
		relationships.add(SUCCESS);
		this.relationships = Collections.unmodifiableSet(relationships);

		List<PropertyDescriptor> properties = new ArrayList<>();
		properties.add(QUERY_PARTIAL);
		properties.add(FROM_DATE);
		properties.add(XPATH);
		properties.add(CONF_FILE_NAME);
		this.properties = Collections.unmodifiableList(properties);
	}

	@Override
	public void onTrigger(final ProcessContext context, ProcessSession session) throws ProcessException {
		// TODO Auto-generated method stub

		FlowFile flowFile = session.get();

		if (flowFile == null) {
			flowFile = session.create();
		}

		session.read(flowFile, new InputStreamCallback() {
			@Override
			public void process(InputStream in) throws IOException {
				try {

					// 1- Build the doc from the XML file
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					dbf.setValidating(false);
					dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
					Document doc = dbf.newDocumentBuilder().parse(in);

					// 2- Locate the node(s) with xpath
					XPath xpath = XPathFactory.newInstance().newXPath();
					// String xPath ="//*[contains(@key, 'sfdc.extractionSOQL')]";
					NodeList nodes = (NodeList) xpath.evaluate(context.getProperty(XPATH).getValue(), doc,
							XPathConstants.NODESET);

					// 3- Make the change on the selected nodes
					for (int idx = 0; idx < nodes.getLength(); idx++) {
						Node value = nodes.item(idx).getAttributes().getNamedItem("value");
						String val = value.getNodeValue();
						System.out.println("old query ::::" + val);
						String fromDate = context.getProperty(FROM_DATE).getValue();

						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
						sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
						String endDate = sdf.format(new Date());
						String dateSubQuery = context.getProperty(QUERY_PARTIAL).getValue()
								+ " AND LastModifiedDate &gt;= " + fromDate + " and LastModifiedDate&lt;= " + endDate;
						value.setNodeValue(dateSubQuery);
					}

					// 4- Save the result to a new XML doc
					/*
					 * Transformer xformer = TransformerFactory.newInstance().newTransformer();
					 * xformer.transform(new DOMSource(doc), new StreamResult(new
					 * File(context.getProperty(CONF_FILE_NAME).getValue())));
					 */

					writeDocument(doc, context.getProperty(CONF_FILE_NAME).getValue());

				} catch (Exception ex) {
					ex.printStackTrace();
					getLogger().error("Failed to replace xml content..");
				}
			}
		});

		session.transfer(flowFile, SUCCESS);
		getLogger().info("DONE !!!!!!!!!!!!!");
		System.out.println("DONE !!!!!!!!!!!!!");

	}

	private static void writeDocument(Document doc, String filename) throws IOException {
		Writer writer = null;
		try {
			/*
			 * Could extract "ls" to an instance attribute, so it can be reused.
			 */
			DOMImplementationLS ls = (DOMImplementationLS) DOMImplementationRegistry.newInstance()
					.getDOMImplementation("LS");
			writer = new OutputStreamWriter(new FileOutputStream(filename));
			LSOutput lsout = ls.createLSOutput();
			lsout.setCharacterStream(writer);
			/*
			 * If "doc" has been constructed by parsing an XML document, we should keep its
			 * encoding when serializing it; if it has been constructed in memory, its
			 * encoding has to be decided by the client code.
			 */
			lsout.setEncoding(doc.getXmlEncoding());
			LSSerializer serializer = ls.createLSSerializer();
			serializer.write(doc, lsout);
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	private Set<Relationship> relationships;
	private List<PropertyDescriptor> properties;

	@Override
	public Set<Relationship> getRelationships() {
		return relationships;
	}

	@Override
	public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
		return properties;
	}

}
