/*
 * Copyright (c) 2007 Henri Sivonen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package nu.validator.htmlparser.dom;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import nu.validator.htmlparser.common.DoctypeExpectation;
import nu.validator.htmlparser.common.DocumentModeHandler;
import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.impl.Tokenizer;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class implements an HTML5 parser that exposes data through the DOM 
 * interface. 
 * 
 * <p>By default, when using the constructor without arguments, the 
 * this parser treats XML 1.0-incompatible infosets as fatal errors. 
 * This corresponds to 
 * <code>FATAL</code> as the general XML violation policy. To make the parser 
 * support non-conforming HTML fully per the HTML 5 spec while on the other 
 * hand potentially violating the DOM API contract, set the general XML 
 * violation policy to <code>ALLOW</code>. This does not work with a standard 
 * DOM implementation. Handling all input without fatal errors and without 
 * violating the DOM API contract is possible by setting 
 * the general XML violation policy to <code>ALTER_INFOSET</code>. <em>This 
 * makes the parser non-conforming</em> but is probably the most useful 
 * setting for most applications.
 * 
 * <p>The doctype is not represented in the tree.
 * 
 * <p>The document mode is represented as user data <code>DocumentMode</code> 
 * object with the key <code>nu.validator.document-mode</code> on the document 
 * node. 
 * 
 * <p>The form pointer is also stored as user data with the key 
 * <code>nu.validator.form-pointer</code>.
 * 
 * @version $Id$
 * @author hsivonen
 */
public class HtmlDocumentBuilder extends DocumentBuilder {

    /**
     * @return the JAXP DOM implementation
     */
    private static DOMImplementation jaxpDOMImplementation() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        return builder.getDOMImplementation();
    }

    private final Tokenizer tokenizer;

    private final DOMTreeBuilder domTreeBuilder;

    private final DOMImplementation implementation;

    private EntityResolver entityResolver;

    /**
     * Instantiates the document builder with a specific DOM 
     * implementation and XML violation policy.
     * 
     * @param implementation
     *            the DOM implementation
     *            @param xmlPolicy the policy
     */
    public HtmlDocumentBuilder(DOMImplementation implementation,
            XmlViolationPolicy xmlPolicy) {
        this.implementation = implementation;
        this.domTreeBuilder = new DOMTreeBuilder(implementation);
        this.tokenizer = new Tokenizer(domTreeBuilder);
        this.tokenizer.setXmlnsPolicy(XmlViolationPolicy.ALTER_INFOSET);
        setXmlPolicy(xmlPolicy);
    }

    /**
     * Instantiates the document builder with a specific DOM implementation 
     * and fatal XML violation policy.
     * 
     * @param implementation
     *            the DOM implementation
     */
    public HtmlDocumentBuilder(DOMImplementation implementation) {
        this(implementation, XmlViolationPolicy.FATAL);
    }

    /**
     * Instantiates the document builder with the JAXP DOM implementation 
     * and fatal XML violation policy.
     */
    public HtmlDocumentBuilder() {
        this(XmlViolationPolicy.FATAL);
    }

    /**
     * Instantiates the document builder with the JAXP DOM implementation 
     * and a specific XML violation policy.
     *            @param xmlPolicy the policy
     */
    public HtmlDocumentBuilder(XmlViolationPolicy xmlPolicy) {
        this(jaxpDOMImplementation(), xmlPolicy);
    }

    /**
     * Returns the DOM implementation
     * @return the DOM implementation
     * @see javax.xml.parsers.DocumentBuilder#getDOMImplementation()
     */
    @Override
    public DOMImplementation getDOMImplementation() {
        return implementation;
    }

    /**
     * Returns <code>true</code>.
     * @return <code>true</code>
     * @see javax.xml.parsers.DocumentBuilder#isNamespaceAware()
     */
    @Override
    public boolean isNamespaceAware() {
        return true;
    }

    /**
     * Returns <code>false</code>
     * @return <code>false</code>
     * @see javax.xml.parsers.DocumentBuilder#isValidating()
     */
    @Override
    public boolean isValidating() {
        return false;
    }

    /**
     * For API compatibility.
     * @see javax.xml.parsers.DocumentBuilder#newDocument()
     */
    @Override
    public Document newDocument() {
        return implementation.createDocument(null, null, null);
    }

    /**
     * Parses a document from a SAX <code>InputSource</code>.
     * @param is the source
     * @return the doc
     * @see javax.xml.parsers.DocumentBuilder#parse(org.xml.sax.InputSource)
     */
    @Override
    public Document parse(InputSource is) throws SAXException, IOException {
        domTreeBuilder.setFragmentContext(null);
        tokenize(is);
        return domTreeBuilder.getDocument();
    }

    /**
     * Parses a document fragment from a SAX <code>InputSource</code>.
     * @param is the source
     * @param context the context element name
     * @return the doc
     * @throws IOException
     * @throws SAXException
     */
    public DocumentFragment parseFragment(InputSource is, String context)
            throws IOException, SAXException {
        domTreeBuilder.setFragmentContext(context);
        tokenize(is);
        return domTreeBuilder.getDocumentFragment();
    }

    /**
     * @param is
     * @throws SAXException
     * @throws IOException
     * @throws MalformedURLException
     */
    private void tokenize(InputSource is) throws SAXException, IOException,
            MalformedURLException {
        if (is == null) {
            throw new IllegalArgumentException("Null input.");
        }
        if (is.getByteStream() == null && is.getCharacterStream() == null) {
            String systemId = is.getSystemId();
            if (systemId == null) {
                throw new IllegalArgumentException(
                        "No byte stream, no character stream nor URI.");
            }
            if (entityResolver != null) {
                is = entityResolver.resolveEntity(is.getPublicId(), systemId);
            }
            if (is.getByteStream() == null || is.getCharacterStream() == null) {
                is = new InputSource();
                is.setSystemId(systemId);
                is.setByteStream(new URL(systemId).openStream());
            }
        }
        tokenizer.tokenize(is);
    }

    /**
     * Sets the entity resolver for URI-only inputs.
     * @param resolver the resolver
     * @see javax.xml.parsers.DocumentBuilder#setEntityResolver(org.xml.sax.EntityResolver)
     */
    @Override
    public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    /**
     * @see javax.xml.parsers.DocumentBuilder#setErrorHandler(org.xml.sax.ErrorHandler)
     */
    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {
        domTreeBuilder.setErrorHandler(errorHandler);
        tokenizer.setErrorHandler(errorHandler);
    }

    /**
     * Sets whether comment nodes appear in the tree.
     * @param ignoreComments <code>true</code> to ignore comments
     * @see nu.validator.htmlparser.impl.TreeBuilder#setIgnoringComments(boolean)
     */
    public void setIgnoringComments(boolean ignoreComments) {
        domTreeBuilder.setIgnoringComments(ignoreComments);
    }

    /**
     * Sets whether the parser considers scripting to be enabled for noscript treatment.
     * @param scriptingEnabled <code>true</code> to enable
     * @see nu.validator.htmlparser.impl.TreeBuilder#setScriptingEnabled(boolean)
     */
    public void setScriptingEnabled(boolean scriptingEnabled) {
        domTreeBuilder.setScriptingEnabled(scriptingEnabled);
    }

    /**
     * Toggles the checking of the NFC normalization of source.
     * @param enable <code>true</code> to check normalization
     * @see nu.validator.htmlparser.impl.Tokenizer#setCheckingNormalization(boolean)
     */
    public void setCheckingNormalization(boolean enable) {
        tokenizer.setCheckingNormalization(enable);
    }

    /**
     * Sets the policy for consecutive hyphens in comments.
     * @param commentPolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setCommentPolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setCommentPolicy(XmlViolationPolicy commentPolicy) {
        tokenizer.setCommentPolicy(commentPolicy);
    }

    /**
     * Sets the policy for non-XML characters except white space.
     * @param contentNonXmlCharPolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setContentNonXmlCharPolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setContentNonXmlCharPolicy(
            XmlViolationPolicy contentNonXmlCharPolicy) {
        tokenizer.setContentNonXmlCharPolicy(contentNonXmlCharPolicy);
    }

    /**
     * Sets the policy for non-XML white space.
     * @param contentSpacePolicy the policy
     * @see nu.validator.htmlparser.impl.Tokenizer#setContentSpacePolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setContentSpacePolicy(XmlViolationPolicy contentSpacePolicy) {
        tokenizer.setContentSpacePolicy(contentSpacePolicy);
    }


    /**
     * Whether the HTML 4 mode reports boolean attributes in a way that repeats
     * the name in the value.
     * @param html4ModeCompatibleWithXhtml1Schemata
     */
    public void setHtml4ModeCompatibleWithXhtml1Schemata(
            boolean html4ModeCompatibleWithXhtml1Schemata) {
        tokenizer.setHtml4ModeCompatibleWithXhtml1Schemata(html4ModeCompatibleWithXhtml1Schemata);
    }

    /**
     * @param mappingLangToXmlLang
     * @see nu.validator.htmlparser.impl.Tokenizer#setMappingLangToXmlLang(boolean)
     */
    public void setMappingLangToXmlLang(boolean mappingLangToXmlLang) {
        tokenizer.setMappingLangToXmlLang(mappingLangToXmlLang);
    }

    /**
     * @param namePolicy
     * @see nu.validator.htmlparser.impl.Tokenizer#setNamePolicy(nu.validator.htmlparser.common.XmlViolationPolicy)
     */
    public void setNamePolicy(XmlViolationPolicy namePolicy) {
        tokenizer.setNamePolicy(namePolicy);
    }

    /**
     * This is a catch-all convenience method for setting name, content space,
     * content non-XML char and comment policies in one go.
     * 
     * @param xmlPolicy
     */
    public void setXmlPolicy(XmlViolationPolicy xmlPolicy) {
        setNamePolicy(xmlPolicy);
        setContentSpacePolicy(xmlPolicy);
        setContentNonXmlCharPolicy(xmlPolicy);
        setCommentPolicy(xmlPolicy);
    }

    /**
     * Sets the doctype expectation.
     * 
     * @param doctypeExpectation
     *            the doctypeExpectation to set
     * @see nu.validator.htmlparser.impl.TreeBuilder#setDoctypeExpectation(nu.validator.htmlparser.common.DoctypeExpectation)
     */
    public void setDoctypeExpectation(DoctypeExpectation doctypeExpectation) {
        domTreeBuilder.setDoctypeExpectation(doctypeExpectation);
    }

    /**
     * Sets the document mode handler.
     * 
     * @param documentModeHandler
     * @see nu.validator.htmlparser.impl.TreeBuilder#setDocumentModeHandler(nu.validator.htmlparser.common.DocumentModeHandler)
     */
    public void setDocumentModeHandler(DocumentModeHandler documentModeHandler) {
        domTreeBuilder.setDocumentModeHandler(documentModeHandler);
    }
    
}
